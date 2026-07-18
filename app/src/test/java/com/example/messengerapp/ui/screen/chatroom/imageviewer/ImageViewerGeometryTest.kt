package com.example.messengerapp.ui.screen.chatroom.imageviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageViewerGeometryTest {

    // --- calculateFitSize ---

    @Test
    fun `縦画面では横幅にフィットしアスペクト比を維持する`() {
        // 正方形画像
        val square = ImageViewerGeometry.calculateFitSize(1000f, 2000f, 500f, 500f, isPortrait = true)
        assertEquals(FitSize(1000f, 1000f), square)

        // 縦長画像（コンテナより表示高さが大きくなっても横幅フィット）
        val tall = ImageViewerGeometry.calculateFitSize(1000f, 2000f, 500f, 1500f, isPortrait = true)
        assertEquals(FitSize(1000f, 3000f), tall)
    }

    @Test
    fun `横画面では縦幅にフィットしアスペクト比を維持する`() {
        val square = ImageViewerGeometry.calculateFitSize(2000f, 1000f, 500f, 500f, isPortrait = false)
        assertEquals(FitSize(1000f, 1000f), square)

        val wide = ImageViewerGeometry.calculateFitSize(2000f, 1000f, 1000f, 500f, isPortrait = false)
        assertEquals(FitSize(2000f, 1000f), wide)
    }

    @Test
    fun `サイズが不正なら 0 サイズを返す`() {
        assertEquals(FitSize(0f, 0f), ImageViewerGeometry.calculateFitSize(0f, 100f, 10f, 10f, true))
        assertEquals(FitSize(0f, 0f), ImageViewerGeometry.calculateFitSize(100f, 100f, 0f, 10f, true))
    }

    // --- calculateMaxPanOffset ---

    @Test
    fun `等倍でコンテンツがコンテナと同サイズなら可動域は 0`() {
        assertEquals(0f, ImageViewerGeometry.calculateMaxPanOffset(1000f, 1000f, 1f))
    }

    @Test
    fun `2 倍ズームでは拡大分の半分が可動域になる`() {
        assertEquals(500f, ImageViewerGeometry.calculateMaxPanOffset(1000f, 1000f, 2f))
    }

    @Test
    fun `コンテンツがコンテナより小さければ可動域は 0`() {
        assertEquals(0f, ImageViewerGeometry.calculateMaxPanOffset(1000f, 400f, 1f))
    }

    // --- consumeVerticalPan ---

    @Test
    fun `可動域内のパンは全量消費され overflow は 0`() {
        val result = ImageViewerGeometry.consumeVerticalPan(currentOffset = 0f, pan = 100f, maxOffset = 300f)
        assertEquals(ImageViewerGeometry.PanResult(100f, 0f), result)
    }

    @Test
    fun `上端を超えるパンは超過分が overflow になる`() {
        val result = ImageViewerGeometry.consumeVerticalPan(currentOffset = 250f, pan = 100f, maxOffset = 300f)
        assertEquals(ImageViewerGeometry.PanResult(300f, 50f), result)
    }

    @Test
    fun `下端を超えるパンは負の overflow になる`() {
        val result = ImageViewerGeometry.consumeVerticalPan(currentOffset = -250f, pan = -100f, maxOffset = 300f)
        assertEquals(ImageViewerGeometry.PanResult(-300f, -50f), result)
    }

    @Test
    fun `端から戻る方向のパンは通常どおり消費される`() {
        val result = ImageViewerGeometry.consumeVerticalPan(currentOffset = 300f, pan = -100f, maxOffset = 300f)
        assertEquals(ImageViewerGeometry.PanResult(200f, 0f), result)
    }

    // --- applyVerticalPan ---

    @Test
    fun `可動域 0 のときは全量が dismiss になる`() {
        val result = ImageViewerGeometry.applyVerticalPan(
            currentPanOffset = 0f, currentDismissOffset = 0f, pan = 120f, maxPanOffset = 0f
        )
        assertEquals(VerticalPanState(panOffset = 0f, dismissOffset = 120f), result)
    }

    @Test
    fun `拡大中は端に達するまでパンとして消費し超過分だけ dismiss になる`() {
        val result = ImageViewerGeometry.applyVerticalPan(
            currentPanOffset = 250f, currentDismissOffset = 0f, pan = 100f, maxPanOffset = 300f
        )
        assertEquals(VerticalPanState(panOffset = 300f, dismissOffset = 50f), result)
    }

    @Test
    fun `dismiss 進行中の逆方向パンは dismiss の解消に充てられる`() {
        val result = ImageViewerGeometry.applyVerticalPan(
            currentPanOffset = 300f, currentDismissOffset = 50f, pan = -30f, maxPanOffset = 300f
        )
        assertEquals(VerticalPanState(panOffset = 300f, dismissOffset = 20f), result)
    }

    @Test
    fun `dismiss を解消しきったら残りは通常のパンとして消費される`() {
        val result = ImageViewerGeometry.applyVerticalPan(
            currentPanOffset = 300f, currentDismissOffset = 50f, pan = -80f, maxPanOffset = 300f
        )
        assertEquals(VerticalPanState(panOffset = 270f, dismissOffset = 0f), result)
    }

    @Test
    fun `dismiss がちょうど 0 になる場合はパンを変更しない`() {
        val result = ImageViewerGeometry.applyVerticalPan(
            currentPanOffset = 300f, currentDismissOffset = 50f, pan = -50f, maxPanOffset = 300f
        )
        assertEquals(VerticalPanState(panOffset = 300f, dismissOffset = 0f), result)
    }
}
