package com.example.messengerapp.ui.screen.settings

import com.example.messengerapp.domain.model.AppUpdateDownload

data class SettingsUiState(
    /** アプリ更新のダウンロード状況。Foreground Service と同じ情報源を見ている */
    val download: AppUpdateDownload = AppUpdateDownload.Idle
)
