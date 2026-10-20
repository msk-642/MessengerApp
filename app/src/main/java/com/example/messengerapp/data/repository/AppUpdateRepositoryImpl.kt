package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.UpdateApi
import com.example.messengerapp.di.ApkCacheDir
import com.example.messengerapp.domain.model.AppUpdateDownload
import com.example.messengerapp.domain.repository.AppUpdateRepository
import com.example.messengerapp.util.AppUpdateResponseReader
import com.example.messengerapp.util.ErrorMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val updateApi: UpdateApi,
    // Context ではなく解決済みのディレクトリを受け取り、このクラスを Android 非依存に保つ
    @ApkCacheDir private val apkCacheDir: File
) : AppUpdateRepository {

    private val _download = MutableStateFlow<AppUpdateDownload>(AppUpdateDownload.Idle)
    override val download: StateFlow<AppUpdateDownload> = _download.asStateFlow()

    /** 進捗の間引き用。同時に走るダウンロードは 1 本だけなので単純な変数で足りる */
    private var lastPublishedPercent = NO_PERCENT
    private var lastPublishedAt = 0L

    override suspend fun downloadLatestApkToCache(): Result<File> {
        lastPublishedPercent = NO_PERCENT
        lastPublishedAt = 0L
        _download.value = AppUpdateDownload.Running(downloadedBytes = 0, totalBytes = UNKNOWN_SIZE)

        return try {
            val apk = fetchApk()
            _download.value = AppUpdateDownload.Succeeded(apk)
            Result.success(apk)
        } catch (e: CancellationException) {
            // runCatching だとキャンセルまで握ってしまうため、ここだけは必ず素通しする
            _download.value = AppUpdateDownload.Idle
            throw e
        } catch (e: Throwable) {
            _download.value = AppUpdateDownload.Failed(ErrorMessages.from(e))
            Result.failure(e)
        }
    }

    override fun consumeResult() {
        val current = _download.value
        if (current is AppUpdateDownload.Succeeded || current is AppUpdateDownload.Failed) {
            _download.value = AppUpdateDownload.Idle
        }
    }

    // デコードとファイル書き込みはブロッキングなので、通信と違い明示的に IO へ逃がす
    private suspend fun fetchApk(): File = withContext(Dispatchers.IO) {
        updateApi.downloadLatestApk().use { body ->
            val totalBytes = body.contentLength()
            val source = body.source().counting { readBytes ->
                // ソケットの read 自体は割り込めないので、チャンクの区切りで中断を判定する
                ensureActive()
                publishProgress(readBytes, totalBytes)
            }
            AppUpdateResponseReader.read(source, File(apkCacheDir, APK_FILE_NAME))
        }.apk.file
    }

    /** 読み出したバイト数を数えながら下流へ流す */
    private fun BufferedSource.counting(onRead: (Long) -> Unit): BufferedSource =
        object : ForwardingSource(this) {
            private var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read != -1L) {
                    total += read
                    onRead(total)
                }
                return read
            }
        }.buffer()

    /**
     * 進捗を流す。64KB ごとにそのまま流すと 100MB 級で 1,600 回を超え、
     * 通知がレート制限で捨てられるうえ電力の無駄になるので間引く。
     */
    private fun publishProgress(readBytes: Long, totalBytes: Long) {
        val percent =
            if (totalBytes > 0) ((readBytes * 100) / totalBytes).toInt() else NO_PERCENT
        val now = System.currentTimeMillis()
        if (percent == lastPublishedPercent && now - lastPublishedAt < MIN_PUBLISH_INTERVAL_MS) {
            return
        }
        lastPublishedPercent = percent
        lastPublishedAt = now
        _download.value = AppUpdateDownload.Running(readBytes, totalBytes)
    }

    private companion object {
        /** 常に最新の 1 つだけを保持すればよいので固定名で上書きする */
        const val APK_FILE_NAME = "app-latest.apk"

        const val UNKNOWN_SIZE = -1L
        const val NO_PERCENT = -1
        const val MIN_PUBLISH_INTERVAL_MS = 250L
    }
}
