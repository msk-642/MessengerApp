package com.example.messengerapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.messengerapp.MainActivity
import com.example.messengerapp.R
import com.example.messengerapp.domain.model.AppUpdateDownload

/**
 * アップデート進捗の常駐通知。
 *
 * Foreground Service は常駐通知が必須だが、今回はそれが
 * 「処理中にアプリを終了しないでほしい」という注意表示そのものを兼ねている。
 */
object AppUpdateNotifications {

    /** 進捗を出す常駐通知。Service の終了とともに消える */
    const val NOTIFICATION_ID = 2001

    /** 完了・失敗を知らせる通知。常駐通知が消えた後も残るよう ID を分ける */
    const val RESULT_NOTIFICATION_ID = 2002

    /** 進捗更新のたびに音を鳴らさないよう IMPORTANCE_LOW で作る */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "アプリの更新", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun build(context: Context, state: AppUpdateDownload.Running): Notification {
        val percent = state.percent
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (percent != null) "アプリを更新しています（$percent%）" else "アプリを更新しています"
            )
            .setContentText(WARNING_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(WARNING_TEXT))
            // Content-Length が無いと総量が分からないため不定表示にする
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))
            .addAction(0, "キャンセル", cancelIntent(context))
            .build()
    }

    /**
     * 終端状態の通知。バックグラウンドのまま終わったとき、常駐通知が消えるだけでは
     * 結果がユーザーに届かないため、別 ID で残る通知を出す。
     *
     * @return 終端状態でなければ null（キャンセル時は何も出さない）
     */
    fun buildResult(context: Context, state: AppUpdateDownload): Notification? = when (state) {
        is AppUpdateDownload.Succeeded ->
            resultBuilder(context)
                .setContentTitle("アプリの更新を準備できました")
                .setContentText("タップして設定画面から続けてください")
                .build()

        is AppUpdateDownload.Failed ->
            resultBuilder(context)
                .setContentTitle("アプリの更新に失敗しました")
                .setContentText(state.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(state.message))
                .build()

        AppUpdateDownload.Idle, is AppUpdateDownload.Running -> null
    }

    /** 結果通知を取り下げる。画面側で結果を閉じたときに呼ぶ */
    fun cancelResult(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(RESULT_NOTIFICATION_ID)
    }

    private fun resultBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun cancelIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            Intent(context, AppUpdateService::class.java).setAction(AppUpdateService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private const val CHANNEL_ID = "messenger_app_update_v1"

    private const val WARNING_TEXT = "アプリを終了すると最初からやり直しになります"

    private const val REQUEST_CONTENT = 2001
    private const val REQUEST_CANCEL = 2002
}
