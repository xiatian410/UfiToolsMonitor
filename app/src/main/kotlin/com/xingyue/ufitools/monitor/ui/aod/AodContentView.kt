package com.xingyue.ufitools.monitor.ui.aod

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** 可自由组合、排序和调整样式的息屏模块画布 */
@SuppressLint("SetTextI18n")
class AodContentView(context: Context) : FrameLayout(context) {

    private val showSeconds = DevicePrefs.isAodShowSeconds(context)
    private val clock24h = DevicePrefs.isAodClock24Hour(context)
    private val timeFmt = SimpleDateFormat(buildTimePattern(clock24h, showSeconds), Locale.getDefault())
    private val dateFmt = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    private val updateFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val moduleKeys = DevicePrefs.getEnabledAodModules(context)
    private val showLabels = DevicePrefs.isAodModuleLabelEnabled(context)
    private val showLastUpdate = DevicePrefs.isAodShowLastUpdate(context)
    private val fontScale = DevicePrefs.getAodFontScale(context) / 100f
    private val clockSize = DevicePrefs.getAodClockSize(context).toFloat()
    private val moduleSpacing = DevicePrefs.getAodModuleSpacing(context)
    private val alignmentMode = DevicePrefs.getAodTextAlignment(context)
    private val outlineEnabled = DevicePrefs.isAodModuleOutlineEnabled(context)
    private val verticalPosition = DevicePrefs.getAodVerticalPosition(context)
    private val burnInAmplitude = DevicePrefs.getAodBurnInAmplitude(context)
    private val palette = PALETTES[DevicePrefs.getAodColorScheme(context)]
    private val moduleViews = linkedMapOf<String, TextView>()

    private val moduleGrid = GridLayout(context).apply {
        alignmentMode = GridLayout.ALIGN_BOUNDS
        useDefaultMargins = false
    }
    private val emptyView = TextView(context).apply {
        text = "请在设置中添加息屏模块"
        textSize = 14f
        setTextColor(palette.secondary)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val connectionView = TextView(context).apply {
        textSize = 12f
        setTextColor(WARNING)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val lastUpdateView = TextView(context).apply {
        textSize = 11f
        setTextColor(palette.faint)
        gravity = Gravity.CENTER
        visibility = if (showLastUpdate) View.VISIBLE else View.GONE
        text = "等待数据…"
    }
    private val hintView = TextView(context).apply {
        textSize = 13f
        setTextColor(palette.faint)
    }

    private var lastStatus: DeviceStatus? = null
    private var landscape = false
    private var burnInPosition = 0
    private var lastUpdateAt = 0L
    private var connectionFailed = false

    init {
        setBackgroundColor(Color.BLACK)
        val contentGravity = when (verticalPosition) {
            0 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            2 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else -> Gravity.CENTER
        }
        val contentLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, contentGravity).apply {
            when (verticalPosition) {
                0 -> topMargin = dp(28)
                2 -> bottomMargin = dp(88)
            }
        }
        addView(moduleGrid, contentLp)
        addView(emptyView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, contentGravity))
        addView(
            connectionView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                .apply { bottomMargin = dp(if (showLastUpdate) 78 else 58) },
        )
        addView(
            lastUpdateView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                .apply { bottomMargin = dp(58) },
        )
        addView(
            hintView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                .apply { bottomMargin = dp(28) },
        )
        rebuildModules(false)
        renderCachedSnapshot()
    }

    fun updateOrientation(useLandscape: Boolean) {
        rebuildModules(useLandscape)
        requestLayout()
    }

    fun showExitHint(text: String) {
        hintView.animate().cancel()
        hintView.text = text
        hintView.alpha = 1f
        hintView.animate().alpha(0f).setStartDelay(4000L).setDuration(1200L).start()
    }

    fun updateClock() {
        val now = Date()
        moduleViews["clock"]?.text = timeFmt.format(now)
        moduleViews["date"]?.text = dateFmt.format(now)
    }

    fun renderCachedSnapshot() {
        val snapTime = DevicePrefs.getWidgetSnapshotTime(context)
        render(
            DevicePrefs.getWidgetSnapshot(context)
                .takeIf { it.isNotBlank() }
                ?.let { DeviceStatus.fromWidgetJson(it) },
            updatedAt = if (snapTime > 0L) snapTime else 0L,
        )
    }

