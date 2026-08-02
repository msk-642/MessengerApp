package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.ChatApi
import com.example.messengerapp.data.remote.dto.ChatMessageDto
import com.example.messengerapp.data.remote.dto.ChatRoomDto
import com.example.messengerapp.data.remote.dto.CreateGroupRequest
import com.example.messengerapp.data.remote.dto.FriendDto
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

    /** 取得済みトークルームのキャッシュ。一覧画面が購読する */
    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())

    override fun getChatRoomsStream(): Flow<List<ChatRoom>> = _chatRooms.asStateFlow()

    override suspend fun refreshChatRooms(): Result<Unit> = runCatching {
        fetchChatRooms()
    }

    override suspend fun getRoomName(roomId: String): Result<String> = runCatching {
        _chatRooms.value.firstOrNull { it.roomId == roomId }?.roomName
        // キャッシュが空のまま個別ルームを開いた場合（通知からの遷移など）は取り直す
            ?: fetchChatRooms().firstOrNull { it.roomId == roomId }?.roomName.orEmpty()
    }

    override suspend fun getMessages(roomId: String): Result<List<ChatMessage>> = runCatching {
        chatApi.getMessages(authHeader(), roomId, limit = INITIAL_LOAD_LIMIT)
            .map { it.toDomain() }
    }

    override suspend fun getMessagesBefore(
        roomId: String,
        beforeMessageId: String
    ): Result<List<ChatMessage>> = runCatching {
        chatApi.getMessages(
            token = authHeader(),
            roomId = roomId,
            beforeMessageId = beforeMessageId,
            limit = OLDER_LOAD_LIMIT
        ).map { it.toDomain() }
    }

    override suspend fun getLastReadAt(roomId: String): Result<Long> = runCatching {
        chatApi.getReadState(authHeader(), roomId).lastReadAt
    }

    override suspend fun sendMessage(roomId: String, body: String): Result<ChatMessage> =
        runCatching {
            val message = chatApi.sendMessage(
                token = authHeader(),
                roomId = roomId,
                request = SendMessageRequest(body = body)
            ).toDomain()
            updateChatRoomLastMessage(roomId, body, message.sentAt)
            message
        }

    override suspend fun sendImageMessage(
        roomId: String,
        imageBase64: String
    ): Result<ChatMessage> = runCatching {
        val message = chatApi.sendMessage(
            token = authHeader(),
            roomId = roomId,
            request = SendMessageRequest(
                body = "",
                messageType = MessageType.IMAGE.name,
                imageBase64 = imageBase64
            )
        ).toDomain()
        updateChatRoomLastMessage(roomId, IMAGE_LAST_MESSAGE_LABEL, message.sentAt)
        message
    }

    override suspend fun getRoomMembers(roomId: String): Result<List<Friend>> = runCatching {
        chatApi.getRoomMembers(authHeader(), roomId).map { it.toDomain() }
    }

    override suspend fun getFriends(): Result<List<Friend>> = runCatching {
        chatApi.getFriends(authHeader()).map { it.toDomain() }
    }

    override suspend fun createGroup(name: String, memberIds: List<String>): Result<ChatRoom> =
        runCatching {
            val newRoom = chatApi.createGroup(
                authHeader(),
                CreateGroupRequest(name, memberIds)
            ).toDomain()
            _chatRooms.value = (listOf(newRoom) + _chatRooms.value)
                .distinctBy { it.roomId }
                .sortedByDescending { it.lastMessageTime }
            newRoom
        }

    /** サーバから一覧を取得してキャッシュへ反映し、取得結果を返す */
    private suspend fun fetchChatRooms(): List<ChatRoom> {
        val rooms = chatApi.getChatRooms(authHeader())
            .map { it.toDomain() }
            .sortedByDescending { it.lastMessageTime }
        _chatRooms.value = rooms
        return rooms
    }

    private suspend fun authHeader(): String =
        "Bearer ${authRepository.getSession().token.orEmpty()}"

    /** 送信直後にキャッシュ上の一覧へ最終メッセージを反映する（再取得を待たずに並び順を更新する） */
    private fun updateChatRoomLastMessage(roomId: String, body: String, sentAt: Long) {
        _chatRooms.value = _chatRooms.value.map { room ->
            if (room.roomId == roomId) {
                room.copy(lastMessage = body, lastMessageTime = sentAt)
            } else {
                room
            }
        }.sortedByDescending { it.lastMessageTime }
    }

    private fun ChatRoomDto.toDomain(): ChatRoom = ChatRoom(
        roomId = roomId,
        roomName = roomName,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime,
        unreadCount = unreadCount
    )

    private fun ChatMessageDto.toDomain(): ChatMessage = ChatMessage(
        messageId = messageId,
        roomId = roomId,
        senderId = senderId,
        senderName = senderName,
        body = body,
        sentAt = sentAt,
        // 種別と画像を取り込まないと、取得し直した写真メッセージがテキスト扱いで表示されてしまう
        messageType = if (messageType == MessageType.IMAGE.name) {
            MessageType.IMAGE
        } else {
            MessageType.TEXT
        },
        imageBase64 = imageBase64
    )

    private fun FriendDto.toDomain(): Friend = Friend(
        userId = userId,
        userName = userName,
        statusMessage = statusMessage
    )

    companion object {
        private const val INITIAL_LOAD_LIMIT = 100
        private const val OLDER_LOAD_LIMIT = 50
        private const val IMAGE_LAST_MESSAGE_LABEL = "📷 写真"
    }
}
