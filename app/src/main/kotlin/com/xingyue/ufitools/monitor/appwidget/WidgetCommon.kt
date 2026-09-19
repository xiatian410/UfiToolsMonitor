package com.xingyue.ufitools.monitor.appwidget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Glance 小组件共用工具：快照读取 / 刷新全部实例 */
object WidgetCommon {

    private data class WidgetEntry(
        val kind: WidgetKind,
        val factory: () -> GlanceAppWidget,
        val clazz: Class<out GlanceAppWidget>,
    )

    private val allWidgets = listOf(
        WidgetEntry(WidgetKind.MAIN, { UfiWidget() }, UfiWidget::class.java),
        WidgetEntry(WidgetKind.TRAFFIC, { UfiTrafficWidget() }, UfiTrafficWidget::class.java),
        WidgetEntry(WidgetKind.SIGNAL, { UfiSignalWidget() }, UfiSignalWidget::class.java),
        WidgetEntry(WidgetKind.SPEED, { UfiSpeedWidget() }, UfiSpeedWidget::class.java),
        WidgetEntry(WidgetKind.STATUS_BAR, { UfiStatusBarWidget() }, UfiStatusBarWidget::class.java),
    )

    fun loadSnapshot(context: Context): DeviceStatus? =
        DevicePrefs.getWidgetSnapshot(context)
            .takeIf { it.isNotEmpty() }
            ?.let { DeviceStatus.fromWidgetJson(it) }

    /** 是否存在任意已放置的小组件实例 */
    fun hasAnyWidgets(context: Context): Boolean = runBlocking {
        val mgr = GlanceAppWidgetManager(context)
        allWidgets.any { mgr.getGlanceIds(it.clazz).isNotEmpty() }
    }

    /** 把最新快照与各组件外观写入 Glance 状态并刷新全部小组件（异步） */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val json = DevicePrefs.getWidgetSnapshot(appContext)
            val mgr = GlanceAppWidgetManager(appContext)
            allWidgets.forEach { entry ->
                val appearance = DevicePrefs.getWidgetAppearance(appContext, entry.kind)
                val widget = entry.factory()
                mgr.getGlanceIds(entry.clazz).forEach { id ->
                    updateAppWidgetState(appContext, id) { prefs ->
                        if (json.isNotEmpty()) prefs[WidgetKeys.SNAPSHOT] = json
                        prefs[WidgetKeys.APPEARANCE] = appearance
                    }
                    widget.update(appContext, id)
                }
            }
        }
    }

    /** 仅刷新某一类型的组件（DIY 设置变更时） */
    fun updateKind(context: Context, kind: WidgetKind) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val json = DevicePrefs.getWidgetSnapshot(appContext)
            val appearance = DevicePrefs.getWidgetAppearance(appContext, kind)
            val entry = allWidgets.firstOrNull { it.kind == kind } ?: return@launch
            val mgr = GlanceAppWidgetManager(appContext)
            val widget = entry.factory()
            mgr.getGlanceIds(entry.clazz).forEach { id ->
                updateAppWidgetState(appContext, id) { prefs ->
                    if (json.isNotEmpty()) prefs[WidgetKeys.SNAPSHOT] = json
                    prefs[WidgetKeys.APPEARANCE] = appearance
                }
                widget.update(appContext, id)
            }
        }
    }
}
