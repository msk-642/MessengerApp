package com.example.messengerapp.ui.screen.friends

import com.example.messengerapp.domain.model.Friend

/** 友だち一覧画面の UI 状態 */
data class FriendsUiState(
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = false,
    /** null 以外なら取得に失敗している。再試行ボタンとともに表示する */
    val errorMessage: String? = null
)
