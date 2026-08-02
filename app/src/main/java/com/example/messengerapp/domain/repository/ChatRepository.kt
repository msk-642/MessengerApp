package com.example.messengerapp.domain.repository

import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.model.ChatRoom
import com.example.messengerapp.domain.model.Friend
import kotlinx.coroutines.flow.Flow

/**
 * 通信を伴う操作はすべて [Result] を返す。
 * 失敗を握りつぶさず、呼び出し元（ViewModel）がエラー表示を判断する。
 */
interface ChatRepository {

    /** 取得済みトークルームのキャッシュ。[refreshChatRooms] の結果が流れる */
    fun getChatRoomsStream(): Flow<List<ChatRoom>>

    /** サーバからトークルーム一覧を取得してキャッシュへ反映する */
    suspend fun refreshChatRooms(): Result<Unit>

    suspend fun getRoomName(roomId: String): Result<String>
    suspend fun getMessages(roomId: String): Result<List<ChatMessage>>
    /** [beforeMessageId] より古いメッセージを取得する（過去メッセージの追加読み込み） */
    suspend fun getMessagesBefore(
        roomId: String,
        beforeMessageId: String
    ): Result<List<ChatMessage>>

    suspend fun getLastReadAt(roomId: String): Result<Long>
    suspend fun sendMessage(roomId: String, body: String): Result<ChatMessage>
    suspend fun sendImageMessage(roomId: String, imageBase64: String): Result<ChatMessage>
    suspend fun getRoomMembers(roomId: String): Result<List<Friend>>
    suspend fun getFriends(): Result<List<Friend>>
    suspend fun createGroup(name: String, memberIds: List<String>): Result<ChatRoom>
}
