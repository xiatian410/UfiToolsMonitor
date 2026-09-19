package com.xingyue.ufitools.monitor.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xingyue.ufitools.monitor.MainActivity
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus
import com.xingyue.ufitools.monitor.data.StatusRepository
import com.xingyue.ufitools.monitor.notify.AlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 前台保活服务：按设定间隔采集设备状态、触发警报、更新小组件与常驻通知 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var knownSmsIds: Set<String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification("正在监控设备状态…"))
        if (loopJob == null) {
            loopJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            if (DevicePrefs.isConfigured(this)) {
                when (val result = StatusRepository.refresh(this)) {
                    is DeviceApi.FetchResult.Success -> {
                        updateNotification(buildNotification(summaryText(result.status)))
                        checkNewSms()
                    }
                    is DeviceApi.FetchResult.Failure ->
                        updateNotification(
                            buildNotification(
                                if (result.reason == DeviceApi.FetchResult.Reason.PERMISSION) {
                                    "缺少「本地网络」权限，打开应用授权后恢复监控"
                                } else {
                                    "设备连接失败，等待重试…"
                                },
                            ),
                        )
                }
            }
            delay(DevicePrefs.getMonitorIntervalSec(this) * 1000L)
        }
    }

    private suspend fun checkNewSms() {
        if (!DevicePrefs.isSmsNotifyEnabled(this)) return
        val list = DeviceApi.fetchSmsList(this) ?: return
        val unread = list.filter { it.unread }
        val known = knownSmsIds
        knownSmsIds = unread.map { it.id }.toSet()
        if (known == null) return
        val fresh = unread.filter { it.id !in known }
        if (fresh.isNotEmpty()) AlertNotifier.notifySms(this, fresh)
    }

    private fun summaryText(s: DeviceStatus): String =
        "🌡${s.temp}  CPU ${s.cpu}  内存 ${s.mem}  🔋${s.battery}  今日 ${s.dailyFlow}"

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AlertNotifier.SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("UFI TOOLS 后台监控")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFY_ID, notification)
    }

    companion object {
        private const val NOTIFY_ID = 30001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
