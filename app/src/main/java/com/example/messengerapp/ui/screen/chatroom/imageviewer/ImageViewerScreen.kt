package com.example.messengerapp.ui.screen.chatroom.imageviewer

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.messengerapp.util.ImageMessageCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 写真メッセージの拡大表示ビューア。
 *
 * - 縦画面は横幅フィット / 横画面は縦幅フィット（アスペクト比維持）で中央表示
 * - ピンチによる拡大縮小（1〜3 倍、ピンチ中は一時的な範囲外を許容しリリース時にスナップバック）
 * - 拡大中はパンによる画像スライド
 * - 上下スワイプ dismiss: 距離に比例して画像縮小 + 背景透過（最大 50%）。
 *   閾値超過でチャットルームへ戻り、未満ならなめらかに復帰する。
 *   拡大中も画像スライドが端に達した後の超過分で同じ dismiss が作動する
 */
@Composable
fun ImageViewerScreen(
    imageBase64: String,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val imageBitmap = remember(imageBase64) {
        ImageMessageCodec.decodeBase64ToImageBitmap(imageBase64)
    }
    val isPortrait =
        LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }

    // ジェスチャー状態
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val fitSize = if (imageBitmap != null) {
        ImageViewerGeometry.calculateFitSize(
            containerWidth = containerSize.width.toFloat(),
            containerHeight = containerSize.height.toFloat(),
            imageWidth = imageBitmap.width.toFloat(),
            imageHeight = imageBitmap.height.toFloat(),
            isPortrait = isPortrait
        )
    } else {
        FitSize(0f, 0f)
    }
    val dismissProgress = (abs(dismissOffsetY) / dismissThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // スワイプ距離に応じて背景を透過する（透過率は最大 50%）
            .background(Color.Black.copy(alpha = 1f - dismissProgress * MAX_BACKGROUND_FADE))
            .onSizeChanged { containerSize = it }
            .pointerInput(imageBitmap, isPortrait) {
                if (imageBitmap == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    settleJob?.cancel()
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoomChange == 1f && pan == Offset.Zero) continue

                        // 下限は常に等倍(MIN_ZOOM)。等倍未満への縮小は許容しない
                        scale = (scale * zoomChange)
                            .coerceIn(MIN_ZOOM, TRANSIENT_MAX_ZOOM)

                        val fit = ImageViewerGeometry.calculateFitSize(
                            size.width.toFloat(), size.height.toFloat(),
                            imageBitmap.width.toFloat(), imageBitmap.height.toFloat(),
                            isPortrait
                        )
                        val maxPanX = ImageViewerGeometry.calculateMaxPanOffset(
                            size.width.toFloat(), fit.width, scale
                        )
                        val maxPanY = ImageViewerGeometry.calculateMaxPanOffset(
                            size.height.toFloat(), fit.height, scale
                        )
                        val vertical = ImageViewerGeometry.applyVerticalPan(
                            currentPanOffset = panOffset.y,
                            currentDismissOffset = dismissOffsetY,
                            pan = pan.y,
                            maxPanOffset = maxPanY
                        )
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = vertical.panOffset
                        )
                        dismissOffsetY = vertical.dismissOffset

                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })

                    // リリース時の確定処理
                    if (abs(dismissOffsetY) >= dismissThresholdPx) {
                        // 閾値までスワイプされたのでチャットルーム画面へ戻る
                        onClose()
                    } else {
                        // ズームを 1〜3 倍へスナップし、パンを可動域内へ、dismiss を 0 へなめらかに戻す
                        val targetScale = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val fit = ImageViewerGeometry.calculateFitSize(
                            size.width.toFloat(), size.height.toFloat(),
                            imageBitmap.width.toFloat(), imageBitmap.height.toFloat(),
                            isPortrait
                        )
                        val maxPanX = ImageViewerGeometry.calculateMaxPanOffset(
                            size.width.toFloat(), fit.width, targetScale
                        )
                        val maxPanY = ImageViewerGeometry.calculateMaxPanOffset(
                            size.height.toFloat(), fit.height, targetScale
                        )
                        val targetPan = Offset(
                            x = panOffset.x.coerceIn(-maxPanX, maxPanX),
                            y = panOffset.y.coerceIn(-maxPanY, maxPanY)
                        )
                        settleJob = scope.launch {
                            coroutineScope {
                                launch {
                                    animate(scale, targetScale) { value, _ -> scale = value }
                                }
                                launch {
                                    animate(panOffset.x, targetPan.x) { value, _ ->
                                        panOffset = panOffset.copy(x = value)
                                    }
                                }
                                launch {
                                    animate(panOffset.y, targetPan.y) { value, _ ->
                                        panOffset = panOffset.copy(y = value)
                                    }
                                }
                                launch {
                                    animate(dismissOffsetY, 0f) { value, _ ->
                                        dismissOffsetY = value
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            ViewerImage(
                bitmap = imageBitmap,
                fitSize = fitSize,
                scale = scale,
                panOffset = panOffset,
                dismissProgress = dismissProgress
            )
            ViewerCloseButton(
                // dismiss ジェスチャー中は非表示にし、元の表示に戻ったら再表示する
                visible = dismissOffsetY == 0f,
                onClose = onClose,
                // ステータスバー下端（下つら）の右側に固定配置
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
            )
        } else {
            Text(
                text = "画像を表示できません",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** 画像表示部。scale / パン / dismiss の変換を graphicsLayer で適用する */
@Composable
private fun ViewerImage(
    bitmap: ImageBitmap,
    fitSize: FitSize,
    scale: Float,
    panOffset: Offset,
    dismissProgress: Float
) {
    val density = LocalDensity.current
    Image(
        bitmap = bitmap,
        contentDescription = "拡大表示された写真",
        modifier = Modifier
            .size(
                width = with(density) { fitSize.width.toDp() },
                height = with(density) { fitSize.height.toDp() }
            )
            .graphicsLayer {
                // dismiss の進行に応じた中央への等倍縮小（scaleX = scaleY でアスペクト比維持）。
                // dismiss オフセットは平行移動に使わず、スワイプ方向に関係なく中央に留まったまま縮小する
                val shrink = 1f - dismissProgress * DISMISS_SHRINK
                scaleX = scale * shrink
                scaleY = scale * shrink
                translationX = panOffset.x
                translationY = panOffset.y
            }
    )
}

/** 「×」ボタン。dismiss ジェスチャー中はフェードアウトする */
@Composable
private fun ViewerCloseButton(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(CLOSE_BUTTON_SIZE_DP.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "閉じる",
                tint = Color.White
            )
        }
    }
}

/** 基準ズーム倍率（フィット表示） */
private const val MIN_ZOOM = 1f

/** 最大ズーム倍率（LINE の写真ビューア相当） */
private const val MAX_ZOOM = 3f

/** ピンチ操作中に一時的に許容する倍率の上限（リリース時に MAX_ZOOM へスナップバック） */
private const val TRANSIENT_MAX_ZOOM = 4f

/** スワイプ dismiss の閾値(dp)。超えたらチャットルーム画面へ戻る */
private const val DISMISS_THRESHOLD_DP = 150

/** dismiss 閾値到達時の画像縮小量（1f - DISMISS_SHRINK = 70% サイズ） */
private const val DISMISS_SHRINK = 0.3f

/** dismiss 閾値到達時の背景透過率（最大 50%） */
private const val MAX_BACKGROUND_FADE = 0.5f

/** 「×」ボタンのサイズ(dp) */
private const val CLOSE_BUTTON_SIZE_DP = 48
