package com.xingyue.ufitools.monitor.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xingyue.ufitools.monitor.MainActivity
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus

/** 阈值警报通知：温度 / 电量 / CPU / 内存 / 流量 / 短信 */
object AlertNotifier {

    const val ALERT_CHANNEL_ID = "device_alerts"
    const val SMS_CHANNEL_ID = "sms_alerts"
    const val SERVICE_CHANNEL_ID = "monitor_service"

    private const val TYPE_TEMP = "temp"
    private const val TYPE_BATTERY = "battery"
    private const val TYPE_CPU = "cpu"
    private const val TYPE_MEM = "mem"
    private const val TYPE_DAILY_FLOW = "daily_flow"
    private const val TYPE_MONTHLY_FLOW = "monthly_flow"
    private const val TYPE_SMS = "sms"

    private val NOTIFY_IDS = mapOf(
        TYPE_TEMP to 10001,
        TYPE_BATTERY to 10002,
        TYPE_CPU to 10003,
        TYPE_MEM to 10004,
        TYPE_DAILY_FLOW to 10005,
        TYPE_MONTHLY_FLOW to 10006,
        TYPE_SMS to 20001,
    )

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "设备警报", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "温度/电量/CPU/内存/流量阈值警报"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(SMS_CHANNEL_ID, "短信提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "设备收到新短信时提醒"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "后台监控服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "前台保活服务常驻通知"
            },
        )
    }

    /**
     * 能否发通知。
     *
     * POST_NOTIFICATIONS 是 Android 13（API 33）才引入的运行时权限，更低版本上属于未知权限，
     * checkSelfPermission 恒为 DENIED——若直接返回 false，Android 12 上所有阈值警报都会静默失效，
     * 所以 33 以下改判系统通知总开关。
     */
    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /** 打开本应用的系统通知设置页（33 以下无法用权限弹窗，只能引导用户手动开启） */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** 根据最新状态检查全部阈值并发通知（含防抖） */
    fun checkAndNotify(context: Context, status: DeviceStatus) {
        if (!DevicePrefs.isAlertEnabled(context)) return
        if (!hasPermission(context)) return

        // cpu_temp_list 常为毫摄氏度（如 37000），必须先换算成 ℃ 再与阈值比较，否则会 37000>=70 误报
        val tempVal = DeviceApi.maxDeviceTempCelsius(status.cpuTempList)
            ?: parseNumber(status.temp)?.let { DeviceApi.toCelsius(it) ?: it }
        val tempThreshold = DevicePrefs.getTempThreshold(context)
        if (tempVal != null && tempVal >= tempThreshold) {
            notifyDebounced(
                context, TYPE_TEMP, "设备温度过高",
                "当前温度 ${String.format("%.1f℃", tempVal)}，已超过阈值 ${tempThreshold}℃",
            )
        }

        val batteryThreshold = DevicePrefs.getBatteryThreshold(context)
        if (status.batteryPercent in 0..batteryThreshold && !status.charging) {
            notifyDebounced(
                context, TYPE_BATTERY, "设备电量不足",
                "当前电量 ${status.batteryPercent}%，已低于阈值 ${batteryThreshold}%",
            )
        }

        val cpuVal = parseNumber(status.cpu)
        val cpuThreshold = DevicePrefs.getCpuThreshold(context)
        if (cpuVal != null && cpuVal >= cpuThreshold) {
            notifyDebounced(
                context, TYPE_CPU, "CPU 使用率过高",
                "当前 CPU ${status.cpu}，已超过阈值 ${cpuThreshold}%",
            )
        }

        val memVal = parseNumber(status.mem)
        val memThreshold = DevicePrefs.getMemThreshold(context)
        if (memVal != null && memVal >= memThreshold) {
            notifyDebounced(
                context, TYPE_MEM, "内存使用率过高",
                "当前内存 ${status.mem}，已超过阈值 ${memThreshold}%",
            )
        }

        val dailyGb = DevicePrefs.getDailyFlowThresholdGb(context)
        if (dailyGb > 0 && status.dailyRawBytes >= dailyGb * 1_073_741_824L) {
            notifyDebounced(
                context, TYPE_DAILY_FLOW, "今日流量超额",
                "今日已用 ${status.dailyFlow}，超过阈值 ${dailyGb}GB",
            )
        }

        val monthlyGb = DevicePrefs.getMonthlyFlowThresholdGb(context)
        if (monthlyGb > 0 && status.monthlyRawBytes >= monthlyGb * 1_073_741_824L) {
            notifyDebounced(
                context, TYPE_MONTHLY_FLOW, "本月流量超额",
                "本月已用 ${status.monthlyFlow}，超过阈值 ${monthlyGb}GB",
            )
        }
    }

    /** 新未读短信通知 */
    fun notifySms(context: Context, unread: List<DeviceApi.SmsMessage>) {
        if (!DevicePrefs.isSmsNotifyEnabled(context)) return
        if (!hasPermission(context)) return
        if (unread.isEmpty()) return
        val first = unread.first()
        val title = if (unread.size == 1) "来自 ${first.number} 的短信" else "${unread.size} 条未读短信"
        val text = first.content.take(100)
        postNotification(context, SMS_CHANNEL_ID, TYPE_SMS, title, text)
    }

    private fun parseNumber(s: String): Double? =
        Regex("-?\\d+(\\.\\d+)?").find(s)?.value?.toDoubleOrNull()

    private fun notifyDebounced(context: Context, type: String, title: String, text: String) {
        val now = System.currentTimeMillis()
        val debounceMs = DevicePrefs.getAlertDebounceMin(context) * 60_000L
        if (now - DevicePrefs.getLastAlertTime(context, type) < debounceMs) return
        DevicePrefs.setLastAlertTime(context, type, now)
        postNotification(context, ALERT_CHANNEL_ID, type, title, text)
    }

    private fun postNotification(context: Context, channelId: String, type: String, title: String, text: String) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_IDS[type] ?: type.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
