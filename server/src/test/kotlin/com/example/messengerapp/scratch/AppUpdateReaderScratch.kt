// =============================================================================
//  AppUpdateResponseReader の各ヘルパーを素で動かし、全パターンの戻り値を見る。
//
//  【実行方法】
//   下の fun main() の左の ▶ を押して Run するだけ。出力は Run ツールウィンドウへ。
//
//  【なぜ :server の test に置いてあるのか】
//   1. Kotlin の Scratch File は K2 モードで動かないため使えない
//   2. :app のモジュールを選ぶと Compose / Firebase などの jar が数百個 -cp に並び、
//      Windows のコマンドライン長 32KB 制限に当たって CreateProcess error=206 になる
//   :server は Ktor だけの軽い JVM モジュールなので、どちらの問題にも当たらない。
//   このファイル自体は JDK 標準ライブラリしか使っていないので、置き場所は何でもよい。
//
//  【本番との違いはここだけ】
//   本番は okio の BufferedSource.peek() で先頭を覗くが、ここでは
//   BufferedInputStream の mark/reset で同じこと（消費せずに覗く）をしている。
//   検証したい 1〜4 のヘルパーは本番と完全に同一のコード。
//
//  【文字化けしたら】
//   Windows の JVM は stdout を MS932 で書くため、main() の先頭で UTF-8 に固定している。
//   それでも化ける場合はコンソール側が MS932 なので、
//   Settings → Editor → File Encodings → Global Encoding を UTF-8 にする。
//
//  【読み方のコツ】
//   JSON は Kotlin のエスケープで読みにくくならないよう、二重引用符のかわりに
//   | と書いて j() で置換している。
// =============================================================================

package com.example.messengerapp.scratch

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Base64

// =============================================================================
//  第 1 部: 検証対象
// =============================================================================

val QUOTE: Byte = 34      // "
val BACKSLASH: Byte = 92  // 逆スラッシュ
val COLON: Byte = 58      // :
val WHITESPACE = byteArrayOf(32, 9, 10, 13) // 半角スペース / タブ / LF / CR
val MAX_HEAD_BYTES = 8 * 1024
val RESULT_CODE_SUCCESS = "0"

class AppUpdateResultCodeException(val resultCode: String) :
    IOException("サーバがエラーコードを返しました: $resultCode")

data class ApkWriteResult(val file: File, val byteCount: Long, val sha256: String)

/** 役割: バイト列の中から部分バイト列を総当たりで探し、先頭位置を返す */
fun ByteArray.indexOfSubArray(target: ByteArray): Int {
    outer@ for (start in 0..size - target.size) {
        for (offset in target.indices) {
            if (this[start + offset] != target[offset]) continue@outer
        }
        return start
    }
    return -1
}

/** 役割: from から空白を読み飛ばし、空白でない最初の位置を返す */
fun ByteArray.skipWhitespaceFrom(from: Int): Int {
    var i = from
    while (i < size && this[i] in WHITESPACE) i++
    return i
}

/** 役割: キーに続く文字列値の、開き引用符の次のバイト位置を返す */
fun ByteArray.jsonStringValueStartOf(field: String): Int? {
    val key = byteArrayOf(QUOTE) + field.toByteArray(Charsets.UTF_8) + byteArrayOf(QUOTE)
    val keyAt = indexOfSubArray(key)
    if (keyAt < 0) return null

    var i = skipWhitespaceFrom(keyAt + key.size)
    if (i >= size || this[i] != COLON) return null
    i = skipWhitespaceFrom(i + 1)
    if (i >= size || this[i] != QUOTE) return null
    return i + 1
}

