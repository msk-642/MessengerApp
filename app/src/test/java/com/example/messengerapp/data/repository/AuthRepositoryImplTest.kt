package com.example.messengerapp.data.repository

import androidx.datastore.core.DataStore
import com.example.messengerapp.data.remote.AuthApi
import com.example.messengerapp.data.remote.dto.AuthResponse
import com.example.messengerapp.data.remote.dto.LoginRequest
import com.example.messengerapp.domain.model.UserSession
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryImplTest {

    private val authApi: AuthApi = mockk()
    private val dataStore: DataStore<UserSession> = mockk()
    private val repository = AuthRepositoryImpl(authApi, dataStore)

    @Test
    fun `login success updates dataStore and returns success`() = runTest {
        val userId = "testUser"
        val response = AuthResponse(token = "token123", userId = userId, userName = "Test User")
        val expectedSession = UserSession(token = "token123", userId = userId, userName = "Test User", isAutoLoginEnabled = true)

        coEvery { authApi.login(any()) } returns response
        coEvery { dataStore.updateData(any()) } returns expectedSession

        val result = repository.login(userId)

        assert(result.isSuccess)
        assertEquals(expectedSession, result.getOrNull())
        coVerify { dataStore.updateData(any()) }
    }

    @Test
    fun `login failure with exception falls back to dummy session`() = runTest {
        val userId = "testUser"
        coEvery { authApi.login(any()) } throws Exception("Network error")
        coEvery { dataStore.updateData(any()) } returns UserSession() // dummy return

        val result = repository.login(userId)

        assert(result.isSuccess)
        assertEquals("dummy_token_12345", result.getOrNull()?.token)
        coVerify { dataStore.updateData(any()) }
    }

    @Test
    fun `logout clears dataStore`() = runTest {
        coEvery { dataStore.updateData(any()) } returns UserSession()

        repository.logout()

        coVerify { dataStore.updateData(any()) }
    }

    @Test
    fun `getSession returns current session from dataStore`() = runTest {
        val expectedSession = UserSession(token = "token123", userId = "user1")
        every { dataStore.data } returns flowOf(expectedSession)

        val result = repository.getSession()

        assertEquals(expectedSession, result)
    }
}
