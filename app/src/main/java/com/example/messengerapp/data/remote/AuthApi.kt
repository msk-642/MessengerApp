package com.example.messengerapp.data.remote

import com.example.messengerapp.data.remote.dto.AuthResponse
import com.example.messengerapp.data.remote.dto.LoginRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse
}
