package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.ChatApi
import com.example.messengerapp.data.remote.dto.CreateGroupRequest
import com.example.messengerapp.data.remote.dto.SendMessageRequest
import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.model.ChatRoom
import com.example.messengerapp.domain.model.Friend
import com.example.messengerapp.domain.model.MessageType
import com.example.messengerapp.domain.repository.AuthRepository
import com.example.messengerapp.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val authRepository: AuthRepository
) : ChatRepository {

    private val _chatRooms = MutableStateFlow(createMockRooms())

    override fun getChatRoomsStream(): Flow<List<ChatRoom>> = _chatRooms.asStateFlow()

    override suspend fun getMessages(roomId: String): List<ChatMessage> {
        return try {
            val token = authRepository.getSession().token ?: ""
            chatApi.getMessages("Bearer $token", roomId, limit = INITIAL_LOAD_LIMIT).map {
                ChatMessage(
                    messageId = it.messageId,
                    roomId = it.roomId,
                    senderId = it.senderId,
                    senderName = it.senderName,
                    body = it.body,
                    sentAt = it.sentAt
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            // 日付区切りの実機試験用に「今日8件 / 昨日6件 / 一昨日6件」に分散する
            val now = System.currentTimeMillis()
            List(20) { index ->
                val sentAt = when {
                    index < 8 -> now - (index * 60_000L)
                    index < 14 -> now - DAY_MILLIS - ((index - 8) * 60_000L)
                    else -> now - (2 * DAY_MILLIS) - ((index - 14) * 60_000L)
                }
                ChatMessage(
                    messageId = "msg_${roomId}_$index",
                    roomId = roomId,
                    senderId = if (index % 2 == 0) "me" else "other",
                    senderName = if (index % 2 == 0) "自分" else "相手",
                    body = "モックメッセージ $index",
                    sentAt = sentAt
                )
            }.sortedBy { it.sentAt }
        }
    }

    override suspend fun getLastReadAt(roomId: String): Long {
        return try {
            val token = authRepository.getSession().token ?: ""
            chatApi.getReadState("Bearer $token", roomId).lastReadAt
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            // 初期モックの新しい方の数件が未読になる位置に既読日時を置く
            System.currentTimeMillis() - 5 * 60_000L
        }
    }

    override suspend fun getMessagesBefore(
        roomId: String,
        beforeMessageId: String,
        beforeSentAt: Long
    ): List<ChatMessage> {
        return try {
            val token = authRepository.getSession().token ?: ""
            chatApi.getMessages(
                token = "Bearer $token",
                roomId = roomId,
                beforeMessageId = beforeMessageId,
                limit = OLDER_LOAD_LIMIT
            ).map {
                ChatMessage(
                    messageId = it.messageId,
                    roomId = it.roomId,
                    senderId = it.senderId,
                    senderName = it.senderName,
                    body = it.body,
                    sentAt = it.sentAt
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）: 基点より古いメッセージを50件生成
            // 日付区切りの実機試験用に3時間刻み（1回のプルで約6日分さかのぼる）
            List(OLDER_LOAD_LIMIT) { index ->
                ChatMessage(
                    messageId = "old_${roomId}_${beforeSentAt}_$index",
                    roomId = roomId,
                    senderId = if (index % 2 == 0) "me" else "other",
                    senderName = if (index % 2 == 0) "自分" else "相手",
                    body = generateRandomBody(),
                    sentAt = beforeSentAt - ((index + 1) * 3 * 60 * 60_000L)
                )
            }.sortedBy { it.sentAt }
        }
    }

    // プロトタイプ用フォールバック（本番では削除）
    private fun generateRandomBody(): String {
        val openings = listOf(
            "おはよう！", "おつかれさま。", "そういえば、", "ねえねえ、", "ところで、",
            "了解！", "ごめん遅くなった、", "今ちょうど", "さっきの件だけど、", "うん、"
        )
        val topics = listOf(
            "週末の予定どうする？", "新しいカフェ見つけたよ。", "資料できたから確認お願い。",
            "電車が遅れてて遅刻しそう。", "今日のランチおすすめある？", "その話めっちゃ面白いね。",
            "写真あとで送るね。", "会議の時間変わったって。", "天気良いから散歩してる。",
            "夜ごはん何にしようかな。"
        )
        return openings.random() + topics.random()
    }

    override suspend fun sendMessage(roomId: String, body: String): ChatMessage {
        return try {
            val session = authRepository.getSession()
            val response = chatApi.sendMessage(
                token = "Bearer ${session.token ?: ""}",
                roomId = roomId,
                request = SendMessageRequest(body = body)
            )
            val newMessage = ChatMessage(
                messageId = response.messageId,
                roomId = response.roomId,
                senderId = response.senderId,
                senderName = response.senderName,
                body = response.body,
                sentAt = response.sentAt
            )
            updateChatRoomLastMessage(roomId, body, newMessage.sentAt)
            newMessage
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            val session = authRepository.getSession()
            val newMessage = ChatMessage(
                messageId = "msg_${System.currentTimeMillis()}",
                roomId = roomId,
                senderId = session.userId ?: "me",
                senderName = session.userName ?: "自分",
                body = body,
                sentAt = System.currentTimeMillis()
            )
            updateChatRoomLastMessage(roomId, body, newMessage.sentAt)
            newMessage
        }
    }

    override suspend fun sendImageMessage(roomId: String, imageBase64: String): ChatMessage {
        return try {
            val session = authRepository.getSession()
            val response = chatApi.sendMessage(
                token = "Bearer ${session.token ?: ""}",
                roomId = roomId,
                request = SendMessageRequest(
                    body = "",
                    messageType = MessageType.IMAGE.name,
                    imageBase64 = imageBase64
                )
            )
            val newMessage = ChatMessage(
                messageId = response.messageId,
                roomId = response.roomId,
                senderId = response.senderId,
                senderName = response.senderName,
                body = response.body,
                sentAt = response.sentAt,
                messageType = MessageType.IMAGE,
                imageBase64 = response.imageBase64 ?: imageBase64
            )
            updateChatRoomLastMessage(roomId, IMAGE_LAST_MESSAGE_LABEL, newMessage.sentAt)
            newMessage
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            val session = authRepository.getSession()
            val newMessage = ChatMessage(
                messageId = "msg_${System.currentTimeMillis()}",
                roomId = roomId,
                senderId = session.userId ?: "me",
                senderName = session.userName ?: "自分",
                body = "",
                sentAt = System.currentTimeMillis(),
                messageType = MessageType.IMAGE,
                imageBase64 = imageBase64
            )
            updateChatRoomLastMessage(roomId, IMAGE_LAST_MESSAGE_LABEL, newMessage.sentAt)
            newMessage
        }
    }

    override suspend fun getFriends(): List<Friend> {
        return try {
            val token = authRepository.getSession().token ?: ""
            chatApi.getFriends("Bearer $token").map {
                Friend(it.userId, it.userName, it.statusMessage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            List(10) { index ->
                Friend("user_$index", "友達 ${index + 1}", "ステータスメッセージ ${index + 1}")
            }
        }
    }

    override suspend fun createGroup(name: String, memberIds: List<String>): Result<ChatRoom> {
        return try {
            val token = authRepository.getSession().token ?: ""
            val response = chatApi.createGroup("Bearer $token", CreateGroupRequest(name, memberIds))
            val newRoom = ChatRoom(
                roomId = response.roomId,
                roomName = response.roomName,
                lastMessage = response.lastMessage,
                lastMessageTime = response.lastMessageTime,
                unreadCount = response.unreadCount
            )
            _chatRooms.value = (listOf(newRoom) + _chatRooms.value)
                .sortedByDescending { it.lastMessageTime }
            Result.success(newRoom)
        } catch (e: Exception) {
            e.printStackTrace()
            // プロトタイプ用フォールバック（本番では削除）
            val mockRoom = ChatRoom(
                roomId = "room_${System.currentTimeMillis()}",
                roomName = name,
                lastMessage = "グループを作成しました",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
            _chatRooms.value = (listOf(mockRoom) + _chatRooms.value)
                .sortedByDescending { it.lastMessageTime }
            Result.success(mockRoom)
        }
    }

    private fun updateChatRoomLastMessage(roomId: String, body: String, sentAt: Long) {
        _chatRooms.value = _chatRooms.value.map { room ->
            if (room.roomId == roomId) room.copy(lastMessage = body, lastMessageTime = sentAt) else room
        }.sortedByDescending { it.lastMessageTime }
    }

    private fun createMockRooms(): List<ChatRoom> {
        val now = System.currentTimeMillis()
        return List(15) { index ->
            ChatRoom(
                roomId = "room_$index",
                roomName = "トークルーム ${index + 1}",
                lastMessage = "最新のメッセージ内容 $index",
                lastMessageTime = now - (index * 3600000L),
                unreadCount = if (index % 3 == 0) index + 1 else 0
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    companion object {
        private const val INITIAL_LOAD_LIMIT = 100
        private const val OLDER_LOAD_LIMIT = 50
        private const val DAY_MILLIS = 24 * 60 * 60_000L
        private const val IMAGE_LAST_MESSAGE_LABEL = "📷 写真"
    }
}
