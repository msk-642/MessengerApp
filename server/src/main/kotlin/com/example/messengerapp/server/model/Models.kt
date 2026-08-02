package com.example.messengerapp.server.model

import kotlinx.serialization.Serializable

/*
 * クライアント（:app）の data/remote/dto と JSON 互換なモデル群。
 * :server は JVM、:app は Android のためモジュール依存は張れない。
 * 結合点はフィールド名だけなので、変更時は双方を揃えること。
 */

@Serializable
data class LoginRequest(
    val userId: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val userName: String
)

@Serializable
data class SendMessageRequest(
    val body: String,
    val messageType: String = "TEXT",
    val imageBase64: String? = null
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val memberIds: List<String>
)

@Serializable
data class ChatMessageDto(
    val messageId: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val sentAt: Long,
    val messageType: String = "TEXT",
    val imageBase64: String? = null
)

@Serializable
data class ChatRoomDto(
    val roomId: String,
    val roomName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)

@Serializable
data class ChatRoomReadStateDto(
    val roomId: String,
    val lastReadAt: Long
)

@Serializable
data class FriendDto(
    val userId: String,
    val userName: String,
    val statusMessage: String? = null
)

/** 異常系のレスポンス本文 */
@Serializable
data class ErrorResponse(
    val message: String
)

/**
 * サーバが保持する全データ。これ一つを JSON ファイルへ丸ごと永続化する。
 * DB へ差し替える場合もこの形を [com.example.messengerapp.server.storage.ServerStore] の中で解けばよい。
 */
@Serializable
data class ServerState(
    /** userId -> userName */
    val users: Map<String, String> = emptyMap(),
    val friends: List<FriendDto> = emptyList(),
    val rooms: List<ChatRoomDto> = emptyList(),
    /** roomId -> メッセージ（sentAt 昇順） */
    val messages: Map<String, List<ChatMessageDto>> = emptyMap(),
    /** roomId -> 参加メンバーの userId */
    val roomMembers: Map<String, List<String>> = emptyMap(),
    /** "roomId|userId" -> 既読日時 */
    val readStates: Map<String, Long> = emptyMap(),
    /** token -> userId */
    val tokens: Map<String, String> = emptyMap()
) {
    companion object {
        fun readStateKey(roomId: String, userId: String): String = "$roomId|$userId"
    }
}
