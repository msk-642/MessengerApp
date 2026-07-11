package com.example.messengerapp.ui.screen.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun signIn(userId: String) {
        if (userId.isBlank()) {
            _uiState.value = SignInUiState.Error("ユーザーIDを入力してください")
            return
        }
        viewModelScope.launch {
            _uiState.value = SignInUiState.Loading
            val result = authRepository.login(userId)
            _uiState.value = if (result.isSuccess) {
                SignInUiState.Success
            } else {
                SignInUiState.Error(result.exceptionOrNull()?.message ?: "サインインに失敗しました")
            }
        }
    }
}
