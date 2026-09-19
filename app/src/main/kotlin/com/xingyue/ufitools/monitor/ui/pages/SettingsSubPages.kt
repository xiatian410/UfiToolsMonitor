package com.xingyue.ufitools.monitor.ui.pages

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.xingyue.ufitools.monitor.appwidget.STATUS_SLOT_TITLES
import com.xingyue.ufitools.monitor.appwidget.WIDGET_CARD_OPTION_LABELS
import com.xingyue.ufitools.monitor.appwidget.WIDGET_METRIC_TITLES
import com.xingyue.ufitools.monitor.appwidget.WidgetCommon
import com.xingyue.ufitools.monitor.appwidget.WidgetKind
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.TrafficHistory
import com.xingyue.ufitools.monitor.notify.AlertNotifier
import com.xingyue.ufitools.monitor.service.MonitorService
import com.xingyue.ufitools.monitor.ui.theme.MonetKeyColorOptions
import com.xingyue.ufitools.monitor.ui.theme.PaletteStyleOptions
import com.xingyue.ufitools.monitor.ui.theme.ThemeSettings
import com.xingyue.ufitools.monitor.worker.RefreshWorker
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置二级页面的通用脚手架：小标题栏 + 返回键 + LazyColumn */
@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
            content = content,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        content()
    }
}

/** 指标条项目列表：按组件目录，长按拖动排序 + 同行开关 */
@Composable
private fun MetricReorderList(
    context: android.content.Context,
    kind: WidgetKind,
    resetTick: Int = 0,
) {
    val catalog = kind.metricKeys
    if (catalog.isEmpty()) return
    var order by remember(kind, resetTick) { mutableStateOf(DevicePrefs.getWidgetMetricOrder(context, kind)) }
    var enabledMap by remember(kind, resetTick) {
        mutableStateOf(catalog.associateWith { DevicePrefs.isWidgetMetricEnabled(context, kind, it) })
    }
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * order.size),
    ) {
        catalog.forEach { key ->
            val index = order.indexOf(key)
            if (index < 0) return@forEach
            val isDragging = draggingKey == key
            val settledY by animateFloatAsState(targetValue = index * rowHeightPx, label = "metricRowY")
            val y = if (isDragging) draggingY else settledY
            val elevation by animateFloatAsState(targetValue = if (isDragging) 8f else 0f, label = "metricRowElevation")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = y
                        shadowElevation = elevation
                        scaleX = if (isDragging) 1.02f else 1f
                        scaleY = if (isDragging) 1.02f else 1f
                    }
                    .background(if (isDragging) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .pointerInput(key, kind) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingKey = key
                                draggingY = order.indexOf(key) * rowHeightPx
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                draggingY = (draggingY + amount.y)
                                    .coerceIn(0f, (order.size - 1) * rowHeightPx)
                                val from = order.indexOf(key)
                                val to = (draggingY / rowHeightPx).roundToInt().coerceIn(order.indices)
                                if (to != from) {
                                    order = order.toMutableList().also {
                                        it.removeAt(from)
                                        it.add(to, key)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                draggingKey = null
                                DevicePrefs.setWidgetMetricOrder(context, kind, order)
                                WidgetCommon.updateKind(context, kind)
                            },
                            onDragCancel = { draggingKey = null },
                        )
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = "拖动排序",
                    modifier = Modifier.width(20.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = WIDGET_METRIC_TITLES[key] ?: key,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = enabledMap[key] == true,
                    onCheckedChange = { on ->
                        if (on && enabledMap.count { it.value } >= kind.metricMax) {
                            Toast.makeText(
                                context,
                                "最多同时开启 ${kind.metricMax} 项，请先关闭其他项目",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@Switch
                        }
                        enabledMap = enabledMap + (key to on)
                        DevicePrefs.setWidgetMetricEnabled(context, kind, key, on)
                        WidgetCommon.updateKind(context, kind)
                    },
                )
            }
        }
    }
}

private val aodModuleTitles = mapOf(
    "clock" to "时钟",
    "date" to "日期",
    "device" to "设备",
    "battery" to "电量",
    "network" to "网络",
    "signal" to "信号",
    "speed" to "速率",
    "wifi" to "WiFi",
    "temperature" to "温度",
    "traffic" to "流量",
    "system" to "系统",
    "qos" to "QoS",
    "custom" to "自定义文字",
)

