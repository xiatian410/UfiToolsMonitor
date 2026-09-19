package com.xingyue.ufitools.monitor.appwidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import com.xingyue.ufitools.monitor.MainActivity
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus

/** 各 Glance 小组件共用的状态 key、配色与基础零件 */
object WidgetKeys {
    val SNAPSHOT = stringPreferencesKey("snapshot_json")
    val APPEARANCE = intPreferencesKey("appearance")
}

const val APPEARANCE_LIGHT = 0
const val APPEARANCE_DARK = 1
const val APPEARANCE_GLASS = 2

internal data class WidgetColors(
    val background: Color,
    val panel: Color,
    val todayCard: Color,
    val monthCard: Color,
    val primary: Color,
    val secondary: Color,
    val divider: Color,
    val isGlass: Boolean = false,
)

internal val LocalWidgetColors = staticCompositionLocalOf { widgetColors(APPEARANCE_LIGHT) }

internal fun widgetColors(appearance: Int): WidgetColors = when (appearance) {
    APPEARANCE_LIGHT -> WidgetColors(
        background = Color(0xFFF9FAFC),
        panel = Color(0xFFFFFFFF),
        todayCard = Color(0xFFF0F5FD),
        monthCard = Color(0xFFF1F7F3),
        primary = Color(0xFF111318),
        secondary = Color(0xFF475569),
        divider = Color(0xFFE2E8F0),
    )
    APPEARANCE_DARK -> WidgetColors(
        background = Color(0xFF111827),
        panel = Color(0xFF1F2937),
        todayCard = Color(0xFF18263E),
        monthCard = Color(0xFF173126),
        primary = Color(0xFFF8FAFC),
        secondary = Color(0xFFCBD5E1),
        divider = Color(0xFF475569),
    )
    else -> WidgetColors(
        background = Color(0xB8F8FAFC),
        panel = Color(0x8CFFFFFF),
        todayCard = Color(0x783B82F6),
        monthCard = Color(0x7022C55E),
        primary = Color(0xFF0F172A),
        secondary = Color(0xFF334155),
        divider = Color(0x66CBD5E1),
        isGlass = true,
    )
}

internal object WidgetPalette {
    val background: Color @Composable get() = LocalWidgetColors.current.background
    val panel: Color @Composable get() = LocalWidgetColors.current.panel
    val todayCard: Color @Composable get() = LocalWidgetColors.current.todayCard
    val monthCard: Color @Composable get() = LocalWidgetColors.current.monthCard
    val primary: Color @Composable get() = LocalWidgetColors.current.primary
    val secondary: Color @Composable get() = LocalWidgetColors.current.secondary
    val divider: Color @Composable get() = LocalWidgetColors.current.divider
    val isGlass: Boolean @Composable get() = LocalWidgetColors.current.isGlass
}

@Composable
internal fun ProvideWidgetTheme(appearance: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWidgetColors provides widgetColors(appearance)) {
        content()
    }
}

internal fun readWidgetSnapshot(state: Preferences, context: Context): DeviceStatus =
    state[WidgetKeys.SNAPSHOT]?.let { DeviceStatus.fromWidgetJson(it) }
        ?: WidgetCommon.loadSnapshot(context)
        ?: DeviceStatus()

internal fun readWidgetAppearance(
    state: Preferences,
    context: Context,
    kind: WidgetKind = WidgetKind.MAIN,
): Int = state[WidgetKeys.APPEARANCE] ?: DevicePrefs.getWidgetAppearance(context, kind)

@Composable
internal fun rootBackground(): GlanceModifier =
    if (WidgetPalette.isGlass) {
        GlanceModifier.background(ImageProvider(R.drawable.bg_widget_glass))
    } else {
        GlanceModifier.background(WidgetPalette.background)
    }

@Composable
internal fun panelBackground(): GlanceModifier =
    if (WidgetPalette.isGlass) {
        GlanceModifier.background(ImageProvider(R.drawable.bg_widget_glass_panel))
    } else {
        GlanceModifier.background(WidgetPalette.panel)
    }

internal fun widgetTapAction(context: Context, kind: WidgetKind = WidgetKind.MAIN) =
    if (DevicePrefs.getWidgetTapAction(context, kind) == 1) {
        actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } else {
        actionSendBroadcast(
            Intent(context, WidgetRefreshReceiver::class.java).apply {
                action = WidgetRefreshReceiver.ACTION_REFRESH_WIDGET
            },
        )
    }

internal fun Dp.scale(value: Float): Dp = this * value
internal fun TextUnit.scale(value: Float): TextUnit = (this.value * value).sp

internal fun buildBatteryText(batteryPercent: Int, charging: Boolean): String {
    val value = if (batteryPercent in 0..100) "$batteryPercent%" else "--"
    return if (charging) "$value 充电中" else value
}

