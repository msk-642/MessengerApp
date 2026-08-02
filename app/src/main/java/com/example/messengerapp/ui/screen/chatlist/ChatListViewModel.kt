package com.example.messengerapp.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.repository.ChatRepository
import com.example.messengerapp.util.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        // 送信・グループ作成でリポジトリのキャッシュが更新されたら一覧へ反映する
        viewModelScope.launch {
            chatRepository.getChatRoomsStream().collect { rooms ->
                _uiState.update { it.copy(rooms = rooms) }
            }
        }
        refresh()
    }

    /** サーバからトークルーム一覧を取得し直す（初回表示・再試行の両方から呼ばれる） */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = chatRepository.refreshChatRooms()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.let(ErrorMessages::from)
                )
            }
        }
    }

    /**
     * プロトタイプ用：ランダムな名前のグループを新規作成する
     */
    fun createNewGroup() {
        viewModelScope.launch {
            val randomId = (100..999).random()
            val result = chatRepository.createGroup("新グループ $randomId", emptyList())
            result.exceptionOrNull()?.let { throwable ->
                _uiState.update { it.copy(errorMessage = ErrorMessages.from(throwable)) }
            }
        }
    }
}
