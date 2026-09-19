package com.xingyue.ufitools.monitor.appwidget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.xingyue.ufitools.monitor.worker.RefreshWorker

/** 小组件生命周期接收器：注册单一自适应 Glance 小组件，并调度后台刷新 */
class UfiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UfiWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetCommon.hasAnyWidgets(context)) {
            RefreshWorker.cancelPeriodic(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            RefreshWorker.enqueueOneShot(context)
        }
    }
}
