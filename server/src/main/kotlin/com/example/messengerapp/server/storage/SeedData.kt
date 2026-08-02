package com.example.messengerapp.server.storage

import com.example.messengerapp.server.model.ChatMessageDto
import com.example.messengerapp.server.model.ChatRoomDto
import com.example.messengerapp.server.model.FriendDto
import com.example.messengerapp.server.model.ServerState

/**
 * 初期データ。これまで :app 側の ChatRepositoryImpl がフォールバックとして生成していた
 * モックデータと同等のものをサーバの初期状態として投入する。
 *
 * DB ファイルが存在しない初回起動時のみ使われる。作り直したい場合は
 * server/data/messenger-db.json を削除してから起動する。
 */
object SeedData {

    /** シードのメッセージで「自分」側の送信者として使う userId */
    const val SELF_USER_ID = "me"

    /** シードのメッセージで「相手」側の送信者として使う userId */
    const val OTHER_USER_ID = "other"

    private const val ROOM_COUNT = 15
    private const val MESSAGES_PER_ROOM = 20
    private const val FRIEND_COUNT = 10
    private const val DAY_MILLIS = 24 * 60 * 60_000L
    private const val UNREAD_OFFSET_MILLIS = 5 * 60_000L

    fun create(now: Long = System.currentTimeMillis()): ServerState {
        val rooms = createRooms(now)
        val messages = rooms.associate { it.roomId to createMessages(it.roomId, now) }
        val members = rooms.associate { it.roomId to listOf(SELF_USER_ID, OTHER_USER_ID) }

        // 最新から数件が未読になる位置に既読日時を置く（未読境界の表示確認用）
        val readStates = rooms.flatMap { room ->
            listOf(SELF_USER_ID, OTHER_USER_ID).map { userId ->
                ServerState.readStateKey(room.roomId, userId) to (now - UNREAD_OFFSET_MILLIS)
            }
        }.toMap()

        val friends = createFriends()
        val users = buildMap {
            put(SELF_USER_ID, "自分")
            put(OTHER_USER_ID, "相手")
            friends.forEach { put(it.userId, it.userName) }
        }

        return ServerState(
            users = users,
            friends = friends,
            rooms = rooms,
            messages = messages,
            roomMembers = members,
            readStates = readStates
        )
    }

    private fun createRooms(now: Long): List<ChatRoomDto> =
        List(ROOM_COUNT) { index ->
            ChatRoomDto(
                roomId = "room_$index",
                roomName = "トークルーム ${index + 1}",
                lastMessage = "最新のメッセージ内容 $index",
                lastMessageTime = now - (index * 3_600_000L),
                unreadCount = if (index % 3 == 0) index + 1 else 0
            )
        }.sortedByDescending { it.lastMessageTime }

    /** 日付区切りの確認用に「今日8件 / 昨日6件 / 一昨日6件」へ分散させる */
    private fun createMessages(roomId: String, now: Long): List<ChatMessageDto> =
        List(MESSAGES_PER_ROOM) { index ->
            val sentAt = when {
                index < 8 -> now - (index * 60_000L)
                index < 14 -> now - DAY_MILLIS - ((index - 8) * 60_000L)
                else -> now - (2 * DAY_MILLIS) - ((index - 14) * 60_000L)
            }
            val isSelf = index % 2 == 0
            ChatMessageDto(
                messageId = "msg_${roomId}_$index",
                roomId = roomId,
                senderId = if (isSelf) SELF_USER_ID else OTHER_USER_ID,
                senderName = if (isSelf) "自分" else "相手",
                body = "モックメッセージ $index",
                sentAt = sentAt
            )
        }.sortedBy { it.sentAt }

    private fun createFriends(): List<FriendDto> =
        List(FRIEND_COUNT) { index ->
            FriendDto(
                userId = "user_$index",
                userName = "友達 ${index + 1}",
                statusMessage = "ステータスメッセージ ${index + 1}"
            )
        }
}
