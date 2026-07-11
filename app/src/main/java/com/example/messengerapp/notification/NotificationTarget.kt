package com.example.messengerapp.notification

import android.content.Intent

/**
 * プッシュ通知タップ時の遷移先。
 *
 * FCM ペイロードの `data` フィールドで以下の形で送信される想定:
 *   data: { "target_type": "chat_room", "target_arg": "ROOM_123" }
 *
 * - notification ペイロード（+data）でバックグラウンド／未起動時に OS が通知を表示した場合、
 *   ユーザーのタップでランチャーActivityが起動され、`data` の各キーが Intent extras として渡される。
 * - フォアグラウンド時および data-only ペイロードでは [com.example.messengerapp.MyFirebaseMessagingService]
 *   が自前で通知を組み立て、PendingIntent の extras に同じキーを埋め込む。
 *
 * どちらの経路でも Activity 側は [fromIntent] で同じキーから復元できる。
 */
sealed interface NotificationTarget {
    data object ChatList : NotificationTarget
    data object Friends : NotificationTarget
    data object Settings : NotificationTarget
    data class ChatRoom(val roomId: String) : NotificationTarget

    companion object {
        const val KEY_TYPE: String = "target_type"
        const val KEY_ARG: String = "target_arg"

        private const val TYPE_CHAT_LIST = "chat_list"
        private const val TYPE_FRIENDS = "friends"
        private const val TYPE_SETTINGS = "settings"
        private const val TYPE_CHAT_ROOM = "chat_room"

        fun fromData(data: Map<String, String>): NotificationTarget? {
            val type = data[KEY_TYPE] ?: return null
            return resolve(type, data[KEY_ARG])
        }

        fun fromIntent(intent: Intent?): NotificationTarget? {
            val extras = intent?.extras ?: return null
            val type = extras.getString(KEY_TYPE) ?: return null
            return resolve(type, extras.getString(KEY_ARG))
        }

        fun writeExtras(intent: Intent, data: Map<String, String>) {
            data[KEY_TYPE]?.let { intent.putExtra(KEY_TYPE, it) }
            data[KEY_ARG]?.let { intent.putExtra(KEY_ARG, it) }
        }

        private fun resolve(type: String, arg: String?): NotificationTarget? = when (type) {
            TYPE_CHAT_LIST -> ChatList
            TYPE_FRIENDS -> Friends
            TYPE_SETTINGS -> Settings
            TYPE_CHAT_ROOM -> arg?.takeIf { it.isNotBlank() }?.let(::ChatRoom)
            else -> null
        }
    }
}
