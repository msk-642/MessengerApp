package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.UpdateApi
import com.example.messengerapp.util.AppUpdateResultCodeException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Base64

class AppUpdateRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val updateApi: UpdateApi = mockk()

    // root は Rule 適用後にしか使えないため、最初に参照された時点で組み立てる
    private val repository by lazy { AppUpdateRepositoryImpl(updateApi, temporaryFolder.root) }

    @Test
    fun `downloadLatestApkToCache writes the decoded apk into the cache directory`() = runTest {
        val apk = apkLikeBytes()
        coEvery { updateApi.downloadLatestApk() } returns responseOf("0", apk)

        val file = repository.downloadLatestApkToCache().getOrThrow()

        assertEquals(File(temporaryFolder.root, "app-latest.apk"), file)
        assertArrayEquals(apk, file.readBytes())
    }

    @Test
    fun `downloadLatestApkToCache returns failure when the api fails`() = runTest {
        coEvery { updateApi.downloadLatestApk() } throws IOException("Network error")

        val result = repository.downloadLatestApkToCache()

        // 失敗を握りつぶさず、そのまま呼び出し元へ返すこと
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `downloadLatestApkToCache returns failure and leaves no file on an error result code`() =
        runTest {
            coEvery { updateApi.downloadLatestApk() } returns responseOf("1", apkLikeBytes())

            val result = repository.downloadLatestApkToCache()

            assertEquals("1", (result.exceptionOrNull() as AppUpdateResultCodeException).resultCode)
            assertFalse(File(temporaryFolder.root, "app-latest.apk").exists())
        }

    // --- テスト用ヘルパー ---

    private fun responseOf(resultCode: String, apk: ByteArray): ResponseBody {
        val base64 = Base64.getEncoder().encodeToString(apk)
        return ("{\"resultCode\":\"" + resultCode + "\",\"apkBase64\":\"" + base64 + "\"}")
            .toResponseBody("application/json".toMediaType())
    }

    /** APK（ZIP）のシグネチャで始まる、決定的なダミーバイト列 */
    private fun apkLikeBytes(): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1024) { (it * 31 % 251).toByte() }
}
