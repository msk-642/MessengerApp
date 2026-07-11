package com.example.messangerapp.domain.repository

import com.example.messangerapp.domain.model.ChatMessage
import com.example.messangerapp.domain.model.ChatRoom
import com.example.messangerapp.domain.model.Friend
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChatRoomsStream(): Flow<List<ChatRoom>>
    suspend fun getMessages(roomId: String): List<ChatMessage>
    suspend fun getMessagesBefore(roomId: String, beforeMessageId: String, beforeSentAt: Long): List<ChatMessage>
    suspend fun getLastReadAt(roomId: String): Long
    suspend fun sendMessage(roomId: String, body: String): ChatMessage
    suspend fun getFriends(): List<Friend>
    suspend fun createGroup(name: String, memberIds: List<String>): Result<ChatRoom>
}
