package com.example.messengerapp.ui.screen.chatlist

import com.example.messengerapp.domain.model.ChatRoom
import com.example.messengerapp.domain.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val chatRepository: ChatRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState reflects the rooms published by the repository`() = runTest {
        val rooms = listOf(
            ChatRoom(roomId = "1", roomName = "Room 1", lastMessage = "Hello", lastMessageTime = 1000L),
            ChatRoom(roomId = "2", roomName = "Room 2", lastMessage = "Hi", lastMessageTime = 2000L)
        )
        every { chatRepository.getChatRoomsStream() } returns flowOf(rooms)
        coEvery { chatRepository.refreshChatRooms() } returns Result.success(Unit)

        val viewModel = ChatListViewModel(chatRepository)

        assertEquals(rooms, viewModel.uiState.value.rooms)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `uiState holds an error message when the refresh fails`() = runTest {
        every { chatRepository.getChatRoomsStream() } returns flowOf(emptyList())
        coEvery { chatRepository.refreshChatRooms() } returns Result.failure(IOException("boom"))

        val viewModel = ChatListViewModel(chatRepository)

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh clears the previous error on success`() = runTest {
        every { chatRepository.getChatRoomsStream() } returns flowOf(emptyList())
        coEvery { chatRepository.refreshChatRooms() } returns Result.failure(IOException("boom"))
        val viewModel = ChatListViewModel(chatRepository)
        assertNotNull(viewModel.uiState.value.errorMessage)

        coEvery { chatRepository.refreshChatRooms() } returns Result.success(Unit)
        viewModel.refresh()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createNewGroup calls repository createGroup`() = runTest {
        every { chatRepository.getChatRoomsStream() } returns flowOf(emptyList())
        coEvery { chatRepository.refreshChatRooms() } returns Result.success(Unit)
        coEvery { chatRepository.createGroup(any(), any()) } returns Result.success(mockk())
        val viewModel = ChatListViewModel(chatRepository)

        viewModel.createNewGroup()

        coVerify { chatRepository.createGroup(match { it.startsWith("新グループ") }, emptyList()) }
    }
}
