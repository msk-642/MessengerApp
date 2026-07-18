package com.example.messengerapp.ui.screen.chatroom.imageviewer

/** フィット表示時の画像サイズ */
data class FitSize(val width: Float, val height: Float)

/** 縦パンを画像スライドと dismiss に分解した結果 */
data class VerticalPanState(
    /** 画像スライドとして消費した後のパンオフセット */
    val panOffset: Float,
    /** dismiss ジェスチャーとして扱うオフセット */
    val dismissOffset: Float
)

/**
 * 画像ビューアのズーム・パン・dismiss 判定の幾何計算。
 * Compose 非依存の純 Kotlin として分離し、ユニットテスト可能にする。
 */
object ImageViewerGeometry {

    /**
     * フィット表示時の画像サイズを返す（アスペクト比維持）。
     * 縦画面では横幅フィット、横画面では縦幅フィット。
     */
    fun calculateFitSize(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        isPortrait: Boolean
    ): FitSize {
        if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
            return FitSize(0f, 0f)
        }
        val aspectRatio = imageWidth / imageHeight
        return if (isPortrait) {
            FitSize(width = containerWidth, height = containerWidth / aspectRatio)
        } else {
            FitSize(width = containerHeight * aspectRatio, height = containerHeight)
        }
    }

    /**
     * ズーム倍率 scale のときのパン可動域（中心基準の最大オフセット絶対値）。
     * 拡大後コンテンツがコンテナに収まる場合は 0（可動不可）。
     */
    fun calculateMaxPanOffset(containerSize: Float, contentSize: Float, scale: Float): Float {
        val scaledSize = contentSize * scale
        return ((scaledSize - containerSize) / 2f).coerceAtLeast(0f)
    }

    /**
     * 縦パンを「画像スライド消費分」と「可動域からの溢れ分(overflow)」に分解する。
     * 上端到達済み（+maxOffset）でさらに下方向、下端到達済み（-maxOffset）で
     * さらに上方向のパンが overflow になる。
     */
    fun consumeVerticalPan(currentOffset: Float, pan: Float, maxOffset: Float): PanResult {
        val desired = currentOffset + pan
        val clamped = desired.coerceIn(-maxOffset, maxOffset)
        return PanResult(newOffset = clamped, overflow = desired - clamped)
    }

    data class PanResult(val newOffset: Float, val overflow: Float)

    /**
     * 縦パン 1 回分を現在のパンオフセットと dismiss オフセットへ反映する。
     *
     * - dismiss 進行中は、まず dismiss オフセットの解消に充てる
     *   （符号が反転するまで解消したら、残りを通常のパンとして消費する）
     * - dismiss していないときは可動域内をパンとして消費し、溢れた分を dismiss に回す
     *   （等倍で可動域 0 の場合は全量が dismiss になる）
     */
    fun applyVerticalPan(
        currentPanOffset: Float,
        currentDismissOffset: Float,
        pan: Float,
        maxPanOffset: Float
    ): VerticalPanState {
        if (currentDismissOffset != 0f) {
            val newDismiss = currentDismissOffset + pan
            val keepsDirection = newDismiss != 0f &&
                (newDismiss > 0f) == (currentDismissOffset > 0f)
            if (newDismiss == 0f || keepsDirection) {
                return VerticalPanState(currentPanOffset, newDismiss)
            }
            // dismiss を解消しきったので、残り(newDismiss)を通常のパンとして消費する
            val result = consumeVerticalPan(currentPanOffset, newDismiss, maxPanOffset)
            return VerticalPanState(result.newOffset, result.overflow)
        }
        val result = consumeVerticalPan(currentPanOffset, pan, maxPanOffset)
        return VerticalPanState(result.newOffset, result.overflow)
    }
}
