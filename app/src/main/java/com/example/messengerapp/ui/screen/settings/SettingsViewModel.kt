package com.example.messengerapp.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.repository.AppUpdateRepository
import com.example.messengerapp.domain.repository.AuthRepository
import com.example.messengerapp.service.AppUpdateLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateLauncher: AppUpdateLauncher
) : ViewModel() {

    // 進捗はリポジトリが持っているため、画面を離れて戻っても購読し直すだけで続きが見える
    val uiState: StateFlow<SettingsUiState> = appUpdateRepository.download
        .map { SettingsUiState(download = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = SettingsUiState()
        )

    private val _logoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    /** 失敗後の再試行もこれを呼ぶ。レジュームせず最初からやり直す */
    fun startUpdate() = appUpdateLauncher.start()

    fun cancelUpdate() = appUpdateLauncher.cancel()

    fun consumeUpdateResult() {
        appUpdateRepository.consumeResult()
        // 画面で結果を閉じたのに通知だけ残る、という状態を作らない
        appUpdateLauncher.dismissResultNotification()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _logoutEvent.emit(Unit)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
