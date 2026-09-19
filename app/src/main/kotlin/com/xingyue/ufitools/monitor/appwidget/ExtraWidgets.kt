package com.xingyue.ufitools.monitor.appwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus
import com.xingyue.ufitools.monitor.worker.RefreshWorker

// ── 流量条（4×1）──────────────────────────────────────────

/** 横向流量卡：今日 + 本月，适合桌面一行展示 */
class UfiTrafficWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            val appearance = readWidgetAppearance(state, context, WidgetKind.TRAFFIC)
            val snapshot = readWidgetSnapshot(state, context)
            ProvideWidgetTheme(appearance) {
                GlanceTheme { TrafficWidgetContent(context, snapshot) }
            }
        }
    }
}

class UfiTrafficWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UfiTrafficWidget()

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
}

@Composable
private fun TrafficWidgetContent(context: Context, snapshot: DeviceStatus) {
    val userScale = DevicePrefs.getWidgetScalePercent(context, WidgetKind.TRAFFIC) / 100f
    val size = LocalSize.current
    val fitScale = minOf(size.width.value / 320f, size.height.value / 52f).coerceIn(0.6f, 1.6f)
    val scale = (fitScale * userScale).coerceIn(0.55f, 1.8f)
    val left = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.TRAFFIC, 0), snapshot)
    val right = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.TRAFFIC, 1), snapshot)
    WidgetShell(context, corner = 14.dp.scale(scale), kind = WidgetKind.TRAFFIC) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(
                horizontal = 8.dp.scale(scale),
                vertical = 6.dp.scale(scale),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetFlowCard(
                modifier = GlanceModifier.defaultWeight().height(52.dp.scale(scale)),
                iconRes = left.iconRes,
                title = left.title,
                value = left.value.ifBlank { "--" },
                background = WidgetPalette.todayCard,
                monthStyle = false,
                valueSize = 16.sp.scale(scale),
                scale = scale,
            )
            Spacer(GlanceModifier.width(8.dp.scale(scale)))
            WidgetFlowCard(
                modifier = GlanceModifier.defaultWeight().height(52.dp.scale(scale)),
                iconRes = right.iconRes,
                title = right.title,
                value = right.value.ifBlank { "--" },
                background = WidgetPalette.monthCard,
                monthStyle = true,
                valueSize = 16.sp.scale(scale),
                scale = scale,
            )
        }
    }
}

// ── 信号卡（2×2）──────────────────────────────────────────

/** 信号专项：格数 + 制式 + RSRP / SINR / 频段 */
class UfiSignalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            ProvideWidgetTheme(readWidgetAppearance(state, context, WidgetKind.SIGNAL)) {
                GlanceTheme {
                    SignalWidgetContent(context, readWidgetSnapshot(state, context))
                }
            }
        }
    }
}

class UfiSignalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UfiSignalWidget()

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
}

@Composable
private fun SignalWidgetContent(context: Context, snapshot: DeviceStatus) {
    val userScale = DevicePrefs.getWidgetScalePercent(context, WidgetKind.SIGNAL) / 100f
    val scale = (minOf(
        LocalSize.current.width.value / 160f,
        LocalSize.current.height.value / 160f,
    ).coerceIn(0.65f, 1.75f) * userScale).coerceIn(0.55f, 2f)
    val useIcon = DevicePrefs.isWidgetSignalIconEnabled(context, WidgetKind.SIGNAL)
    val bottomMetrics = DevicePrefs.getEnabledWidgetMetrics(context, WidgetKind.SIGNAL)
        .mapNotNull { resolveMetric(it, snapshot) }
    WidgetShell(context, corner = 16.dp.scale(scale), kind = WidgetKind.SIGNAL) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(10.dp.scale(scale)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "信号",
                    style = TextStyle(
                        color = ColorProvider(WidgetPalette.secondary),
                        fontSize = 11.sp.scale(scale),
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    shortNetworkMode(snapshot.netType),
                    style = TextStyle(
                        color = ColorProvider(WidgetPalette.primary),
                        fontSize = 12.sp.scale(scale),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp.scale(scale)))
            // 信号格：默认图标 + 5/5；关闭图标时只显示文字
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (useIcon) {
                    val level = if (snapshot.signalBar in 0..5) snapshot.signalBar else 0
                    Image(
                        ImageProvider(signalBarIconRes(level)),
                        contentDescription = signalBarText(snapshot.signalBar),
                        modifier = GlanceModifier
                            .width(36.dp.scale(scale))
                            .height(36.dp.scale(scale)),
                    )
                    Spacer(GlanceModifier.width(10.dp.scale(scale)))
                }
                Text(
                    signalBarText(snapshot.signalBar),
                    style = TextStyle(
                        color = ColorProvider(WidgetPalette.primary),
                        fontSize = 26.sp.scale(scale),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp.scale(scale)))
            if (bottomMetrics.isNotEmpty()) {
                val panel = panelBackground()
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .then(panel)
                        .cornerRadius(10.dp.scale(scale))
                        .padding(horizontal = 6.dp.scale(scale), vertical = 6.dp.scale(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    bottomMetrics.forEachIndexed { index, metric ->
                        MiniMetric(
                            iconRes = metric.iconRes,
                            label = metric.label,
                            value = metric.value.ifBlank { "--" },
                            scale = scale,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        if (index < bottomMetrics.lastIndex) {
                            WidgetDivider(46.dp.scale(scale))
                        }
                    }
                }
            }
        }
    }
}

// ── 流量速率卡（2×2）──────────────────────────────────────

/**
 * 2×2 紧凑卡：日流量 / 月流量 + QCI / 速率。
 * 不用瞬时上下行——小组件刷新间隔长，容易误导。
 */
class UfiSpeedWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            ProvideWidgetTheme(readWidgetAppearance(state, context, WidgetKind.SPEED)) {
                GlanceTheme {
                    FlowQosWidgetContent(context, readWidgetSnapshot(state, context))
                }
            }
        }
    }
}

class UfiSpeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UfiSpeedWidget()

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
}

@Composable
private fun FlowQosWidgetContent(context: Context, snapshot: DeviceStatus) {
    val userScale = DevicePrefs.getWidgetScalePercent(context, WidgetKind.SPEED) / 100f
    val scale = (minOf(
        LocalSize.current.width.value / 160f,
        LocalSize.current.height.value / 160f,
    ).coerceIn(0.65f, 1.75f) * userScale).coerceIn(0.55f, 2f)
    val top = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.SPEED, 0), snapshot)
    val bottom = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.SPEED, 1), snapshot)
    val footerMetrics = DevicePrefs.getEnabledWidgetMetrics(context, WidgetKind.SPEED)
        .mapNotNull { resolveMetric(it, snapshot) }
    fun trafficValueSize(value: String) = when {
        value.length >= 14 -> 13.sp.scale(scale)
        value.length >= 10 -> 15.sp.scale(scale)
        else -> 17.sp.scale(scale)
    }

    WidgetShell(context, corner = 16.dp.scale(scale), kind = WidgetKind.SPEED) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp.scale(scale)),
        ) {
            WidgetFlowCard(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                iconRes = top.iconRes,
                title = top.title,
                value = top.value.ifBlank { "--" },
                background = WidgetPalette.todayCard,
                monthStyle = false,
                valueSize = trafficValueSize(top.value),
                scale = scale,
                valueMaxLines = 2,
            )
            Spacer(GlanceModifier.height(6.dp.scale(scale)))
            WidgetFlowCard(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                iconRes = bottom.iconRes,
                title = bottom.title,
                value = bottom.value.ifBlank { "--" },
                background = WidgetPalette.monthCard,
                monthStyle = true,
                valueSize = trafficValueSize(bottom.value),
                scale = scale,
                valueMaxLines = 2,
            )
            if (footerMetrics.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp.scale(scale)))
                val panel = panelBackground()
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                        .then(panel)
                        .cornerRadius(10.dp.scale(scale))
                        .padding(horizontal = 6.dp.scale(scale), vertical = 7.dp.scale(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    footerMetrics.forEachIndexed { index, metric ->
                        MiniMetric(
                            iconRes = metric.iconRes,
                            label = metric.label,
                            value = metric.value.ifBlank { "--" },
                            scale = scale,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        if (index < footerMetrics.lastIndex) {
                            WidgetDivider(42.dp.scale(scale))
                        }
                    }
                }
            }
        }
    }
}

// ── 状态条（4×1）──────────────────────────────────────────

/** 一行状态：机型 · 信号 · 网络 · 电量 · 今日流量 */
class UfiStatusBarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            ProvideWidgetTheme(readWidgetAppearance(state, context, WidgetKind.STATUS_BAR)) {
                GlanceTheme {
                    StatusBarWidgetContent(context, readWidgetSnapshot(state, context))
                }
            }
        }
    }
}

class UfiStatusBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UfiStatusBarWidget()

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
}

