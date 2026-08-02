package com.example.messengerapp.ui.screen.friends

import com.example.messengerapp.domain.model.Friend
import com.example.messengerapp.domain.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

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
    fun `uiState holds the friends returned by the repository`() = runTest {
        val friends = listOf(Friend(userId = "u1", userName = "友だち 1", statusMessage = null))
        coEvery { chatRepository.getFriends() } returns Result.success(friends)

        val viewModel = FriendsViewModel(chatRepository)

        assertEquals(friends, viewModel.uiState.value.friends)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `uiState holds an error message when the fetch fails`() = runTest {
        coEvery { chatRepository.getFriends() } returns Result.failure(IOException("boom"))

        val viewModel = FriendsViewModel(chatRepository)

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadFriends clears the previous error on success`() = runTest {
        coEvery { chatRepository.getFriends() } returns Result.failure(IOException("boom"))
        val viewModel = FriendsViewModel(chatRepository)
        assertNotNull(viewModel.uiState.value.errorMessage)

        coEvery { chatRepository.getFriends() } returns Result.success(emptyList())
        viewModel.loadFriends()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
