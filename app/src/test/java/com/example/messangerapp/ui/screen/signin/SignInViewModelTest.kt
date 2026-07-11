package com.example.messangerapp.ui.screen.signin

import app.cash.turbine.test
import com.example.messangerapp.domain.repository.AuthRepository
import com.example.messangerapp.domain.model.UserSession
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: SignInViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SignInViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signIn success updates state to Success`() = runTest {
        val userId = "testUser"
        coEvery { authRepository.login(userId) } returns Result.success(UserSession(token = "token", userId = userId))

        viewModel.uiState.test {
            assertEquals(SignInUiState.Idle, awaitItem())
            viewModel.signIn(userId)
            runCurrent()
            assertEquals(SignInUiState.Loading, awaitItem())
            runCurrent()
            assertEquals(SignInUiState.Success, awaitItem())
        }
    }

    @Test
    fun `signIn failure updates state to Error`() = runTest {
        val userId = "testUser"
        val errorMessage = "Login failed"
        coEvery { authRepository.login(userId) } returns Result.failure(Exception(errorMessage))

        viewModel.uiState.test {
            assertEquals(SignInUiState.Idle, awaitItem())
            viewModel.signIn(userId)
            runCurrent()
            assertEquals(SignInUiState.Loading, awaitItem())
            runCurrent()
            val state = awaitItem()
            assert(state is SignInUiState.Error)
            assertEquals(errorMessage, (state as SignInUiState.Error).message)
        }
    }

    @Test
    fun `signIn with blank userId updates state to Error`() = runTest {
        viewModel.uiState.test {
            assertEquals(SignInUiState.Idle, awaitItem())
            viewModel.signIn("")
            val state = awaitItem()
            assert(state is SignInUiState.Error)
            assertEquals("ユーザーIDを入力してください", (state as SignInUiState.Error).message)
        }
    }
}
