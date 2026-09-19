package com.xingyue.ufitools.monitor.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.xingyue.ufitools.monitor.data.DevicePrefs
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val MonetKeyColors: List<Pair<String, Color>> = listOf(
    "蓝色" to Color(0xFF3482FF),
    "绿色" to Color(0xFF36D167),
    "紫色" to Color(0xFF7C4DFF),
    "黄色" to Color(0xFFFFB21D),
    "橙色" to Color(0xFFFF5722),
    "粉色" to Color(0xFFE91E63),
    "青色" to Color(0xFF00BCD4),
)

val MonetKeyColorOptions: List<String> = listOf("系统动态取色") + MonetKeyColors.map { it.first }

val PaletteStyleOptions: List<String> = ThemePaletteStyle.entries.map { it.name }

data class ThemeSettings(
    val mode: Int = 0,
    val monet: Boolean = false,
    val seedIndex: Int = 0,
    val paletteIndex: Int = ThemePaletteStyle.TonalSpot.ordinal,
) {
    fun persist(ctx: Context) {
        DevicePrefs.setThemeMode(ctx, mode)
        DevicePrefs.setMonetEnabled(ctx, monet)
        DevicePrefs.setMonetSeedIndex(ctx, seedIndex)
        DevicePrefs.setMonetPaletteIndex(ctx, paletteIndex)
    }

    companion object {
        fun load(ctx: Context) = ThemeSettings(
            mode = DevicePrefs.getThemeMode(ctx),
            monet = DevicePrefs.isMonetEnabled(ctx),
            seedIndex = DevicePrefs.getMonetSeedIndex(ctx),
            paletteIndex = DevicePrefs.getMonetPaletteIndex(ctx, ThemePaletteStyle.TonalSpot.ordinal),
        )
    }
}

fun ThemeSettings.createThemeController(): ThemeController {
    val colorSchemeMode = when (mode.coerceIn(0, 2)) {
        1 -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        2 -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    if (!monet) return ThemeController(colorSchemeMode = colorSchemeMode)

    val keyColor = if (seedIndex <= 0) null else MonetKeyColors.getOrNull(seedIndex - 1)?.second
    val paletteStyle = ThemePaletteStyle.entries.getOrNull(paletteIndex) ?: ThemePaletteStyle.TonalSpot

    return ThemeController(
        colorSchemeMode = colorSchemeMode,
        keyColor = keyColor,
        paletteStyle = paletteStyle,
    )
}
