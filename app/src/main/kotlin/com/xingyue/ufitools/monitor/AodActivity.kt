package com.xingyue.ufitools.monitor

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.StatusRepository
import com.xingyue.ufitools.monitor.ui.aod.AodContentView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 息屏显示页面：黑底低亮度常亮展示关键指标 */
class AodActivity : ComponentActivity(), SensorEventListener {

    private lateinit var gestureDetector: GestureDetector
    private lateinit var contentView: AodContentView
    private var activeScope: CoroutineScope? = null
    private var proximityJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var batteryReceiverRegistered = false
    private var exitScheduled = false
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let(::applyPowerPolicy)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientationPreference()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(charging = false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val doubleTapExit = DevicePrefs.isAodDoubleTapExit(this)
        contentView = AodContentView(this)
        contentView.updateOrientation(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            contentView.applySafeInsets(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (doubleTapExit) {
                        contentView.showExitHint("双击屏幕退出")
                    } else {
                        finish()
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (doubleTapExit) finish()
                    return true
                }
            },
        )
        contentView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        if (DevicePrefs.isAodShowEntryHint(this)) {
            contentView.showExitHint(
                if (doubleTapExit) "伪息屏：屏幕仍保持点亮 · 双击退出" else "伪息屏：屏幕仍保持点亮 · 点击退出",
            )
        }
        setContentView(contentView)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        contentView.updateOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        ViewCompat.requestApplyInsets(contentView)
    }

    override fun onStart() {
        super.onStart()
        startAodLoops()
        registerBatteryProtection()
        registerPocketProtection()
    }

    override fun onStop() {
        unregisterPocketProtection()
        unregisterBatteryProtection()
        activeScope?.cancel()
        activeScope = null
        super.onStop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = proximitySensor ?: return
        val covered = event.sensor.type == Sensor.TYPE_PROXIMITY &&
            event.values.firstOrNull()?.let { it < sensor.maximumRange } == true
        proximityJob?.cancel()
        proximityJob = if (covered) {
            activeScope?.launch {
                delay(2_000L)
                requestExit("检测到遮挡，正在退出")
            }
        } else {
            null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startAodLoops() {
        activeScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        activeScope = scope

        scope.launch {
            val step = if (DevicePrefs.isAodShowSeconds(this@AodActivity)) 1_000L else 60_000L
            while (isActive) {
                contentView.updateClock()
                delay(step - System.currentTimeMillis() % step)
            }
        }

        scope.launch {
            val baseRefreshSec = DevicePrefs.getAodRefreshSec(this@AodActivity)
            var failureCount = 0
            while (isActive) {
                if (DevicePrefs.isConfigured(this@AodActivity)) {
                    when (
                        val result = StatusRepository.refreshForAod(
                            this@AodActivity,
                            baseRefreshSec * 1_000L,
                        )
                    ) {
                        is DeviceApi.FetchResult.Success -> {
                            failureCount = 0
                            contentView.render(result.status, System.currentTimeMillis())
                            contentView.setConnectionFailure(false)
                        }
                        is DeviceApi.FetchResult.Failure -> {
                            failureCount++
                            contentView.renderCachedSnapshot()
                            contentView.setConnectionFailure(
                                true,
                                if (result.reason == DeviceApi.FetchResult.Reason.PERMISSION) {
                                    "● 缺少「本地网络」权限，正在显示缓存"
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                val multiplier = 1 shl failureCount.coerceAtMost(3)
                delay((baseRefreshSec * multiplier).coerceAtMost(300) * 1_000L)
            }
        }

        if (DevicePrefs.isAodAntiBurnIn(this)) {
            val intervalSec = DevicePrefs.getAodBurnInIntervalSec(this)
            scope.launch {
                while (isActive) {
                    delay(intervalSec * 1_000L)
                    contentView.advanceBurnInPosition()
                }
            }
        }

        val timeoutMin = DevicePrefs.getAodTimeoutMin(this)
        if (timeoutMin > 0) {
            scope.launch {
                delay(timeoutMin * 60_000L)
                requestExit("已达到自动退出时长")
            }
        }
    }

    private fun applyOrientationPreference() {
        requestedOrientation = when (DevicePrefs.getAodOrientation(this)) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    private fun applyBrightness(charging: Boolean) {
        val normal = DevicePrefs.getAodBrightness(this)
        val onBattery = DevicePrefs.getAodBatteryBrightness(this)
        val pct = if (!charging && onBattery > 0) onBattery else normal
        window.attributes = window.attributes.apply {
            screenBrightness = pct.coerceIn(1, 40) / 100f
        }
    }

    private fun registerBatteryProtection() {
        if (batteryReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // RECEIVER_NOT_EXPORTED 是 Android 13（API 33）才有的标记；更低版本直接注册即可，
        // ACTION_BATTERY_CHANGED 属于系统受保护广播，第三方应用无法伪造。返回值是当前电量的 sticky Intent。
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        batteryReceiverRegistered = true
        sticky?.let(::applyPowerPolicy)
    }

    private fun unregisterBatteryProtection() {
        if (!batteryReceiverRegistered) return
        unregisterReceiver(batteryReceiver)
        batteryReceiverRegistered = false
    }

    private fun applyPowerPolicy(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        isCharging = charging
        applyBrightness(charging)

        if (DevicePrefs.isAodChargingOnly(this) && !charging) {
            requestExit("仅充电时可使用息屏显示")
            return
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val threshold = DevicePrefs.getAodLowBatteryThreshold(this)
        if (!charging && threshold > 0 && percent in 0..threshold) {
            requestExit("手机电量较低，正在退出")
        }
    }

    private fun registerPocketProtection() {
        if (!DevicePrefs.isAodPocketProtectionEnabled(this)) return
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        sensorManager = manager
        proximitySensor = sensor
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun unregisterPocketProtection() {
        proximityJob?.cancel()
        proximityJob = null
        sensorManager?.unregisterListener(this)
        sensorManager = null
        proximitySensor = null
    }

    private fun requestExit(message: String) {
        if (exitScheduled || isFinishing) return
        exitScheduled = true
        contentView.showExitHint(message)
        contentView.postDelayed({ if (!isFinishing) finish() }, 1_200L)
    }
}