/** AOD 模块列表：所有模块均可独立开关并长按排序 */
@Composable
private fun AodModuleReorderList(
    context: android.content.Context,
    resetTick: Int,
    onChanged: () -> Unit,
) {
    var order by remember(resetTick) { mutableStateOf(DevicePrefs.getAodModuleOrder(context)) }
    var enabledMap by remember(resetTick) {
        mutableStateOf(DevicePrefs.AOD_MODULE_KEYS.associateWith { DevicePrefs.isAodModuleEnabled(context, it) })
    }
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * order.size),
    ) {
        DevicePrefs.AOD_MODULE_KEYS.forEach { key ->
            val index = order.indexOf(key)
            val isDragging = draggingKey == key
            val settledY by animateFloatAsState(targetValue = index * rowHeightPx, label = "aodModuleRowY")
            val y = if (isDragging) draggingY else settledY
            val elevation by animateFloatAsState(
                targetValue = if (isDragging) 8f else 0f,
                label = "aodModuleElevation",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = y
                        shadowElevation = elevation
                        scaleX = if (isDragging) 1.02f else 1f
                        scaleY = if (isDragging) 1.02f else 1f
                    }
                    .background(if (isDragging) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingKey = key
                                draggingY = order.indexOf(key) * rowHeightPx
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                draggingY = (draggingY + amount.y)
                                    .coerceIn(0f, (order.size - 1) * rowHeightPx)
                                val from = order.indexOf(key)
                                val to = (draggingY / rowHeightPx).roundToInt().coerceIn(order.indices)
                                if (to != from) {
                                    order = order.toMutableList().also {
                                        it.removeAt(from)
                                        it.add(to, key)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                draggingKey = null
                                DevicePrefs.setAodModuleOrder(context, order)
                                onChanged()
                            },
                            onDragCancel = { draggingKey = null },
                        )
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = "拖动排序",
                    modifier = Modifier.width(20.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = aodModuleTitles[key] ?: key,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = enabledMap[key] == true,
                    onCheckedChange = { enabled ->
                        enabledMap = enabledMap + (key to enabled)
                        DevicePrefs.setAodModuleEnabled(context, key, enabled)
                        onChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun AodModulePreview(context: android.content.Context, refreshTick: Int) {
    val keys = remember(refreshTick) { DevicePrefs.getEnabledAodModules(context) }
    val requestedColumns = DevicePrefs.getAodModuleColumns(context)
    // 与真机竖屏一致：自动 = 1 列
    val columns = (if (requestedColumns == 0) 1 else requestedColumns).coerceAtMost(keys.size.coerceAtLeast(1))
    val fontScale = DevicePrefs.getAodFontScale(context) / 100f
    val clockSize = DevicePrefs.getAodClockSize(context)
    val spacing = DevicePrefs.getAodModuleSpacing(context)
    val showLabels = DevicePrefs.isAodModuleLabelEnabled(context)
    val outline = DevicePrefs.isAodModuleOutlineEnabled(context)
    val alignment = DevicePrefs.getAodTextAlignment(context)
    val clock24h = DevicePrefs.isAodClock24Hour(context)
    val showSeconds = DevicePrefs.isAodShowSeconds(context)
    val verticalPosition = DevicePrefs.getAodVerticalPosition(context)
    val color = listOf(
        Color(0xFFE6E6E6), Color(0xFFF0E2CE), Color(0xFFD66A6A),
        Color(0xFF82C891), Color(0xFFD5A84C),
    )[DevicePrefs.getAodColorScheme(context)]
    val textAlign = when (alignment) {
        0 -> TextAlign.Start
        2 -> TextAlign.End
        else -> TextAlign.Center
    }
    val snapshot = remember(refreshTick) {
        DevicePrefs.getWidgetSnapshot(context)
            .takeIf { it.isNotBlank() }
            ?.let { com.xingyue.ufitools.monitor.data.DeviceStatus.fromWidgetJson(it) }
    }
    val topPad = when (verticalPosition) {
        0 -> 4.dp
        2 -> 18.dp
        else -> 12.dp
    }
    val bottomPad = when (verticalPosition) {
        2 -> 4.dp
        0 -> 18.dp
        else -> 12.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(start = 12.dp, end = 12.dp, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        if (keys.isEmpty()) {
            Text(
                text = "请至少选择一个模块",
                modifier = Modifier.fillMaxWidth(),
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        keys.chunked(columns).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            ) {
                rowKeys.forEach { key ->
                    val title = aodModuleTitles[key] ?: key
                    val sample = previewSample(key, snapshot, clock24h, showSeconds, context)
                    val text = if (showLabels && key !in setOf("clock", "date", "custom")) {
                        "$title\n$sample"
                    } else {
                        sample
                    }
                    Text(
                        text = text,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (outline) {
                                    Modifier
                                        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .padding(7.dp)
                                } else {
                                    Modifier.padding(7.dp)
                                },
                            ),
                        color = color,
                        fontSize = if (key == "clock") (clockSize * 0.55f).sp else (14f * fontScale).sp,
                        textAlign = textAlign,
                    )
                }
                repeat(columns - rowKeys.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun previewSample(
    key: String,
    snapshot: com.xingyue.ufitools.monitor.data.DeviceStatus?,
    clock24h: Boolean,
    showSeconds: Boolean,
    context: android.content.Context,
): String {
    val s = snapshot
    return when (key) {
        "clock" -> when {
            clock24h && showSeconds -> "12:34:56"
            clock24h -> "12:34"
            showSeconds -> "12:34:56 下午"
            else -> "12:34 下午"
        }
        "date" -> "7月11日 星期六"
        "device" -> listOfNotNull(s?.model?.takeIf { it.isNotBlank() }, s?.deviceModel?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifBlank { "UFI-TOOLS" }
        "battery" -> when {
            s == null -> "80% · 充电中"
            !s.hasBattery -> "外接供电"
            else -> listOfNotNull(
                s.battery.takeIf { it.isNotBlank() },
                if (s.charging) "充电中" else null,
            ).joinToString(" · ").ifBlank { "80%" }
        }
        "network" -> listOfNotNull(
            s?.carrier?.takeIf { it.isNotBlank() && it != "--" },
            s?.netType?.takeIf { it.isNotBlank() && it != "--" },
        ).joinToString(" · ").ifBlank { "中国移动 · 5G" }
        "signal" -> listOfNotNull(
            s?.rsrp?.let { "$it dBm" },
            s?.sinr?.takeUnless { it.isBlank() || it == "--" }?.let { "SINR $it" },
        ).joinToString(" · ").ifBlank { "-86 dBm · SINR 18" }
        "speed" -> if (s != null && (s.rxSpeed >= 0 || s.txSpeed >= 0)) {
            buildString {
                if (s.rxSpeed >= 0) append("↓ ${com.xingyue.ufitools.monitor.data.DeviceApi.formatSpeed(s.rxSpeed)}")
                if (s.txSpeed >= 0) {
                    if (isNotEmpty()) append(" · ")
                    append("↑ ${com.xingyue.ufitools.monitor.data.DeviceApi.formatSpeed(s.txSpeed)}")
                }
            }.ifBlank { "↓ -- · ↑ --" }
        } else {
            "↓ 8.2 MB/s · ↑ 1.3 MB/s"
        }
        "wifi" -> if (s != null && s.wifiCount >= 0) "${s.wifiCount} 台设备" else "3 台设备"
        "temperature" -> if (s != null) {
            "设备 ${s.temp} · 电池 ${s.batteryTemp}"
        } else {
            "设备 42℃ · 电池 36℃"
        }
        "traffic" -> if (s != null) {
            "今日 ${s.dailyFlow} · 本月 ${s.monthlyFlow}"
        } else {
            "今日 1.2GB · 本月 36GB"
        }
        "system" -> if (s != null) "CPU ${s.cpu} · 内存 ${s.mem}" else "CPU 18% · 内存 42%"
        "qos" -> if (s != null) "QCI ${s.qci} · 速率 ${s.ambr}" else "QCI 9 · 速率 1Gbps"
        else -> DevicePrefs.getAodCustomText(context).ifBlank { "自定义文字" }
    }
}

@Composable
private fun AodCustomTextPreference(
    context: android.content.Context,
    onChanged: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(DevicePrefs.getAodCustomText(context)) }

    ArrowPreference(
        title = "自定义文字内容",
        summary = DevicePrefs.getAodCustomText(context).ifBlank { "点击输入任意文字，启用“自定义文字”模块后显示" },
        onClick = {
            input = DevicePrefs.getAodCustomText(context)
            showDialog = true
        },
    )
    WindowDialog(
        show = showDialog,
        title = "自定义文字",
        summary = "最多 80 个字符",
        onDismissRequest = { showDialog = false },
    ) {
        TextField(
            value = input,
            onValueChange = { input = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = "显示内容",
            useLabelAsPlaceholder = true,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "保存",
                onClick = {
                    DevicePrefs.setAodCustomText(context, input)
                    showDialog = false
                    onChanged()
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AodGuideDialog(
    show: Boolean,
    launchAfterConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "伪息屏显示使用教程",
        summary = "使用前请了解工作方式与耗电风险",
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "重要提示：这不是手机系统级 AOD。应用会用纯黑低亮界面保持屏幕持续点亮，因此仍会消耗手机电量，LCD 屏幕也不会因黑色背景而省电。",
            color = Color(0xFFD46A4C),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("1. 先在「模块开关与顺序」中选择内容，长按模块可拖动排序。", fontSize = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("2. 在「显示 / 布局」中调整亮度、位置、列数、字号、配色，并通过实时预览确认。", fontSize = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("3. 建议 1%–10% 亮度；未充电可单独设更低亮度。开启防烧屏并设置自动退出或仅充电运行。", fontSize = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("4. 可开启低电量退出与口袋保护；进入后按设置单击或双击退出。", fontSize = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("5. OLED 长时间固定内容仍可能残影，请勿整夜无限制运行。", fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (launchAfterConfirm) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                text = if (launchAfterConfirm) "了解并进入" else "知道了",
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 小组件与刷新总览；点进组件走导航栈打开独立 DIY 页 */
@Composable
fun WidgetSettingsPage(
    onBack: () -> Unit,
    onOpenKind: (WidgetKind) -> Unit,
) {
    val context = LocalContext.current
    var resetTick by remember { mutableIntStateOf(0) }
    var dashboardInterval by remember { mutableIntStateOf(DevicePrefs.getRefreshIntervalSec(context)) }
    var widgetInterval by remember(resetTick) { mutableIntStateOf(DevicePrefs.getWidgetRefreshMin(context)) }

    SettingsSubPage(title = "小组件与刷新", onBack = onBack) {
        item {
            SmallTitle("仪表盘")
            SettingsCard {
                SliderPreference(
                    value = dashboardInterval.toFloat(),
                    onValueChange = {
                        dashboardInterval = it.toInt()
                        DevicePrefs.setRefreshIntervalSec(context, it.toInt())
                    },
                    title = "仪表盘刷新间隔",
                    summary = "打开应用时自动刷新数据的间隔",
                    valueRange = 1f..60f,
                    valueText = "${dashboardInterval}s",
                )
            }
        }
        item {
            SmallTitle("通用")
            SettingsCard {
                SliderPreference(
                    value = widgetInterval.toFloat(),
                    onValueChange = {
                        widgetInterval = it.toInt()
                        DevicePrefs.setWidgetRefreshMin(context, it.toInt())
                        RefreshWorker.schedulePeriodic(context)
                    },
                    title = "后台刷新间隔",
                    summary = "所有桌面组件共用；系统限制最短 15 分钟",
                    valueRange = 15f..120f,
                    valueText = "${widgetInterval}min",
                )
                ArrowPreference(
                    title = "立即刷新全部小组件",
                    summary = "马上向设备取数并更新桌面小组件",
                    onClick = { RefreshWorker.enqueueOneShot(context) },
                )
            }
        }
        item {
            SmallTitle("按组件 DIY")
            SettingsCard {
                WidgetKind.entries.forEach { kind ->
                    val appearance = DevicePrefs.getWidgetAppearance(context, kind)
                    val scale = DevicePrefs.getWidgetScalePercent(context, kind)
                    ArrowPreference(
                        title = kind.displayTitle,
                        summary = "${kind.summary} · ${DevicePrefs.widgetAppearanceLabel(appearance)} · ${scale}%",
                        onClick = { onOpenKind(kind) },
                    )
                }
            }
        }
        item {
            var showResetDialog by remember { mutableStateOf(false) }
            SmallTitle("其他")
            SettingsCard {
                ArrowPreference(
                    title = "恢复全部默认",
                    summary = "重置刷新间隔与全部组件 DIY 设置",
                    onClick = { showResetDialog = true },
                )
            }
            WindowDialog(
                show = showResetDialog,
                title = "恢复全部默认",
                summary = "将重置后台刷新间隔，以及各组件的外观、缩放、点击、内容与指标条",
                onDismissRequest = { showResetDialog = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showResetDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "恢复默认",
                        onClick = {
                            showResetDialog = false
                            DevicePrefs.resetWidgetSettings(context)
                            resetTick++
                            RefreshWorker.schedulePeriodic(context)
                            WidgetCommon.updateAll(context)
                            Toast.makeText(context, "已恢复默认设置", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

/** 单个组件的 DIY 设置页（独立导航目标） */
@Composable
fun WidgetKindDiyPage(kind: WidgetKind, onBack: () -> Unit) {
    val context = LocalContext.current
    var resetTick by remember { mutableIntStateOf(0) }
    var appearance by remember(kind, resetTick) {
        mutableIntStateOf(DevicePrefs.getWidgetAppearance(context, kind))
    }
    var scalePercent by remember(kind, resetTick) {
        mutableIntStateOf(DevicePrefs.getWidgetScalePercent(context, kind))
    }
    var tapAction by remember(kind, resetTick) {
        mutableIntStateOf(DevicePrefs.getWidgetTapAction(context, kind))
    }
    var signalIcon by remember(kind, resetTick) {
        mutableStateOf(DevicePrefs.isWidgetSignalIconEnabled(context, kind))
    }
    var showHeader by remember(resetTick) { mutableStateOf(DevicePrefs.isWidgetHeaderEnabled(context)) }
    var showFlowCards by remember(resetTick) { mutableStateOf(DevicePrefs.isWidgetFlowCardsEnabled(context)) }

    fun applyUpdate() = WidgetCommon.updateKind(context, kind)

    SettingsSubPage(title = kind.displayTitle, onBack = onBack) {
        item {
            SmallTitle("样式")
            SettingsCard {
                Text(
                    text = kind.summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                OverlayDropdownPreference(
                    items = listOf("浅色", "深色", "玻璃"),
                    selectedIndex = appearance,
                    onSelectedIndexChange = {
                        appearance = it
                        DevicePrefs.setWidgetAppearance(context, kind, it)
                        applyUpdate()
                    },
                    title = "外观",
                    summary = "仅影响本组件",
                )
                SliderPreference(
                    value = scalePercent.toFloat(),
                    onValueChange = {
                        scalePercent = it.toInt()
                        DevicePrefs.setWidgetScalePercent(context, kind, it.toInt())
                        applyUpdate()
                    },
                    title = "内容缩放",
                    summary = "适配不同 DPI 与格子大小",
                    valueRange = 70f..150f,
                    valueText = "${scalePercent}%",
                )
                OverlayDropdownPreference(
                    items = listOf("刷新数据", "打开应用"),
                    selectedIndex = tapAction,
                    onSelectedIndexChange = {
                        tapAction = it
                        DevicePrefs.setWidgetTapAction(context, kind, it)
                        applyUpdate()
                    },
                    title = "点击行为",
                    summary = "点击本组件时执行的操作",
                )
            }
        }
        if (kind.hasSignalIcon) {
            item {
                SmallTitle("信号显示")
                SettingsCard {
                    SwitchPreference(
                        checked = signalIcon,
                        onCheckedChange = {
                            signalIcon = it
                            DevicePrefs.setWidgetSignalIconEnabled(context, kind, it)
                            applyUpdate()
                        },
                        title = "信号用图标显示",
                        summary = if (signalIcon) {
                            if (kind == WidgetKind.SIGNAL) "显示信号格图标 + 格数" else "显示信号格图标"
                        } else {
                            "显示文字如 5/5"
                        },
                    )
                }
            }
        }
        if (kind.hasHeader || kind.hasFlowCardsToggle) {
            item {
                SmallTitle("布局开关")
                SettingsCard {
                    if (kind.hasHeader) {
                        SwitchPreference(
                            checked = showHeader,
                            onCheckedChange = {
                                showHeader = it
                                DevicePrefs.setWidgetHeaderEnabled(context, it)
                                applyUpdate()
                            },
                            title = "顶部状态栏",
                            summary = "信号、网络、设备型号、电池",
                        )
                    }
                    if (kind.hasFlowCardsToggle) {
                        SwitchPreference(
                            checked = showFlowCards,
                            onCheckedChange = {
                                showFlowCards = it
                                DevicePrefs.setWidgetFlowCardsEnabled(context, it)
                                applyUpdate()
                            },
                            title = "数据卡片",
                            summary = "中部左右两张大字卡片",
                        )
                    }
                }
            }
        }
        if (kind.hasCardSlots && (!kind.hasFlowCardsToggle || showFlowCards)) {
            item {
                SmallTitle("数据卡片内容")
                SettingsCard {
                    for (slot in 0 until kind.cardSlotCount) {
                        key(kind.id, slot, resetTick) {
                            var card by remember(kind, slot, resetTick) {
                                mutableIntStateOf(DevicePrefs.getWidgetCardSlot(context, kind, slot))
                            }
                            OverlayDropdownPreference(
                                items = WIDGET_CARD_OPTION_LABELS,
                                selectedIndex = card,
                                onSelectedIndexChange = {
                                    card = it
                                    DevicePrefs.setWidgetCardSlot(context, kind, slot, it)
                                    applyUpdate()
                                },
                                title = kind.cardSlotTitles.getOrElse(slot) { "卡片 ${slot + 1}" },
                            )
                        }
                    }
                }
            }
        }
        if (kind.hasMetrics) {
            item {
                SmallTitle(
                    when (kind) {
                        WidgetKind.SIGNAL -> "底部指标（最多 ${kind.metricMax} 项，长按排序）"
                        WidgetKind.SPEED -> "底部指标（最多 ${kind.metricMax} 项，长按排序）"
                        else -> "指标条（最多 ${kind.metricMax} 项，长按拖动排序）"
                    },
                )
                SettingsCard {
                    MetricReorderList(context, kind, resetTick)
                }
            }
        }
        if (kind.hasStatusSlots) {
            item {
                SmallTitle("四列内容")
                SettingsCard {
                    val slotLabels = WidgetKind.STATUS_SLOT_KEYS.map { STATUS_SLOT_TITLES[it] ?: it }
                    for (i in 0 until WidgetKind.STATUS_SLOT_COUNT) {
                        key(kind.id, "status", i, resetTick) {
                            var slotKey by remember(kind, i, resetTick) {
                                mutableStateOf(DevicePrefs.getStatusBarSlot(context, i))
                            }
                            val selectedIndex = WidgetKind.STATUS_SLOT_KEYS.indexOf(slotKey)
                                .takeIf { it >= 0 } ?: 0
                            OverlayDropdownPreference(
                                items = slotLabels,
                                selectedIndex = selectedIndex,
                                onSelectedIndexChange = {
                                    val next = WidgetKind.STATUS_SLOT_KEYS[it]
                                    slotKey = next
                                    DevicePrefs.setStatusBarSlot(context, i, next)
                                    applyUpdate()
                                },
                                title = "第 ${i + 1} 列",
                            )
                        }
                    }
                }
            }
        }
        item {
            var showResetDialog by remember { mutableStateOf(false) }
            SmallTitle("其他")
            SettingsCard {
                ArrowPreference(
                    title = "恢复本组件默认",
                    summary = "仅重置 ${kind.title} 的 DIY 设置",
                    onClick = { showResetDialog = true },
                )
            }
            WindowDialog(
                show = showResetDialog,
                title = "恢复本组件默认",
                summary = "将重置 ${kind.displayTitle} 的外观、缩放、点击与专属内容设置",
                onDismissRequest = { showResetDialog = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showResetDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "恢复默认",
                        onClick = {
                            showResetDialog = false
                            DevicePrefs.resetWidgetKindSettings(context, kind)
                            resetTick++
                            applyUpdate()
                            Toast.makeText(context, "已恢复 ${kind.title} 默认", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

/** 通知警报设置 */
@Composable
fun AlertSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current

    var alertEnabled by remember { mutableStateOf(DevicePrefs.isAlertEnabled(context)) }
    var tempThreshold by remember { mutableIntStateOf(DevicePrefs.getTempThreshold(context)) }
    var batteryThreshold by remember { mutableIntStateOf(DevicePrefs.getBatteryThreshold(context)) }
    var cpuThreshold by remember { mutableIntStateOf(DevicePrefs.getCpuThreshold(context)) }
    var memThreshold by remember { mutableIntStateOf(DevicePrefs.getMemThreshold(context)) }
    var dailyFlowGb by remember { mutableIntStateOf(DevicePrefs.getDailyFlowThresholdGb(context)) }
    var monthlyFlowGb by remember { mutableIntStateOf(DevicePrefs.getMonthlyFlowThresholdGb(context)) }
    var smsNotify by remember { mutableStateOf(DevicePrefs.isSmsNotifyEnabled(context)) }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingPermissionAction?.invoke()
        pendingPermissionAction = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        if (AlertNotifier.hasPermission(context)) {
            action()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 13 以下没有 POST_NOTIFICATIONS 运行时权限，申请必然被拒（否则开关永远打不开）：
            // 设置照常保存，同时把用户送到系统通知设置页重新打开通知
            action()
            AlertNotifier.openNotificationSettings(context)
            return
        }
        pendingPermissionAction = action
        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    SettingsSubPage(title = "通知警报", onBack = onBack) {
        item {
            SmallTitle("阈值警报")
            SettingsCard {
                SwitchPreference(
                    checked = alertEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            withNotificationPermission {
                                alertEnabled = true
                                DevicePrefs.setAlertEnabled(context, true)
                            }
                        } else {
                            alertEnabled = false
                            DevicePrefs.setAlertEnabled(context, false)
                        }
                    },
                    title = "阈值警报",
                    summary = "温度/电量/CPU/内存/流量超过阈值时通知",
                )
                if (alertEnabled) {
                    SliderPreference(
                        value = tempThreshold.toFloat(),
                        onValueChange = {
                            tempThreshold = it.toInt()
                            DevicePrefs.setTempThreshold(context, it.toInt())
                        },
                        title = "温度阈值",
                        valueRange = 40f..90f,
                        valueText = "${tempThreshold}℃",
                    )
                    SliderPreference(
                        value = batteryThreshold.toFloat(),
                        onValueChange = {
                            batteryThreshold = it.toInt()
                            DevicePrefs.setBatteryThreshold(context, it.toInt())
                        },
                        title = "低电量阈值",
                        valueRange = 5f..50f,
                        valueText = "${batteryThreshold}%",
                    )
                    SliderPreference(
                        value = cpuThreshold.toFloat(),
                        onValueChange = {
                            cpuThreshold = it.toInt()
                            DevicePrefs.setCpuThreshold(context, it.toInt())
                        },
                        title = "CPU 使用率阈值",
                        valueRange = 50f..100f,
                        valueText = "${cpuThreshold}%",
                    )
                    SliderPreference(
                        value = memThreshold.toFloat(),
                        onValueChange = {
                            memThreshold = it.toInt()
                            DevicePrefs.setMemThreshold(context, it.toInt())
                        },
                        title = "内存使用率阈值",
                        valueRange = 50f..100f,
                        valueText = "${memThreshold}%",
                    )
                    FlowThresholdPreference(
                        title = "每日流量阈值",
                        valueGb = dailyFlowGb,
                        onValueChange = {
                            dailyFlowGb = it
                            DevicePrefs.setDailyFlowThresholdGb(context, it)
                        },
                    )
                    FlowThresholdPreference(
                        title = "每月流量阈值",
                        valueGb = monthlyFlowGb,
                        onValueChange = {
                            monthlyFlowGb = it
                            DevicePrefs.setMonthlyFlowThresholdGb(context, it)
                        },
                    )
                }
            }
        }
        item {
            SmallTitle("短信")
            SettingsCard {
                SwitchPreference(
                    checked = smsNotify,
                    onCheckedChange = { enable ->
                        if (enable) {
                            withNotificationPermission {
                                smsNotify = true
                                DevicePrefs.setSmsNotifyEnabled(context, true)
                            }
                        } else {
                            smsNotify = false
                            DevicePrefs.setSmsNotifyEnabled(context, false)
                        }
                    },
                    title = "新短信通知",
                    summary = "后台监控运行时，设备收到新短信将通知（需开启保活服务）",
                )
            }
        }
    }
}

/** 后台服务设置 */
@Composable
fun ServiceSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current

    var keepAlive by remember { mutableStateOf(DevicePrefs.isKeepAliveEnabled(context)) }
    var monitorInterval by remember { mutableIntStateOf(DevicePrefs.getMonitorIntervalSec(context)) }
    var trafficRecord by remember { mutableStateOf(DevicePrefs.isTrafficRecordEnabled(context)) }
    var retentionDays by remember { mutableIntStateOf(DevicePrefs.getTrafficRetentionDays(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingPermissionAction?.invoke()
        pendingPermissionAction = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        if (AlertNotifier.hasPermission(context)) {
            action()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 同上：33 以下只能引导用户去系统通知设置，保活服务本身不受通知开关影响
            action()
            AlertNotifier.openNotificationSettings(context)
            return
        }
        pendingPermissionAction = action
        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    SettingsSubPage(title = "后台服务", onBack = onBack) {
        item {
            SmallTitle("保活")
            SettingsCard {
                SwitchPreference(
                    checked = keepAlive,
                    onCheckedChange = { enable ->
                        if (enable) {
                            withNotificationPermission {
                                keepAlive = true
                                DevicePrefs.setKeepAliveEnabled(context, true)
                                MonitorService.start(context)
                            }
                        } else {
                            keepAlive = false
                            DevicePrefs.setKeepAliveEnabled(context, false)
                            MonitorService.stop(context)
                        }
                    },
                    title = "前台保活服务",
                    summary = "常驻通知栏，持续监控设备状态并触发警报",
                )
                if (keepAlive) {
                    SliderPreference(
                        value = monitorInterval.toFloat(),
                        onValueChange = {
                            monitorInterval = it.toInt()
                            DevicePrefs.setMonitorIntervalSec(context, it.toInt())
                        },
                        title = "后台监控间隔",
                        valueRange = 15f..300f,
                        valueText = "${monitorInterval}s",
                    )
                }
            }
        }
        item {
            SmallTitle("流量历史")
            SettingsCard {
                SwitchPreference(
                    checked = trafficRecord,
                    onCheckedChange = {
                        trafficRecord = it
                        DevicePrefs.setTrafficRecordEnabled(context, it)
                    },
                    title = "记录流量历史",
                    summary = "每次采集时记录当日用量，供流量历史页展示",
                )
                if (trafficRecord) {
                    SliderPreference(
                        value = retentionDays.toFloat(),
                        onValueChange = {
                            retentionDays = it.toInt()
                            DevicePrefs.setTrafficRetentionDays(context, it.toInt())
                        },
                        title = "历史保留天数",
                        summary = "超过保留天数的记录将自动清理",
                        valueRange = 7f..180f,
                        valueText = "${retentionDays}天",
                    )
                }
                ArrowPreference(
                    title = "清空流量历史",
                    summary = "删除所有已记录的每日流量数据",
                    onClick = { showClearDialog = true },
                )
            }
            WindowDialog(
                show = showClearDialog,
                title = "清空流量历史",
                summary = "将删除所有已记录的每日流量数据，无法恢复",
                onDismissRequest = { showClearDialog = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showClearDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "清空",
                        onClick = {
                            TrafficHistory.clear(context)
                            showClearDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

/** 显示与个性化设置 */
@Composable
fun DisplaySettingsPage(
    onBack: () -> Unit,
    themeSettings: ThemeSettings,
    onThemeSettingsChange: (ThemeSettings) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    floatingBottomBar: Boolean,
    onFloatingBottomBarChange: (Boolean) -> Unit,
    floatingBottomBarBlur: Boolean,
    onFloatingBottomBarBlurChange: (Boolean) -> Unit,
) {
    // 模糊与液态玻璃都基于 AGSL（API 33），Android 12 上只能关掉开关
    val blurSupported = isRuntimeShaderSupported()
    val context = LocalContext.current

    SettingsSubPage(title = "显示与个性化", onBack = onBack) {
        item {
            SmallTitle("主题")
            SettingsCard {
                OverlayDropdownPreference(
                    items = listOf("跟随系统", "浅色模式", "深色模式"),
                    selectedIndex = themeSettings.mode,
                    onSelectedIndexChange = { onThemeSettingsChange(themeSettings.copy(mode = it)) },
                    title = "主题模式",
                    summary = "切换应用的深浅色外观",
                )
                SwitchPreference(
                    checked = themeSettings.monet,
                    onCheckedChange = { onThemeSettingsChange(themeSettings.copy(monet = it)) },
                    title = "莫奈取色",
                    summary = "根据种子色动态生成配色方案",
                )
                if (themeSettings.monet) {
                    OverlayDropdownPreference(
                        items = MonetKeyColorOptions,
                        selectedIndex = themeSettings.seedIndex,
                        onSelectedIndexChange = { onThemeSettingsChange(themeSettings.copy(seedIndex = it)) },
                        title = "种子色",
                        summary = "莫奈配色的取色来源",
                    )
                    OverlayDropdownPreference(
                        items = PaletteStyleOptions,
                        selectedIndex = themeSettings.paletteIndex,
                        onSelectedIndexChange = { onThemeSettingsChange(themeSettings.copy(paletteIndex = it)) },
                        title = "调色板风格",
                    )
                }
            }
        }
        item {
            SmallTitle("界面效果")
            SettingsCard {
                SwitchPreference(
                    checked = blurEnabled && blurSupported,
                    onCheckedChange = { onBlurEnabledChange(it) },
                    title = "顶栏/底栏模糊",
                    summary = if (blurSupported) "使用实时背景模糊" else "当前系统不支持（需 Android 13+）",
                    enabled = blurSupported,
                )
                SwitchPreference(
                    checked = floatingBottomBar,
                    onCheckedChange = { onFloatingBottomBarChange(it) },
                    title = "悬浮底栏",
                    summary = "使用 Apple 风格的悬浮底栏",
                )
                if (floatingBottomBar) {
                    SwitchPreference(
                        checked = floatingBottomBarBlur && blurSupported,
                        onCheckedChange = { onFloatingBottomBarBlurChange(it) },
                        title = "液态玻璃",
                        summary = if (blurSupported) "启用悬浮底栏的液态玻璃效果" else "当前系统不支持（需 Android 13+）",
                        enabled = blurSupported,
                    )
                }
            }
        }
    }
}

/** 锁屏与息屏显示设置 */
@Composable
fun LockAodSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var showAodGuide by remember { mutableStateOf(false) }
    var launchAfterGuide by remember { mutableStateOf(false) }
    var moduleResetTick by remember { mutableIntStateOf(0) }
    var modulePreviewTick by remember { mutableIntStateOf(0) }
    var runtimeResetTick by remember { mutableIntStateOf(0) }
    var moduleColumns by remember { mutableIntStateOf(DevicePrefs.getAodModuleColumns(context)) }
    var moduleFontScale by remember { mutableIntStateOf(DevicePrefs.getAodFontScale(context)) }
    var moduleClockSize by remember { mutableIntStateOf(DevicePrefs.getAodClockSize(context)) }
    var moduleSpacing by remember { mutableIntStateOf(DevicePrefs.getAodModuleSpacing(context)) }
    var moduleAlignment by remember { mutableIntStateOf(DevicePrefs.getAodTextAlignment(context)) }
    var moduleColor by remember { mutableIntStateOf(DevicePrefs.getAodColorScheme(context)) }
    var moduleLabels by remember { mutableStateOf(DevicePrefs.isAodModuleLabelEnabled(context)) }
    var moduleOutline by remember { mutableStateOf(DevicePrefs.isAodModuleOutlineEnabled(context)) }
    var verticalPosition by remember { mutableIntStateOf(DevicePrefs.getAodVerticalPosition(context)) }

    SettingsSubPage(title = "锁屏与息屏", onBack = onBack) {
        item {
            SmallTitle("锁屏")
            SettingsCard {
                var showOnLockScreen by remember { mutableStateOf(DevicePrefs.isShowOnLockScreen(context)) }
                SwitchPreference(
                    checked = showOnLockScreen,
                    onCheckedChange = {
                        showOnLockScreen = it
                        DevicePrefs.setShowOnLockScreen(context, it)
                    },
                    title = "锁屏上显示界面",
                    summary = "无需解锁，在锁屏之上直接查看应用界面（从锁屏通知或最近任务进入）",
                )
            }
        }
        item {
            SmallTitle("息屏 · 进入")
            SettingsCard {
                Text(
                    text = "注意：这是伪息屏显示，屏幕实际仍保持点亮，会持续耗电；并非手机系统级 AOD。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color(0xFFD46A4C),
                    fontSize = 14.sp,
                )
                ArrowPreference(
                    title = "使用教程与注意事项",
                    summary = "模块 DIY、退出方式、省电与防烧屏建议",
                    onClick = {
                        launchAfterGuide = false
                        showAodGuide = true
                    },
                )
                ArrowPreference(
                    title = "进入息屏显示",
                    summary = "按当前模块、顺序与样式进入 DIY 息屏画布",
                    onClick = {
                        if (DevicePrefs.isAodGuideAcknowledged(context)) {
                            context.startActivity(
                                android.content.Intent(context, com.xingyue.ufitools.monitor.AodActivity::class.java),
                            )
                        } else {
                            launchAfterGuide = true
                            showAodGuide = true
                        }
                    },
                )
            }
        }
        item {
            SmallTitle("息屏 · 显示")
            SettingsCard {
                key(runtimeResetTick) {
                    var aodBrightness by remember { mutableIntStateOf(DevicePrefs.getAodBrightness(context)) }
                    var aodBatteryBrightness by remember {
                        mutableIntStateOf(DevicePrefs.getAodBatteryBrightness(context))
                    }
                    var aodOrientation by remember { mutableIntStateOf(DevicePrefs.getAodOrientation(context)) }
                    var aodSeconds by remember { mutableStateOf(DevicePrefs.isAodShowSeconds(context)) }
                    var aodClock24h by remember { mutableStateOf(DevicePrefs.isAodClock24Hour(context)) }
                    var aodShowLastUpdate by remember { mutableStateOf(DevicePrefs.isAodShowLastUpdate(context)) }
                    var aodShowEntryHint by remember { mutableStateOf(DevicePrefs.isAodShowEntryHint(context)) }

                    SliderPreference(
                        value = aodBrightness.toFloat(),
                        onValueChange = {
                            aodBrightness = it.toInt()
                            DevicePrefs.setAodBrightness(context, it.toInt())
                        },
                        title = "息屏亮度",
                        summary = "越低越省电（OLED 黑像素不发光）",
                        valueRange = 1f..40f,
                        valueText = "${aodBrightness}%",
                    )
                    SliderPreference(
                        value = aodBatteryBrightness.toFloat(),
                        onValueChange = {
                            aodBatteryBrightness = it.toInt()
                            DevicePrefs.setAodBatteryBrightness(context, it.toInt())
                        },
                        title = "未充电亮度",
                        summary = "未插电时使用的亮度；0 表示与上方一致",
                        valueRange = 0f..40f,
                        valueText = if (aodBatteryBrightness == 0) "同息屏亮度" else "${aodBatteryBrightness}%",
                    )
                    OverlayDropdownPreference(
                        items = listOf("跟随系统", "固定竖屏", "固定横屏"),
                        selectedIndex = aodOrientation,
                        onSelectedIndexChange = {
                            aodOrientation = it
                            DevicePrefs.setAodOrientation(context, it)
                        },
                        title = "屏幕方向",
                        summary = "模块网格会根据横竖屏自动重新排版",
                    )
                    OverlayDropdownPreference(
                        items = listOf("偏上", "居中", "偏下"),
                        selectedIndex = verticalPosition,
                        onSelectedIndexChange = {
                            verticalPosition = it
                            DevicePrefs.setAodVerticalPosition(context, it)
                            modulePreviewTick++
                        },
                        title = "内容位置",
                        summary = "整体内容在屏幕中的垂直位置",
                    )
                    SwitchPreference(
                        checked = aodSeconds,
                        onCheckedChange = {
                            aodSeconds = it
                            DevicePrefs.setAodShowSeconds(context, it)
                        },
                        title = "时钟显示秒",
                        summary = "开启后时钟每秒更新，略增加耗电",
                    )
                    SwitchPreference(
                        checked = aodClock24h,
                        onCheckedChange = {
                            aodClock24h = it
                            DevicePrefs.setAodClock24Hour(context, it)
                            modulePreviewTick++
                        },
                        title = "24 小时制",
                        summary = "关闭后使用 12 小时制（上午/下午）",
                    )
                    SwitchPreference(
                        checked = aodShowLastUpdate,
                        onCheckedChange = {
                            aodShowLastUpdate = it
                            DevicePrefs.setAodShowLastUpdate(context, it)
                        },
                        title = "显示更新时间",
                        summary = "底部展示最近一次成功取数时间",
                    )
                    SwitchPreference(
                        checked = aodShowEntryHint,
                        onCheckedChange = {
                            aodShowEntryHint = it
                            DevicePrefs.setAodShowEntryHint(context, it)
                        },
                        title = "进入时提示",
                        summary = "进入息屏后短暂提示退出手势",
                    )
                }
            }
        }
        item {
            SmallTitle("息屏 · 省电与安全")
            SettingsCard {
                key(runtimeResetTick) {
                    var aodRefresh by remember { mutableIntStateOf(DevicePrefs.getAodRefreshSec(context)) }
                    var aodDoubleTap by remember { mutableStateOf(DevicePrefs.isAodDoubleTapExit(context)) }
                    var aodChargingOnly by remember { mutableStateOf(DevicePrefs.isAodChargingOnly(context)) }
                    var aodTimeout by remember { mutableIntStateOf(DevicePrefs.getAodTimeoutMin(context)) }
                    var aodLowBattery by remember {
                        mutableIntStateOf(DevicePrefs.getAodLowBatteryThreshold(context))
                    }
                    var aodPocketProtection by remember {
                        mutableStateOf(DevicePrefs.isAodPocketProtectionEnabled(context))
                    }

                    SliderPreference(
                        value = aodRefresh.toFloat(),
                        onValueChange = {
                            aodRefresh = it.toInt()
                            DevicePrefs.setAodRefreshSec(context, it.toInt())
                        },
                        title = "数据刷新间隔",
                        summary = "离线时会自动延长间隔，最长 5 分钟",
                        valueRange = 30f..300f,
                        valueText = "${aodRefresh}s",
                    )
                    SwitchPreference(
                        checked = aodDoubleTap,
                        onCheckedChange = {
                            aodDoubleTap = it
                            DevicePrefs.setAodDoubleTapExit(context, it)
                        },
                        title = "双击退出",
                        summary = "防误触；关闭后单击即退出",
                    )
                    SwitchPreference(
                        checked = aodChargingOnly,
                        onCheckedChange = {
                            aodChargingOnly = it
                            DevicePrefs.setAodChargingOnly(context, it)
                        },
                        title = "仅充电时运行",
                        summary = "拔下充电器后自动退出，减少手机耗电",
                    )
                    SliderPreference(
                        value = aodTimeout.toFloat(),
                        onValueChange = {
                            aodTimeout = it.toInt()
                            DevicePrefs.setAodTimeoutMin(context, it.toInt())
                        },
                        title = "自动退出时长",
                        summary = "限制单次息屏显示的最长运行时间",
                        valueRange = 0f..120f,
                        valueText = if (aodTimeout == 0) "不限制" else "${aodTimeout}min",
                    )
                    SliderPreference(
                        value = aodLowBattery.toFloat(),
                        onValueChange = {
                            aodLowBattery = it.toInt()
                            DevicePrefs.setAodLowBatteryThreshold(context, it.toInt())
                        },
                        title = "手机低电量退出",
                        summary = "未充电且达到阈值时自动退出",
                        valueRange = 0f..30f,
                        valueText = if (aodLowBattery == 0) "关闭" else "${aodLowBattery}%",
                    )
                    SwitchPreference(
                        checked = aodPocketProtection,
                        onCheckedChange = {
                            aodPocketProtection = it
                            DevicePrefs.setAodPocketProtectionEnabled(context, it)
                        },
                        title = "口袋防误触",
                        summary = "距离传感器持续被遮挡 2 秒后自动退出",
                    )
                }
            }
        }
        item {
            SmallTitle("息屏 · 防烧屏")
            SettingsCard {
                key(runtimeResetTick) {
                    var aodAntiBurnIn by remember { mutableStateOf(DevicePrefs.isAodAntiBurnIn(context)) }
                    var burnInInterval by remember {
                        mutableIntStateOf(DevicePrefs.getAodBurnInIntervalSec(context))
                    }
                    var burnInAmplitude by remember {
                        mutableIntStateOf(DevicePrefs.getAodBurnInAmplitude(context))
                    }

                    SwitchPreference(
                        checked = aodAntiBurnIn,
                        onCheckedChange = {
                            aodAntiBurnIn = it
                            DevicePrefs.setAodAntiBurnIn(context, it)
                        },
                        title = "防烧屏位移",
                        summary = "按路径轻微移动内容，减轻 OLED 残影",
                    )
                    if (aodAntiBurnIn) {
                        SliderPreference(
                            value = burnInInterval.toFloat(),
                            onValueChange = {
                                burnInInterval = it.toInt()
                                DevicePrefs.setAodBurnInIntervalSec(context, it.toInt())
                            },
                            title = "位移间隔",
                            summary = "越短越安全，也更易察觉内容在动",
                            valueRange = 30f..300f,
                            valueText = "${burnInInterval}s",
                        )
                        OverlayDropdownPreference(
                            items = listOf("弱", "中", "强"),
                            selectedIndex = burnInAmplitude,
                            onSelectedIndexChange = {
                                burnInAmplitude = it
                                DevicePrefs.setAodBurnInAmplitude(context, it)
                            },
                            title = "位移幅度",
                            summary = "弱更不易察觉，强更利于防残影",
                        )
                    }
                }
            }
        }
        item {
            SmallTitle("DIY 实时预览")
            SettingsCard {
                Text(
                    text = "预览按竖屏自动 1 列排版；有缓存时会尽量使用真实数据。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
                AodModulePreview(context, modulePreviewTick)
            }
        }
        item {
            SmallTitle("模块开关与顺序")
            SettingsCard {
                AodModuleReorderList(
                    context = context,
                    resetTick = moduleResetTick,
                    onChanged = { modulePreviewTick++ },
                )
            }
        }
        item {
            SmallTitle("模块布局与样式")
            SettingsCard {
                OverlayDropdownPreference(
                    items = listOf("自动", "1 列", "2 列", "3 列"),
                    selectedIndex = moduleColumns,
                    onSelectedIndexChange = {
                        moduleColumns = it
                        DevicePrefs.setAodModuleColumns(context, it)
                        modulePreviewTick++
                    },
                    title = "模块列数",
                    summary = "自动：竖屏 1 列、横屏 2 列",
                )
                OverlayDropdownPreference(
                    items = listOf("左对齐", "居中", "右对齐"),
                    selectedIndex = moduleAlignment,
                    onSelectedIndexChange = {
                        moduleAlignment = it
                        DevicePrefs.setAodTextAlignment(context, it)
                        modulePreviewTick++
                    },
                    title = "文字对齐",
                    summary = "统一调整所有模块的内容对齐方式",
                )
                OverlayDropdownPreference(
                    items = listOf("灰白", "暖白", "护眼红", "护眼绿", "琥珀"),
                    selectedIndex = moduleColor,
                    onSelectedIndexChange = {
                        moduleColor = it
                        DevicePrefs.setAodColorScheme(context, it)
                        modulePreviewTick++
                    },
                    title = "息屏配色",
                    summary = "低亮度单色方案更适合夜间和 OLED",
                )
                SliderPreference(
                    value = moduleFontScale.toFloat(),
                    onValueChange = {
                        moduleFontScale = it.toInt()
                        DevicePrefs.setAodFontScale(context, it.toInt())
                        modulePreviewTick++
                    },
                    title = "数据字号",
                    summary = "调整除时钟外的全部模块字号",
                    valueRange = 70f..160f,
                    valueText = "${moduleFontScale}%",
                )
                SliderPreference(
                    value = moduleClockSize.toFloat(),
                    onValueChange = {
                        moduleClockSize = it.toInt()
                        DevicePrefs.setAodClockSize(context, it.toInt())
                        modulePreviewTick++
                    },
                    title = "时钟字号",
                    summary = "时钟模块可独立缩放",
                    valueRange = 36f..96f,
                    valueText = "${moduleClockSize}sp",
                )
                SliderPreference(
                    value = moduleSpacing.toFloat(),
                    onValueChange = {
                        moduleSpacing = it.toInt()
                        DevicePrefs.setAodModuleSpacing(context, it.toInt())
                        modulePreviewTick++
                    },
                    title = "模块间距",
                    summary = "0 为紧凑，数值越大留白越多",
                    valueRange = 0f..24f,
                    valueText = "${moduleSpacing}dp",
                )
                SwitchPreference(
                    checked = moduleLabels,
                    onCheckedChange = {
                        moduleLabels = it
                        DevicePrefs.setAodModuleLabelEnabled(context, it)
                        modulePreviewTick++
                    },
                    title = "显示模块标题",
                    summary = "关闭后仅保留数据，界面更简洁",
                )
                SwitchPreference(
                    checked = moduleOutline,
                    onCheckedChange = {
                        moduleOutline = it
                        DevicePrefs.setAodModuleOutlineEnabled(context, it)
                        modulePreviewTick++
                    },
                    title = "显示模块边框",
                    summary = "为每个模块添加低亮度圆角边框",
                )
                AodCustomTextPreference(context) { modulePreviewTick++ }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = "恢复模块",
                        onClick = {
                            DevicePrefs.resetAodModuleSettings(context)
                            moduleColumns = DevicePrefs.getAodModuleColumns(context)
                            moduleFontScale = DevicePrefs.getAodFontScale(context)
                            moduleClockSize = DevicePrefs.getAodClockSize(context)
                            moduleSpacing = DevicePrefs.getAodModuleSpacing(context)
                            moduleAlignment = DevicePrefs.getAodTextAlignment(context)
                            moduleColor = DevicePrefs.getAodColorScheme(context)
                            moduleLabels = DevicePrefs.isAodModuleLabelEnabled(context)
                            moduleOutline = DevicePrefs.isAodModuleOutlineEnabled(context)
                            verticalPosition = DevicePrefs.getAodVerticalPosition(context)
                            moduleResetTick++
                            modulePreviewTick++
                            Toast.makeText(context, "已恢复模块布局", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "恢复全部",
                        onClick = {
                            DevicePrefs.resetAodAllSettings(context)
                            moduleColumns = DevicePrefs.getAodModuleColumns(context)
                            moduleFontScale = DevicePrefs.getAodFontScale(context)
                            moduleClockSize = DevicePrefs.getAodClockSize(context)
                            moduleSpacing = DevicePrefs.getAodModuleSpacing(context)
                            moduleAlignment = DevicePrefs.getAodTextAlignment(context)
                            moduleColor = DevicePrefs.getAodColorScheme(context)
                            moduleLabels = DevicePrefs.isAodModuleLabelEnabled(context)
                            moduleOutline = DevicePrefs.isAodModuleOutlineEnabled(context)
                            verticalPosition = DevicePrefs.getAodVerticalPosition(context)
                            moduleResetTick++
                            modulePreviewTick++
                            runtimeResetTick++
                            Toast.makeText(context, "已恢复全部息屏设置", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    AodGuideDialog(
        show = showAodGuide,
        launchAfterConfirm = launchAfterGuide,
        onDismiss = { showAodGuide = false },
        onConfirm = {
            DevicePrefs.setAodGuideAcknowledged(context, true)
            showAodGuide = false
            if (launchAfterGuide) {
                context.startActivity(
                    android.content.Intent(context, com.xingyue.ufitools.monitor.AodActivity::class.java),
                )
            }
        },
    )
}

/** 流量阈值输入项：点击弹出对话框，用户输入 GB 数，0 表示不监控 */
@Composable
private fun FlowThresholdPreference(
    title: String,
    valueGb: Int,
    onValueChange: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    ArrowPreference(
        title = title,
        summary = if (valueGb == 0) "关（0 表示不监控）" else "${valueGb}GB",
        onClick = {
            input = valueGb.toString()
            showDialog = true
        },
    )

    WindowDialog(
        show = showDialog,
        title = title,
        summary = "单位 GB，0 表示不监控",
        onDismissRequest = { showDialog = false },
    ) {
        TextField(
            value = input,
            onValueChange = { text -> input = text.filter { it.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = "阈值（GB）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确定",
                onClick = {
                    onValueChange(input.toIntOrNull() ?: 0)
                    showDialog = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
