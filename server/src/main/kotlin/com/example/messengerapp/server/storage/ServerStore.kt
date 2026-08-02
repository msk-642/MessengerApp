package com.example.messengerapp.server.storage

import com.example.messengerapp.server.model.ServerState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * サーバの全データを保持し、変更のたびに JSON ファイルへ永続化する。
 *
 * ファイル I/O はこのクラスに閉じているため、後で SQLite 等へ差し替える場合も
 * ここだけを書き換えればルーティング側へは波及しない。
 */
class ServerStore(private val file: File) {

    private val logger = LoggerFactory.getLogger(ServerStore::class.java)
    private val mutex = Mutex()
    private var state: ServerState = load()

    /** 状態を読むだけの操作 */
    suspend fun <T> read(block: (ServerState) -> T): T = mutex.withLock { block(state) }

    /**
     * 状態を更新する操作。[block] は「次の状態」と「呼び出し元へ返す値」の組を返す。
     * 更新が成功した場合のみファイルへ書き出す。
     */
    suspend fun <T> mutate(block: (ServerState) -> Pair<ServerState, T>): T = mutex.withLock {
        val (next, result) = block(state)
        state = next
        persist(next)
        result
    }

    private fun load(): ServerState {
        if (!file.exists()) {
            logger.info("DB ファイルが無いため初期データを生成します: {}", file.absolutePath)
            return SeedData.create().also { persist(it) }
        }
        return try {
            json.decodeFromString<ServerState>(file.readText()).also {
                logger.info("DB ファイルを読み込みました: {}", file.absolutePath)
            }
        } catch (e: Exception) {
            // 壊れたファイルで起動不能にならないよう、退避してから作り直す
            val backup = File(file.parentFile, "${file.name}.broken")
            logger.error("DB ファイルの読み込みに失敗したため初期化します。退避先: {}", backup.absolutePath, e)
            file.copyTo(backup, overwrite = true)
            SeedData.create().also { persist(it) }
        }
    }

    private fun persist(state: ServerState) {
        file.parentFile?.mkdirs()
        // 書き込み中の異常終了で本体を壊さないよう、一時ファイルへ書いてから置き換える
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(state))
        temp.copyTo(file, overwrite = true)
        temp.delete()
    }

    private companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
