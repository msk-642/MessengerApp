package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.ChatApi
import com.example.messengerapp.data.remote.dto.ChatMessageDto
import com.example.messengerapp.data.remote.dto.ChatRoomDto
import com.example.messengerapp.domain.model.MessageType
import com.example.messengerapp.domain.model.UserSession
import com.example.messengerapp.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ChatRepositoryImplTest {

    private val chatApi: ChatApi = mockk()
    private val authRepository: AuthRepository = mockk()
    private val repository = ChatRepositoryImpl(chatApi, authRepository)

    @Before
    fun setup() {
        coEvery { authRepository.getSession() } returns UserSession(token = "token123")
    }

    @Test
    fun `refreshChatRooms publishes rooms sorted by lastMessageTime desc`() = runTest {
        coEvery { chatApi.getChatRooms(any()) } returns listOf(
            room("room_0", "古い部屋", lastMessageTime = 1_000L),
            room("room_1", "新しい部屋", lastMessageTime = 3_000L)
        )

        assertTrue(repository.refreshChatRooms().isSuccess)

        val rooms = repository.getChatRoomsStream().first()
        assertEquals(listOf("room_1", "room_0"), rooms.map { it.roomId })
    }

    @Test
    fun `refreshChatRooms returns failure when the api fails`() = runTest {
        coEvery { chatApi.getChatRooms(any()) } throws IOException("Network error")

        val result = repository.refreshChatRooms()

        // モックへフォールバックせず、失敗がそのまま呼び出し元へ伝わること
        assertTrue(result.isFailure)
        assertTrue(repository.getChatRoomsStream().first().isEmpty())
    }

    @Test
    fun `getRoomName fetches from the server when the cache is empty`() = runTest {
        coEvery { chatApi.getChatRooms(any()) } returns listOf(room("room_0", "トークルーム 1"))

        assertEquals("トークルーム 1", repository.getRoomName("room_0").getOrThrow())
    }

    @Test
    fun `getRoomName returns empty string for unknown roomId`() = runTest {
        coEvery { chatApi.getChatRooms(any()) } returns listOf(room("room_0", "トークルーム 1"))

        assertEquals("", repository.getRoomName("no_such_room").getOrThrow())
    }

    @Test
    fun `getRoomName reflects a newly created group`() = runTest {
        coEvery { chatApi.createGroup(any(), any()) } returns room("room_new", "新グループ 123")

        val created = repository.createGroup("新グループ 123", emptyList()).getOrThrow()

        assertEquals("新グループ 123", repository.getRoomName(created.roomId).getOrThrow())
    }

    @Test
    fun `getMessages maps the image message type and payload`() = runTest {
        coEvery { chatApi.getMessages(any(), any(), any(), any()) } returns listOf(
            message("m1", messageType = "TEXT"),
            message("m2", messageType = "IMAGE", imageBase64 = "base64data")
        )

        val messages = repository.getMessages("room_0").getOrThrow()

        assertEquals(MessageType.TEXT, messages[0].messageType)
        assertEquals(MessageType.IMAGE, messages[1].messageType)
        assertEquals("base64data", messages[1].imageBase64)
    }

    @Test
    fun `sendMessage updates the cached last message`() = runTest {
        coEvery { chatApi.getChatRooms(any()) } returns listOf(
            room("room_0", "トークルーム 1", lastMessageTime = 1_000L)
        )
        coEvery { chatApi.sendMessage(any(), any(), any()) } returns
            message("m1", body = "こんにちは", sentAt = 9_000L)
        repository.refreshChatRooms()

        repository.sendMessage("room_0", "こんにちは").getOrThrow()

        val cached = repository.getChatRoomsStream().first().first()
        assertEquals("こんにちは", cached.lastMessage)
        assertEquals(9_000L, cached.lastMessageTime)
    }

    @Test
    fun `getFriends returns failure when the api fails`() = runTest {
        coEvery { chatApi.getFriends(any()) } throws IOException("Network error")

        assertTrue(repository.getFriends().isFailure)
    }

    private fun room(
        roomId: String,
        roomName: String,
        lastMessageTime: Long = 0L
    ): ChatRoomDto = ChatRoomDto(
        roomId = roomId,
        roomName = roomName,
        lastMessage = "最終メッセージ",
        lastMessageTime = lastMessageTime,
        unreadCount = 0
    )

    private fun message(
        messageId: String,
        body: String = "本文",
        sentAt: Long = 0L,
        messageType: String? = null,
        imageBase64: String? = null
    ): ChatMessageDto = ChatMessageDto(
        messageId = messageId,
        roomId = "room_0",
        senderId = "me",
        senderName = "自分",
        body = body,
        sentAt = sentAt,
        messageType = messageType,
        imageBase64 = imageBase64
    )
}
