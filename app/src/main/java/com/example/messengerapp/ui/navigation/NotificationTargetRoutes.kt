package com.example.messengerapp.ui.navigation

import com.example.messengerapp.notification.NotificationTarget

/**
 * 現在表示中の destination に対応する [NotificationTarget] を求める。
 *
 * `AppNavHost` の `navigateToTarget`（遷移先 → route）の逆写像にあたる。
 * 通知で遷移できる画面が増えたときは両方を対で更新すること。
 *
 * 戻り値が null の destination（Splash / 認証フロー / カメラ等）は通知の遷移先になり得ないため、
 * 到達しても通知の自動取り下げは行わない。
 *
 * @param route 現在の destination の route。パターン文字列（例: `chat_room/{roomId}`）を渡す。
 * @param roomId ChatRoom の場合の実引数。
 */
fun notificationTargetOf(route: String?, roomId: String?): NotificationTarget? = when (route) {
    Routes.Main.CHAT_LIST -> NotificationTarget.ChatList
    Routes.Main.FRIENDS_LIST -> NotificationTarget.Friends
    Routes.Main.SETTINGS -> NotificationTarget.Settings
    Routes.Main.CHAT_ROOM -> roomId?.takeIf { it.isNotBlank() }?.let(NotificationTarget::ChatRoom)
    else -> null
}
