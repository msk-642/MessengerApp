package com.example.messengerapp.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.model.ChatRoom
import com.example.messengerapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    // リポジトリから取得したトークルームのリスト（降順ソート済みの Flow を StateFlow に変換）
    val chatRooms: StateFlow<List<ChatRoom>> = chatRepository
        .getChatRoomsStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * プロトタイプ用：ランダムな名前のグループを新規作成する
     */
    fun createNewGroup() {
        viewModelScope.launch {
            val randomId = (100..999).random()
            chatRepository.createGroup("新グループ $randomId", emptyList())
        }
    }
}
