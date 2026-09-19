package com.xingyue.ufitools.monitor

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.ui.App
import com.xingyue.ufitools.monitor.ui.theme.ThemeSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()

        setContent {
            var themeSettings by remember {
                mutableStateOf(ThemeSettings.load(this))
            }
            val darkMode = when (themeSettings.mode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            App(
                themeSettings = themeSettings,
                onThemeSettingsChange = {
                    themeSettings = it
                    it.persist(this)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyLockScreenFlags()
    }

    /** 根据设置决定是否允许在锁屏之上显示界面 */
    private fun applyLockScreenFlags() {
        val show = DevicePrefs.isShowOnLockScreen(this)
        setShowWhenLocked(show)
        setTurnScreenOn(show)
    }
}
