package com.example.messengerapp.domain.model

data class ChatRoom(
    val roomId: String,
    val roomName: String,
    val lastMessage: String,
    val lastMessageTime: Long,  // epoch millis
    val unreadCount: Int = 0
)

/** メッセージ種別 */
enum class MessageType {
    TEXT,
    IMAGE
}

data class ChatMessage(
    val messageId: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val sentAt: Long,  // epoch millis
    val messageType: MessageType = MessageType.TEXT,
    /** IMAGE のとき JPEG 画像の Base64 文字列 */
    val imageBase64: String? = null
)

data class Friend(
    val userId: String,
    val userName: String,
    val statusMessage: String?
)
