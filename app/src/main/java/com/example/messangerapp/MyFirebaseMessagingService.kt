package com.example.messangerapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.messangerapp.notification.NotificationTarget
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger

/**
 * FCM からのメッセージ受信処理。
 *
 * 【前提】サーバは常に data-only メッセージ（`notification` フィールドなし）で送信する。
 *  送信側ペイロード例:
 *   {
 *     "message": {
 *       "token": "<FCM_TOKEN>",
 *       "android": { "priority": "HIGH" },
 *       "data": {
 *         "title": "新着メッセージ",
 *         "body":  "...",
 *         "target_type": "chat_room",
 *         "target_arg":  "ROOM_123"
 *       }
 *     }
 *   }
 *
 * data-only に統一することで以下が得られる:
 * - フォアグラウンド／バックグラウンド／未起動の 3 状態すべてで [onMessageReceived] が呼ばれ、
 *   通知生成と PendingIntent への extras 埋め込みを本クラス一箇所に集約できる。
 * - OS 側の自動通知表示経路（notification ペイロード）を通らないため、アイコン・タップ挙動・
 *   extras の引き回しが送信経路に依らず常に一致する。
 *
 * タップ後の遷移は [MainActivity] が Intent extras から [NotificationTarget] を復元し、
 * [com.example.messangerapp.notification.NotificationNavigator] 経由で AppNavHost に通知する。
 *
 * 注意: data-only メッセージは Doze モードでの配信保証のため、サーバ側で
 * `android.priority = "HIGH"`（legacy API の `"priority": "high"`）を指定すること。
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // 実運用ではここでサーバー（今回はFirestoreのUsersコレクション等）にトークンを保存する
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            // data-only 運用なので、data が空なら表示すべき内容がない（想定外メッセージ）
            Log.w(TAG, "Empty data payload received; ignored.")
            return
        }
        Log.d(TAG, "Message data payload: $data")

        val title = data[KEY_TITLE] ?: ""
        val body = data[KEY_BODY].orEmpty()

        sendNotification(title, body, data)
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val target = NotificationTarget.fromData(data)
        val channel = channelSpecFor(target)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            NotificationTarget.writeExtras(this, data)
        }

        // request code を通知ごとにユニークにしないと、FLAG_UPDATE_CURRENT を付けても
        // 先行する PendingIntent と同一視され extras が共有されてしまう。
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        ensureChannel(channel)

        val notification = NotificationCompat.Builder(this, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            // pre-O（API 25 以下）でもヘッドアップ表示させるため。O+ ではチャンネル importance が優先される。
            .setPriority(
                if (channel.importance >= NotificationManager.IMPORTANCE_HIGH)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID.incrementAndGet(), notification)
    }

    private fun ensureChannel(spec: ChannelSpec) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 旧チャンネル（IMPORTANCE_DEFAULT 固定で作成されていたもの）は importance を後から上げられないため削除。
        // 存在しない場合は no-op。
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        if (notificationManager.getNotificationChannel(spec.id) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(spec.id, spec.name, spec.importance)
            )
        }
    }

    /**
     * target_type 別の通知チャンネル定義。
     *
     * チャンネル importance はチャンネル作成後に変更できないため、用途の粒度で分割する。
     * ユーザーは OS の設定画面から種類別にミュート可能になる。
     */
    private data class ChannelSpec(
        val id: String,
        val name: String,
        val importance: Int,
    )

    private fun channelSpecFor(target: NotificationTarget?): ChannelSpec = when (target) {
        is NotificationTarget.ChatRoom, NotificationTarget.ChatList ->
            ChannelSpec(CHANNEL_MESSAGES, "メッセージ", NotificationManager.IMPORTANCE_HIGH)
        NotificationTarget.Friends ->
            ChannelSpec(CHANNEL_FRIENDS, "フレンド", NotificationManager.IMPORTANCE_DEFAULT)
        NotificationTarget.Settings ->
            ChannelSpec(CHANNEL_SETTINGS, "お知らせ", NotificationManager.IMPORTANCE_DEFAULT)
        null ->
            ChannelSpec(CHANNEL_GENERAL, "一般", NotificationManager.IMPORTANCE_DEFAULT)
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"

        // 旧 channel (IMPORTANCE_DEFAULT 固定)。起動時に削除される。
        private const val LEGACY_CHANNEL_ID = "messenger_channel_id"

        // target_type 別チャンネル ID（新体系）
        private const val CHANNEL_MESSAGES = "messenger_messages_v1"
        private const val CHANNEL_FRIENDS = "messenger_friends_v1"
        private const val CHANNEL_SETTINGS = "messenger_settings_v1"
        private const val CHANNEL_GENERAL = "messenger_general_v1"

        // data payload の表示文言キー（送信側と合意）
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        private val NOTIFICATION_ID = AtomicInteger(1000)
    }
}
