package com.example.messengerapp.domain.repository

import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.model.ChatRoom
import com.example.messengerapp.domain.model.Friend
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChatRoomsStream(): Flow<List<ChatRoom>>
    suspend fun getMessages(roomId: String): List<ChatMessage>
    suspend fun getMessagesBefore(roomId: String, beforeMessageId: String, beforeSentAt: Long): List<ChatMessage>
    suspend fun getLastReadAt(roomId: String): Long
    suspend fun sendMessage(roomId: String, body: String): ChatMessage
    suspend fun sendImageMessage(roomId: String, imageBase64: String): ChatMessage
    suspend fun getRoomMembers(roomId: String): List<Friend>
    suspend fun getFriends(): List<Friend>
    suspend fun createGroup(name: String, memberIds: List<String>): Result<ChatRoom>
}
