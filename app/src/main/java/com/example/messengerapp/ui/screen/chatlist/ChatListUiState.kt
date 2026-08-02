package com.example.messengerapp.ui.screen.chatlist

import com.example.messengerapp.domain.model.ChatRoom

/** トークルーム一覧画面の UI 状態 */
data class ChatListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = false,
    /** null 以外なら取得に失敗している。再試行ボタンとともに表示する */
    val errorMessage: String? = null
)
