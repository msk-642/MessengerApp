package com.example.messengerapp.data.remote

import com.example.messengerapp.data.remote.dto.ChatMessageDto
import com.example.messengerapp.data.remote.dto.ChatRoomDto
import com.example.messengerapp.data.remote.dto.ChatRoomReadStateDto
import com.example.messengerapp.data.remote.dto.CreateGroupRequest
import com.example.messengerapp.data.remote.dto.FriendDto
import com.example.messengerapp.data.remote.dto.SendMessageRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {

    @GET("api/v1/chats")
    suspend fun getChatRooms(
        @Header("Authorization") token: String
    ): List<ChatRoomDto>

    @GET("api/v1/friends")
    suspend fun getFriends(
        @Header("Authorization") token: String
    ): List<FriendDto>

    @GET("api/v1/chats/{roomId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        // null の場合はパラメータ自体が送信されず、サーバは最新メッセージから limit 件を返す
        @Query("beforeMessageId") beforeMessageId: String? = null,
        @Query("limit") limit: Int
    ): List<ChatMessageDto>

    @GET("api/v1/chats/{roomId}/read-state")
    suspend fun getReadState(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): ChatRoomReadStateDto

    @POST("api/v1/chats/{roomId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: SendMessageRequest
    ): ChatMessageDto

    @GET("api/v1/chats/{roomId}/members")
    suspend fun getRoomMembers(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): List<FriendDto>

    @POST("api/v1/chats")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): ChatRoomDto
}
