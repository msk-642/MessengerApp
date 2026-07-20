package com.example.messengerapp.ui.screen.chatroom

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 尻尾付き吹き出しのメッセージバブル Shape。
 *
 * 本体（角丸矩形）と尻尾（側面から水平に突き出す三角形）を別々のパスとして構築し、
 * 和集合で 1 つの Path に合成して単一の Shape として扱う。
 * これにより Surface の背景塗り・クリップ・縁取り（メンション時の border）が
 * すべて尻尾を含む吹き出しの輪郭に沿って処理される。
 *
 * @param isMyMessage true なら尻尾は右側面（自分のメッセージ）、false なら左側面（相手のメッセージ）
 */
class MessageBubbleShape(private val isMyMessage: Boolean) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val tailWidth = with(density) { BUBBLE_TAIL_WIDTH_DP.dp.toPx() }
        val tailHeight = with(density) { TAIL_HEIGHT_DP.dp.toPx() }
        val cornerRadius = with(density) { CORNER_RADIUS_DP.dp.toPx() }

        // 本体: 尻尾の突き出し分だけ幅を除いた角丸矩形
        val bodyLeft = if (isMyMessage) 0f else tailWidth
        val bodyRight = if (isMyMessage) size.width - tailWidth else size.width
        val bodyPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(bodyLeft, 0f, bodyRight, size.height),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }

        // 尻尾: 本体側面を基部として水平に突き出す三角形。
        // 角丸に食い込まないよう縦位置をクランプする
        val minCenterY = cornerRadius + tailHeight / 2f
        val maxCenterY = size.height - cornerRadius - tailHeight / 2f
        val tailCenterY = if (maxCenterY <= minCenterY) {
            size.height / 2f
        } else {
            (size.height * TAIL_CENTER_RATIO).coerceIn(minCenterY, maxCenterY)
        }
        val tailPath = Path().apply {
            if (isMyMessage) {
                moveTo(bodyRight, tailCenterY - tailHeight / 2f)
                lineTo(size.width, tailCenterY)   // 右向きの先端
                lineTo(bodyRight, tailCenterY + tailHeight / 2f)
            } else {
                moveTo(bodyLeft, tailCenterY - tailHeight / 2f)
                lineTo(0f, tailCenterY)           // 左向きの先端
                lineTo(bodyLeft, tailCenterY + tailHeight / 2f)
            }
            close()
        }

        // 和集合で合成し、縁取りが内部の境界線を通らない単一輪郭にする
        return Outline.Generic(Path.combine(PathOperation.Union, bodyPath, tailPath))
    }
}

/** 本体の角丸半径(dp) */
private const val CORNER_RADIUS_DP = 16

/** 尻尾の突き出し量(dp)。コンテンツの尻尾側パディングにも使用する */
internal const val BUBBLE_TAIL_WIDTH_DP = 10

/** 尻尾基部の縦幅(dp) */
private const val TAIL_HEIGHT_DP = 14

/** 尻尾中心の縦位置（バブル高さに対する比率） */
private const val TAIL_CENTER_RATIO = 0.4f
