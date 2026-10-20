package com.example.messengerapp.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.example.messengerapp.domain.model.AppUpdateDownload
import com.example.messengerapp.domain.repository.AppUpdateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * APK のダウンロードをフォアグラウンド／バックグラウンドの双方で継続させるための Service。
 *
 * 通常のバックグラウンドプロセスは数十秒で回収対象になり 80MB を落とし切れないため、
 * Foreground Service に昇格してプロセス優先度を保つ。
 *
 * **プロセスが殺された場合は復活させない。**
 * [START_NOT_STICKY] と Manifest の `android:stopWithTask="true"` がその表明で、
 * 中断されたら次回ユーザーが開始し直したときに最初からやり直す、という割り切りを取っている
 * （レジュームは行わない）。
 */
@AndroidEntryPoint
class AppUpdateService : Service() {

    @Inject
    lateinit var repository: AppUpdateRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopDownload()
            return START_NOT_STICKY
        }
        // 設定画面で連打されても 2 本目を走らせない
        if (downloadJob?.isActive != true) {
            startDownload()
        }
        return START_NOT_STICKY
    }

    /** Android 15 以降 dataSync は 24 時間あたり合計 6 時間までで、超過時は自分で止めないと ANR になる */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopDownload()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownload() {
        AppUpdateNotifications.ensureChannel(this)
        startForeground(
            AppUpdateNotifications.NOTIFICATION_ID,
            AppUpdateNotifications.build(this, INITIAL_STATE),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        observeProgress()

        downloadJob = scope.launch {
            repository.downloadLatestApkToCache()
            // バックグラウンドのまま終わっても結果が届くよう、常駐通知とは別に残る通知を出す
            notifyResult()
            // 終端状態はリポジトリに入っているので、Service は役目を終える
            stopSelf()
        }
    }

    private fun notifyResult() {
        val notification =
            AppUpdateNotifications.buildResult(this, repository.download.value) ?: return
        getSystemService(NotificationManager::class.java)
            .notify(AppUpdateNotifications.RESULT_NOTIFICATION_ID, notification)
    }

    private fun stopDownload() {
        downloadJob?.cancel()
        stopSelf()
    }

    /** 進捗の間引きはリポジトリ側で済んでいるので、ここはそのまま通知へ反映するだけでよい */
    private fun observeProgress() {
        scope.launch {
            repository.download.collect { state ->
                if (state !is AppUpdateDownload.Running) return@collect
                getSystemService(NotificationManager::class.java).notify(
                    AppUpdateNotifications.NOTIFICATION_ID,
                    AppUpdateNotifications.build(this@AppUpdateService, state)
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "com.example.messengerapp.action.APP_UPDATE_START"
        const val ACTION_CANCEL = "com.example.messengerapp.action.APP_UPDATE_CANCEL"

        private val INITIAL_STATE = AppUpdateDownload.Running(downloadedBytes = 0, totalBytes = -1)
    }
}
