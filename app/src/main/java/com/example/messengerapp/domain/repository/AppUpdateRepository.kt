package com.example.messengerapp.domain.repository

import com.example.messengerapp.domain.model.AppUpdateDownload
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * アプリ自身の更新を扱う。
 * 他の Repository と同じく失敗は握りつぶさず [Result] で呼び出し元へ返す。
 */
interface AppUpdateRepository {

    /** Service と画面が同じ状態を見るための単一の情報源 */
    val download: StateFlow<AppUpdateDownload>

    /**
     * 最新 APK を取得し、デコードしてキャッシュディレクトリへ書き出す。
     * 進行状況は [download] に流れる。
     *
     * 中断された場合は [kotlinx.coroutines.CancellationException] がそのまま伝播する
     * （[Result] の失敗には変換しない）。
     *
     * @return 書き出した APK ファイル
     */
    suspend fun downloadLatestApkToCache(): Result<File>

    /** [AppUpdateDownload.Succeeded] / [AppUpdateDownload.Failed] を表示し終えた後に Idle へ戻す */
    fun consumeResult()
}
