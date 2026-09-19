package com.xingyue.ufitools.monitor.data

import android.content.Context
import android.content.SharedPreferences
import com.xingyue.ufitools.monitor.appwidget.WidgetKind

object DevicePrefs {

    const val DEFAULT_DEVICE_ADDRESS = "192.168.0.1:2333"
    const val DEFAULT_DEVICE_INFO_PATH = "/api/baseDeviceInfo"
    const val DEFAULT_GOFORM_COMMAND_PATH = "/api/goform/goform_get_cmd_process"
    const val DEFAULT_NEED_TOKEN_PATH = "/api/need_token"
    const val DEFAULT_VERSION_INFO_PATH = "/api/version_info"
    const val DEFAULT_SECRET_KEY = "minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ufi_tools_prefs", Context.MODE_PRIVATE)

    // ── 连接配置 ──
    fun getDeviceAddress(ctx: Context): String =
        sp(ctx).getString("device_address", "")?.ifBlank { DEFAULT_DEVICE_ADDRESS } ?: DEFAULT_DEVICE_ADDRESS

    fun setDeviceAddress(ctx: Context, v: String) =
        sp(ctx).edit().putString("device_address", v.trim()).apply()

    fun isConfigured(ctx: Context): Boolean = sp(ctx).getBoolean("configured", false)

    fun setConfigured(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("configured", v).apply()

    /** 最近一次成功获取状态的设备型号（如 "F50"、"U60Pro"），用于区分机型 API 差异 */
    fun getCachedModel(ctx: Context): String = sp(ctx).getString("cached_model", "") ?: ""

    fun setCachedModel(ctx: Context, v: String) {
        val model = v.trim()
        if (model.isEmpty()) return
        val prefs = sp(ctx)
        val old = prefs.getString("cached_model", "") ?: ""
        val editor = prefs.edit().putString("cached_model", model)
        // 切换到另一机型时，清空上一台设备的接口探测缓存，
        // 两台设备常用同一地址，残留 ufi_api/goform 模式会导致短信删除按钮或接口路径错误
        if (old.isNotEmpty() && !old.equals(model, ignoreCase = true)) {
            removeProbeKeys(prefs, editor)
            editor.remove("active_sim_slot")
        }
        editor.apply()
        // 同步到当前连接档案，便于切换时显示机型
        ConnectionProfiles.updateActiveModel(ctx, model)
    }

    fun getAuthToken(ctx: Context): String = sp(ctx).getString("auth_token", "") ?: ""

    fun setAuthToken(ctx: Context, v: String) = sp(ctx).edit().putString("auth_token", v).apply()

    fun getSecretKey(ctx: Context): String =
        sp(ctx).getString("secret_key", "")?.ifBlank { DEFAULT_SECRET_KEY } ?: DEFAULT_SECRET_KEY

    fun setSecretKey(ctx: Context, v: String) = sp(ctx).edit().putString("secret_key", v.trim()).apply()

    fun getDeviceToken(ctx: Context): String = sp(ctx).getString("device_token", "") ?: ""

    fun setDeviceToken(ctx: Context, v: String) = sp(ctx).edit().putString("device_token", v).apply()

    /**
     * 通用版双卡：最近一次探测到的有效 SIM 槽位（"0"/"1"）。
     * 仅插一张卡时由 currentCellInfo 打分选定；发短信等会复用。
     */
    fun getActiveSimSlot(ctx: Context): String =
        sp(ctx).getString("active_sim_slot", "0")?.ifBlank { "0" } ?: "0"

    fun setActiveSimSlot(ctx: Context, slot: String) {
        val n = slot.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 3) ?: 0
        sp(ctx).edit().putString("active_sim_slot", n.toString()).apply()
    }

    fun getGoformCompatMode(ctx: Context, baseUrl: String, scope: String = "default"): String {
        val prefs = sp(ctx)
        val suffix = scope.filter { it.isLetterOrDigit() || it == '_' }
        if (prefs.getString("goform_compat_base_url_$suffix", "") != baseUrl) return "auto"
        return prefs.getString("goform_compat_mode_$suffix", "auto") ?: "auto"
    }

    fun setGoformCompatMode(ctx: Context, baseUrl: String, v: String, scope: String = "default") {
        val suffix = scope.filter { it.isLetterOrDigit() || it == '_' }
        sp(ctx).edit()
            .putString("goform_compat_base_url_$suffix", baseUrl)
            .putString("goform_compat_mode_$suffix", v)
            .apply()
    }

    fun getGoformCookie(ctx: Context, baseUrl: String): String {
        val prefs = sp(ctx)
        if (prefs.getString("goform_cookie_base_url", "") != baseUrl) return ""
        return prefs.getString("goform_cookie", "") ?: ""
    }

    fun setGoformCookie(ctx: Context, baseUrl: String, v: String) =
        sp(ctx).edit()
            .putString("goform_cookie_base_url", baseUrl)
            .putString("goform_cookie", v)
            .apply()

    /**
     * 清洗用户输入的地址：去空白/零宽字符、全角冒号→半角、去协议与尾斜杠。
     * 返回仅 host 或 host:port（半角冒号）。
     */
    fun sanitizeHostPort(raw: String): String {
        var s = raw.trim()
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace('：', ':') // 中文输入法全角冒号
            .replace('．', '.') // 全角句点
            .trim()
        // 去掉首尾空白后，再 strip 协议（大小写不敏感）
        val lower = s.lowercase()
        s = when {
            lower.startsWith("https://") -> s.substring(8)
            lower.startsWith("http://") -> s.substring(7)
            else -> s
        }
        s = s.trim().trimEnd('/')
        // 冒号两侧多余空格：192.168.0.1 : 2333 → 192.168.0.1:2333
        val colon = s.lastIndexOf(':')
        if (colon > 0) {
            val host = s.substring(0, colon).trim()
            val portPart = s.substring(colon + 1).trim()
            if (portPart.toIntOrNull() != null) {
                return if (host.isEmpty()) portPart else "$host:$portPart"
            }
        }
        return s.trim()
    }

    /**
     * 解析协议：支持地址前缀 `http://` / `https://`，默认 http。
     */
    fun parseScheme(address: String): String {
        val t = address.trim().lowercase()
            .replace('：', ':')
        return if (t.startsWith("https://")) "https" else "http"
    }

    /**
     * 解析地址为 host / port。
     * 未写端口时 port=null；**请求、保存、预览均不补任何默认端口**。
     * 无端口时由协议自身决定（http→80、https→443）。
     */
    fun parseAddress(address: String): Pair<String, Int?> {
        val raw = sanitizeHostPort(address)
        val idx = raw.lastIndexOf(':')
        return if (idx > 0 && raw.substring(idx + 1).toIntOrNull() != null) {
            raw.substring(0, idx) to raw.substring(idx + 1).toInt()
        } else {
            raw to null
        }
    }

    /**
     * 显示/编辑用 host 或 host:port。
     * 用户没写端口则只返回 host，绝不补端口。
     */
    fun hostPortOf(address: String): String {
        val (host, port) = parseAddress(address)
        return if (port != null) "$host:$port" else host
    }

    /**
     * 是否为可保存的设备地址。
     * 端口完全可选：可只写 `192.168.0.1`；若写了端口则须在 1–65535。
     */
    fun isValidHostPort(address: String): Boolean {
        val s = sanitizeHostPort(address)
        if (s.isBlank()) return false
        val (host, port) = parseAddress(s)
        if (host.isBlank() || host.contains("://")) return false
        if (port != null && port !in 1..65535) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        return host.any { it == '.' } || host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
    }

    /**
     * 组装端点。仅用户写了端口才带 `:port`，绝不自动补 2333。
     * 例：`http://192.168.0.1`、`http://192.168.0.1:8080`
     */
    fun composeEndpoint(scheme: String, hostPort: String): String {
        val s = if (scheme.equals("https", ignoreCase = true)) "https" else "http"
        val (host, port) = parseAddress(hostPort)
        return if (port != null) "$s://$host:$port" else "$s://$host"
    }

    /** 实际请求 baseUrl：与保存内容一致，无端口则 URL 中不含端口。 */
    fun buildBaseUrl(ctx: Context): String {
        val address = getDeviceAddress(ctx)
        val scheme = parseScheme(address)
        val (host, port) = parseAddress(address)
        return if (port != null) "$scheme://$host:$port" else "$scheme://$host"
    }

    // ── 刷新设置 ──
    fun getRefreshIntervalSec(ctx: Context): Int = sp(ctx).getInt("refresh_interval_sec", 10)

    fun setRefreshIntervalSec(ctx: Context, v: Int) = sp(ctx).edit().putInt("refresh_interval_sec", v).apply()

    // ── 缓存 ──
    fun getCachedMonthlyData(ctx: Context): Long = sp(ctx).getLong("cached_monthly_data", 0L)

    fun setCachedMonthlyData(ctx: Context, v: Long) = sp(ctx).edit().putLong("cached_monthly_data", v).apply()

    /** 小组件快照（最近一次成功采集的数据，JSON 序列化） */
    fun getWidgetSnapshot(ctx: Context): String = sp(ctx).getString("widget_snapshot", "") ?: ""

    fun setWidgetSnapshot(ctx: Context, json: String) = sp(ctx).edit().putString("widget_snapshot", json).apply()

    fun getWidgetSnapshotTime(ctx: Context): Long = sp(ctx).getLong("widget_snapshot_time", 0L)

    fun setWidgetSnapshotTime(ctx: Context, v: Long) = sp(ctx).edit().putLong("widget_snapshot_time", v).apply()

    // ── 通知警报 ──
    fun isAlertEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("alert_enabled", false)

    fun setAlertEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("alert_enabled", v).apply()

    fun getTempThreshold(ctx: Context): Int = sp(ctx).getInt("alert_temp_threshold", 70)

    fun setTempThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_temp_threshold", v).apply()

    fun getBatteryThreshold(ctx: Context): Int = sp(ctx).getInt("alert_battery_threshold", 20)

    fun setBatteryThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_battery_threshold", v).apply()

    fun getCpuThreshold(ctx: Context): Int = sp(ctx).getInt("alert_cpu_threshold", 80)

    fun setCpuThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_cpu_threshold", v).apply()

    fun getMemThreshold(ctx: Context): Int = sp(ctx).getInt("alert_mem_threshold", 90)

    fun setMemThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_mem_threshold", v).apply()

    /** 每日流量警报阈值（GB，0 = 关闭） */
    fun getDailyFlowThresholdGb(ctx: Context): Int = sp(ctx).getInt("alert_daily_flow_gb", 0)

    fun setDailyFlowThresholdGb(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_daily_flow_gb", v).apply()

    /** 每月流量警报阈值（GB，0 = 关闭） */
    fun getMonthlyFlowThresholdGb(ctx: Context): Int = sp(ctx).getInt("alert_monthly_flow_gb", 0)

    fun setMonthlyFlowThresholdGb(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_monthly_flow_gb", v).apply()

    /** 短信通知开关 */
    fun isSmsNotifyEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("sms_notify_enabled", false)

    fun setSmsNotifyEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("sms_notify_enabled", v).apply()

    /** 同类警报最短间隔（分钟） */
    fun getAlertDebounceMin(ctx: Context): Int = sp(ctx).getInt("alert_debounce_min", 30)

    fun setAlertDebounceMin(ctx: Context, v: Int) = sp(ctx).edit().putInt("alert_debounce_min", v).apply()

    fun getLastAlertTime(ctx: Context, type: String): Long = sp(ctx).getLong("alert_last_$type", 0L)

    fun setLastAlertTime(ctx: Context, type: String, v: Long) = sp(ctx).edit().putLong("alert_last_$type", v).apply()

    // ── 前台保活服务 ──
    fun isKeepAliveEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("keepalive_enabled", false)

    fun setKeepAliveEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("keepalive_enabled", v).apply()

    /** 保活服务后台监控间隔（秒） */
    fun getMonitorIntervalSec(ctx: Context): Int = sp(ctx).getInt("monitor_interval_sec", 60)

    fun setMonitorIntervalSec(ctx: Context, v: Int) = sp(ctx).edit().putInt("monitor_interval_sec", v).apply()

    // ── 流量历史 ──
    fun isTrafficRecordEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("traffic_record_enabled", true)

    fun setTrafficRecordEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("traffic_record_enabled", v).apply()

    /** 流量历史保留天数 */
    fun getTrafficRetentionDays(ctx: Context): Int = sp(ctx).getInt("traffic_retention_days", 60)

    fun setTrafficRetentionDays(ctx: Context, v: Int) = sp(ctx).edit().putInt("traffic_retention_days", v).apply()

    /** 流量历史 JSON（{"yyyy-MM-dd": bytes, ...}） */
    fun getTrafficHistoryJson(ctx: Context): String = sp(ctx).getString("traffic_history", "") ?: ""

    fun setTrafficHistoryJson(ctx: Context, v: String) = sp(ctx).edit().putString("traffic_history", v).apply()

    // ── 短信本地已读（按设备地址隔离；设备无已读接口时仅本机记录） ──
    private const val KEY_LOCAL_SMS_READ_IDS = "local_sms_read_ids"

    /** 本地已读 key：按当前连接地址分桶，避免多设备测试时 id 串号 */
    private fun localSmsReadKey(ctx: Context): String {
        val addr = getDeviceAddress(ctx).trim().lowercase()
        return if (addr.isEmpty()) KEY_LOCAL_SMS_READ_IDS
        else "${KEY_LOCAL_SMS_READ_IDS}_${addr.hashCode()}"
    }

    /** 本地已标为已读的短信 id 集合（逗号分隔存储） */
    fun getLocalSmsReadIds(ctx: Context): Set<String> {
        val prefs = sp(ctx)
        val key = localSmsReadKey(ctx)
        // 兼容旧版全局 key（仅当前地址桶为空时迁移一次）
        var raw = prefs.getString(key, "") ?: ""
        if (raw.isBlank() && key != KEY_LOCAL_SMS_READ_IDS) {
            val legacy = prefs.getString(KEY_LOCAL_SMS_READ_IDS, "") ?: ""
            if (legacy.isNotBlank()) {
                prefs.edit()
                    .putString(key, legacy)
                    .remove(KEY_LOCAL_SMS_READ_IDS)
                    .apply()
                raw = legacy
            }
        }
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun addLocalSmsReadIds(ctx: Context, ids: Collection<String>) {
        val clean = ids.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val key = localSmsReadKey(ctx)
        val merged = getLocalSmsReadIds(ctx).toMutableSet().apply { addAll(clean) }
        // 防止无限膨胀：只保留最近 2000 条 id
        val limited = if (merged.size > 2000) merged.toList().takeLast(2000).toSet() else merged
        sp(ctx).edit()
            .putString(key, limited.joinToString(","))
            .remove(KEY_LOCAL_SMS_READ_IDS) // 清理旧全局桶，避免串设备
            .apply()
    }

    // ── 小组件（全局 + 按 WidgetKind DIY）──

    /** 小组件后台刷新间隔（分钟，WorkManager 最小 15） */
    fun getWidgetRefreshMin(ctx: Context): Int = sp(ctx).getInt("widget_refresh_min", 15)

    fun setWidgetRefreshMin(ctx: Context, v: Int) = sp(ctx).edit().putInt("widget_refresh_min", v).apply()

    /**
     * 兼容旧版：无分组件键时回退到全局键。
     * @param kind 指定组件；null 时读全局遗留键
     */
    fun getWidgetAppearance(ctx: Context, kind: WidgetKind? = null): Int {
        if (kind != null) {
            val key = "widget_${kind.id}_appearance"
            if (sp(ctx).contains(key)) return sp(ctx).getInt(key, 2)
        }
        return sp(ctx).getInt("widget_appearance", 2)
    }

    fun setWidgetAppearance(ctx: Context, kind: WidgetKind, v: Int) =
        sp(ctx).edit().putInt("widget_${kind.id}_appearance", v.coerceIn(0, 2)).apply()

    fun getWidgetScalePercent(ctx: Context, kind: WidgetKind? = null): Int {
        if (kind != null) {
            val key = "widget_${kind.id}_scale"
            if (sp(ctx).contains(key)) return sp(ctx).getInt(key, 100).coerceIn(70, 150)
        }
        return sp(ctx).getInt("widget_scale_percent", 100).coerceIn(70, 150)
    }

    fun setWidgetScalePercent(ctx: Context, kind: WidgetKind, v: Int) =
        sp(ctx).edit().putInt("widget_${kind.id}_scale", v.coerceIn(70, 150)).apply()

    fun getWidgetTapAction(ctx: Context, kind: WidgetKind? = null): Int {
        if (kind != null) {
            val key = "widget_${kind.id}_tap"
            if (sp(ctx).contains(key)) return sp(ctx).getInt(key, 0).coerceIn(0, 1)
        }
        return sp(ctx).getInt("widget_tap_action", 0).coerceIn(0, 1)
    }

    fun setWidgetTapAction(ctx: Context, kind: WidgetKind, v: Int) =
        sp(ctx).edit().putInt("widget_${kind.id}_tap", v.coerceIn(0, 1)).apply()

    /** 全量卡：顶部状态栏（信号/网络/型号/电池） */
    fun isWidgetHeaderEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("widget_show_header", true)

    fun setWidgetHeaderEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("widget_show_header", v).apply()

    /** 全量卡：中部数据卡片 */
    fun isWidgetFlowCardsEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("widget_show_flow_cards", true)

    fun setWidgetFlowCardsEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("widget_show_flow_cards", v).apply()

    /**
     * 信号格显示方式（按组件）：
     * true = 图标；false = 文字（如 5/5）。默认图标。
     */
    fun isWidgetSignalIconEnabled(ctx: Context, kind: WidgetKind? = null): Boolean {
        if (kind != null) {
            val key = "widget_${kind.id}_signal_icon"
            if (sp(ctx).contains(key)) return sp(ctx).getBoolean(key, true)
        }
        return sp(ctx).getBoolean("widget_signal_icon", true)
    }

    fun setWidgetSignalIconEnabled(ctx: Context, kind: WidgetKind, v: Boolean) =
        sp(ctx).edit().putBoolean("widget_${kind.id}_signal_icon", v).apply()

    /** 兼容旧全量卡指标目录 */
    val WIDGET_METRIC_KEYS: List<String> get() = WidgetKind.MAIN.metricKeys
    val WIDGET_METRIC_DEFAULT_ON: Set<String> get() = WidgetKind.MAIN.metricDefaultOn
    val WIDGET_METRIC_MAX: Int get() = WidgetKind.MAIN.metricMax

    private fun defaultCardSlot(kind: WidgetKind, slot: Int): Int = when (kind) {
        WidgetKind.MAIN -> if (slot == 0) 0 else 3
        WidgetKind.TRAFFIC, WidgetKind.SPEED -> if (slot == 0) 0 else 1
        else -> 0
    }

    /**
     * 数据卡片槽位（按组件）：
     * 0=今日流量 1=本月流量 2=今日上下行 3=本月上下行 4=电池温度 5=CPU温度 6=放电电流 7=WiFi连接数
     */
    fun getWidgetCardSlot(ctx: Context, kind: WidgetKind, slot: Int): Int {
        val def = defaultCardSlot(kind, slot)
        val kindKey = "widget_${kind.id}_card_$slot"
        if (sp(ctx).contains(kindKey)) {
            return sp(ctx).getInt(kindKey, def).takeIf { it in 0..7 } ?: def
        }
        // 全量卡兼容旧全局键
        if (kind == WidgetKind.MAIN) {
            val legacy = "widget_card_$slot"
            if (sp(ctx).contains(legacy)) {
                return sp(ctx).getInt(legacy, def).takeIf { it in 0..7 } ?: def
            }
        }
        return def
    }

    fun setWidgetCardSlot(ctx: Context, kind: WidgetKind, slot: Int, v: Int) =
        sp(ctx).edit().putInt("widget_${kind.id}_card_$slot", v.coerceIn(0, 7)).apply()

    /** @deprecated 请用 [getWidgetCardSlot] */
    fun getWidgetCardContent(ctx: Context, slot: Int): Int =
        getWidgetCardSlot(ctx, WidgetKind.MAIN, slot)

    fun setWidgetCardContent(ctx: Context, slot: Int, v: Int) =
        setWidgetCardSlot(ctx, WidgetKind.MAIN, slot, v)

    /** 指标条单项开关（按组件） */
    fun isWidgetMetricEnabled(ctx: Context, kind: WidgetKind, key: String): Boolean {
        val def = key in kind.metricDefaultOn
        val kindKey = "widget_${kind.id}_metric_$key"
        if (sp(ctx).contains(kindKey)) return sp(ctx).getBoolean(kindKey, def)
        // 全量卡兼容旧全局键
        if (kind == WidgetKind.MAIN) {
            val legacy = "widget_metric_$key"
            if (sp(ctx).contains(legacy)) return sp(ctx).getBoolean(legacy, def)
        }
        return def
    }

    fun setWidgetMetricEnabled(ctx: Context, kind: WidgetKind, key: String, v: Boolean) =
        sp(ctx).edit().putBoolean("widget_${kind.id}_metric_$key", v).apply()

    /** 兼容旧调用：默认全量卡 */
    fun isWidgetMetricEnabled(ctx: Context, key: String, default: Boolean = true): Boolean {
        val kindKey = "widget_main_metric_$key"
        if (sp(ctx).contains(kindKey)) return sp(ctx).getBoolean(kindKey, default)
        return sp(ctx).getBoolean("widget_metric_$key", default)
    }

    fun setWidgetMetricEnabled(ctx: Context, key: String, v: Boolean) =
        setWidgetMetricEnabled(ctx, WidgetKind.MAIN, key, v)

    fun getWidgetMetricOrder(ctx: Context, kind: WidgetKind): List<String> {
        val catalog = kind.metricKeys
        if (catalog.isEmpty()) return emptyList()
        val kindKey = "widget_${kind.id}_metric_order"
        val saved = when {
            sp(ctx).contains(kindKey) ->
                sp(ctx).getString(kindKey, null)
                    ?.split(',')
                    ?.filter { it in catalog }
                    .orEmpty()
            kind == WidgetKind.MAIN && sp(ctx).contains("widget_metric_order") ->
                sp(ctx).getString("widget_metric_order", null)
                    ?.split(',')
                    ?.filter { it in catalog }
                    .orEmpty()
            else -> emptyList()
        }
        return saved + catalog.filter { it !in saved }
    }

    fun setWidgetMetricOrder(ctx: Context, kind: WidgetKind, order: List<String>) =
        sp(ctx).edit().putString("widget_${kind.id}_metric_order", order.joinToString(",")).apply()

    fun getWidgetMetricOrder(ctx: Context): List<String> =
        getWidgetMetricOrder(ctx, WidgetKind.MAIN)

    fun setWidgetMetricOrder(ctx: Context, order: List<String>) =
        setWidgetMetricOrder(ctx, WidgetKind.MAIN, order)

    /** 已开启的指标项，按用户排序，最多 [WidgetKind.metricMax] 个 */
    fun getEnabledWidgetMetrics(ctx: Context, kind: WidgetKind = WidgetKind.MAIN): List<String> {
        if (!kind.hasMetrics) return emptyList()
        return getWidgetMetricOrder(ctx, kind)
            .filter { isWidgetMetricEnabled(ctx, kind, it) }
            .take(kind.metricMax)
    }

    /** 状态条第 [index] 列内容 key（0..3） */
    fun getStatusBarSlot(ctx: Context, index: Int): String {
        val i = index.coerceIn(0, WidgetKind.STATUS_SLOT_COUNT - 1)
        val def = WidgetKind.STATUS_SLOT_DEFAULTS.getOrElse(i) { "signal" }
        val key = "widget_status_bar_slot_$i"
        val saved = sp(ctx).getString(key, null)
        return if (saved != null && saved in WidgetKind.STATUS_SLOT_KEYS) saved else def
    }

    fun setStatusBarSlot(ctx: Context, index: Int, slotKey: String) {
        val i = index.coerceIn(0, WidgetKind.STATUS_SLOT_COUNT - 1)
        val v = if (slotKey in WidgetKind.STATUS_SLOT_KEYS) slotKey else WidgetKind.STATUS_SLOT_DEFAULTS[i]
        sp(ctx).edit().putString("widget_status_bar_slot_$i", v).apply()
    }

    fun getStatusBarSlots(ctx: Context): List<String> =
        (0 until WidgetKind.STATUS_SLOT_COUNT).map { getStatusBarSlot(ctx, it) }

    /** 外观文案 */
    fun widgetAppearanceLabel(appearance: Int): String = when (appearance) {
        0 -> "浅色"
        1 -> "深色"
        else -> "玻璃"
    }

    private fun clearKindContentKeys(editor: SharedPreferences.Editor, kind: WidgetKind) {
        editor.remove("widget_${kind.id}_appearance")
        editor.remove("widget_${kind.id}_scale")
        editor.remove("widget_${kind.id}_tap")
        editor.remove("widget_${kind.id}_signal_icon")
        editor.remove("widget_${kind.id}_metric_order")
        kind.metricKeys.forEach { editor.remove("widget_${kind.id}_metric_$it") }
        for (slot in 0 until kind.cardSlotCount.coerceAtLeast(0)) {
            editor.remove("widget_${kind.id}_card_$slot")
        }
        if (kind.hasStatusSlots) {
            for (i in 0 until WidgetKind.STATUS_SLOT_COUNT) {
                editor.remove("widget_status_bar_slot_$i")
            }
        }
        if (kind == WidgetKind.MAIN) {
            listOf(
                "widget_show_header", "widget_show_flow_cards",
                "widget_card_0", "widget_card_1", "widget_metric_order",
                "widget_signal_icon",
            ).forEach { editor.remove(it) }
            WIDGET_METRIC_KEYS.forEach { editor.remove("widget_metric_$it") }
        }
    }

    /** 恢复全部小组件相关设置（不含仪表盘刷新间隔） */
    fun resetWidgetSettings(ctx: Context) {
        sp(ctx).edit().apply {
            listOf(
                "widget_refresh_min", "widget_appearance", "widget_scale_percent",
                "widget_tap_action", "widget_show_header", "widget_show_flow_cards",
                "widget_signal_icon",
                "widget_card_0", "widget_card_1", "widget_metric_order",
            ).forEach { remove(it) }
            WIDGET_METRIC_KEYS.forEach { remove("widget_metric_$it") }
            WidgetKind.entries.forEach { clearKindContentKeys(this, it) }
        }.apply()
    }

    /** 仅恢复某一组件的 DIY 项 */
    fun resetWidgetKindSettings(ctx: Context, kind: WidgetKind) {
        sp(ctx).edit().apply {
            clearKindContentKeys(this, kind)
        }.apply()
    }

    // ── 息屏显示（伪 AOD）──
    /** 息屏亮度百分比（1~40） */
    fun getAodBrightness(ctx: Context): Int =
        sp(ctx).getInt("aod_brightness", 5).coerceIn(1, 40)

    fun setAodBrightness(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_brightness", v.coerceIn(1, 40)).apply()

    /**
     * 未充电时的亮度（1~40）。
     * 0 = 与「息屏亮度」相同。
     */
    fun getAodBatteryBrightness(ctx: Context): Int =
        sp(ctx).getInt("aod_battery_brightness", 0).coerceIn(0, 40)

    fun setAodBatteryBrightness(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_battery_brightness", v.coerceIn(0, 40)).apply()

    /** 息屏数据刷新间隔（秒，30~300） */
    fun getAodRefreshSec(ctx: Context): Int = sp(ctx).getInt("aod_refresh_sec", 60).coerceIn(30, 300)

    fun setAodRefreshSec(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_refresh_sec", v.coerceIn(30, 300)).apply()

    /** 防烧屏位移 */
    fun isAodAntiBurnIn(ctx: Context): Boolean = sp(ctx).getBoolean("aod_anti_burn_in", true)

    fun setAodAntiBurnIn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod_anti_burn_in", v).apply()

    /** 防烧屏位移间隔（秒，30~300，默认 90） */
    fun getAodBurnInIntervalSec(ctx: Context): Int =
        sp(ctx).getInt("aod_burn_in_interval_sec", 90).coerceIn(30, 300)

    fun setAodBurnInIntervalSec(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_burn_in_interval_sec", v.coerceIn(30, 300)).apply()

    /** 防烧屏位移幅度：0=弱 1=中 2=强 */
    fun getAodBurnInAmplitude(ctx: Context): Int =
        sp(ctx).getInt("aod_burn_in_amplitude", 1).coerceIn(0, 2)

    fun setAodBurnInAmplitude(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_burn_in_amplitude", v.coerceIn(0, 2)).apply()

    /** 双击退出（关闭则单击退出） */
    fun isAodDoubleTapExit(ctx: Context): Boolean = sp(ctx).getBoolean("aod_double_tap_exit", true)

    fun setAodDoubleTapExit(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod_double_tap_exit", v).apply()

    /** 显示秒 */
    fun isAodShowSeconds(ctx: Context): Boolean = sp(ctx).getBoolean("aod_show_seconds", false)

    fun setAodShowSeconds(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod_show_seconds", v).apply()

    /** 24 小时制时钟（关闭为 12 小时制） */
    fun isAodClock24Hour(ctx: Context): Boolean = sp(ctx).getBoolean("aod_clock_24h", true)

    fun setAodClock24Hour(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("aod_clock_24h", v).apply()

    /** 息屏方向：0=跟随系统 1=竖屏 2=横屏 */
    fun getAodOrientation(ctx: Context): Int = sp(ctx).getInt("aod_orientation", 0).coerceIn(0, 2)

    fun setAodOrientation(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_orientation", v.coerceIn(0, 2)).apply()

    /** 内容垂直位置：0=偏上 1=居中 2=偏下 */
    fun getAodVerticalPosition(ctx: Context): Int =
        sp(ctx).getInt("aod_vertical_position", 1).coerceIn(0, 2)

    fun setAodVerticalPosition(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_vertical_position", v.coerceIn(0, 2)).apply()

    /** 底部显示最近数据更新时间 */
    fun isAodShowLastUpdate(ctx: Context): Boolean =
        sp(ctx).getBoolean("aod_show_last_update", true)

    fun setAodShowLastUpdate(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("aod_show_last_update", v).apply()

    /** 进入息屏时显示操作提示 */
    fun isAodShowEntryHint(ctx: Context): Boolean =
        sp(ctx).getBoolean("aod_show_entry_hint", true)

    fun setAodShowEntryHint(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("aod_show_entry_hint", v).apply()

    /** 仅在手机充电时允许息屏显示 */
    fun isAodChargingOnly(ctx: Context): Boolean = sp(ctx).getBoolean("aod_charging_only", false)

    fun setAodChargingOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod_charging_only", v).apply()

    /** 自动退出时长（分钟，0=不限制） */
    fun getAodTimeoutMin(ctx: Context): Int = sp(ctx).getInt("aod_timeout_min", 0).coerceIn(0, 120)

    fun setAodTimeoutMin(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_timeout_min", v.coerceIn(0, 120)).apply()

    /** 手机低电量退出阈值（百分比，0=关闭） */
    fun getAodLowBatteryThreshold(ctx: Context): Int =
        sp(ctx).getInt("aod_low_battery_threshold", 0).coerceIn(0, 30)

    fun setAodLowBatteryThreshold(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("aod_low_battery_threshold", v.coerceIn(0, 30)).apply()

    /** 距离传感器被遮挡时自动退出 */
    fun isAodPocketProtectionEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("aod_pocket_protection", false)

    fun setAodPocketProtectionEnabled(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("aod_pocket_protection", v).apply()

    val AOD_MODULE_KEYS = listOf(
        "clock", "date", "device", "battery", "network", "signal", "speed",
        "wifi", "temperature", "traffic", "system", "qos", "custom",
    )

    val AOD_MODULE_DEFAULT_ON = setOf(
        "clock", "date", "battery", "network", "signal", "wifi", "temperature", "traffic",
    )

    fun getAodModuleOrder(ctx: Context): List<String> {
        val saved = sp(ctx).getString("aod_module_order", null)
            ?.split(',')
            ?.filter { it in AOD_MODULE_KEYS }
            ?.distinct()
            .orEmpty()
        return saved + AOD_MODULE_KEYS.filter { it !in saved }
    }

    fun setAodModuleOrder(ctx: Context, order: List<String>) =
        sp(ctx).edit().putString("aod_module_order", order.filter { it in AOD_MODULE_KEYS }.joinToString(",")).apply()

    fun isAodModuleEnabled(ctx: Context, key: String): Boolean =
        sp(ctx).getBoolean("aod_module_$key", key in AOD_MODULE_DEFAULT_ON)

    fun setAodModuleEnabled(ctx: Context, key: String, enabled: Boolean) {
        if (key in AOD_MODULE_KEYS) sp(ctx).edit().putBoolean("aod_module_$key", enabled).apply()
    }

    fun getEnabledAodModules(ctx: Context): List<String> =
        getAodModuleOrder(ctx).filter { isAodModuleEnabled(ctx, it) }

    /** 模块列数：0=自动，1~3=固定列数 */
    fun getAodModuleColumns(ctx: Context): Int = sp(ctx).getInt("aod_module_columns", 0).coerceIn(0, 3)

    fun setAodModuleColumns(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_module_columns", value.coerceIn(0, 3)).apply()

    /** 普通模块字号缩放百分比 */
    fun getAodFontScale(ctx: Context): Int = sp(ctx).getInt("aod_font_scale", 100).coerceIn(70, 160)

    fun setAodFontScale(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_font_scale", value.coerceIn(70, 160)).apply()

    fun getAodClockSize(ctx: Context): Int = sp(ctx).getInt("aod_clock_size", 64).coerceIn(36, 96)

    fun setAodClockSize(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_clock_size", value.coerceIn(36, 96)).apply()

    fun getAodModuleSpacing(ctx: Context): Int = sp(ctx).getInt("aod_module_spacing", 8).coerceIn(0, 24)

    fun setAodModuleSpacing(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_module_spacing", value.coerceIn(0, 24)).apply()

    /** 模块文字对齐：0=左，1=居中，2=右 */
    fun getAodTextAlignment(ctx: Context): Int = sp(ctx).getInt("aod_text_alignment", 1).coerceIn(0, 2)

    fun setAodTextAlignment(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_text_alignment", value.coerceIn(0, 2)).apply()

    /** 配色：0=灰白，1=暖白，2=护眼红，3=护眼绿，4=琥珀 */
    fun getAodColorScheme(ctx: Context): Int = sp(ctx).getInt("aod_color_scheme", 0).coerceIn(0, 4)

    fun setAodColorScheme(ctx: Context, value: Int) =
        sp(ctx).edit().putInt("aod_color_scheme", value.coerceIn(0, 4)).apply()

    fun isAodModuleLabelEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("aod_module_labels", true)

    fun setAodModuleLabelEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean("aod_module_labels", enabled).apply()

    fun isAodModuleOutlineEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("aod_module_outline", false)

    fun setAodModuleOutlineEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean("aod_module_outline", enabled).apply()

    fun getAodCustomText(ctx: Context): String = sp(ctx).getString("aod_custom_text", "") ?: ""

    fun setAodCustomText(ctx: Context, value: String) =
        sp(ctx).edit().putString("aod_custom_text", value.trim().take(80)).apply()

    fun isAodGuideAcknowledged(ctx: Context): Boolean =
        sp(ctx).getBoolean("aod_guide_acknowledged", false)

    fun setAodGuideAcknowledged(ctx: Context, acknowledged: Boolean) =
        sp(ctx).edit().putBoolean("aod_guide_acknowledged", acknowledged).apply()

    fun resetAodModuleSettings(ctx: Context) {
        sp(ctx).edit().apply {
            listOf(
                "aod_module_order", "aod_module_columns", "aod_font_scale", "aod_clock_size",
                "aod_module_spacing", "aod_text_alignment", "aod_color_scheme",
                "aod_module_labels", "aod_module_outline", "aod_custom_text",
                "aod_vertical_position",
            ).forEach { remove(it) }
            AOD_MODULE_KEYS.forEach { remove("aod_module_$it") }
        }.apply()
    }

    /** 恢复息屏运行策略（亮度、刷新、省电、防烧屏等，不含模块 DIY） */
    fun resetAodRuntimeSettings(ctx: Context) {
        sp(ctx).edit().apply {
            listOf(
                "aod_brightness", "aod_battery_brightness", "aod_refresh_sec",
                "aod_anti_burn_in", "aod_burn_in_interval_sec", "aod_burn_in_amplitude",
                "aod_double_tap_exit", "aod_show_seconds", "aod_clock_24h",
                "aod_orientation", "aod_charging_only", "aod_timeout_min",
                "aod_low_battery_threshold", "aod_pocket_protection",
                "aod_show_last_update", "aod_show_entry_hint",
            ).forEach { remove(it) }
        }.apply()
    }

    /** 恢复全部息屏相关设置（模块 DIY + 运行策略） */
    fun resetAodAllSettings(ctx: Context) {
        resetAodModuleSettings(ctx)
        resetAodRuntimeSettings(ctx)
    }

    // ── 界面偏好 ──
    /** 锁屏上显示应用界面（无需解锁） */
    fun isShowOnLockScreen(ctx: Context): Boolean = sp(ctx).getBoolean("ui_show_on_lockscreen", false)

    fun setShowOnLockScreen(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_show_on_lockscreen", v).apply()

    fun getThemeMode(ctx: Context): Int = sp(ctx).getInt("ui_theme_mode", 0)

    fun setThemeMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("ui_theme_mode", v).apply()

    fun isMonetEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("ui_monet", false)

    fun setMonetEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_monet", v).apply()

    fun getMonetSeedIndex(ctx: Context): Int = sp(ctx).getInt("ui_monet_seed", 0)

    fun setMonetSeedIndex(ctx: Context, v: Int) = sp(ctx).edit().putInt("ui_monet_seed", v).apply()

    fun getMonetPaletteIndex(ctx: Context, def: Int): Int = sp(ctx).getInt("ui_monet_palette", def)

    fun setMonetPaletteIndex(ctx: Context, v: Int) = sp(ctx).edit().putInt("ui_monet_palette", v).apply()

    fun isBlurEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("ui_blur", true)

    fun setBlurEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_blur", v).apply()

    fun isFloatingBottomBar(ctx: Context): Boolean = sp(ctx).getBoolean("ui_floating_bar", false)

    fun setFloatingBottomBar(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_floating_bar", v).apply()

    fun isFloatingBottomBarBlur(ctx: Context): Boolean = sp(ctx).getBoolean("ui_floating_bar_blur", true)

    fun setFloatingBottomBarBlur(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_floating_bar_blur", v).apply()

    /**
     * 清除接口探测缓存（goform 路径模式、登录 Cookie、device_token、机型）。
     * 同地址热切换 F50/E5、或重跑连接测试时应调用，强制重新探测。
     * 不清理流量历史 / 本地短信已读（那些按地址分桶或由 [clearDeviceCache] 处理）。
     */
    fun clearProbeCache(ctx: Context) {
        val prefs = sp(ctx)
        val editor = prefs.edit()
            .remove("device_token")
            .remove("cached_model")
            .remove("cached_monthly_data")
            .remove("widget_snapshot")
            .remove("widget_snapshot_time")
            .remove("active_sim_slot")
        removeProbeKeys(prefs, editor)
        editor.apply()
    }

    /**
     * 清除设备相关缓存（切换设备地址时调用）。
     * 包含探测缓存 + 流量历史（流量历史未按设备分桶，换机必须清掉以免串数据）。
     * 本地短信已读已按地址分桶，换地址后自动隔离，无需清空其它设备的已读记录。
     */
    fun clearDeviceCache(ctx: Context) {
        val prefs = sp(ctx)
        val editor = prefs.edit()
            .remove("device_token")
            .remove("cached_model")
            .remove("cached_monthly_data")
            .remove("widget_snapshot")
            .remove("widget_snapshot_time")
            .remove("traffic_history")
            .remove("active_sim_slot")
            .remove(KEY_LOCAL_SMS_READ_IDS) // 仅清旧版全局桶
        removeProbeKeys(prefs, editor)
        editor.apply()
    }

    /** 移除 goform 兼容模式 / cookie 相关 key（含带 scope 后缀的实际存储） */
    private fun removeProbeKeys(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        // 旧版无后缀 key + 新版 goform_compat_mode_*/goform_compat_base_url_* / goform_cookie*
        editor
            .remove("goform_compat_mode")
            .remove("goform_cookie")
            .remove("goform_cookie_base_url")
        prefs.all.keys
            .filter {
                it.startsWith("goform_compat_") ||
                    it.startsWith("goform_cookie")
            }
            .forEach { editor.remove(it) }
    }
}
