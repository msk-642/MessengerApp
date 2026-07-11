package com.example.messengerapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/**
 * 写真メッセージ用の画像変換ユーティリティ。
 * ストレージへのキャッシュは行わず、すべてメモリ上で変換を完結させる。
 */
object ImageMessageCodec {

    /** 撮影結果の ImageProxy(JPEG) を回転補正済みの JPEG バイト列に変換する */
    fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bytes

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val output = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, CAPTURE_QUALITY, output)
        return output.toByteArray()
    }

    /** JPEG バイト列を送信用に縮小・再圧縮して Base64 文字列に変換する */
    fun encodeJpegBytesToBase64(
        jpeg: ByteArray,
        maxLongSide: Int = SEND_MAX_LONG_SIDE,
        quality: Int = SEND_QUALITY
    ): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)

        var inSampleSize = 1
        var longSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longSide / 2 >= maxLongSide) {
            inSampleSize *= 2
            longSide /= 2
        }

        val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: return Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /** 受信メッセージの Base64 文字列を表示用 ImageBitmap にデコードする（失敗時 null） */
    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        return try {
            decodeJpegToImageBitmap(Base64.decode(base64, Base64.DEFAULT))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** JPEG バイト列を表示用 ImageBitmap にデコードする（失敗時 null） */
    fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap? {
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
    }

    /** 回転補正の再圧縮品質（送信時に改めて縮小・圧縮するため高めに保つ） */
    private const val CAPTURE_QUALITY = 95

    /** 送信画像の長辺上限(px) */
    private const val SEND_MAX_LONG_SIDE = 1280

    /** 送信画像の JPEG 品質 */
    private const val SEND_QUALITY = 80
}
