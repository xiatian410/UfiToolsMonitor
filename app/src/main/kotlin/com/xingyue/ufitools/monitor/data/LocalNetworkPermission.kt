package com.xingyue.ufitools.monitor.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android 17（API 37）的「本地网络访问」运行时权限。
 *
 * targetSdk ≥ 37 的应用默认被禁止收发局域网流量（10/8、172.16/12、192.168/16、169.254/16、
 * 100.64/10 及组播/广播地址），未授权时 TCP 连接不会立刻报错、而是一直等到超时，
 * 表现就是「设备无响应，请检查地址与网络」。
 *
 * Android 16 及更低版本没有该权限（有 INTERNET 即隐式放行），因此低版本上不检查、不申请。
 */
object LocalNetworkPermission {

    /**
     * 用字面量而不是 `Manifest.permission.ACCESS_LOCAL_NETWORK`，
     * 避免低版本系统上解析不到该常量字段。
     */
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    /** 各处共用的缺权限提示文案 */
    const val HINT = "缺少「本地网络」权限：Android 17 起访问局域网设备需先授权"

    /** Android 17 = API 37（Build.VERSION_CODES.CINNAMON_BUN） */
    private const val API_ANDROID_17 = 37

    /** 当前系统是否强制该权限 */
    val isEnforced: Boolean
        get() = Build.VERSION.SDK_INT >= API_ANDROID_17

    fun isGranted(context: Context): Boolean = !isEnforced ||
        context.checkSelfPermission(NAME) == PackageManager.PERMISSION_GRANTED

    fun isMissing(context: Context): Boolean = !isGranted(context)

    /**
     * 权限缺失且当前配置的设备地址确实落在受限网段——只有这种情况才需要提示、申请，
     * 走公网地址的连接不受此限制。
     */
    fun blocksCurrentDevice(context: Context): Boolean =
        isMissing(context) && isRestrictedHost(currentHost(context))

    fun currentHost(context: Context): String =
        DevicePrefs.parseAddress(DevicePrefs.getDeviceAddress(context)).first

    /**
     * 是否为受限的局域网地址。
     * 主机名在此无法判断归属（随身 WiFi 多为局域网名），按受限处理，宁可多提示一次。
     */
    fun isRestrictedHost(host: String): Boolean {
        val h = host.trim().removeSurrounding("[", "]")
        if (h.isEmpty()) return true
        if (h.equals("localhost", ignoreCase = true)) return false
        // IPv6 字面量：链路本地/直连路由等无法逐一判断，除回环外一律按局域网处理
        if (h.contains(':')) return h != "::1"
        val octets = h.split('.')
            .takeIf { it.size == 4 }
            ?.map { part -> part.toIntOrNull() ?: return true }
            ?: return true
        if (octets.any { it !in 0..255 }) return true
        val (a, b) = octets
        return when {
            a == 127 -> false // 回环不属于局域网
            a == 10 -> true // RFC1918
            a == 172 && b in 16..31 -> true // RFC1918
            a == 192 && b == 168 -> true // RFC1918
            a == 169 && b == 254 -> true // Link Local
            a == 100 && b in 64..127 -> true // CGNAT
            a in 224..239 -> true // 组播
            octets.all { it == 255 } -> true // 广播
            else -> false // 公网地址
        }
    }

    /** 系统是否还会弹授权框；已被永久拒绝时返回 false，只能引导到系统设置 */
    fun canShowRationale(context: Context): Boolean {
        val activity = findActivity(context) ?: return true
        return activity.shouldShowRequestPermissionRationale(NAME)
    }

    /** 跳转系统「应用信息」页，供已永久拒绝时手动授予 */
    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
