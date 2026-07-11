package com.example.messengerapp.domain.model

data class UserSession(
    val token: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val isAutoLoginEnabled: Boolean = false
)
