package com.example.messengerapp.ui.screen.chatroom.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.messengerapp.util.ImageMessageCodec

/**
 * 撮影結果確認画面。
 * 画像表示領域・戻るボタン・送信ボタンで構成する。
 * 戻るボタンでカメラ設定を維持したままカメラ画面へ戻り、
 * 送信ボタンで撮影画像を写真メッセージとして送信する。
 */
@Composable
fun PhotoPreviewScreen(
    photoJpeg: ByteArray?,
    isSending: Boolean,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    BackHandler(enabled = !isSending, onBack = onBack)

    val imageBitmap = remember(photoJpeg) {
        photoJpeg?.let { ImageMessageCodec.decodeJpegToImageBitmap(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
