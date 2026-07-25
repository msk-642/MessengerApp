package com.example.messengerapp.ui.navigation

import com.example.messengerapp.notification.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetRoutesTest {

    // --- 通知で遷移できる 4 画面 ---

    @Test
    fun `チャット一覧の route は ChatList になる`() {
        assertEquals(
            NotificationTarget.ChatList,
            notificationTargetOf(Routes.Main.CHAT_LIST, roomId = null)
        )
    }

    @Test
    fun `フレンド一覧の route は Friends になる`() {
        assertEquals(
            NotificationTarget.Friends,
            notificationTargetOf(Routes.Main.FRIENDS_LIST, roomId = null)
        )
    }

    @Test
    fun `設定の route は Settings になる`() {
        assertEquals(
            NotificationTarget.Settings,
            notificationTargetOf(Routes.Main.SETTINGS, roomId = null)
        )
    }

    @Test
    fun `チャットルームの route は roomId を保持した ChatRoom になる`() {
        assertEquals(
            NotificationTarget.ChatRoom("ROOM_123"),
            notificationTargetOf(Routes.Main.CHAT_ROOM, roomId = "ROOM_123")
        )
    }

    // --- ChatRoom の引数欠落 ---

    @Test
    fun `チャットルームで roomId が null なら null`() {
        assertNull(notificationTargetOf(Routes.Main.CHAT_ROOM, roomId = null))
    }

    @Test
    fun `チャットルームで roomId が空白のみなら null`() {
        assertNull(notificationTargetOf(Routes.Main.CHAT_ROOM, roomId = ""))
        assertNull(notificationTargetOf(Routes.Main.CHAT_ROOM, roomId = "  "))
    }

    @Test
    fun `roomId が異なれば別の ChatRoom として扱われる`() {
        val opened = notificationTargetOf(Routes.Main.CHAT_ROOM, roomId = "ROOM_123")

        assertEquals(NotificationTarget.ChatRoom("ROOM_123"), opened)
        // 厳密一致のため、別ルームの通知は取り下げ対象にならない
        assert(opened != NotificationTarget.ChatRoom("ROOM_456"))
    }

    // --- 対象外 destination ---

    @Test
    fun `通知の遷移先になり得ない route は null`() {
        assertNull(notificationTargetOf(Routes.SPLASH, roomId = null))
        assertNull(notificationTargetOf(Routes.Auth.SIGN_IN, roomId = null))
        assertNull(notificationTargetOf(Routes.Auth.GRAPH, roomId = null))
        assertNull(notificationTargetOf(Routes.Main.GRAPH, roomId = null))
    }

    @Test
    fun `route が null なら null`() {
        assertNull(notificationTargetOf(null, roomId = null))
    }

    @Test
    fun `パターンではない実 route 文字列は一致しない`() {
        // NavDestination.route はパターン文字列を返すため、実引数入りの文字列は想定外の入力
        assertNull(notificationTargetOf("chat_room/ROOM_123", roomId = "ROOM_123"))
    }
}
