package com.example.messengerapp.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

class ApkBase64DecoderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `decodes base64 back to the original bytes`() {
        val original = apkLikeBytes()
        val destination = destination()

        val result = ApkBase64Decoder.decodeToFile(original.toBase64Stream(), destination)

        assertArrayEquals(original, destination.readBytes())
        assertEquals(destination, result.file)
        assertEquals(original.size.toLong(), result.byteCount)
    }

    @Test
    fun `creates the destination directory when it does not exist yet`() {
        val destination = File(temporaryFolder.root, "update/app-latest.apk")
        assertFalse(destination.parentFile!!.exists())

        ApkBase64Decoder.decodeToFile(apkLikeBytes().toBase64Stream(), destination)

        assertTrue(destination.exists())
    }

    @Test
    fun `accepts MIME formatted base64 that contains line breaks`() {
        val original = apkLikeBytes()
        val mime = Base64.getMimeEncoder().encodeToString(original)
        // 改行入りであることを前提条件として先に確認しておく
        assertTrue(mime.contains("\n"))
        val destination = destination()

        ApkBase64Decoder.decodeToFile(ByteArrayInputStream(mime.toByteArray()), destination)

        assertArrayEquals(original, destination.readBytes())
    }

    @Test
    fun `reports the sha256 and the size of the written file`() {
        val original = apkLikeBytes()
        val destination = destination()

        val result = ApkBase64Decoder.decodeToFile(original.toBase64Stream(), destination)

        assertEquals(sha256(original), result.sha256)
        assertEquals(original.size.toLong(), result.byteCount)
    }

    @Test
    fun `leaves no file behind when reading fails midway`() {
        val destination = destination()
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("接続が切れました")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("接続が切れました")
        }

        assertThrows(IOException::class.java) {
            ApkBase64Decoder.decodeToFile(failing, destination)
        }

        // 完成品も、書き込み途中の中間ファイルも残らないこと
        assertFalse(destination.exists())
        assertFalse(File(destination.parentFile, "${destination.name}.part").exists())
    }

    // --- テスト用ヘルパー ---

    private fun destination(): File = File(temporaryFolder.root, "app-latest.apk")

    /** APK（ZIP）のシグネチャで始まる、決定的なダミーバイト列 */
    private fun apkLikeBytes(): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1024) { (it * 31 % 251).toByte() }

    private fun ByteArray.toBase64Stream(): InputStream =
        ByteArrayInputStream(Base64.getEncoder().encode(this))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
