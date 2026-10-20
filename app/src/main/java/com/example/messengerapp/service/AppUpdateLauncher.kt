package com.example.messengerapp.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** ViewModel に Context を持ち込まずに [AppUpdateService] を操作するための薄いラッパ */
@Singleton
class AppUpdateLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun start() = send(AppUpdateService.ACTION_START)

    fun cancel() = send(AppUpdateService.ACTION_CANCEL)

    /** 画面側で結果を閉じたときに、残っている結果通知も取り下げる */
    fun dismissResultNotification() = AppUpdateNotifications.cancelResult(context)

    private fun send(action: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AppUpdateService::class.java).setAction(action)
        )
    }
}
