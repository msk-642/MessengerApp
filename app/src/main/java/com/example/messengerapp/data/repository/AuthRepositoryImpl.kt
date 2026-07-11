package com.example.messengerapp.data.repository

import androidx.datastore.core.DataStore
import com.example.messengerapp.data.remote.AuthApi
import com.example.messengerapp.data.remote.dto.LoginRequest
import com.example.messengerapp.domain.model.UserSession
import com.example.messengerapp.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val dataStore: DataStore<UserSession>
) : AuthRepository {

    override suspend fun login(userId: String): Result<UserSession> {
        return try {
            val response = authApi.login(LoginRequest(userId = userId))
            val session = UserSession(
                token = response.token,
                userId = response.userId,
                userName = response.userName,
                isAutoLoginEnabled = true
            )
            updateSession(session)
            Result.success(session)
        } catch (e: Exception) {
            // プロトタイプ用フォールバック：サーバーが未起動でもデモ動作可能にする（本番では削除）
            val dummySession = UserSession(
                token = "dummy_token_12345",
                userId = userId,
                userName = "Dummy User ($userId)",
                isAutoLoginEnabled = true
            )
            updateSession(dummySession)
            Result.success(dummySession)
        }
    }

    override suspend fun logout() {
        dataStore.updateData { UserSession() }
    }

    override suspend fun getSession(): UserSession {
        return dataStore.data.first()
    }

    override suspend fun updateSession(session: UserSession) {
        dataStore.updateData { session }
    }
}
