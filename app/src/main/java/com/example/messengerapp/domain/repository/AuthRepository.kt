package com.example.messengerapp.domain.repository

import com.example.messengerapp.domain.model.UserSession

interface AuthRepository {
    suspend fun login(userId: String): Result<UserSession>
    suspend fun logout()
    suspend fun getSession(): UserSession
    suspend fun updateSession(session: UserSession)
}
