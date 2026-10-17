package com.example.messengerapp.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Base64

/** [ApkBase64Decoder.decodeToFile] の結果。書き出したファイルと、その実測サイズ・ダイジェスト */
data class ApkWriteResult(
    val file: File,
    val byteCount: Long,
    val sha256: String
)

/**
 * サーバから受け取った Base64 文字列を APK ファイルへ復元する。
 *
 * APK は 100MB 級になりうるため、文字列やバイト配列としてメモリへ載せず、
 * 入力ストリームから出力ファイルまでを一本のストリームで流す。メモリ使用量はサイズに依存しない。
 *
 * [android.util.Base64] ではなく [java.util.Base64] を使っている。
 * ストリーム復号（[Base64.Decoder.wrap]）が使えることと、JVM ユニットテストで動かせることが理由。
 */
object ApkBase64Decoder {

    /**
     * [source] の Base64 をデコードして [destination] へ書き出す。
     * 処理の途中で失敗した場合、[destination] は作られない。
     *
     * @throws IOException 読み書きに失敗した場合
     */
    @Throws(IOException::class)
    fun decodeToFile(source: InputStream, destination: File): ApkWriteResult {
        destination.parentFile?.mkdirs()

        // 通信が途中で切れた APK が完成品として残らないよう、いったん .part へ書いて最後に差し替える
        val working = File(destination.parentFile, destination.name + PART_SUFFIX)
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)

        val byteCount = try {
            // 改行入り（MIME 形式）の Base64 も受け付けられるよう getMimeDecoder を使う。
            // デコーダは 1 バイトずつ read() を呼ぶため、間にバッファを挟まないと
            // source 側の呼び出し回数が復元後のバイト数ぶんだけ発生して極端に遅くなる
            Base64.getMimeDecoder().wrap(source.buffered(BUFFER_SIZE)).use { decoded ->
                DigestOutputStream(working.outputStream().buffered(), digest).use { output ->
                    decoded.copyTo(output, BUFFER_SIZE)
                }
            }
        } catch (e: IOException) {
            working.delete()
            throw e
        }

        // renameTo は既存ファイルがあると失敗する環境があるため、先に消しておく
        destination.delete()
        if (!working.renameTo(destination)) {
            working.delete()
            throw IOException("APK を ${destination.absolutePath} へ移動できませんでした")
        }

        return ApkWriteResult(
            file = destination,
            byteCount = byteCount,
            sha256 = digest.digest().toHex()
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** 書き込み完了前の中間ファイルに付ける拡張子 */
    private const val PART_SUFFIX = ".part"

    private const val DIGEST_ALGORITHM = "SHA-256"

    private const val BUFFER_SIZE = 64 * 1024
}
