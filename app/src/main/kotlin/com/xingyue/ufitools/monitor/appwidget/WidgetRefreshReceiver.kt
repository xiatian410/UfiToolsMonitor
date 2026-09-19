package com.xingyue.ufitools.monitor.appwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xingyue.ufitools.monitor.worker.RefreshWorker

/** 处理小组件点击刷新广播：提示反馈并触发一次性刷新 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_WIDGET) return
        Toast.makeText(context, "正在刷新…", Toast.LENGTH_SHORT).show()
        RefreshWorker.enqueueOneShot(context.applicationContext)
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.xingyue.ufitools.monitor.action.REFRESH_WIDGET"
    }
}
