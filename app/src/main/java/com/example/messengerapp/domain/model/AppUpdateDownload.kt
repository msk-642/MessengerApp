package com.example.messengerapp.domain.model

import java.io.File

/**
 * APK ダウンロードの進行状況。
 *
 * Foreground Service と画面の双方がこれを購読する。状態をリポジトリに置いているため、
 * 画面の破棄・再生成を跨いでも購読し直すだけで現在の進捗が取れる。
 */
sealed interface AppUpdateDownload {

    data object Idle : AppUpdateDownload

    data class Running(
        val downloadedBytes: Long,
        /** レスポンスの Content-Length。不明な場合は -1 */
        val totalBytes: Long
    ) : AppUpdateDownload {

        /** 総量が不明なら null。呼び出し側は不定（インジケータ）表示にする */
        val percent: Int?
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else null
    }

    data class Succeeded(val apk: File) : AppUpdateDownload

    data class Failed(val message: String) : AppUpdateDownload
}