    fun render(status: DeviceStatus?, updatedAt: Long = System.currentTimeMillis()) {
        lastStatus = status
        if (status != null && updatedAt > 0L) {
            lastUpdateAt = updatedAt
        }
        updateClock()
        moduleViews.forEach { (key, view) ->
            if (key !in TIME_MODULES) view.text = moduleText(key, status)
        }
        refreshFooter()
    }

    /** @param message 自定义提示（如缺少本地网络权限）；null 用默认的连接中断文案 */
    fun setConnectionFailure(failed: Boolean, message: String? = null) {
        connectionFailed = failed
        connectionView.text = if (failed) message ?: "● 连接中断，正在显示缓存" else ""
        connectionView.visibility = if (failed) View.VISIBLE else View.GONE
        refreshFooter()
    }

    fun advanceBurnInPosition() {
        burnInPosition = (burnInPosition + 1) % BURN_IN_POSITIONS.size
        val (xFactor, yFactor) = BURN_IN_POSITIONS[burnInPosition]
        val amp = when (burnInAmplitude) {
            0 -> 0.55f
            2 -> 1.45f
            else -> 1f
        }
        val xRange = (if (landscape) dp(24) else dp(16)) * amp
        val yRange = (if (landscape) dp(12) else dp(28)) * amp
        moduleGrid.animate().cancel()
        moduleGrid.animate()
            .translationX(xRange * xFactor)
            .translationY(yRange * yFactor)
            .setDuration(400L)
            .start()
        emptyView.animate().cancel()
        emptyView.animate()
            .translationX(xRange * xFactor)
            .translationY(yRange * yFactor)
            .setDuration(400L)
            .start()
    }

