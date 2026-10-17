package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.UpdateApi
import com.example.messengerapp.di.ApkCacheDir
import com.example.messengerapp.domain.repository.AppUpdateRepository
import com.example.messengerapp.util.AppUpdateResponseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val updateApi: UpdateApi,
    // Context ではなく解決済みのディレクトリを受け取り、このクラスを Android 非依存に保つ
    @ApkCacheDir private val apkCacheDir: File
) : AppUpdateRepository {

    override suspend fun downloadLatestApkToCache(): Result<File> = runCatching {
        // デコードとファイル書き込みはブロッキングなので、通信と違い明示的に IO へ逃がす
        withContext(Dispatchers.IO) {
            updateApi.downloadLatestApk().use { body ->
                AppUpdateResponseReader.read(
                    source = body.source(),
                    destination = File(apkCacheDir, APK_FILE_NAME)
                )
            }.apk.file
        }
    }

    private companion object {
        /** 常に最新の 1 つだけを保持すればよいので固定名で上書きする */
        const val APK_FILE_NAME = "app-latest.apk"
    }
}
