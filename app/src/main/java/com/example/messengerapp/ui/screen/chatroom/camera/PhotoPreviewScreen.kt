package com.example.messengerapp.ui.screen.chatroom.camera

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.messengerapp.util.ImageMessageCodec
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 撮影結果確認画面。
 * 画像表示領域・戻るボタン・送信ボタンで構成する。
 * 戻るボタンでカメラ設定を維持したままカメラ画面へ戻り、
 * 送信ボタンで撮影画像を写真メッセージとして送信する。
 *
 * チャットルーム画面へ直接戻る導線として、右上の「×」ボタンと
 * スワイプダウンによる縮小 dismiss（iOS ライク）を提供する。
 */
@Composable
fun PhotoPreviewScreen(
    photoJpeg: ByteArray?,
    isSending: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit
) {
    BackHandler(enabled = !isSending, onBack = onBack)

    val imageBitmap = remember(photoJpeg) {
        photoJpeg?.let { ImageMessageCodec.decodeJpegToImageBitmap(it) }
    }

    // 上下スワイプ dismiss の状態（画像ビューアの等倍スワイプ dismiss と同一の処理）
    val dismissThresholdPx = with(LocalDensity.current) { DISMISS_THRESHOLD_DP.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val progress = (abs(dragOffsetY.value) / dismissThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // スワイプが進むほど背景を薄くして「画面が抜けていく」印象にする
            .background(Color.Black.copy(alpha = 1f - progress * 0.5f))
            .pointerInput(isSending) {
                if (isSending) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            // 上下どちらの方向も dismiss として扱う
                            dragOffsetY.snapTo(dragOffsetY.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (abs(dragOffsetY.value) >= dismissThresholdPx) {
                            // 規定距離までスワイプされたらチャットルーム画面へ
                            onClose()
                        } else {
                            scope.launch { dragOffsetY.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetY.animateTo(0f) }
                    }
                )
            }
    ) {
        // スワイプ距離に比例して中央のまま等倍縮小（scaleX = scaleY のためアスペクト比は維持される）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - progress * MAX_SHRINK
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            // 画像表示領域
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "撮影した写真",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "画像を表示できません",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 戻るボタン（撮影時点の設定を維持してカメラ画面へ）
            IconButton(
                onClick = onBack,
                enabled = !isSending,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "カメラに戻る",
                    tint = Color.White
                )
            }

            // ×ボタン（撮影画像を破棄してチャットルーム画面へ）
            IconButton(
                onClick = onClose,
                enabled = !isSending,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "閉じる",
                    tint = Color.White
                )
            }

            // 送信ボタン
            FloatingActionButton(
                onClick = { if (!isSending && imageBitmap != null) onSend() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送信")
                }
            }
        }
    }
}

/** スワイプ dismiss の規定距離(dp)。画像ビューアと同一値。この距離に達したらチャットルーム画面へ遷移する */
private const val DISMISS_THRESHOLD_DP = 150

/** 規定距離到達時の縮小量（画像ビューアと同一値。1f - MAX_SHRINK = 70% サイズまで縮小） */
private const val MAX_SHRINK = 0.3f
