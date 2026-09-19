package com.xingyue.ufitools.monitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.StatusRepository
import java.util.concurrent.TimeUnit

/** 后台采集设备状态并刷新桌面小组件 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!DevicePrefs.isConfigured(ctx)) return Result.success()

        return when (StatusRepository.refresh(ctx)) {
            is DeviceApi.FetchResult.Success -> Result.success()

            is DeviceApi.FetchResult.Failure -> {
                if (runAttemptCount < 2) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        private const val PERIODIC_WORK = "ufi_widget_periodic_refresh"
        private const val ONESHOT_WORK = "ufi_widget_oneshot_refresh"

        fun schedulePeriodic(context: Context) {
            val intervalMin = DevicePrefs.getWidgetRefreshMin(context).coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMin, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_WORK, ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