    fun applySafeInsets(left: Int, top: Int, right: Int, bottom: Int) {
        setPadding(
            max(dp(12), left),
            max(dp(12), top),
            max(dp(12), right),
            max(dp(12), bottom),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val useLandscape = w > h
        if (useLandscape != landscape) rebuildModules(useLandscape)
    }

    private fun refreshFooter() {
        if (!showLastUpdate) {
            lastUpdateView.visibility = View.GONE
            return
        }
        lastUpdateView.visibility = View.VISIBLE
        lastUpdateView.text = when {
            lastUpdateAt <= 0L -> "等待数据…"
            connectionFailed -> "缓存 · ${updateFmt.format(Date(lastUpdateAt))}"
            else -> "更新于 ${updateFmt.format(Date(lastUpdateAt))}"
        }
    }

    private fun rebuildModules(useLandscape: Boolean) {
        landscape = useLandscape
        moduleGrid.removeAllViews()
        moduleViews.clear()

        emptyView.visibility = if (moduleKeys.isEmpty()) View.VISIBLE else View.GONE
        if (moduleKeys.isEmpty()) return

        val requestedColumns = DevicePrefs.getAodModuleColumns(context)
        val columns = min(
            moduleKeys.size,
            if (requestedColumns == 0) {
                if (landscape) 2 else 1
            } else {
                requestedColumns
            },
        ).coerceAtLeast(1)
        moduleGrid.columnCount = columns
        moduleGrid.rowCount = ceil(moduleKeys.size / columns.toDouble()).toInt()

        moduleKeys.forEachIndexed { index, key ->
            val view = createModuleView(key)
            val row = index / columns
            val column = index % columns
            view.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column, 1, 1f),
            ).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                val margin = dp(moduleSpacing) / 2
                setMargins(margin, margin, margin, margin)
            }
            moduleViews[key] = view
            moduleGrid.addView(view)
        }
        render(lastStatus, lastUpdateAt)
    }

    private fun createModuleView(key: String) = TextView(context).apply {
        textSize = when (key) {
            "clock" -> clockSize * if (landscape) 0.88f else 1f
            "date" -> 16f * fontScale
            else -> 15f * fontScale
        }
        setTextColor(if (key == "clock") palette.primary else palette.secondary)
        typeface = if (key == "clock") {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        } else {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        gravity = when (alignmentMode) {
            0 -> Gravity.START or Gravity.CENTER_VERTICAL
            2 -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        setPadding(dp(10), dp(7), dp(10), dp(7))
        if (outlineEnabled) {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(10).toFloat()
                setStroke(max(1, dp(1)), (palette.secondary and 0x00FFFFFF) or 0x55000000)
            }
        }
    }

    private fun moduleText(key: String, status: DeviceStatus?): String {
        if (key == "custom") return DevicePrefs.getAodCustomText(context).ifBlank { "自定义文字" }
        if (status == null) return labeled(moduleTitle(key), "暂无数据")

        val value = when (key) {
            "device" -> values(status.model, status.deviceModel).ifBlank { "--" }
            "battery" -> values(
                if (status.hasBattery) status.battery else "外接供电",
                if (!status.hasBattery) "无内置电池" else if (status.charging) "充电中" else "",
                if (status.hasBattery) status.batteryCurrent else "",
                if (status.hasBattery) status.batteryVoltage else "",
            )
            "network" -> values(status.carrier, status.netType, status.band)
            "signal" -> values(
                status.signal,
                status.rsrp?.let { "$it dBm" }.orEmpty(),
                status.sinr.takeUnless { it == "--" }?.let { "SINR $it" }.orEmpty(),
            )
            "speed" -> values(
                if (status.rxSpeed >= 0) "↓ ${DeviceApi.formatSpeed(status.rxSpeed)}" else "",
                if (status.txSpeed >= 0) "↑ ${DeviceApi.formatSpeed(status.txSpeed)}" else "",
            )
            "wifi" -> if (status.wifiCount >= 0) "${status.wifiCount} 台设备" else "--"
            "temperature" -> values("设备 ${status.temp}", "电池 ${status.batteryTemp}")
            "traffic" -> values("今日 ${status.dailyFlow}", "本月 ${status.monthlyFlow}")
            "system" -> values("CPU ${status.cpu}", "内存 ${status.mem}")
            "qos" -> values("QCI ${status.qci}", "速率 ${status.ambr}")
            else -> "--"
        }.ifBlank { "--" }
        return labeled(moduleTitle(key), value)
    }

    private fun labeled(title: String, value: String): String =
        if (showLabels) "$title\n$value" else value

    private fun values(vararg values: String): String =
        values.filter { it.isNotBlank() && it != "--" }.joinToString("  ·  ")

    private fun moduleTitle(key: String): String = when (key) {
        "device" -> "设备"
        "battery" -> "电量"
        "network" -> "网络"
        "signal" -> "信号"
        "speed" -> "速率"
        "wifi" -> "WiFi"
        "temperature" -> "温度"
        "traffic" -> "流量"
        "system" -> "系统"
        "qos" -> "QoS"
        else -> key
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Palette(val primary: Int, val secondary: Int, val faint: Int)

    private companion object {
        const val WARNING = 0xFF9A6262.toInt()
        val TIME_MODULES = setOf("clock", "date")
        val PALETTES = listOf(
            Palette(0xFFE6E6E6.toInt(), 0xFFA8A8A8.toInt(), 0xFF666666.toInt()),
            Palette(0xFFF0E2CE.toInt(), 0xFFB5A58E.toInt(), 0xFF6E6456.toInt()),
            Palette(0xFFD66A6A.toInt(), 0xFFA65050.toInt(), 0xFF693535.toInt()),
            Palette(0xFF82C891.toInt(), 0xFF60986C.toInt(), 0xFF3B5F43.toInt()),
            Palette(0xFFD5A84C.toInt(), 0xFF9D7D3B.toInt(), 0xFF635027.toInt()),
        )
        val BURN_IN_POSITIONS = arrayOf(
            -1f to -1f,
            0f to -1f,
            1f to -1f,
            1f to 0f,
            1f to 1f,
            0f to 1f,
            -1f to 1f,
            -1f to 0f,
            0f to 0f,
        )

        fun buildTimePattern(clock24h: Boolean, showSeconds: Boolean): String = when {
            clock24h && showSeconds -> "HH:mm:ss"
            clock24h -> "HH:mm"
            showSeconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
    }
}
