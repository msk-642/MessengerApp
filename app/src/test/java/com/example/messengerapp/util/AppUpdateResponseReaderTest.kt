package com.example.messengerapp.util

import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

class AppUpdateResponseReaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `writes the apk and reports the result code`() {
        val apk = apkLikeBytes()
        val destination = destination()

        val response = AppUpdateResponseReader.read(responseOf("0", apk.toBase64()), destination)

        assertEquals("0", response.resultCode)
        assertArrayEquals(apk, destination.readBytes())
        assertEquals(apk.size.toLong(), response.apk.byteCount)
        assertEquals(sha256(apk), response.apk.sha256)
    }

    @Test
    fun `fails without writing anything when the result code is not success`() {
        val destination = destination()

        val error = assertThrows(AppUpdateResultCodeException::class.java) {
            AppUpdateResponseReader.read(responseOf("1", apkLikeBytes().toBase64()), destination)
        }

        assertEquals("1", error.resultCode)
        // エラーコードを見た時点で打ち切るので、APK のデコードには入らない
        assertFalse(destination.exists())
        assertFalse(partOf(destination).exists())
    }

    @Test
    fun `accepts whitespace and line breaks between tokens`() {
        val apk = apkLikeBytes()
        val json = "{\n  \"resultCode\" : \"0\" ,\n  \"apkBase64\" : \"" + apk.toBase64() + "\"\n}"
        val destination = destination()

        AppUpdateResponseReader.read(json.toSource(), destination)

        assertArrayEquals(apk, destination.readBytes())
    }

    @Test
    fun `decodes base64 whose slashes are escaped`() {
        // Base64 の / を \/ とエスケープして出力する JSON 実装がある
        val apk = apkLikeBytes()
        val base64 = apk.toBase64()
        assertTrue("テストデータの Base64 に / が含まれていない", base64.contains("/"))
        // 逆スラッシュは文字コードから組み立てる（Kotlin のエスケープ記法を避けるため）
        val escaped = base64.replace("/", "" + 92.toChar() + "/")
        val destination = destination()

        AppUpdateResponseReader.read(responseJson("0", escaped).toSource(), destination)

        assertArrayEquals(apk, destination.readBytes())
    }

    @Test
    fun `fails and leaves no file when the response is cut off midway`() {
        // Base64 の途中で切れたレスポンス。
        // 末尾のパディング（=）まで届いていると Base64 デコーダはそこで正常終了してしまうため、
        // 「閉じ引用符だけが無い」ではなく値そのものを途中で切る。
        val base64 = apkLikeBytes().toBase64()
        val cutLength = base64.length / 2 / 4 * 4 // 4 の倍数で切り、端数バイトによる失敗と区別する
        val truncated = "{\"resultCode\":\"0\",\"apkBase64\":\"" + base64.substring(0, cutLength)
        val destination = destination()

        assertThrows(IOException::class.java) {
            AppUpdateResponseReader.read(truncated.toSource(), destination)
        }

        assertFalse(destination.exists())
        assertFalse(partOf(destination).exists())
    }

    @Test
    fun `fails when the apkBase64 field is missing`() {
        val destination = destination()

        assertThrows(IOException::class.java) {
            AppUpdateResponseReader.read("{\"resultCode\":\"0\"}".toSource(), destination)
        }

        assertFalse(destination.exists())
    }

    @Test
    fun `restores the real debug apk byte for byte`() {
        // generateMockUpdatePayload が未実行のときはスキップする
        val mockUpdateDir = System.getProperty("mockUpdateDir")?.let(::File)
        val responseFile = mockUpdateDir?.resolve("response.json")
        Assume.assumeTrue(
            "模擬レスポンスが未生成のためスキップ（:app:generateMockUpdatePayload を実行すると検証される）",
            responseFile != null && responseFile.exists()
        )
        requireNotNull(mockUpdateDir)
        requireNotNull(responseFile)

        val originalApk = mockUpdateDir.resolve("app-debug.apk")
        val destination = destination()

        val response = responseFile.source().buffer().use { source ->
            AppUpdateResponseReader.read(source, destination)
        }

        assertEquals("0", response.resultCode)
        assertEquals(originalApk.length(), response.apk.byteCount)
        assertEquals(sha256(originalApk), response.apk.sha256)
        assertTrue("復元した APK が元の APK と一致しない", contentEquals(originalApk, destination))
        // ZIP（APK）のシグネチャで始まっていること
        assertArrayEquals(
            byteArrayOf(0x50, 0x4B, 0x03, 0x04),
            destination.inputStream().use { it.readNBytes(4) }
        )
    }

    // --- テスト用ヘルパー ---

    private fun destination(): File = File(temporaryFolder.root, "app-latest.apk")

    private fun partOf(file: File): File = File(file.parentFile, "${file.name}.part")

    private fun responseJson(resultCode: String, base64: String): String =
        "{\"resultCode\":\"" + resultCode + "\",\"apkBase64\":\"" + base64 + "\"}"

    private fun responseOf(resultCode: String, base64: String): BufferedSource =
        responseJson(resultCode, base64).toSource()

    private fun String.toSource(): BufferedSource = Buffer().writeUtf8(this)

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    /** APK（ZIP）のシグネチャで始まる、決定的なダミーバイト列 */
    private fun apkLikeBytes(): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1024) { (it * 31 % 251).toByte() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 100MB 級のファイルを丸ごとメモリへ載せずにダイジェストを取る */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 100MB 級のファイルを丸ごとメモリへ載せずに内容を比較する */
    private fun contentEquals(left: File, right: File): Boolean {
        if (left.length() != right.length()) return false
        left.inputStream().buffered().use { a ->
            right.inputStream().buffered().use { b ->
                val bufferA = ByteArray(BUFFER_SIZE)
                val bufferB = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = a.readNBytes(bufferA, 0, BUFFER_SIZE)
                    b.readNBytes(bufferB, 0, BUFFER_SIZE)
                    if (read == 0) return true
                    if (!java.util.Arrays.equals(bufferA, 0, read, bufferB, 0, read)) return false
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
