package com.example.messangerapp.data.remote.dto

data class LoginRequest(
    val userId: String
)

data class AuthResponse(
    val token: String,
    val userId: String,
    val userName: String
)
