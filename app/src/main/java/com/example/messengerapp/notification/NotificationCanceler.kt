package com.example.messengerapp.notification

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 表示中の通知のうち、指定された遷移先と一致するものを取り下げる。
 *
 * ユーザーが通知バナーをタップせず自力で該当画面へ辿り着いた場合、その通知はもう役目を終えている。
 * [com.example.messengerapp.ui.navigation.AppNavHost] が画面到達を検知して本クラスを呼ぶ。
 *
 * 判定は `NotificationManager.getActiveNotifications()`（自アプリの通知のみ返る）を走査し、
 * 通知 extras から復元した [NotificationTarget] との厳密一致で行う。
 * 「通知 ID → 遷移先」の対応表をアプリ側に持たないため、プロセスが再生成された後も正しく動く。
 */
@Singleton
class NotificationCanceler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * [target] と完全に一致する通知をすべて取り下げる。
     *
     * data class / data object の等価性をそのまま使うため、
     * [NotificationTarget.ChatRoom] は roomId が一致した場合のみ対象になる。
     * （チャット一覧を開いても個別ルームの通知は残る）
     */
    fun cancelMatching(target: NotificationTarget) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        notificationManager.activeNotifications
            .filter { NotificationTarget.fromBundle(it.notification.extras) == target }
            .forEach { notificationManager.cancel(it.tag, it.id) }
    }
}
