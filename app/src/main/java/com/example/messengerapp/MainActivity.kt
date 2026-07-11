package com.example.messengerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.messengerapp.notification.NotificationNavigator
import com.example.messengerapp.notification.NotificationTarget
import com.example.messengerapp.ui.navigation.AppNavHost
import com.example.messengerapp.ui.theme.MessengerAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notificationNavigator: NotificationNavigator

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("MainActivity", "通知権限: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()

        // 未起動時に通知タップで起動された場合はこちらで extras を処理する
        handleNotificationIntent(intent)

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { android.util.Log.d("FCM_TOKEN", it) }

        setContent {
            MessengerAppTheme {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // バックグラウンド／フォアグラウンド状態で通知タップされた場合はこちらに来る
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val target = NotificationTarget.fromIntent(intent) ?: return
        notificationNavigator.post(target)
        // 構成変更（回転など）で onCreate が再実行されても同じ extras で再遷移しないよう除去
        intent?.removeExtra(NotificationTarget.KEY_TYPE)
        intent?.removeExtra(NotificationTarget.KEY_ARG)
    }

    private fun askNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
