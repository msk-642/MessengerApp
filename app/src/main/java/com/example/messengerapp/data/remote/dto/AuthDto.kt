package com.example.messengerapp.data.remote.dto

data class LoginRequest(
    val userId: String
)

data class AuthResponse(
    val token: String,
    val userId: String,
    val userName: String
)
