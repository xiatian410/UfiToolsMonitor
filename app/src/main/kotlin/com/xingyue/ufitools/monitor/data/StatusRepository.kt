package com.xingyue.ufitools.monitor.data

import android.content.Context
import com.xingyue.ufitools.monitor.appwidget.WidgetCommon
import com.xingyue.ufitools.monitor.notify.AlertNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 状态采集的统一入口：刷新 + 快照缓存 + 小组件更新 + 流量记录 + 阈值警报 */
object StatusRepository {

    private val refreshMutex = Mutex()

    suspend fun refresh(context: Context): DeviceApi.FetchResult = refreshMutex.withLock {
        val result = DeviceApi.fetchStatus(context)
        if (result is DeviceApi.FetchResult.Success) {
            onStatus(context, result.status)
        }
        result
    }

    suspend fun refreshForAod(context: Context, maxCacheAgeMillis: Long): DeviceApi.FetchResult {
        cachedStatus(context, maxCacheAgeMillis)?.let { return DeviceApi.FetchResult.Success(it) }
        return refreshMutex.withLock {
            cachedStatus(context, maxCacheAgeMillis)?.let { return@withLock DeviceApi.FetchResult.Success(it) }
            val result = DeviceApi.fetchStatus(context)
            if (result is DeviceApi.FetchResult.Success) {
                cacheSnapshot(context, result.status)
            }
            result
        }
    }

    /** 采集成功后的统一副作用处理 */
    fun onStatus(context: Context, status: DeviceStatus) {
        cacheSnapshot(context, status)
        if (WidgetCommon.hasAnyWidgets(context)) {
            WidgetCommon.updateAll(context)
        }
        if (DevicePrefs.isTrafficRecordEnabled(context)) {
            TrafficHistory.record(context, status.dailyRawBytes)
        }
        AlertNotifier.checkAndNotify(context, status)
    }

    private fun cacheSnapshot(context: Context, status: DeviceStatus) {
        DevicePrefs.setWidgetSnapshot(context, status.toWidgetJson())
        DevicePrefs.setWidgetSnapshotTime(context, status.updateTime)
    }

    private fun cachedStatus(context: Context, maxAgeMillis: Long): DeviceStatus? {
        val snapshotTime = DevicePrefs.getWidgetSnapshotTime(context)
        val age = System.currentTimeMillis() - snapshotTime
        if (snapshotTime <= 0L || age !in 0..maxAgeMillis) return null
        return DevicePrefs.getWidgetSnapshot(context)
            .takeIf { it.isNotBlank() }
            ?.let { DeviceStatus.fromWidgetJson(it) }
    }
}
