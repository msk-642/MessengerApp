package com.example.messengerapp.ui

import androidx.lifecycle.ViewModel
import com.example.messengerapp.notification.NotificationCanceler
import com.example.messengerapp.notification.NotificationNavigator
import com.example.messengerapp.notification.NotificationTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * MainActivity スコープの ViewModel。
 * Compose 側（AppNavHost）から Hilt 経由で通知層（[NotificationNavigator] / [NotificationCanceler]）
 * を参照するためのゲートウェイ。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val notificationNavigator: NotificationNavigator,
    private val notificationCanceler: NotificationCanceler
) : ViewModel() {

    val pendingTarget: StateFlow<NotificationTarget?> = notificationNavigator.pendingTarget

    fun onTargetConsumed() {
        notificationNavigator.consume()
    }

    /**
     * 通知の遷移先と一致する画面が表示されたことを通知する。
     * バナーをタップせず自力で到達した場合でも、残っている通知を取り下げる。
     */
    fun onScreenShown(target: NotificationTarget) {
        notificationCanceler.cancelMatching(target)
    }
}
