package com.example.messengerapp.util

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream

/** サーバが成功以外の結果コードを返したことを表す */
class AppUpdateResultCodeException(val resultCode: String) :
    IOException("サーバがエラーコードを返しました: $resultCode")

/** [AppUpdateResponseReader.read] の結果 */
data class AppUpdateResponse(
    val resultCode: String,
    val apk: ApkWriteResult
)

/**
 * アップデート取得 API のレスポンスを、受け取りながら APK ファイルへ復元する。
 *
 * レスポンスはこの形を前提とする:
 * ```
 * {"resultCode":"0","apkBase64":"UEsDBBQ....（約100MB）"}
 * ```
 *
 * apkBase64 が 100MB 級になるため、JSON を丸ごとパースすることはできない
 * （値が String へ展開された時点でヒープが尽きる）。
 * そこで先頭の小さな部分だけを覗いて resultCode と値の開始位置を確定し、
 * 残りはストリームのままデコードしてファイルへ流す。メモリ使用量は応答サイズに依存しない。
 *
 * この方式が成立する条件として **apkBase64 を JSON の最後のフィールドに置く**ことをサーバと取り決めている。
 * 後ろに別のフィールドがあると、それを読むために本体全体をバッファすることになる。
 */
object AppUpdateResponseReader {

    /** 処理成功を表す結果コード */
    const val RESULT_CODE_SUCCESS = "0"

    /**
     * [source] を走査し、apkBase64 をデコードして [destination] へ書き出す。
     *
     * @throws AppUpdateResultCodeException resultCode が [RESULT_CODE_SUCCESS] 以外だった場合
     * @throws IOException 応答の形式が想定と異なる場合、または読み書きに失敗した場合
     */
    @Throws(IOException::class)
    fun read(source: BufferedSource, destination: File): AppUpdateResponse {
        val head = source.peekHead()

        val resultCode = head.jsonStringValueOf(FIELD_RESULT_CODE)
            ?: throw IOException("レスポンスから $FIELD_RESULT_CODE を読み取れませんでした")
        // 失敗時は APK のダウンロードに入らず、ここで打ち切る
        if (resultCode != RESULT_CODE_SUCCESS) throw AppUpdateResultCodeException(resultCode)

        val valueStart = head.jsonStringValueStartOf(FIELD_APK_BASE64)
            ?: throw IOException(
                "レスポンスの先頭 $MAX_HEAD_BYTES バイト以内に $FIELD_APK_BASE64 が見つかりませんでした"
            )

        source.skip(valueStart.toLong())
        val apk = ApkBase64Decoder.decodeToFile(JsonStringValueStream(source), destination)
        return AppUpdateResponse(resultCode = resultCode, apk = apk)
    }

    /** 先頭を最大 [MAX_HEAD_BYTES] バイトだけ消費せずに覗く */
    private fun BufferedSource.peekHead(): ByteArray {
        val head = Buffer()
        peek().use { peeked ->
            while (head.size < MAX_HEAD_BYTES) {
                if (peeked.read(head, MAX_HEAD_BYTES - head.size) == -1L) break
            }
        }
        return head.readByteArray()
    }

    /** `"field"` に続く文字列値の、開き引用符の次のバイト位置。見つからなければ null */
    private fun ByteArray.jsonStringValueStartOf(field: String): Int? {
        val key = byteArrayOf(QUOTE) + field.toByteArray(Charsets.UTF_8) + byteArrayOf(QUOTE)
        val keyAt = indexOfSubArray(key)
        if (keyAt < 0) return null

        var i = skipWhitespaceFrom(keyAt + key.size)
        if (i >= size || this[i] != COLON) return null
        i = skipWhitespaceFrom(i + 1)
        if (i >= size || this[i] != QUOTE) return null
        return i + 1
    }

    /** `"field"` の文字列値。先頭バッファ内で閉じていなければ null */
    private fun ByteArray.jsonStringValueOf(field: String): String? {
        var i = jsonStringValueStartOf(field) ?: return null
        val value = ByteArrayOutputStream()
        while (i < size) {
            when (this[i]) {
                QUOTE -> return String(value.toByteArray(), Charsets.UTF_8)
                BACKSLASH -> {
                    i++
                    if (i >= size) return null
                    value.write(this[i].toInt())
                }
                else -> value.write(this[i].toInt())
            }
            i++
        }
        return null
    }

    private fun ByteArray.indexOfSubArray(target: ByteArray): Int {
        outer@ for (start in 0..size - target.size) {
            for (offset in target.indices) {
                if (this[start + offset] != target[offset]) continue@outer
            }
            return start
        }
        return -1
    }

    private fun ByteArray.skipWhitespaceFrom(from: Int): Int {
        var i = from
        while (i < size && this[i] in WHITESPACE) i++
        return i
    }

    private const val FIELD_RESULT_CODE = "resultCode"
    private const val FIELD_APK_BASE64 = "apkBase64"

    /** 先頭から覗く上限。resultCode と apkBase64 のキーまでが収まれば足りる */
    private const val MAX_HEAD_BYTES = 8L * 1024

    private const val QUOTE: Byte = 34      // "
    private const val BACKSLASH: Byte = 92  // 逆スラッシュ
    private const val COLON: Byte = 58      // :

    private val WHITESPACE = byteArrayOf(32, 9, 10, 13) // 半角空白 / タブ / LF / CR

    private val QUOTE_OR_BACKSLASH = ByteString.of(QUOTE, BACKSLASH)

    /**
     * JSON 文字列値の中身だけを返す [InputStream]。エスケープされていない `"` に達したら EOF を返す。
     *
     * 100MB を 1 バイトずつ読むと呼び出しが 1 億回になるため、区切り文字の手前までを一括で移す。
     * [Buffer.indexOfElement] はバッファ済みの範囲しか見ないので、
     * はるか先にある終端の `"` を探して本体全体を抱え込むことはない。
     */
    private class JsonStringValueStream(private val source: BufferedSource) : InputStream() {

        private var finished = false

        /** 1 バイト読みのたびに配列を確保しないよう使い回す */
        private val single = ByteArray(1)

        override fun read(): Int =
            if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xFF

        override fun read(sink: ByteArray, offset: Int, length: Int): Int {
            if (finished) return -1
            if (length == 0) return 0
            if (!source.request(1)) throw EOFException("$FIELD_APK_BASE64 の値が閉じられないまま終了しました")

            val buffer = source.buffer
            val until = buffer.indexOfElement(QUOTE_OR_BACKSLASH)
            if (until == 0L) return readDelimited(sink, offset)

            // 区切りが見つからなければバッファ末尾まで、見つかればその手前までを一括で移す
            val available = if (until == -1L) buffer.size else until
            return buffer.read(sink, offset, minOf(available, length.toLong()).toInt())
        }

        /** 先頭が区切り文字だった場合。終端なら EOF、エスケープなら次の 1 バイトを値として返す */
        private fun readDelimited(sink: ByteArray, offset: Int): Int {
            if (source.readByte() == QUOTE) {
                finished = true
                return -1
            }
            // Base64 に現れうるのは / を \/ と出力する実装のみ。続く 1 バイトをそのまま採用する
            if (!source.request(1)) throw EOFException("$FIELD_APK_BASE64 の値が閉じられないまま終了しました")
            sink[offset] = source.readByte()
            return 1
        }

        /** 下層の ResponseBody は呼び出し元が閉じる */
        override fun close() = Unit
    }
}