@Composable
private fun StatusBarWidgetContent(context: Context, snapshot: DeviceStatus) {
    val size = LocalSize.current
    val userScale = DevicePrefs.getWidgetScalePercent(context, WidgetKind.STATUS_BAR) / 100f
    val fitScale = minOf(size.width.value / 320f, size.height.value / 52f).coerceIn(0.65f, 1.6f)
    val scale = (fitScale * userScale).coerceIn(0.55f, 1.8f)
    val slots = DevicePrefs.getStatusBarSlots(context)
    val useSignalIcon = DevicePrefs.isWidgetSignalIconEnabled(context, WidgetKind.STATUS_BAR)
    val netText = shortNetworkMode(snapshot.netType)

    WidgetShell(context, corner = 14.dp.scale(scale), kind = WidgetKind.STATUS_BAR) {
        val panel = panelBackground()
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 5.dp.scale(scale), vertical = 5.dp.scale(scale))
                .then(panel)
                .cornerRadius(10.dp.scale(scale))
                .padding(horizontal = 4.dp.scale(scale), vertical = 4.dp.scale(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { index, slotKey ->
                if (slotKey == "signal") {
                    StatusBarSignalMetric(
                        modifier = GlanceModifier.defaultWeight(),
                        context = context,
                        scale = scale,
                        signalBar = snapshot.signalBar,
                        netText = netText,
                        useIcon = useSignalIcon,
                    )
                } else {
                    val metric = resolveStatusSlotMetric(slotKey, snapshot)
                        ?: MetricSpec(R.drawable.ic_widget_network, "--", "--")
                    StatusBarMetric(
                        modifier = GlanceModifier.defaultWeight(),
                        scale = scale,
                        iconRes = metric.iconRes,
                        label = metric.label,
                        value = metric.value,
                    )
                }
                if (index < slots.lastIndex) {
                    WidgetDivider(28.dp.scale(scale))
                }
            }
        }
    }
}

/** 状态条「信号」列：图标格数或 5/5 文字 + 制式 */
@Composable
private fun StatusBarSignalMetric(
    modifier: GlanceModifier,
    context: Context,
    scale: Float,
    signalBar: Int,
    netText: String,
    useIcon: Boolean,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp.scale(scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "信号",
            style = TextStyle(
                color = ColorProvider(WidgetPalette.secondary),
                fontSize = 9.sp.scale(scale),
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp.scale(scale)))
        if (useIcon) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SignalBarView(
                    context = context,
                    signalBar = signalBar,
                    scale = scale * 0.9f,
                    kind = WidgetKind.STATUS_BAR,
                    useIcon = true,
                )
                if (netText != "--") {
                    Spacer(GlanceModifier.width(3.dp.scale(scale)))
                    Text(
                        netText,
                        style = TextStyle(
                            color = ColorProvider(WidgetPalette.primary),
                            fontSize = 10.sp.scale(scale),
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }
        } else {
            val text = buildString {
                append(signalBarText(signalBar))
                if (netText != "--") {
                    append(" ")
                    append(netText)
                }
            }
            Text(
                text,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.primary),
                    fontSize = statusBarValueSize(text, scale),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/** 状态条单列：小图标 + 标签 / 加粗数值（与全量卡指标条一致） */
@Composable
private fun StatusBarMetric(
    modifier: GlanceModifier,
    scale: Float,
    iconRes: Int,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp.scale(scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(iconRes),
                label,
                GlanceModifier.width(11.dp.scale(scale)).height(11.dp.scale(scale)),
            )
            Spacer(GlanceModifier.width(2.dp.scale(scale)))
            Text(
                label,
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.secondary),
                    fontSize = 9.sp.scale(scale),
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp.scale(scale)))
        Text(
            value.ifBlank { "--" },
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(WidgetPalette.primary),
                fontSize = statusBarValueSize(value, scale),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

private fun statusBarValueSize(value: String, scale: Float) = when {
    value.length >= 12 -> 9.sp.scale(scale)
    value.length >= 9 -> 10.sp.scale(scale)
    else -> 11.sp.scale(scale)
}

@Composable
private fun MiniMetric(
    iconRes: Int,
    label: String,
    value: String,
    scale: Float,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = label,
            modifier = GlanceModifier
                .width(16.dp.scale(scale))
                .height(16.dp.scale(scale)),
        )
        Spacer(GlanceModifier.height(2.dp.scale(scale)))
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(WidgetPalette.secondary),
                fontSize = 9.sp.scale(scale),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(1.dp.scale(scale)))
        Text(
            value,
            style = TextStyle(
                color = ColorProvider(WidgetPalette.primary),
                fontSize = 11.sp.scale(scale),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