/** 役割: キーの文字列値そのものを取り出す（エスケープを解きつつ閉じ引用符まで） */
fun ByteArray.jsonStringValueOf(field: String): String? {
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

/**
 * 役割: 先頭を最大 8KB だけ「消費せずに」覗く。
 * 本番は okio の peek() を使うが、ここでは mark/reset で同じことをしている。
 */
fun BufferedInputStream.peekHead(): ByteArray {
    mark(MAX_HEAD_BYTES + 1)
    val head = readNBytes(MAX_HEAD_BYTES)
    reset()
    return head
}

/** 役割: Base64 ストリームをデコードしてファイルへ書く（ApkBase64Decoder の移植） */
fun decodeToFile(source: InputStream, destination: File): ApkWriteResult {
    destination.parentFile?.mkdirs()
    val working = File(destination.parentFile, destination.name + ".part")
    val digest = MessageDigest.getInstance("SHA-256")

    val byteCount = try {
        Base64.getMimeDecoder().wrap(source.buffered(64 * 1024)).use { decoded ->
            DigestOutputStream(working.outputStream().buffered(), digest).use { out ->
                decoded.copyTo(out, 64 * 1024)
            }
        }
    } catch (e: Throwable) {
        working.delete()
        throw e
    }

    destination.delete()
    if (!working.renameTo(destination)) {
        working.delete()
        throw IOException("移動できませんでした")
    }
    return ApkWriteResult(destination, byteCount, digest.digest().joinToString("") { "%02x".format(it) })
}

/**
 * 役割: レスポンス全体を解釈し、apkBase64 をデコードしてファイルへ書く。
 * JsonStringValueStream を消した想定なので source をそのまま decodeToFile へ渡す。
 */
fun readResponse(source: BufferedInputStream, destination: File): ApkWriteResult {
    val head = source.peekHead()

    val resultCode = head.jsonStringValueOf("resultCode")
        ?: throw IOException("レスポンスから resultCode を読み取れませんでした")
    if (resultCode != RESULT_CODE_SUCCESS) throw AppUpdateResultCodeException(resultCode)

    val valueStart = head.jsonStringValueStartOf("apkBase64")
        ?: throw IOException("先頭 $MAX_HEAD_BYTES バイト以内に apkBase64 が見つかりませんでした")

    source.skipNBytes(valueStart.toLong())
    return decodeToFile(source, destination)
}

// =============================================================================
//  第 2 部: ドライバ
// =============================================================================

val BS = 92.toChar()  // 逆スラッシュ
val tmpDir: File = Files.createTempDirectory("reader-scratch").toFile()

/** | を " に置き換える。JSON をエスケープまみれにしないための小細工 */
fun j(s: String): String = s.replace('|', '"')

fun title(s: String) {
    println()
    println("=".repeat(76))
    println("  $s")
    println("=".repeat(76))
}

fun case(label: String, block: () -> Any?) {
    val result = try {
        block()?.toString() ?: "null"
    } catch (e: Throwable) {
        "[" + (e::class.simpleName ?: "?") + "] " + e.message
    }
    println("  - $label")
    println("      => $result")
}

fun String.bytes(): ByteArray = toByteArray(Charsets.UTF_8)
fun String.input(): BufferedInputStream = ByteArrayInputStream(bytes()).buffered()
fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

/** APK(ZIP) のシグネチャで始まるダミーデータ。size を変えるとパディングの有無が変わる */
fun apkBytes(size: Int = 1024): ByteArray =
    byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(size) { (it * 31 % 251).toByte() }

fun destFile(name: String) = File(tmpDir, name)

fun fileState(f: File): String {
    val part = File(f.parentFile, f.name + ".part")
    return "apk=" + (if (f.exists()) "${f.length()}B" else "なし") +
        " / .part=" + (if (part.exists()) "残存" else "なし")
}

fun main() {
    // Windows の JVM は stdout を MS932 で書くが、IDE の Run ウィンドウは UTF-8 で読むため
    // そのままだと日本語が文字化けする。明示的に UTF-8 で書き出して食い違いを無くす。
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    println(
        "stdout.encoding=" + System.getProperty("stdout.encoding") +
            " / native.encoding=" + System.getProperty("native.encoding") +
            " → UTF-8 で出力します（この行が読めていれば文字化けは解消しています）"
    )

// -----------------------------------------------------------------------------
title("1) indexOfSubArray : バイト列から部分列を総当たりで探す")
println("  戻り値は見つかった先頭位置。見つからなければ -1。最初の 1 件しか返さない。")

case("先頭で一致    abcdef から abc") { "abcdef".bytes().indexOfSubArray("abc".bytes()) }
case("中間で一致    abcdef から cd") { "abcdef".bytes().indexOfSubArray("cd".bytes()) }
case("末尾で一致    abcdef から ef") { "abcdef".bytes().indexOfSubArray("ef".bytes()) }
case("不一致        abcdef から xyz") { "abcdef".bytes().indexOfSubArray("xyz".bytes()) }
case("部分一致に騙されないか  aaab から aab") { "aaab".bytes().indexOfSubArray("aab".bytes()) }
case("target のほうが長い     ab から abcd") { "ab".bytes().indexOfSubArray("abcd".bytes()) }
case("target が空（境界）") { "abc".bytes().indexOfSubArray(ByteArray(0)) }
case("haystack が空") { ByteArray(0).indexOfSubArray("a".bytes()) }
case("日本語（バイト単位で探す）あいうえお から うえ") {
    "あいうえお".bytes().indexOfSubArray("うえ".bytes())
}

// -----------------------------------------------------------------------------
title("2) skipWhitespaceFrom : 空白を読み飛ばして次の位置を返す")
println("  空白とみなすのは 半角スペース(32) / タブ(9) / LF(10) / CR(13) の 4 つだけ。")

case("空白なし        abc / from=0") { "abc".bytes().skipWhitespaceFrom(0) }
case("先頭に空白 1 つ  _abc / from=0") { " abc".bytes().skipWhitespaceFrom(0) }
case("4 種類すべて     sp,tab,LF,CR のあとに abc") {
    (" " + 9.toChar() + 10.toChar() + 13.toChar() + "abc").bytes().skipWhitespaceFrom(0)
}
case("全部空白（末尾に到達）___ / from=0") { "   ".bytes().skipWhitespaceFrom(0) }
case("from が既に size 以上（境界）abc / from=99") { "abc".bytes().skipWhitespaceFrom(99) }
case("全角スペースは空白扱いされるか") { "　abc".bytes().skipWhitespaceFrom(0) }

// -----------------------------------------------------------------------------
title("3) jsonStringValueStartOf : 値の開始位置（開き引用符の次）を返す")
println("  キー -> 空白 -> コロン -> 空白 -> 引用符 の順に辿る。1 つでも崩れたら null。")
println("  readResponse はこの戻り値で source を skip し、Base64 の先頭に頭出しする。")

val normal = j("{|resultCode|:|0|,|apkBase64|:|UEsDBBQ|}")
println()
println("  基準データ: $normal")

case("resultCode の値開始位置") { normal.bytes().jsonStringValueStartOf("resultCode") }
case("  その位置から 6 文字") {
    normal.substring(normal.bytes().jsonStringValueStartOf("resultCode")!!).take(6)
}
case("apkBase64 の値開始位置") { normal.bytes().jsonStringValueStartOf("apkBase64") }
case("  その位置から 10 文字") {
    normal.substring(normal.bytes().jsonStringValueStartOf("apkBase64")!!).take(10)
}
case("空白入り  { |resultCode| : |0| }") {
    j("{ |resultCode| : |0| }").bytes().jsonStringValueStartOf("resultCode")
}
case("存在しないキー") { normal.bytes().jsonStringValueStartOf("noSuchField") }
case("コロンが無い  {|resultCode| |0|}") {
    j("{|resultCode| |0|}").bytes().jsonStringValueStartOf("resultCode")
}
case("値が文字列でない  {|resultCode|:0}") {
    j("{|resultCode|:0}").bytes().jsonStringValueStartOf("resultCode")
}
case("キーの直後で切れている  {|resultCode|") {
    j("{|resultCode|").bytes().jsonStringValueStartOf("resultCode")
}
case("別フィールドの値に同じキー名が埋まっている（注目）") {
    val tricky = j("{|note|:|xx |resultCode| xx|,|resultCode|:|0|}")
    "入力=" + tricky + " / 位置=" + tricky.bytes().jsonStringValueStartOf("resultCode") +
        " / 取れる値=" + tricky.bytes().jsonStringValueOf("resultCode")
}

// -----------------------------------------------------------------------------
title("4) jsonStringValueOf : 値そのものを文字列で取り出す")
println("  readResponse では resultCode の判定にだけ使う。apkBase64 の値には使わない。")

case("通常  resultCode") { normal.bytes().jsonStringValueOf("resultCode") }
case("通常  apkBase64") { normal.bytes().jsonStringValueOf("apkBase64") }
case("エスケープ  値が a" + BS + "/b") {
    (j("{|resultCode|:|a") + BS + j("/b|}")).bytes().jsonStringValueOf("resultCode")
}
case("日本語の値（UTF-8 が壊れないか）") {
    j("{|resultCode|:|エラー発生|}").bytes().jsonStringValueOf("resultCode")
}
case("空文字の値") { j("{|resultCode|:||}").bytes().jsonStringValueOf("resultCode") }
case("閉じ引用符が無い（途中で切れた）") {
    j("{|resultCode|:|0").bytes().jsonStringValueOf("resultCode")
}
case("末尾が逆スラッシュで切れた") {
    (j("{|resultCode|:|0") + BS).bytes().jsonStringValueOf("resultCode")
}
case("存在しないキー") { normal.bytes().jsonStringValueOf("noSuchField") }

// -----------------------------------------------------------------------------
title("5) peekHead : 先頭を最大 8KB だけ『消費せずに』覗く")
println("  100MB の本文を読み進めてしまわないための入口。覗いた内容を 3) と 4) に渡す。")

case("短い入力はすべて返る（15 バイト）") {
    "${"hello, scratch!".input().peekHead().size} バイト"
}
case("覗いたあとも source が減っていないこと（これが peek の肝）") {
    val s = "hello, scratch!".input()
    val head = s.peekHead()
    "peek=${head.size}バイト / source から読み直せる内容=${String(s.readBytes())}"
}
case("8KB を超える入力は 8192 で打ち切られる（20000 バイト入力）") {
    val s = "x".repeat(20000).input()
    val head = s.peekHead()
    "peek=${head.size}バイト / そのあと source から読める総量=${s.readBytes().size}バイト（未消費）"
}
case("空の入力") { "${"".input().peekHead().size} バイト" }

// -----------------------------------------------------------------------------
title("6) readResponse : 全体を通した本番経路")

val apk = apkBytes()
println("  ダミー APK: ${apk.size} バイト / Base64 ${apk.b64().length} 文字")

case("正常系") {
    val d = destFile("ok.apk")
    val r = readResponse(j("{|resultCode|:|0|,|apkBase64|:|${apk.b64()}|}").input(), d)
    "書き出し=${r.byteCount}B / 元と一致=${d.readBytes().contentEquals(apk)}" +
        " / sha256=${r.sha256.take(16)}..."
}

case("resultCode が 1（APK をダウンロードせず打ち切る）") {
    val d = destFile("ng.apk")
    try {
        readResponse(j("{|resultCode|:|1|,|apkBase64|:|${apk.b64()}|}").input(), d)
        "例外が出なかった"
    } catch (e: AppUpdateResultCodeException) {
        "[${e::class.simpleName}] code=${e.resultCode} / ${fileState(d)}"
    }
}

case("apkBase64 フィールドが無い") {
    val d = destFile("nofield.apk")
    try {
        readResponse(j("{|resultCode|:|0|}").input(), d)
        "例外が出なかった"
    } catch (e: IOException) {
        "[${e::class.simpleName}] ${e.message} / ${fileState(d)}"
    }
}

case("apkBase64 が 8KB より後ろにある（MAX_HEAD_BYTES の効果）") {
    val d = destFile("far.apk")
    val filler = j("|filler|:|") + "A".repeat(9000) + j("|,")
    val json = j("{|resultCode|:|0|,") + filler + j("|apkBase64|:|${apk.b64()}|}")
    try {
        readResponse(json.input(), d)
        "例外が出なかった"
    } catch (e: IOException) {
        "[${e::class.simpleName}] ${e.message}"
    }
}

case("空白・改行入りの JSON（skipWhitespaceFrom が効いている）") {
    val d = destFile("ws.apk")
    val nl = 10.toChar()
    val json = "{" + nl + j(" |resultCode| : |0| ,") + nl +
        j(" |apkBase64| : |${apk.b64()}| ") + nl + "}"
    val r = readResponse(json.input(), d)
    "書き出し=${r.byteCount}B / 元と一致=${d.readBytes().contentEquals(apk)}"
}

// -----------------------------------------------------------------------------
title("7) JsonStringValueStream を消したことで変わる挙動（判断材料）")
println("  MIME デコーダは Base64 アルファベット外の文字を黙って無視する。")
println("  apkBytes(1024) だと Base64 の末尾にパディング = が付く。")
println("  apkBytes(1023) などに変えるとパディングが消え、結果が変わるので試す価値あり。")

case("後続フィールドがある（apkBase64 が最後でない）") {
    val d = destFile("trailing.apk")
    val json = j("{|resultCode|:|0|,|apkBase64|:|${apk.b64()}|,|version|:|2.0|}")
    try {
        val r = readResponse(json.input(), d)
        "例外なし / 書き出し=${r.byteCount}B（元 ${apk.size}B） / 元と一致=" +
            d.readBytes().contentEquals(apk)
    } catch (e: Throwable) {
        "[" + (e::class.simpleName ?: "?") + "] " + e.message
    }
}

for (cut in 1..4) {
    case("レスポンスが途中で切れた（Base64 の末尾 $cut 文字が欠落）") {
        val d = destFile("cut$cut.apk")
        val b64 = apk.b64()
        val json = j("{|resultCode|:|0|,|apkBase64|:|") + b64.substring(0, b64.length - cut)
        try {
            val r = readResponse(json.input(), d)
            "例外なし / 書き出し=${r.byteCount}B（元 ${apk.size}B） / 元と一致=" +
                d.readBytes().contentEquals(apk)
        } catch (e: Throwable) {
            "[" + (e::class.simpleName ?: "?") + "] " + e.message + " / " + fileState(d)
        }
    }
}

println()
println("=".repeat(76))
println("  一時ディレクトリ: ${tmpDir.absolutePath}")
println("=".repeat(76))
}
