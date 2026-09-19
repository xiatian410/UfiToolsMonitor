package com.xingyue.ufitools.monitor.appwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus

/** 全量状态卡：默认 4×2，可限幅缩放；内容随格子自适应（无 2×2 小布局） */
class UfiWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            val appearance = readWidgetAppearance(state, context, WidgetKind.MAIN)
            val snapshot = readWidgetSnapshot(state, context)
            ProvideWidgetTheme(appearance) {
                GlanceTheme { MainWidgetContent(context, snapshot) }
            }
        }
    }
}

@Composable
private fun MainWidgetContent(context: Context, snapshot: DeviceStatus) {
    val size = LocalSize.current
    val userScale = DevicePrefs.getWidgetScalePercent(context, WidgetKind.MAIN) / 100f
    // 随实际格子缩放：大桌面拉大时内容也放大，过小格子压缩避免裁切
    val fitScale = minOf(size.width.value / 320f, size.height.value / 176f).coerceIn(0.55f, 1.75f)
    val scale = (fitScale * userScale).coerceIn(0.5f, 2f)
    val rootBackground = if (Palette.isGlass) {
        GlanceModifier.background(ImageProvider(R.drawable.bg_widget_glass))
    } else {
        GlanceModifier.background(Palette.background)
    }
    val showHeader = DevicePrefs.isWidgetHeaderEnabled(context)
    val showFlowCards = DevicePrefs.isWidgetFlowCardsEnabled(context)
    val metrics = DevicePrefs.getEnabledWidgetMetrics(context, WidgetKind.MAIN)
        .mapNotNull { resolveMetric(it, snapshot)?.let { m -> MetricItem(m.iconRes, m.label, m.value) } }
    Box(
        modifier = GlanceModifier.fillMaxSize().then(rootBackground)
            .clickable(widgetTapAction(context, WidgetKind.MAIN))
            .cornerRadius(18.dp.scale(scale)),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(
                horizontal = 10.dp.scale(scale),
                vertical = 8.dp.scale(scale),
            ),
        ) {
            if (showHeader) {
                WideHeader(context, snapshot, compact = scale < 0.85f, scale = scale)
                Spacer(GlanceModifier.height(4.dp.scale(scale)))
            }
            if (showFlowCards) {
                val leftCard = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.MAIN, 0), snapshot)
                val rightCard = resolveFlowCard(DevicePrefs.getWidgetCardSlot(context, WidgetKind.MAIN, 1), snapshot)
                Row(modifier = GlanceModifier.fillMaxWidth().height(60.dp.scale(scale))) {
                    FlowCard(
                        modifier = GlanceModifier.defaultWeight().height(60.dp.scale(scale)),
                        iconRes = leftCard.iconRes,
                        title = leftCard.title,
                        value = leftCard.value,
                        background = Palette.todayCard,
                        monthStyle = false,
                        valueSize = flowValueSize(leftCard.value, scale),
                    )
                    Spacer(GlanceModifier.width(8.dp.scale(scale)))
                    FlowCard(
                        modifier = GlanceModifier.defaultWeight().height(60.dp.scale(scale)),
                        iconRes = rightCard.iconRes,
                        title = rightCard.title,
                        value = rightCard.value,
                        background = Palette.monthCard,
                        monthStyle = true,
                        valueSize = flowValueSize(rightCard.value, scale),
                    )
                }
                Spacer(GlanceModifier.height(6.dp.scale(scale)))
            }
            if (metrics.isNotEmpty()) {
                MetricsStrip(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    compact = scale < 0.85f,
                    height = 50.dp.scale(scale),
                    items = metrics,
                    fontScale = scale.coerceAtLeast(0.7f),
                )
            }
        }
    }
}

@Composable
private fun WideHeader(context: Context, snapshot: DeviceStatus, compact: Boolean, scale: Float) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(32.dp.scale(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBarView(
                context = context,
                signalBar = snapshot.signalBar,
                scale = scale.coerceAtLeast(0.7f),
                kind = WidgetKind.MAIN,
            )
            if (!compact) {
                Spacer(GlanceModifier.width(5.dp.scale(scale)))
                Divider(18.dp.scale(scale))
                Spacer(GlanceModifier.width(5.dp.scale(scale)))
                HeaderText(shortNetworkMode(snapshot.netType), scale.coerceAtLeast(0.7f))
            }
        }
        Text(
            snapshot.deviceModel.ifBlank { "--" },
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(Palette.primary),
                fontSize = deviceNameSize(snapshot.deviceModel, scale),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.End,
        ) {
            Image(
                ImageProvider(R.drawable.ic_widget_battery),
                if (snapshot.hasBattery) "电池" else "外接供电",
                GlanceModifier.width(21.dp.scale(scale)).height(21.dp.scale(scale)),
            )
            Spacer(GlanceModifier.width(4.dp.scale(scale)))
            HeaderText(
                if (snapshot.hasBattery) {
                    buildBatteryText(snapshot.batteryPercent, snapshot.charging)
                } else {
                    "外接供电"
                },
                scale.coerceAtLeast(0.7f),
            )
        }
    }
}

