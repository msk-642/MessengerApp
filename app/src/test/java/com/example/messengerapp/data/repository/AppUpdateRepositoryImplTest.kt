package com.example.messengerapp.data.repository

import app.cash.turbine.test
import com.example.messengerapp.data.remote.UpdateApi
import com.example.messengerapp.domain.model.AppUpdateDownload
import com.example.messengerapp.util.AppUpdateResultCodeException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
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

    @Test
    fun `download state ends at Succeeded after reporting progress`() = runTest {
        val apk = apkLikeBytes()
        coEvery { updateApi.downloadLatestApk() } returns responseOf("0", apk)

        repository.download.test {
            assertEquals(AppUpdateDownload.Idle, awaitItem())

            repository.downloadLatestApkToCache().getOrThrow()

            // 進捗は間引かれるため件数は保証しないが、Running を経て Succeeded で終わること
            var state = awaitItem()
            while (state is AppUpdateDownload.Running) state = awaitItem()
            assertTrue(state is AppUpdateDownload.Succeeded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `percent is computed from the content length`() = runTest {
        val apk = apkLikeBytes()
        val body = responseOf("0", apk)
        val contentLength = body.contentLength()
        assertTrue("Content-Length が無いと計算できない", contentLength > 0)
        coEvery { updateApi.downloadLatestApk() } returns body

        val seen = mutableListOf<AppUpdateDownload.Running>()
        repository.download.test {
            awaitItem()
            repository.downloadLatestApkToCache().getOrThrow()
            var state = awaitItem()
            while (state is AppUpdateDownload.Running) {
                seen += state
                state = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }

        val last = seen.last()
        assertEquals(contentLength, last.totalBytes)
        assertEquals(((last.downloadedBytes * 100) / contentLength).toInt(), last.percent)
    }

    @Test
    fun `percent is null when the content length is unknown`() = runTest {
        // Content-Length が無いレスポンス（gzip 転送などで起こる）
        coEvery { updateApi.downloadLatestApk() } returns unknownLengthResponseOf("0", apkLikeBytes())

        val seen = mutableListOf<AppUpdateDownload.Running>()
        repository.download.test {
            awaitItem()
            repository.downloadLatestApkToCache().getOrThrow()
            var state = awaitItem()
            while (state is AppUpdateDownload.Running) {
                seen += state
                state = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(seen.isNotEmpty())
        // 総量が分からないので、呼び出し側は不定表示にするしかない
        assertTrue(seen.all { it.totalBytes < 0 })
        assertTrue(seen.all { it.percent == null })
    }

    @Test
    fun `download state becomes Failed when the api fails`() = runTest {
        coEvery { updateApi.downloadLatestApk() } throws IOException("Network error")

        repository.downloadLatestApkToCache()

        assertTrue(repository.download.value is AppUpdateDownload.Failed)
    }

    @Test
    fun `cancellation propagates instead of becoming a failed Result`() = runTest {
        coEvery { updateApi.downloadLatestApk() } returns endlessResponse()
        val job = async(Dispatchers.Default) { repository.downloadLatestApkToCache() }
        awaitWritingStarted()

        job.cancel()
        job.join()

        // runCatching のままだと CancellationException が Result.failure に化けてしまう
        var propagated = false
        try {
            job.await()
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
        assertEquals(AppUpdateDownload.Idle, repository.download.value)
    }

    @Test
    fun `cancellation leaves no apk and no part file behind`() = runTest {
        coEvery { updateApi.downloadLatestApk() } returns endlessResponse()
        val job = async(Dispatchers.Default) { repository.downloadLatestApkToCache() }
        awaitWritingStarted()

        job.cancel()
        // join で実際にほどけるまで待つ。await はクリーンアップ完了前に投げうる
        job.join()

        assertFalse(apkFile().exists())
        assertFalse(partFile().exists())
    }

    // --- テスト用ヘルパー ---

    private fun responseOf(resultCode: String, apk: ByteArray): ResponseBody {
        val base64 = Base64.getEncoder().encodeToString(apk)
        return ("{\"resultCode\":\"" + resultCode + "\",\"apkBase64\":\"" + base64 + "\"}")
            .toResponseBody("application/json".toMediaType())
    }

    /**
     * APK の書き込みが始まる（.part が出来る）まで待つ。
     * レスポンスの先頭を覗いている段階でキャンセルすると .part がそもそも作られず、
     * 「中間ファイルが残らない」ことを検証したつもりで何も検証できていない状態になる。
     */
    private suspend fun awaitWritingStarted() = withContext(Dispatchers.Default) {
        val part = partFile()
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (!part.exists() && System.currentTimeMillis() < deadline) {
            delay(AWAIT_POLL_MS)
        }
        assertTrue("ダウンロードの書き込みが始まらなかった", part.exists())
    }

    private fun apkFile(): File = File(temporaryFolder.root, "app-latest.apk")

    private fun partFile(): File = File(temporaryFolder.root, "app-latest.apk.part")

    /**
     * 終わらない Base64 を流し続けるレスポンス。
     * キャンセルでしか止まらないので、「ダウンロード途中での中断」を決定的に再現できる。
     */
    private fun endlessResponse(): ResponseBody {
        val source = object : Source {
            private var prefixSent = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!prefixSent) {
                    prefixSent = true
                    val prefix = "{\"resultCode\":\"0\",\"apkBase64\":\""
                    sink.writeUtf8(prefix)
                    return prefix.length.toLong()
                }
                Thread.sleep(1) // 無駄に CPU を焼かないための間合い
                val chunk = "QUJD".repeat(256) // 4 の倍数なので Base64 として妥当
                sink.writeUtf8(chunk)
                return chunk.length.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }
        return source.buffer().asResponseBody("application/json".toMediaType(), -1L)
    }

    /** Content-Length を返さない ResponseBody（gzip 転送時などを模す） */
    private fun unknownLengthResponseOf(resultCode: String, apk: ByteArray): ResponseBody {
        val delegate = responseOf(resultCode, apk)
        return object : ResponseBody() {
            override fun contentLength(): Long = -1L
            override fun contentType() = delegate.contentType()
            override fun source() = delegate.source()
        }
    }

    /** APK（ZIP）のシグネチャで始まる、決定的なダミーバイト列 */
    private fun apkLikeBytes(): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1024) { (it * 31 % 251).toByte() }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5_000L
        const val AWAIT_POLL_MS = 10L
    }
}
