package com.example.messangerapp.ui.screen.signin

sealed class SignInUiState {
    data object Idle : SignInUiState()
    data object Loading : SignInUiState()
    data object Success : SignInUiState()
    data class Error(val message: String) : SignInUiState()
}