@Composable
private fun HeaderText(value: String, scale: Float = 1f) {
    Text(
        text = value,
        style = TextStyle(color = ColorProvider(Palette.secondary), fontSize = 10.sp.scale(scale)),
        maxLines = 1,
    )
}

@Composable
private fun FlowCard(
    modifier: GlanceModifier,
    iconRes: Int,
    title: String,
    value: String,
    background: Color,
    monthStyle: Boolean,
    valueSize: TextUnit,
) {
    val cardBackground = if (Palette.isGlass) {
        GlanceModifier.background(
            ImageProvider(
                if (monthStyle) R.drawable.bg_widget_glass_month
                else R.drawable.bg_widget_glass_today,
            ),
        )
    } else {
        GlanceModifier.background(background)
    }
    Box(
        modifier = modifier.then(cardBackground).cornerRadius(9.dp).padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(ImageProvider(iconRes), title, GlanceModifier.width(15.dp).height(15.dp))
                Spacer(GlanceModifier.width(7.dp))
                Text(title, style = TextStyle(color = ColorProvider(Palette.primary), fontSize = 10.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(1.dp))
            Text(
                value.ifBlank { "--" },
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(Palette.primary), fontSize = valueSize,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
        }
    }
}

private data class MetricItem(val iconRes: Int, val label: String, val value: String)

@Composable
private fun MetricsStrip(modifier: GlanceModifier, compact: Boolean, height: Dp, items: List<MetricItem>, fontScale: Float = 1f) {
    val panelBackground = if (Palette.isGlass) {
        GlanceModifier.background(ImageProvider(R.drawable.bg_widget_glass_panel))
    } else {
        GlanceModifier.background(Palette.panel)
    }
    Row(
        modifier = modifier.then(panelBackground).cornerRadius(9.dp).padding(horizontal = 5.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            MetricColumn(
                item,
                GlanceModifier.defaultWeight(),
                compact,
                fontScale,
            )
            if (index < items.lastIndex) Divider(if (compact) 38.dp else 44.dp)
        }
    }
}

@Composable
private fun MetricColumn(item: MetricItem, modifier: GlanceModifier, compact: Boolean = false, fontScale: Float = 1f) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val iconSize = (if (compact) 18.dp else 22.dp) * fontScale
            Image(ImageProvider(item.iconRes), item.label, GlanceModifier.width(iconSize).height(iconSize))
            Spacer(GlanceModifier.height(1.dp))
            Text(
                if (compact) compactLabel(item.label) else item.label,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = ColorProvider(Palette.secondary), fontSize = (if (compact) 7.sp else 9.sp).scale(fontScale), textAlign = TextAlign.Center),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(1.dp))
            Text(
                item.value.ifBlank { "--" },
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(Palette.primary),
                    fontSize = metricValueSize(item.value, compact).scale(fontScale),
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Divider(height: Dp) {
    Box(GlanceModifier.width(1.dp).height(height).background(Palette.divider)) {}
}

/** 卡片大字号：值越长字号越小（如上下行拼接值） */
private fun flowValueSize(value: String, scale: Float): TextUnit {
    val base = when {
        value.length >= 12 -> if (scale < 0.85f) 12 else 15
        value.length >= 9 -> if (scale < 0.85f) 14 else 18
        else -> if (scale < 0.85f) 18 else 24
    }
    return base.sp.scale(scale.coerceAtLeast(0.7f))
}

/** 指标值字号：值越长字号越小，避免在低分辨率/窄格设备上被截断 */
private fun metricValueSize(value: String, compact: Boolean): TextUnit {
    val base = if (compact) 9 else 10
    val size = when {
        value.length >= 9 -> base - 2
        value.length >= 7 -> base - 1
        else -> base
    }
    return size.sp
}

private fun deviceNameSize(value: String, scale: Float): TextUnit {
    val base = when {
        value.length >= 28 -> 9
        value.length >= 20 -> 10
        value.length >= 14 -> 11
        else -> 13
    }
    return base.sp.scale(scale.coerceAtLeast(0.7f))
}

private fun compactLabel(label: String): String = when (label) {
    "当前频段" -> "频段"
    "CPU温度" -> "温度"
    "电池温度" -> "电温"
    else -> label
}

/** 兼容旧引用：外观已迁至 WidgetStyle */
private object Palette {
    val background: Color @Composable get() = WidgetPalette.background
    val panel: Color @Composable get() = WidgetPalette.panel
    val todayCard: Color @Composable get() = WidgetPalette.todayCard
    val monthCard: Color @Composable get() = WidgetPalette.monthCard
    val primary: Color @Composable get() = WidgetPalette.primary
    val secondary: Color @Composable get() = WidgetPalette.secondary
    val divider: Color @Composable get() = WidgetPalette.divider
    val isGlass: Boolean @Composable get() = WidgetPalette.isGlass
}
