package com.example.messengerapp.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知タップで要求された遷移先を Activity から UI 層（AppNavHost）へ受け渡すバス。
 *
 * 未起動時は Activity 初期化より Compose の collect が遅れるため、一度限りのイベントではなく
 * StateFlow で保持し、UI 側が navigate 完了後に [consume] で明示的にクリアする。
 * これによりスプラッシュ→メインへの遷移中に通知タップ由来の遷移指示が失われない。
 */
@Singleton
class NotificationNavigator @Inject constructor() {

    private val _pendingTarget = MutableStateFlow<NotificationTarget?>(null)
    val pendingTarget: StateFlow<NotificationTarget?> = _pendingTarget.asStateFlow()

    fun post(target: NotificationTarget) {
        _pendingTarget.value = target
    }

    fun consume() {
        _pendingTarget.value = null
    }
}
