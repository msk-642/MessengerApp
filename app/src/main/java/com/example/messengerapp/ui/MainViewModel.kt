package com.example.messengerapp.ui

import androidx.lifecycle.ViewModel
import com.example.messengerapp.notification.NotificationNavigator
import com.example.messengerapp.notification.NotificationTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * MainActivity スコープの ViewModel。
 * Compose 側（AppNavHost）から Hilt 経由で [NotificationNavigator] を参照するためのゲートウェイ。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val notificationNavigator: NotificationNavigator
) : ViewModel() {

    val pendingTarget: StateFlow<NotificationTarget?> = notificationNavigator.pendingTarget

    fun onTargetConsumed() {
        notificationNavigator.consume()
    }
}
