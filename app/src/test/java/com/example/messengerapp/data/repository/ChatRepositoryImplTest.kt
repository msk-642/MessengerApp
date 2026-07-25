package com.example.messengerapp.data.repository

import com.example.messengerapp.data.remote.ChatApi
import com.example.messengerapp.domain.model.UserSession
import com.example.messengerapp.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryImplTest {

    private val chatApi: ChatApi = mockk()
    private val authRepository: AuthRepository = mockk()
    private val repository = ChatRepositoryImpl(chatApi, authRepository)

    @Test
    fun `getRoomName returns the name of the matching room`() = runTest {
        // プロトタイプ用モックルーム（room_0 = "トークルーム 1"）から引ける
        val rooms = repository.getChatRoomsStream().first()
        val target = rooms.first()

        assertEquals(target.roomName, repository.getRoomName(target.roomId))
    }

    @Test
    fun `getRoomName returns empty string for unknown roomId`() = runTest {
        // 該当なしでハング／例外にならないこと（旧実装はここでサスペンドしっぱなしになった）
        assertEquals("", repository.getRoomName("no_such_room"))
    }

    @Test
    fun `getRoomName reflects a newly created group`() = runTest {
        coEvery { authRepository.getSession() } returns UserSession(token = "token123")
        coEvery { chatApi.createGroup(any(), any()) } throws Exception("Network error")

        val created = repository.createGroup("新グループ 123", emptyList()).getOrThrow()

        assertEquals("新グループ 123", repository.getRoomName(created.roomId))
    }
}
