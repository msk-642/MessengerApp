package com.example.messengerapp.domain.repository

import java.io.File

/**
 * アプリ自身の更新を扱う。
 * 他の Repository と同じく失敗は握りつぶさず [Result] で呼び出し元へ返す。
 */
interface AppUpdateRepository {

    /**
     * 最新 APK を取得し、デコードしてキャッシュディレクトリへ書き出す。
     *
     * @return 書き出した APK ファイル
     */
    suspend fun downloadLatestApkToCache(): Result<File>
}