internal fun shortNetworkMode(mode: String): String {
    val normalized = mode.trim().replace("NR5G", "5G", ignoreCase = true)
    return normalized.ifBlank { "--" }
}

/** 仅数字（指标条等窄位） */
internal fun rsrpText(rsrp: Int?): String = rsrp?.toString() ?: "--"

/** 统一 RSRP 展示：-86dBm；与 signal 字段等价，UI 只应保留一处 */
internal fun formatRsrpDbm(rsrp: Int?): String = rsrp?.let { "${it}dBm" } ?: "--"

internal fun wifiText(count: Int): String = if (count >= 0) "$count 台" else "--"

internal fun shortBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes == 0L) return "0"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 100 -> String.format(java.util.Locale.getDefault(), "%.0fG", gb)
        gb >= 1 -> String.format(java.util.Locale.getDefault(), "%.1fG", gb)
        mb >= 1 -> String.format(java.util.Locale.getDefault(), "%.0fM", mb)
        else -> "<1M"
    }
}

internal fun formatLiveSpeed(bytesPerSec: Long): String =
    if (bytesPerSec < 0) "--" else DeviceApi.formatSpeed(bytesPerSec)

/** 信号格 0~5 对应 drawable；非法值用 0 格 */
internal fun signalBarIconRes(level: Int): Int = when (level.coerceIn(0, 5)) {
    1 -> R.drawable.ic_signal_bar_1
    2 -> R.drawable.ic_signal_bar_2
    3 -> R.drawable.ic_signal_bar_3
    4 -> R.drawable.ic_signal_bar_4
    5 -> R.drawable.ic_signal_bar_5
    else -> R.drawable.ic_signal_bar_0
}

internal fun signalBarText(level: Int): String =
    if (level in 0..5) "$level/5" else "--"

/**
 * 信号格显示：图标或文字。
 * @param useIcon 为 null 时读 [kind] 对应设置
 * @param large 信号专项卡等大图标场景
 */
@Composable
internal fun SignalBarView(
    context: Context,
    signalBar: Int,
    scale: Float,
    large: Boolean = false,
    kind: WidgetKind = WidgetKind.MAIN,
    useIcon: Boolean? = null,
) {
    val showIcon = useIcon ?: DevicePrefs.isWidgetSignalIconEnabled(context, kind)
    val size = (if (large) 36.dp else 18.dp).scale(scale)
    if (showIcon) {
        val level = if (signalBar in 0..5) signalBar else 0
        Image(
            provider = ImageProvider(signalBarIconRes(level)),
            contentDescription = signalBarText(signalBar),
            modifier = GlanceModifier.width(size).height(size),
        )
    } else {
        Text(
            text = signalBarText(signalBar),
            modifier = if (large) GlanceModifier.fillMaxWidth() else GlanceModifier,
            style = TextStyle(
                color = ColorProvider(WidgetPalette.primary),
                fontSize = (if (large) 28.sp else 10.sp).scale(scale),
                fontWeight = if (large) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (large) TextAlign.Center else TextAlign.Start,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun WidgetHeaderText(value: String, scale: Float = 1f) {
    Text(
        text = value,
        style = TextStyle(color = ColorProvider(WidgetPalette.secondary), fontSize = 10.sp.scale(scale)),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetDivider(height: Dp) {
    Box(GlanceModifier.width(1.dp).height(height).background(WidgetPalette.divider)) {}
}

@Composable
internal fun WidgetFlowCard(
    modifier: GlanceModifier,
    iconRes: Int,
    title: String,
    value: String,
    background: Color,
    monthStyle: Boolean,
    valueSize: TextUnit,
    scale: Float = 1f,
    valueMaxLines: Int = 2,
) {
    val cardBackground = if (WidgetPalette.isGlass) {
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
        modifier = modifier.then(cardBackground)
            .cornerRadius(9.dp.scale(scale))
            .padding(horizontal = 8.dp.scale(scale), vertical = 5.dp.scale(scale)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    ImageProvider(iconRes),
                    title,
                    GlanceModifier.width(15.dp.scale(scale)).height(15.dp.scale(scale)),
                )
                Spacer(GlanceModifier.width(7.dp.scale(scale)))
                Text(
                    title,
                    style = TextStyle(
                        color = ColorProvider(WidgetPalette.primary),
                        fontSize = 10.sp.scale(scale),
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
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = valueMaxLines.coerceIn(1, 3),
            )
        }
    }
}

@Composable
internal fun WidgetShell(
    context: Context,
    corner: Dp,
    kind: WidgetKind = WidgetKind.MAIN,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .then(rootBackground())
            .clickable(widgetTapAction(context, kind))
            .cornerRadius(corner),
    ) {
        content()
    }
}
