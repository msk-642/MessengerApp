package com.example.messangerapp.ui.screen.chatlist

import app.cash.turbine.test
import com.example.messangerapp.domain.model.ChatRoom
import com.example.messangerapp.domain.repository.ChatRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val chatRepository: ChatRepository = mockk()
    private lateinit var viewModel: ChatListViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // chatRooms is initialized in the constructor, so we need to mock getChatRoomsStream before initialization
        every { chatRepository.getChatRoomsStream() } returns flowOf(emptyList())
        viewModel = ChatListViewModel(chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `chatRooms flow provides data from repository`() = runTest {
        val mockRooms = listOf(
            ChatRoom(roomId = "1", roomName = "Room 1", lastMessage = "Hello", lastMessageTime = 1000L),
            ChatRoom(roomId = "2", roomName = "Room 2", lastMessage = "Hi", lastMessageTime = 2000L)
        )
        every { chatRepository.getChatRoomsStream() } returns flowOf(mockRooms)
        
        // Re-initialize to pick up the new flow
        val vm = ChatListViewModel(chatRepository)
        
        vm.chatRooms.test {
            assertEquals(mockRooms, awaitItem())
        }
    }

    @Test
    fun `createNewGroup calls repository createGroup`() = runTest {
        coEvery { chatRepository.createGroup(any(), any()) } returns Result.success(mockk())
        
        viewModel.createNewGroup()
        
        coVerify { chatRepository.createGroup(match { it.startsWith("新グループ") }, emptyList()) }
    }
}
