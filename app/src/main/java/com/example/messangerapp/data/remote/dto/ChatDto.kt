package com.example.messangerapp.data.remote.dto

data class SendMessageRequest(
    val body: String
)

data class CreateGroupRequest(
    val name: String,
    val memberIds: List<String>
)

data class ChatMessageDto(
    val messageId: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val sentAt: Long
)

data class ChatRoomDto(
    val roomId: String,
    val roomName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)

data class ChatRoomReadStateDto(
    val roomId: String,
    val lastReadAt: Long
)

data class FriendDto(
    val userId: String,
    val userName: String,
    val statusMessage: String?
)
