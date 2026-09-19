package com.xingyue.ufitools.monitor.data

import android.content.Context
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** 设备 HTTP API 客户端（UFI-TOOLS kano 签名协议） */
object DeviceApi {

    @Volatile
    var lastError: String = ""

    private val goformLoginMutex = Mutex()
    private val GOFORM_SINR_KEYS = setOf("Nr_snr", "nr5g_snr", "Z5g_SINR", "Lte_snr", "lte_snr")
    private val GOFORM_BAND_KEYS = setOf(
        "nr5g_action_band",
        "wan_active_band",
        "Nr_bands",
        "Lte_bands",
        "lte_ca_pcell_band",
    )
    /**
     * UFI-TOOLS 前端 getUFIData **原样** cmd（短串）。
     * U30 Pro(MU5358) 对超长 multi_data 更敏感，必须先走这套；F50Pro 同用。
     * @see requests.js getUFIData
     */
    private const val PORTABLE_UFI_GOFORM_COMMAND =
        "usb_port_switch,battery_charging,sms_received_flag,sms_unread_num,sms_sim_unread_num," +
            "sim_msisdn,data_volume_limit_switch,battery_value,battery_vol_percent,network_signalbar," +
            "network_rssi,cr_version,iccid,imei,imsi,ipv6_wan_ipaddr,lan_ipaddr,mac_address,msisdn," +
            "network_information,Lte_ca_status,rssi,Z5g_rsrp,lte_rsrp,wifi_access_sta_num,loginfo," +
            "data_volume_alert_percent,data_volume_limit_size," +
            "realtime_rx_thrpt,realtime_tx_thrpt,realtime_time," +
            "monthly_tx_bytes,monthly_rx_bytes,monthly_time," +
            "network_type,network_provider,ppp_status"

    /**
     * 二次短请求：SINR/频段/日流量等扩展字段（失败忽略，不影响主信号）。
     * 与主 cmd 拆开，避免 U30Pro 超长 multi_data 整包失败。
     */
    private const val PORTABLE_UFI_GOFORM_EXT_COMMAND =
        "nr5g_rsrp,Lte_snr,lte_snr,Nr_snr,nr5g_snr,Z5g_SINR," +
            "wan_active_band,nr5g_action_band,Lte_bands,Nr_bands," +
            "day_rx_bytes,day_tx_bytes,month_rx_bytes,month_tx_bytes," +
            "daily_rx_bytes,daily_tx_bytes,monthly_rx_bytes,monthly_tx_bytes," +
            "realtime_rx_speed,realtime_tx_speed,real_rx_speed,real_tx_speed," +
            "qci,ambr_dl_max,ambr_ul_max,battery_temperature," +
            "internal_available_storage,internal_total_storage,internal_used_storage," +
            "external_available_storage,external_total_storage,external_used_storage," +
            "wa_inner_version,hardware_version,web_version,wan_ipaddr"

    /** @deprecated 名称保留兼容 */
    private const val F50_HAR_COMMAND = PORTABLE_UFI_GOFORM_COMMAND

    /** 通用版 goform 信号/流量/存储（gateway 可用时）；失败由 base + currentCellInfo 兜底 */
    private const val GENERIC_GOFORM_COMMAND =
        "usb_port_switch,battery_charging,sms_received_flag,sms_unread_num,sms_sim_unread_num," +
            "sim_msisdn,data_volume_limit_switch,battery_value,battery_vol_percent,network_signalbar," +
            "network_rssi,cr_version,wa_inner_version,hardware_version,web_version,iccid,imei,imsi," +
            "wan_ipaddr,ipv6_wan_ipaddr,lan_ipaddr,mac_address,msisdn,network_information,Lte_ca_status," +
            "rssi,Z5g_rsrp,lte_rsrp,nr5g_rsrp,wifi_access_sta_num,station_number,sta_count,user_number," +
            "loginfo,data_volume_alert_percent,data_volume_limit_size," +
            "realtime_rx_bytes,realtime_tx_bytes,realtime_rx_thrpt,realtime_tx_thrpt," +
            "real_rx_speed,real_tx_speed,realtime_rx_speed,realtime_tx_speed,realtime_time," +
            "day_rx_bytes,day_tx_bytes,daily_rx_bytes,daily_tx_bytes," +
            "month_rx_bytes,month_tx_bytes,monthly_rx_bytes,monthly_tx_bytes,monthly_time," +
            "network_type,network_provider,network_provider_fullname,ppp_status," +
            "qci,ambr_dl_max,ambr_ul_max,lte_snr,Lte_snr,Z5g_SINR,Nr_snr,nr5g_snr," +
            "Nr_bands,Lte_bands,Nr_bands_widths,Lte_bands_widths,wan_active_band,nr5g_action_band," +
            "lteca_state,wan_lte_ca,lteca,ltecasig,nrca,lte_multi_ca_scell_sig_info," +
            "lte_ca_pcell_band,lte_ca_pcell_bandwidth,lte_ca_scell_band,lte_ca_scell_info," +
            "lte_multi_ca_scell_info,nr_ca_pcell_band,nr_multi_ca_scell_info,battery_temperature," +
            "internal_available_storage,internal_total_storage,internal_used_storage," +
            "external_available_storage,external_total_storage,external_used_storage"

    sealed interface FetchResult {
        data class Success(val status: DeviceStatus) : FetchResult
        data class Failure(val reason: Reason, val message: String) : FetchResult

        enum class Reason { NETWORK, API, PERMISSION }
    }

    suspend fun fetchStatus(context: Context): FetchResult = withContext(Dispatchers.IO) {
        try {
            lastError = ""
            // Android 17 未授予本地网络权限时，所有请求只会静默等到超时，先行短路
            if (LocalNetworkPermission.blocksCurrentDevice(context)) {
                lastError = LocalNetworkPermission.HINT
                return@withContext FetchResult.Failure(FetchResult.Reason.PERMISSION, lastError)
            }
            val t = System.currentTimeMillis()
            val auth = DevicePrefs.getAuthToken(context)

            ensureDeviceToken(t, context)

            val base = fetchApi(context, DevicePrefs.DEFAULT_DEVICE_INFO_PATH, t, auth)
                ?: return@withContext FetchResult.Failure(
                    if (lastError.startsWith("HTTP")) FetchResult.Reason.API else FetchResult.Reason.NETWORK,
                    lastError.ifEmpty { "无法与设备通信" },
                )
            val modelRaw = base.optString("model")
            DevicePrefs.setCachedModel(context, modelRaw)
            // F50 无电池等旧逻辑仍按精确型号；信号/goform 则认整套随身 WiFi 机型
            val isF50 = modelRaw.equals("F50", ignoreCase = true)
            val isPortableUfi = isZtePortableUfiModel(modelRaw)

            var signalInfo: JSONObject? = null
            var goformInfo: JSONObject? = null
            var versionInfo: JSONObject? = null
            var qosInfo: QosInfo? = null
            var cellInfo: JSONObject? = null
            coroutineScope {
                val goformDeferred = async {
                    if (isPortableUfi) {
                        // 与 UFI-TOOLS getUFIData 一致：短 multi_data cmd，U30Pro/F50Pro 与 F50 同源
                        fetchPortableUfiGoform(context, t, auth)
                    } else {
                        // 通用版（E5 等）goform 可能因 gateway 未配置而 500，失败时由 currentCellInfo/base 兜底
                        fetchGoformApi(
                            context,
                            "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?multi_data=1&isTest=false" +
                                "&cmd=$GENERIC_GOFORM_COMMAND",
                            t, auth,
                            "network_type", "lte_rsrp", "Z5g_rsrp", "rssi", "network_rssi",
                            "network_signalbar", "qci", "ambr_dl_max", "ambr_ul_max",
                            "Lte_snr", "Nr_snr", "wan_active_band", "nr5g_action_band",
                            "Lte_bands", "Nr_bands", "imsi", "cr_version", "day_rx_bytes",
                            "month_rx_bytes", "monthly_rx_bytes", "real_rx_speed",
                            "realtime_rx_speed", "realtime_rx_thrpt",
                            "internal_total_storage", "external_total_storage",
                        )
                    }
                }
                val addressDeferred = async {
                    if (isPortableUfi) {
                        fetchGoformApi(
                            context,
                            "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?is_all=true" +
                                "&cmd=wan_ipaddr,ipv6_wan_ipaddr",
                            t,
                            auth,
                            "wan_ipaddr",
                            "ipv6_wan_ipaddr",
                        )
                    } else {
                        null
                    }
                }
                // 通用版双卡：自动探测实际有卡的 slot（只插一张也能用）
                val cellDeferred = async {
                    fetchBestCellInfo(context, t, auth, base)
                }
                val versionDeferred = async {
                    fetchApiNoAuth(context, DevicePrefs.DEFAULT_VERSION_INFO_PATH, t)
                }
                val qosDeferred = async {
                    fetchQosInfo(context, auth)
                }
                goformInfo = goformDeferred.await()
                addressDeferred.await()?.let { address ->
                    val target = goformInfo
                    if (target == null) {
                        goformInfo = address
                    } else {
                        address.keys().forEach { key -> target.put(key, address.opt(key)) }
                    }
                }
                cellInfo = cellDeferred.await()
                // 信号优先 goform；缺失时用 base + currentCellInfo 合成
                signalInfo = mergeSignalSources(goformInfo, base, cellInfo)
                versionInfo = versionDeferred.await()
                qosInfo = qosDeferred.await()
            }

            // 存储：base 数值 + goform 字符串（"784.4M"）合并；缺则 root_shell 读内部/SD
            mergeStorageIntoBase(base, goformInfo)
            val needInternal = (parseStorageSize(base.opt("internal_total_storage")) ?: 0L) <= 0L
            val needExternal = (parseStorageSize(base.opt("external_total_storage")) ?: 0L) <= 0L
            if (needInternal || needExternal) {
                fetchRootShellStorageVolumes(context)?.let { volumes ->
                    if (needInternal) {
                        volumes.internal?.let { disk ->
                            base.put("internal_total_storage", disk.total)
                            base.put("internal_used_storage", disk.used)
                            base.put("internal_available_storage", disk.available)
                        }
                    }
                    if (needExternal) {
                        volumes.external?.let { disk ->
                            base.put("external_total_storage", disk.total)
                            base.put("external_used_storage", disk.used)
                            base.put("external_available_storage", disk.available)
                        }
                    }
                }
            }

            FetchResult.Success(
                parseStatus(context, base, signalInfo, goformInfo, versionInfo, qosInfo, isF50, cellInfo),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "Parse Error: ${e.message}"
            FetchResult.Failure(FetchResult.Reason.API, lastError)
        }
    }

    /**
     * 是否某兴随身 WiFi goform 系（信号/流量与 UFI-TOOLS getUFIData 同源）。
     * model 可能是 F50、F50Pro、U30 Air、U30Pro、MU3356(F50Pro)、MU5358(U30 Pro)、
     * MU300、MU5352 等，**绝不能**只判断 `equals("F50")`。
     */
    fun isZtePortableUfiModel(model: String): Boolean {
        val m = model.trim().uppercase(Locale.US)
        if (m.isEmpty()) return false
        if (m == "F50" || m == "U20") return true
        if (m.contains("F50")) return true // F50Pro 等
        if (m.startsWith("U30") || m.contains("U30")) return true
        if (m.startsWith("U20")) return true
        // MU5358=U30 Pro、MU3356=F50Pro 等硬件代号
        if (m.startsWith("MU")) return true
        if (m.startsWith("M3") || m.contains("M3")) return true
        return false
    }

    /**
     * 将 baseDeviceInfo.model 硬件代号映射为常见商品名（接口原文仍用 [DeviceStatus.model]）。
     * 未知代号返回 null，由调用方回退 versionInfo / 原文。
     */
    fun portableUfiProductName(model: String): String? {
        return when (model.trim().uppercase(Locale.US)) {
            "MU5358" -> "U30 Pro"
            "MU3356" -> "F50 Pro"
            "MU5352" -> "U30 Air"
            "F50" -> "F50"
            else -> null
        }
    }

    /**
     * 拉取随身 WiFi goform（F50 / F50Pro / U30Pro(MU5358)…）。
     *
     * 历史坑：
     * - 超长 multi_data：U30 Pro 整包失败，F50Pro 仍可能部分成功 → 表现为「只有 U30 读不到信号」
     * - Cookie 写成字面量 "null"：部分 modem 直接拒答
     * - 未走 is_all / multi_data 多路径 + 登录重试
     *
     * 现逻辑：官方短 cmd + [fetchGoformApi] 多路径/登录，再补一次扩展字段。
     */
    private suspend fun fetchPortableUfiGoform(
        context: Context,
        t: Long,
        auth: String,
    ): JSONObject? {
        val mainPath = "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?multi_data=1&isTest=false" +
            "&cmd=$PORTABLE_UFI_GOFORM_COMMAND&_=$t"
        // 用评分探测 multi_data / is_all / direct，并在无数据时 login 重试
        val main = fetchGoformApi(
            context,
            mainPath,
            t,
            auth,
            "network_type",
            "lte_rsrp",
            "Z5g_rsrp",
            "rssi",
            "network_rssi",
            "network_signalbar",
            "network_provider",
            "cr_version",
            "monthly_rx_bytes",
            "realtime_rx_thrpt",
            "battery_value",
            "loginfo",
        )
        // 扩展字段单独拉，失败不影响主包
        val extPath = "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?multi_data=1&isTest=false" +
            "&cmd=$PORTABLE_UFI_GOFORM_EXT_COMMAND&_=${System.currentTimeMillis()}"
        val ext = fetchGoformApi(
            context,
            extPath,
            System.currentTimeMillis(),
            auth,
            "Lte_snr",
            "Nr_snr",
            "Z5g_SINR",
            "wan_active_band",
            "nr5g_action_band",
            "month_rx_bytes",
            "qci",
            "ambr_dl_max",
        )
        val merged = when {
            main == null && ext == null -> null
            main == null -> ext
            ext == null -> main
            else -> {
                ext.keys().forEach { key ->
                    val v = ext.opt(key) ?: return@forEach
                    val s = v.toString()
                    if (s.isBlank() || s.equals("null", ignoreCase = true)) return@forEach
                    if (!main.has(key) || main.optString(key).isBlank()) {
                        main.put(key, v)
                    }
                }
                main
            }
        }
        merged?.let { normalizePortableUfiGoform(it) }
        return merged
    }

    /** U30Air/U30Pro：msisdn←sim_msisdn；空字符串字段剔除以免盖住有效值 */
    private fun normalizePortableUfiGoform(json: JSONObject) {
        if (json.optString("msisdn").isBlank()) {
            val sim = json.optString("sim_msisdn")
            if (sim.isNotBlank()) json.put("msisdn", sim)
        }
        // 部分 U30 固件 battery 只在 goform 的 battery_value
        if (json.optString("battery_value").isBlank()) {
            val vol = json.optString("battery_vol_percent")
            if (vol.isNotBlank()) json.put("battery_value", vol)
        }
    }

    /**
     * 合成信号数据源（互不污染）：
     * 1) goform 字段优先
     * 2) baseDeviceInfo 扩展字段（E5 1.1.50 含 network_type/provider/thrpt）
     * 3) /api/currentCellInfo 小区明细（rsrp/sinr/band/rssi）
     */
    private fun mergeSignalSources(
        goform: JSONObject?,
        base: JSONObject,
        cell: JSONObject?,
    ): JSONObject {
        val out = JSONObject()
        fun putIfMissing(key: String, value: String?) {
            if (value.isNullOrBlank() || value == "null") return
            if (!out.has(key) || out.optString(key).isBlank()) out.put(key, value)
        }
        // goform first
        goform?.keys()?.forEach { key ->
            val v = goform.opt(key) ?: return@forEach
            if (v is String && (v.isBlank() || v == "null")) return@forEach
            out.put(key, v)
        }
        // baseDeviceInfo network/speed fields (generic UFI)
        listOf(
            "network_type", "network_provider", "network_provider_fullname", "network_signalbar",
            "network_rssi", "realtime_rx_thrpt", "realtime_tx_thrpt", "realtime_rx_speed",
            "realtime_tx_speed", "cr_version", "ipv6_wan_ipaddr", "wan_ipaddr", "msisdn",
            "battery_charging", "sim_slot",
        ).forEach { k -> putIfMissing(k, base.optString(k, "").ifBlank { null }) }
        // currentCellInfo → normalize into goform-like keys
        cell?.let { c ->
            putIfMissing("imsi", c.optString("imsi").trim().ifBlank { null })
            putIfMissing("iccid", c.optString("iccid").trim().ifBlank { null })
            val netMode = c.optString("netMode", "")
            val rat = c.optString("rat", "")
            val mappedType = when {
                netMode.equals("SA", ignoreCase = true) || rat.equals("NR", ignoreCase = true) ->
                    if (netMode.equals("NSA", ignoreCase = true)) "5G NSA" else "5G SA"
                netMode.equals("NSA", ignoreCase = true) -> "5G NSA"
                rat.equals("LTE", ignoreCase = true) || netMode.contains("LTE", ignoreCase = true) -> "4G"
                else -> null
            }
            putIfMissing("network_type", mappedType)
            val nr = c.optJSONObject("info")?.optJSONObject("nr")
            val lte = c.optJSONObject("info")?.optJSONObject("lte")
            val cellDetail = nr ?: lte
            cellDetail?.let { d ->
                val rsrp = d.optString("rsrp").ifBlank { null }
                val sinr = d.optString("sinr").ifBlank { null }
                val band = d.optString("band").ifBlank { null }
                val rssi = d.optString("rssi").ifBlank { null }
                if (nr != null) {
                    putIfMissing("Z5g_rsrp", rsrp)
                    putIfMissing("nr5g_rsrp", rsrp)
                    putIfMissing("Nr_snr", sinr)
                    putIfMissing("nr5g_snr", sinr)
                    putIfMissing("Z5g_SINR", sinr)
                    putIfMissing("nr5g_action_band", band)
                    putIfMissing("Nr_bands", band)
                } else {
                    putIfMissing("lte_rsrp", rsrp)
                    putIfMissing("Lte_snr", sinr)
                    putIfMissing("lte_snr", sinr)
                    putIfMissing("Lte_bands", band)
                    putIfMissing("wan_active_band", band)
                }
                putIfMissing("rssi", rsrp ?: rssi)
                // rssi 0-5 作为格数；rsrp 为负 dBm
                rssi?.toIntOrNull()?.takeIf { it in 0..5 }?.let {
                    putIfMissing("network_signalbar", it.toString())
                    putIfMissing("network_rssi", it.toString())
                }
            }
        }
        return out
    }

    private fun parseStatus(
        context: Context,
        base: JSONObject,
        signalInfo: JSONObject?,
        goformInfo: JSONObject?,
        versionInfo: JSONObject?,
        qosInfo: QosInfo?,
        isF50: Boolean,
        cellInfo: JSONObject? = null,
    ): DeviceStatus {
        val model = base.optString("model", "UFI")
        // U30 Pro 等：电量常在 goform battery_value / battery_vol_percent，base.battery 可能为 0/-1
        val batteryFromGoform = goformInfo?.optString("battery_value")?.toIntOrNull()
            ?: goformInfo?.optString("battery_vol_percent")?.toIntOrNull()
        val batteryFromBase = base.optInt("battery", -1)
        val batteryPercent = when {
            batteryFromBase in 0..100 -> batteryFromBase
            batteryFromGoform != null && batteryFromGoform in 0..100 -> batteryFromGoform
            else -> batteryFromBase
        }
        val memUsage = base.optDouble("mem_usage", 0.0)
        val tempRaw = base.optDouble("cpu_temp", 0.0)
        val appVer = base.optString("app_ver", "")
        val currentNow = if (base.has("current_now")) base.optInt("current_now") else Int.MIN_VALUE
        val voltageNow = base.optInt("voltage_now", -1)

        // CPU 占用
        val cpuUsage = base.optDouble("cpu_usage", -1.0)
        val cpuUsageMap = mutableMapOf<String, String>()
        base.optJSONObject("cpuUsageInfo")?.let { obj ->
            obj.keys().forEach { key -> cpuUsageMap[key] = obj.optString(key, "--") }
        }
        val finalCpuUsage = if (cpuUsage in 0.0..100.0) cpuUsage
        else cpuUsageMap["cpu"]?.toDoubleOrNull() ?: 0.0

        // 各模块温度
        val cpuTempList = mutableListOf<CpuTempItem>()
        base.optJSONArray("cpu_temp_list")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                cpuTempList.add(CpuTempItem(o.optString("type", "unknown"), o.optDouble("temp", 0.0)))
            }
        }

        // 各核心频率
        val cpuFreqMap = mutableMapOf<String, CpuFreqItem>()
        base.optJSONObject("cpuFreqInfo")?.let { freqObj ->
            freqObj.keys().forEach { key ->
                val core = freqObj.optJSONObject(key) ?: return@forEach
                cpuFreqMap[key] = CpuFreqItem(core.optInt("cur", 0), core.optInt("max", 0))
            }
        }

        // 内存详情
        var memTotalKb = 0L
        var memAvailableKb = 0L
        var memUsedKb = 0L
        var swapTotalKb = 0L
        var swapUsedKb = 0L
        base.optJSONObject("memInfo")?.let { memObj ->
            memTotalKb = memObj.optLong("mem_total_kb", 0L)
            memAvailableKb = memObj.optLong("mem_available_kb", 0L)
            memUsedKb = memObj.optLong("mem_used_kb", 0L)
            swapTotalKb = memObj.optLong("swap_total_kb", 0L)
            swapUsedKb = memObj.optLong("swap_used_kb", 0L)
        }

        // 流量：
        // - 通用版：base daily/monthly_data 总量 + goform 上下行拆分
        // - 随身 WiFi goform 系（F50/U30/F50Pro…）：今日用 base；本月优先 goform 本月下行
        // - 通用版 goform 不可用时同样只显示总量（E5 实测 gateway 未配置）
        val dailyRaw: Long
        val monthlyRawBase: Long
        val dTx: Long
        val dRx: Long
        val mTx: Long
        val mRx: Long
        val portableUfi = isZtePortableUfiModel(model)
        if (portableUfi) {
            dailyRaw = readPresentTrafficBytes(base, "daily_data", "day_data") ?: 0L
            val monthRx = readPresentTrafficBytes(
                goformInfo,
                "month_rx_bytes",
                "monthly_rx_bytes",
            )
            monthlyRawBase = monthRx
                ?: readPresentTrafficBytes(base, "monthly_data", "month_data")
                ?: 0L
            dTx = -1L
            dRx = -1L
            mTx = -1L
            mRx = monthRx ?: -1L
        } else {
            val dailyTraffic = resolveTrafficBucket(
                baseTotal = readPresentTrafficBytes(base, "daily_data", "day_data"),
                goform = goformInfo,
                splitPairs = listOf(
                    "day_tx_bytes" to "day_rx_bytes",
                    "daily_tx_bytes" to "daily_rx_bytes",
                ),
            )
            val monthlyTraffic = resolveTrafficBucket(
                baseTotal = readPresentTrafficBytes(base, "monthly_data", "month_data"),
                goform = goformInfo,
                splitPairs = listOf(
                    "monthly_tx_bytes" to "monthly_rx_bytes",
                    "month_tx_bytes" to "month_rx_bytes",
                ),
            )
            dailyRaw = dailyTraffic.totalBytes
            monthlyRawBase = monthlyTraffic.totalBytes
            dTx = dailyTraffic.uploadBytes
            dRx = dailyTraffic.downloadBytes
            mTx = monthlyTraffic.uploadBytes
            mRx = monthlyTraffic.downloadBytes
        }
        var monthlyRaw = monthlyRawBase
        if (monthlyRaw <= 0L) {
            monthlyRaw = DevicePrefs.getCachedMonthlyData(context)
        } else {
            DevicePrefs.setCachedMonthlyData(context, monthlyRaw)
        }
        // 随身 WiFi goform 系不展示上下行拆分；通用版有可信拆分才展示
        val showTrafficSplit = !portableUfi && (dTx >= 0L || dRx >= 0L || mTx >= 0L || mRx >= 0L)

        // 存储：base / goform 均可；数值字节或 "784.4M" 字符串
        val internalTotal = firstPositiveStorage(
            base, goformInfo, "internal_total_storage",
        )
        val internalUsed = firstPositiveStorage(
            base, goformInfo, "internal_used_storage",
        )
        val internalAvailable = firstPositiveStorage(
            base, goformInfo, "internal_available_storage",
        )
        val externalTotal = firstPositiveStorage(
            base, goformInfo, "external_total_storage",
        )
        val externalUsed = firstPositiveStorage(
            base, goformInfo, "external_used_storage",
        )
        val externalAvailable = firstPositiveStorage(
            base, goformInfo, "external_available_storage",
        )

        // 地址/版本（goform 优先，base/cell 兜底）
        val wanIp = goformInfo?.optString("wan_ipaddr", "")?.ifBlank { null }
            ?: base.optString("wan_ipaddr", "")
        val wanIpv6 = goformInfo?.optString("ipv6_wan_ipaddr", "")?.ifBlank { null }
            ?: base.optString("ipv6_wan_ipaddr", "")
        val imsi = goformInfo?.optString("imsi", "")?.ifBlank { null }
            ?: cellInfo?.optString("imsi", "")?.trim().orEmpty()
        val hwVersion = goformInfo?.optString("hardware_version", "") ?: ""
        val webVersion = goformInfo?.optString("web_version", "") ?: ""
        val macAddr = goformInfo?.optString("mac_address", "") ?: ""

        // 实时速率：goform 与 base 均可（E5 base 直接带 realtime_*_thrpt）
        val rxSpeed = resolveRealtimeSpeed(
            goformInfo, base,
            "realtime_rx_thrpt", "realtime_rx_speed", "real_rx_speed",
        )
        val txSpeed = resolveRealtimeSpeed(
            goformInfo, base,
            "realtime_tx_thrpt", "realtime_tx_speed", "real_tx_speed",
        )

        // 信号（已由 mergeSignalSources 融合 goform / base / currentCellInfo）
        val netType = mapNetworkType(
            signalInfo?.optString("network_type")?.ifBlank { null }
                ?: base.optString("network_type", ""),
        )
        val rssiRaw = signalInfo?.optString("rssi")?.ifBlank { null }
        val rssiNumber = rssiRaw?.toIntOrNull()
        // U30/F50Pro 等同系：可能只有 Z5g 或只有 lte，或 network_rssi；勿因制式名过滤掉唯一有效值
        val goformSignalRaw = when {
            netType.contains("5G", ignoreCase = true) ->
                signalInfo?.optString("Z5g_rsrp")?.ifBlank { null }
                    ?: signalInfo?.optString("nr5g_rsrp")?.ifBlank { null }
                    ?: signalInfo?.optString("lte_rsrp")?.ifBlank { null }
            else ->
                signalInfo?.optString("lte_rsrp")?.ifBlank { null }
                    ?: signalInfo?.optString("Z5g_rsrp")?.ifBlank { null }
                    ?: signalInfo?.optString("nr5g_rsrp")?.ifBlank { null }
        } ?: signalInfo?.optString("network_rssi")?.ifBlank { null }
            ?: rssiRaw?.takeUnless { rssiNumber in 0..5 }
            ?: "--"
        val signalInt = goformSignalRaw.toIntOrNull()
        val rsrp = when {
            signalInt == null -> null
            signalInt > 0 -> -signalInt
            else -> signalInt
        }
        val signalStr = when {
            goformSignalRaw.isEmpty() || goformSignalRaw == "null" || goformSignalRaw == "--" -> "--"
            rsrp != null -> "${rsrp}dBm"
            else -> "${goformSignalRaw}dBm"
        }
        val signalBar = signalInfo?.optString("network_signalbar")?.toIntOrNull()
            ?.takeIf { it in 0..5 }
            ?: rssiNumber?.takeIf { it in 0..5 }
            ?: -1
        val qci = signalInfo?.optString("qci", "")?.ifBlank { null }
            ?: qosInfo?.qci
            ?: ""
        val ambrDl = signalInfo?.optString("ambr_dl_max", "")?.ifBlank { null }
        val ambrUl = signalInfo?.optString("ambr_ul_max", "")?.ifBlank { null }
        val ambr = when {
            ambrDl != null || ambrUl != null -> "${ambrDl ?: "--"}/${ambrUl ?: "--"}"
            qosInfo != null -> qosInfo.rate
            else -> "--"
        }
        val sinr5g = signalInfo?.optString("Nr_snr")?.ifBlank { null }
            ?: signalInfo?.optString("nr5g_snr")?.ifBlank { null }
            ?: signalInfo?.optString("Z5g_SINR")?.ifBlank { null }
        val sinrLte = signalInfo?.optString("Lte_snr")?.ifBlank { null }
            ?: signalInfo?.optString("lte_snr")?.ifBlank { null }
        val sinrRaw = if (netType.contains("5G")) sinr5g ?: sinrLte else sinrLte ?: sinr5g
        val sinr = sinrRaw?.let { "${it}dB" } ?: "--"
        val bandRaw = signalInfo?.optString("nr5g_action_band")?.ifBlank { null }
            ?: signalInfo?.optString("wan_active_band")?.ifBlank { null }
            ?: signalInfo?.optString("Nr_bands")?.ifBlank { null }
            ?: signalInfo?.optString("Lte_bands")?.ifBlank { null }
            ?: signalInfo?.optString("lte_ca_pcell_band")?.ifBlank { null }
            ?: "--"
        val band = normalizeBand(bandRaw, netType)
        val caStatus = parseCaStatus(signalInfo, netType)


        // carrier
        val goformProvider = signalInfo?.optString("network_provider_number")?.ifBlank { null }
            ?: signalInfo?.optString("network_provider")?.ifBlank { null }
            ?: base.optString("network_provider", "").ifBlank { null }
        val carrier = goformProvider?.let { translateCarrier(it) }?.ifBlank { null }
            ?: mapPlmnToCarrier(imsi).ifBlank { null }
            ?: mapPlmnToCarrier(cellInfo?.optString("imsi") ?: "").ifBlank { null }
            ?: ""

        // 运营商

        // 版本：固件版本走 goform，UFI-TOOLS 版本（app_ver）走 /api/baseDeviceInfo，两者独立
        // 展示名：商品名（如 MU5358→U30 Pro）优先于 versionInfo.model，接口原文仍写在 model
        val deviceModel = portableUfiProductName(model)
            ?: versionInfo?.optString("model", "")?.ifBlank { null }
            ?: model
        val firmwareVer = goformInfo?.optString("cr_version", "")?.ifBlank { null }
            ?: goformInfo?.optString("wa_inner_version", "")?.ifBlank { null }
            ?: base.optString("cr_version", "").ifBlank { null }
            ?: versionInfo?.optString("cr_version", "")
            ?: ""

        // 电池温度：goform battery_temperature 优先（如 "28" 表示 28℃），cpu_temp_list 中 type=battery 兜底
        val batteryTempRaw = goformInfo?.optString("battery_temperature", "")?.trim()?.toDoubleOrNull()
            ?: cpuTempList.firstOrNull { it.type.equals("battery", ignoreCase = true) }?.temp


        // WiFi 连接数（goform wifi_access_sta_num，兼容 station_number/sta_count/user_number，baseDeviceInfo 兜底）
        val wifiCount = sequenceOf(
            goformInfo?.optString("wifi_access_sta_num", ""),
            goformInfo?.optString("station_number", ""),
            goformInfo?.optString("sta_count", ""),
            goformInfo?.optString("user_number", ""),
            base.optString("wifi_user_count", ""),
            base.optString("station_number", ""),
        ).mapNotNull { it?.trim()?.toIntOrNull() }.firstOrNull { it >= 0 } ?: -1

        // 通用版双卡：当前数据卡槽（探测/base.sim_slot）
        val activeSim = DevicePrefs.getActiveSimSlot(context).toIntOrNull()
            ?.takeIf { it in 0..3 }
            ?: normalizeSimSlot(base.opt("sim_slot"))?.toIntOrNull()
            ?: -1
        val showSim = !isF50

        return DeviceStatus(
            model = model,
            deviceModel = deviceModel,
            firmwareVer = firmwareVer,
            appVer = appVer,
            webVersion = webVersion.ifBlank { "--" },
            hardwareVersion = hwVersion.ifBlank { "--" },
            signal = signalStr,
            netType = netType.ifBlank { "--" },
            carrier = carrier,
            activeSimSlot = activeSim,
            showSimSlot = showSim,
            rsrp = rsrp,
            signalBar = signalBar,
            qci = qci.ifBlank { "--" },
            ambr = ambr,
            sinr = sinr,
            band = band,
            caStatus = caStatus,
            rxSpeed = rxSpeed,
            txSpeed = txSpeed,
            temp = formatTemp(tempRaw),
            cpuTempList = cpuTempList,
            cpu = String.format(Locale.getDefault(), "%.1f%%", finalCpuUsage),
            cpuUsageInfo = cpuUsageMap,
            cpuFreqInfo = cpuFreqMap,
            mem = String.format(Locale.getDefault(), "%.1f%%", memUsage),
            memTotalKb = memTotalKb,
            memAvailableKb = memAvailableKb,
            memUsedKb = memUsedKb,
            swapTotalKb = swapTotalKb,
            swapUsedKb = swapUsedKb,
            dailyFlow = formatFlow(dailyRaw),
            monthlyFlow = formatFlow(monthlyRaw),
            dailyRawBytes = dailyRaw,
            monthlyRawBytes = monthlyRaw,
            // 上下行未知/不可信时为 -1；F50 由 showTrafficSplit=false 在 UI 侧直接屏蔽
            dailyUploadBytes = dTx,
            dailyDownloadBytes = dRx,
            monthlyUploadBytes = mTx,
            monthlyDownloadBytes = mRx,
            showTrafficSplit = showTrafficSplit,
            battery = when {
                isF50 -> "外接供电"
                batteryPercent >= 0 -> "${batteryPercent}%"
                else -> "--"
            },
            batteryPercent = if (isF50) -1 else batteryPercent,
            batteryCurrent = if (isF50) "无电池" else formatCurrent(currentNow),
            batteryVoltage = if (isF50) "无电池" else formatVoltage(voltageNow),
            batteryTemp = if (isF50) {
                "无电池"
            } else {
                batteryTempRaw?.let { formatBatteryTemp(it) } ?: "--"
            },
            hasBattery = !isF50,
            // ⚠ 充电状态判定不可修改：固定使用 baseDeviceInfo 的 current_now > 50 000 µA 判断，勿改用 goform battery_charging
            charging = !isF50 && currentNow > 50_000,
            wifiCount = wifiCount,
            internalStorage = formatStorage(internalTotal, internalUsed),
            internalTotalStorage = internalTotal,
            internalUsedStorage = internalUsed,
            internalAvailableStorage = internalAvailable,
            externalTotalStorage = externalTotal,
            externalUsedStorage = externalUsed,
            externalAvailableStorage = externalAvailable,
            clientIp = base.optString("client_ip", "").ifBlank { "--" },
            wanIp = wanIp.ifBlank { "--" },
            wanIpv6 = wanIpv6.ifBlank { "--" },
            macAddress = macAddr.ifBlank { "--" },
            updateTime = System.currentTimeMillis(),
        )
    }

    private fun parseCaStatus(signalInfo: JSONObject?, netType: String): String {
        if (signalInfo == null) return "--"

        val lteBands = linkedSetOf<String>()
        addSimpleBands(lteBands, signalInfo.optString("lte_ca_pcell_band"), "B")
        addSimpleBands(lteBands, signalInfo.optString("lte_ca_scell_band"), "B")
        addStructuredBands(lteBands, signalInfo.optString("lte_ca_scell_info"), 2, "B")
        addStructuredBands(lteBands, signalInfo.optString("lte_multi_ca_scell_info"), 3, "B")
        signalInfo.optString("lteca")
            .takeIf { it.contains("b", ignoreCase = true) || Regex("""\d{1,3}[A-Ea-e]""").containsMatchIn(it) }
            ?.let { addSimpleBands(lteBands, it, "B") }
        if (lteBands.isEmpty() && !netType.contains("5G SA", ignoreCase = true)) {
            addSimpleBands(lteBands, signalInfo.optString("Lte_bands"), "B")
            signalInfo.optString("wan_active_band")
                .takeUnless { it.contains("n", ignoreCase = true) }
                ?.let { addSimpleBands(lteBands, it, "B") }
        }

        val nrBands = linkedSetOf<String>()
        addSimpleBands(nrBands, signalInfo.optString("nr_ca_pcell_band"), "n")
        addSimpleBands(nrBands, signalInfo.optString("nr5g_action_band"), "n")
        addStructuredBands(nrBands, signalInfo.optString("nr_multi_ca_scell_info"), 3, "n")
        signalInfo.optString("nrca")
            .takeIf { it.contains("n", ignoreCase = true) }
            ?.let { addSimpleBands(nrBands, it, "n") }
        if (nrBands.isEmpty() && netType.contains("5G", ignoreCase = true)) {
            addSimpleBands(nrBands, signalInfo.optString("Nr_bands"), "n")
            signalInfo.optString("wan_active_band")
                .takeIf { it.contains("n", ignoreCase = true) }
                ?.let { addSimpleBands(nrBands, it, "n") }
        }

        val parts = buildList {
            if (lteBands.isNotEmpty()) add("4G ${lteBands.joinToString("+")}")
            if (nrBands.isNotEmpty()) add("5G ${nrBands.joinToString("+")}")
        }
        if (parts.isNotEmpty()) return parts.joinToString("\n")

        val lteCaActive = signalInfo.optInt("lteca_state", 0) > 0 ||
            signalInfo.optString("wan_lte_ca").equals("ca_activated", ignoreCase = true) ||
            signalInfo.optString("Lte_ca_status").let {
                it == "1" || it.equals("ca_activated", ignoreCase = true) ||
                    it.equals("active", ignoreCase = true)
            }
        val nrCaActive = signalInfo.optString("nrca").isNotBlank()
        return when {
            lteCaActive && nrCaActive -> "4G CA · 5G CA"
            nrCaActive -> "5G CA"
            lteCaActive -> "4G CA"
            netType.contains("5G", ignoreCase = true) -> "5G 未聚合"
            netType.contains("4G", ignoreCase = true) || netType.contains("LTE", ignoreCase = true) -> "4G 未聚合"
            else -> "未聚合"
        }
    }

    private data class QosInfo(
        val qci: String,
        val rate: String,
    )

    private suspend fun fetchQosInfo(context: Context, auth: String): QosInfo? {
        for (slot in 0..1) {
            val command = java.net.URLEncoder.encode("AT+CGEQOSRDP=1", "UTF-8")
            val result = fetchApi(
                context,
                "/api/AT?command=$command&slot=$slot",
                System.currentTimeMillis(),
                auth,
            )?.optString("result")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            if (result.contains("ERROR", ignoreCase = true)) continue
            parseQosInfo(result)?.let { return it }
        }
        return null
    }

    private fun parseQosInfo(raw: String): QosInfo? {
        val values = Regex("""\+CGEQOSRDP:\s*(.+?)(?:\s+OK|$)""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(',')
            ?.map { it.trim() }
            ?: return null
        if (values.size < 8) return null
        val qci = values[1].takeIf { it.isNotBlank() } ?: return null
        val down = values[6].toDoubleOrNull()?.div(1000.0)
        val up = values[7].toDoubleOrNull()?.div(1000.0)
        val rate = if (down != null || up != null) {
            "${formatMbps(down)}/${formatMbps(up)}"
        } else {
            "--"
        }
        return QosInfo(qci, rate)
    }

    private fun formatMbps(value: Double?): String {
        if (value == null) return "--"
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)
    }

    private fun normalizeBand(raw: String, netType: String): String {
        val value = raw.trim()
        if (value.isEmpty() || value == "--") return "--"
        if (value.startsWith("n", ignoreCase = true) ||
            value.startsWith("B", ignoreCase = true)
        ) {
            return value
        }
        return when {
            netType.contains("5G", ignoreCase = true) -> "n$value"
            netType.contains("4G", ignoreCase = true) ||
                netType.contains("LTE", ignoreCase = true) -> "B$value"
            else -> value
        }
    }

    private fun addStructuredBands(
        target: MutableSet<String>,
        raw: String,
        bandIndex: Int,
        prefix: String,
    ) {
        raw.split(';').forEach { cell ->
            val parts = cell.split(',')
            parts.getOrNull(bandIndex)?.let { addSimpleBands(target, it, prefix) }
        }
    }

    private fun addSimpleBands(target: MutableSet<String>, raw: String, prefix: String) {
        val normalized = raw.trim()
        if (normalized.isEmpty() || normalized == "0" || normalized.equals("null", ignoreCase = true)) return

        val explicitPattern = if (prefix == "n") {
            Regex("""(?i)\bn\s*(\d{1,3})\b""")
        } else {
            Regex("""(?i)\b(?:band|b)\s*(\d{1,3})\b""")
        }
        val explicit = explicitPattern.findAll(normalized).map { it.groupValues[1] }.toList()
        val numbers = if (explicit.isNotEmpty()) {
            explicit
        } else {
            Regex("""\d{1,3}""").findAll(normalized)
                .map { it.value }
                .take(8)
                .toList()
        }
        numbers.mapNotNull { it.toIntOrNull() }
            .filter { it in 1..261 }
            .forEach { target.add("$prefix$it") }
    }

    // ── HTTP ──

    private data class HttpResponse(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String,
        val kanoCookie: String?,
    )

    /**
     * 所有设备请求的统一出口。
     *
     * Android 17（API 37）起未授予「本地网络」权限时，发往局域网地址的连接会被静默拦截、
     * 只能等到超时，故在此直接短路，避免短信列表/发送/删除等路径每次都白等十几秒。
     */
    private suspend fun executeRequest(context: Context, req: Request): HttpResponse? {
        if (LocalNetworkPermission.blocksCurrentDevice(context)) {
            lastError = LocalNetworkPermission.HINT
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val call = NetClient.client.newCall(req)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val body = it.body?.string() ?: ""
                            if (continuation.isActive) {
                                continuation.resume(
                                    HttpResponse(
                                        code = it.code,
                                        isSuccessful = it.isSuccessful,
                                        body = body,
                                        kanoCookie = it.headers.values("kano-cookie")
                                            .firstOrNull()
                                            ?.substringBefore(';')
                                            ?.trim(),
                                    ),
                                )
                            }
                        }
                    } catch (_: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
    }

    private suspend fun fetchApi(
        context: Context,
        path: String,
        t: Long,
        auth: String,
        goformCookie: String = "",
    ): JSONObject? {
        val purePath = path.substringBefore("?")
        val sign = NetClient.generateKanoSign("GET", purePath, t, DevicePrefs.getSecretKey(context))
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("kano-t", t.toString())
            .addHeader("kano-sign", sign)
            .addHeader("Authorization", auth)
        if (goformCookie.isNotEmpty()) {
            builder.addHeader("Cookie", goformCookie)
            builder.addHeader("kano-cookie", goformCookie)
        }
        DevicePrefs.getDeviceToken(context).takeIf { it.isNotEmpty() }
            ?.let { builder.addHeader("X-Device-Token", it) }

        val resp = executeRequest(context, builder.build()) ?: run {
            // 被本地网络权限拦截时 lastError 已写明原因，不要覆盖成网络错误
            if (lastError != LocalNetworkPermission.HINT) {
                lastError = "Network error (timeout or unreachable)"
            }
            return null
        }
        return if (resp.isSuccessful && resp.body.isNotEmpty()) {
            try {
                JSONObject(resp.body)
            } catch (e: org.json.JSONException) {
                lastError = "JSON parse error on $purePath: ${e.message}"
                null
            }
        } else {
            lastError = "HTTP ${resp.code} on $purePath"
            null
        }
    }

    private suspend fun fetchGoformApi(
        context: Context,
        path: String,
        t: Long,
        auth: String,
        vararg expectedKeys: String,
    ): JSONObject? {
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        val scope = goformScope(path)
        val variants = linkedMapOf(
            "is_all_v4" to currentGoformPath(path),
            "direct_v4" to directGoformPath(path),
            "multi_data_v4" to legacyGoformPath(path),
        )
        val cachedMode = DevicePrefs.getGoformCompatMode(context, baseUrl, scope)
            .takeIf { variants.containsKey(it) }
        val requestedMode = when {
            path.contains("multi_data=1") -> "multi_data_v4"
            path.contains("is_all=true") -> "is_all_v4"
            else -> "direct_v4"
        }
        var cookie = DevicePrefs.getGoformCookie(context, baseUrl)

        suspend fun findBest(goformCookie: String, preferredMode: String?): Pair<String, JSONObject?> {
            var bestMode = preferredMode.orEmpty()
            var bestJson: JSONObject? = null
            var bestGroupScore = -1
            var bestScore = -1
            var bestSize = -1
            val requiredGroupCount = goformRequiredGroupCount(expectedKeys)
            val cachedMinimumScore = when {
                expectedKeys.size <= 2 -> 1
                else -> minOf(5, (expectedKeys.size + 2) / 3)
            }
            val orderedModes = buildList {
                preferredMode?.let(::add)
                variants.keys.filterNot { it == preferredMode }.forEach(::add)
            }
            for ((index, mode) in orderedModes.withIndex()) {
                val response = fetchApi(
                    context,
                    variants.getValue(mode),
                    if (index == 0) t else System.currentTimeMillis(),
                    auth,
                    goformCookie,
                )
                val groupScore = goformRequiredGroupScore(response, expectedKeys)
                val score = goformDataScore(response, expectedKeys)
                val size = response?.length() ?: 0
                if (groupScore > bestGroupScore ||
                    groupScore == bestGroupScore && score > bestScore ||
                    groupScore == bestGroupScore && score == bestScore && size > bestSize
                ) {
                    bestGroupScore = groupScore
                    bestScore = score
                    bestSize = size
                    bestMode = mode
                    bestJson = response
                }
                if (preferredMode != null && index == 0 &&
                    score >= cachedMinimumScore && groupScore == requiredGroupCount
                ) {
                    break
                }
                if (score == expectedKeys.size && expectedKeys.isNotEmpty() &&
                    groupScore == requiredGroupCount
                ) {
                    break
                }
            }
            return bestMode to bestJson
        }

        var (bestMode, bestJson) = findBest(cookie, cachedMode ?: requestedMode)
        if (!hasExpectedGoformData(bestJson, expectedKeys)) {
            cookie = loginOfficialBackend(context, auth, cookie)
            if (cookie.isNotEmpty()) {
                val authenticated = findBest(cookie, null)
                if (goformDataScore(authenticated.second, expectedKeys) >
                    goformDataScore(bestJson, expectedKeys)
                ) {
                    bestMode = authenticated.first
                    bestJson = authenticated.second
                }
            }
        }
        if (hasExpectedGoformData(bestJson, expectedKeys)) {
            DevicePrefs.setGoformCompatMode(context, baseUrl, bestMode, scope)
        }
        return bestJson
    }

    private fun hasExpectedGoformData(json: JSONObject?, expectedKeys: Array<out String>): Boolean {
        return goformDataScore(json, expectedKeys) > 0
    }

    private fun goformDataScore(json: JSONObject?, expectedKeys: Array<out String>): Int {
        if (json == null || json.has("error")) return 0
        if (expectedKeys.isEmpty()) return 1
        return expectedKeys.count { key ->
            json.has(key) && json.optString(key).let {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            }
        }
    }

    private fun goformRequiredGroupCount(expectedKeys: Array<out String>): Int {
        var count = 0
        if (expectedKeys.any { it in GOFORM_SINR_KEYS }) count++
        if (expectedKeys.any { it in GOFORM_BAND_KEYS }) count++
        return count
    }

    private fun goformRequiredGroupScore(
        json: JSONObject?,
        expectedKeys: Array<out String>,
    ): Int {
        if (json == null) return 0
        var score = 0
        if (expectedKeys.any { it in GOFORM_SINR_KEYS } &&
            GOFORM_SINR_KEYS.any { hasGoformValue(json, it) }
        ) {
            score++
        }
        if (expectedKeys.any { it in GOFORM_BAND_KEYS } &&
            GOFORM_BAND_KEYS.any { hasGoformValue(json, it) }
        ) {
            score++
        }
        return score
    }

    private fun hasGoformValue(json: JSONObject, key: String): Boolean =
        json.has(key) && json.optString(key).let {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }

    private fun goformScope(path: String): String {
        val cmd = path.substringAfter("cmd=", "").substringBefore('&')
        return when {
            cmd.contains("sms_data_total") -> "sms"
            cmd == "LD" || cmd == "RD" -> "auth"
            cmd.contains("network_type") || cmd.contains("lte_rsrp") || cmd.contains("Z5g_rsrp") -> "radio"
            else -> "device"
        }
    }

    private fun legacyGoformPath(path: String): String {
        val purePath = path.substringBefore("?")
        val query = path.substringAfter("?", "")
        val parameters = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore('=')
                key == "is_all" || key == "multi_data" || key == "isTest" || key == "_"
            }
            .map {
                if (!query.contains("cmd=sms_data_total")) return@map it
                when (it.substringBefore('=')) {
                    "mem_store" -> "mem_store=1"
                    "tags" -> "tags=100"
                    else -> it
                }
            }
        return buildString {
            append(purePath)
            append("?multi_data=1&isTest=false")
            if (parameters.isNotEmpty()) {
                append('&')
                append(parameters.joinToString("&"))
            }
            append("&_=")
            append(System.currentTimeMillis())
        }
    }

    private fun currentGoformPath(path: String): String {
        val purePath = path.substringBefore("?")
        val query = path.substringAfter("?", "")
        val parameters = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore('=')
                key == "is_all" || key == "multi_data" || key == "isTest" || key == "_"
            }
            .map {
                if (!query.contains("cmd=sms_data_total")) return@map it
                when (it.substringBefore('=')) {
                    "mem_store" -> "mem_store=0"
                    "tags" -> "tags=10"
                    else -> it
                }
            }
        return buildString {
            append(purePath)
            append("?is_all=true")
            if (parameters.isNotEmpty()) {
                append('&')
                append(parameters.joinToString("&"))
            }
            append("&_=")
            append(System.currentTimeMillis())
        }
    }

    private fun directGoformPath(path: String): String {
        val purePath = path.substringBefore("?")
        val query = path.substringAfter("?", "")
        val parameters = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore('=')
                key == "is_all" || key == "multi_data" || key == "isTest" || key == "_"
            }
        return buildString {
            append(purePath)
            append("?isTest=false")
            if (parameters.isNotEmpty()) {
                append('&')
                append(parameters.joinToString("&"))
            }
            append("&_=")
            append(System.currentTimeMillis())
        }
    }

    private suspend fun loginOfficialBackend(
        context: Context,
        auth: String,
        failedCookie: String,
    ): String = goformLoginMutex.withLock {
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        val savedCookie = DevicePrefs.getGoformCookie(context, baseUrl)
        if (savedCookie.isNotEmpty() && savedCookie != failedCookie) {
            return@withLock savedCookie
        }
        DevicePrefs.setGoformCookie(context, baseUrl, "")

        val sharedCookie = fetchApi(
            context,
            "/api/get_cookie",
            System.currentTimeMillis(),
            auth,
        )?.optString("cookie").orEmpty().substringBefore(';').trim()
        if (sharedCookie.isNotEmpty() && sharedCookie != failedCookie) {
            DevicePrefs.setGoformCookie(context, baseUrl, sharedCookie)
            return@withLock sharedCookie
        }

        val officialPasswordHash = fetchApi(
            context,
            "/api/get_official_web_password",
            System.currentTimeMillis(),
            auth,
        )?.optString("pwd")
            ?.takeIf { it.isNotBlank() }
            ?.let { NetClient.sha256(it).uppercase(Locale.US) }
            ?: auth.uppercase(Locale.US)
        if (officialPasswordHash.isEmpty()) return@withLock ""

        val ldPath = legacyGoformPath("${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?cmd=LD")
        val ld = fetchApi(context, ldPath, System.currentTimeMillis(), auth)
            ?.optString("LD")
            ?.takeIf { it.isNotBlank() }
            ?: return@withLock ""
        val password = NetClient.sha256(officialPasswordHash + ld).uppercase(Locale.US)

        val loginForms = listOf(
            "goformId=LOGIN&isTest=false&password=$password&user=admin",
            "goformId=LOGIN_MULTI_USER&isTest=false&password=$password&IP=localhost&user=admin",
        )
        for (form in loginForms) {
            val response = postGoformRaw(context, form, auth, "")
            val cookie = response?.kanoCookie.orEmpty()
            val accepted = response?.body?.let { body ->
                runCatching {
                    JSONObject(body).optString("result").let { it != "3" && it.isNotBlank() }
                }.getOrDefault(false)
            } ?: false
            if (accepted && cookie.isNotEmpty()) {
                DevicePrefs.setGoformCookie(context, baseUrl, cookie)
                return@withLock cookie
            }
        }
        ""
    }

    private suspend fun postGoformRaw(
        context: Context,
        form: String,
        auth: String,
        goformCookie: String,
    ): HttpResponse? {
        val t = System.currentTimeMillis()
        val path = DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH
            .replace("goform_get_cmd_process", "goform_set_cmd_process")
        val sign = NetClient.generateKanoSign("POST", path, t, DevicePrefs.getSecretKey(context))
        val body = form.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val builder = Request.Builder()
            .url("${DevicePrefs.buildBaseUrl(context)}$path")
            .post(body)
            .addHeader("kano-t", t.toString())
            .addHeader("kano-sign", sign)
            .addHeader("Authorization", auth)
        if (goformCookie.isNotEmpty()) {
            builder.addHeader("Cookie", goformCookie)
            builder.addHeader("kano-cookie", goformCookie)
        }
        DevicePrefs.getDeviceToken(context).takeIf { it.isNotEmpty() }
            ?.let { builder.addHeader("X-Device-Token", it) }
        return executeRequest(context, builder.build())
    }

    private suspend fun fetchApiNoAuth(context: Context, path: String, t: Long): JSONObject? {
        val sign = NetClient.generateKanoSign("GET", path, t, DevicePrefs.getSecretKey(context))
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        val req = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("kano-t", t.toString())
            .addHeader("kano-sign", sign)
            .build()
        val resp = executeRequest(context, req) ?: return null
        return if (resp.isSuccessful && resp.body.isNotEmpty()) {
            try {
                JSONObject(resp.body)
            } catch (_: org.json.JSONException) {
                null
            }
        } else null
    }

    /**
     * device_token 由免鉴权的 /api/need_token 公开返回，
     * 必须在鉴权请求前就绪，否则 login_token_enabled 的设备会返回 401。
     */
    private suspend fun ensureDeviceToken(t: Long, context: Context): String {
        DevicePrefs.getDeviceToken(context).takeIf { it.isNotEmpty() }?.let { return it }
        val resp = fetchApiNoAuth(context, DevicePrefs.DEFAULT_NEED_TOKEN_PATH, t) ?: return ""
        val token = resp.optString("device_token", "")
        if (token.isNotEmpty()) DevicePrefs.setDeviceToken(context, token)
        return token
    }

    /** 连接测试结果（用于连接配置页展示） */
    data class ConnectionTestResult(
        val ok: Boolean,
        val model: String = "",
        val message: String = "",
    )

    /** 连接测试：请求免鉴权的 /api/need_token 验证设备可达 */
    suspend fun testConnection(context: Context): Boolean = testConnectionDetailed(context).ok

    /**
     * 详细连接测试：
     * 1) 探活 need_token
     * 2) 再拉 baseDeviceInfo（可校验密码并拿到型号）
     */
    suspend fun testConnectionDetailed(context: Context): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            try {
                lastError = ""
                if (LocalNetworkPermission.blocksCurrentDevice(context)) {
                    return@withContext ConnectionTestResult(
                        ok = false,
                        message = "${LocalNetworkPermission.HINT}，请授予后重试",
                    )
                }
                val t = System.currentTimeMillis()
                fetchApiNoAuth(context, DevicePrefs.DEFAULT_NEED_TOKEN_PATH, t)
                    ?: return@withContext ConnectionTestResult(
                        ok = false,
                        message = lastError.ifBlank { "设备无响应，请检查地址与网络" },
                    )

                ensureDeviceToken(t, context)
                val auth = DevicePrefs.getAuthToken(context)
                val base = fetchApi(context, DevicePrefs.DEFAULT_DEVICE_INFO_PATH, t, auth)
                if (base != null) {
                    val model = base.optString("model")
                        .ifBlank { base.optString("device_model") }
                        .trim()
                    if (model.isNotEmpty()) {
                        DevicePrefs.setCachedModel(context, model)
                    }
                    return@withContext ConnectionTestResult(
                        ok = true,
                        model = model,
                        message = if (model.isNotEmpty()) "连接成功 · $model" else "连接成功",
                    )
                }

                // 设备可达但鉴权/状态接口失败
                val err = lastError
                return@withContext if (auth.isNotEmpty() &&
                    (err.contains("401") || err.contains("403") || err.contains("鉴权") ||
                        err.contains("token", ignoreCase = true) || err.contains("password", ignoreCase = true))
                ) {
                    ConnectionTestResult(
                        ok = false,
                        message = "设备可达，但鉴权失败，请检查访问密码",
                    )
                } else {
                    ConnectionTestResult(
                        ok = true,
                        message = "设备可达（部分接口暂未通过，可稍后在仪表盘刷新）",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionTestResult(
                    ok = false,
                    message = e.message?.takeIf { it.isNotBlank() } ?: "连接失败",
                )
            }
        }

    // ── 短信（SMS） ──

    /** 一条短信 */
    data class SmsMessage(
        val id: String,
        val number: String,
        val content: String,
        /** 设备返回的原始时间串（如 "26,07,04,17,00,39,+32"），无法解析时用于兜底展示 */
        val date: String,
        /** 解析后的 epoch 毫秒，-1 表示无法解析 */
        val timestamp: Long,
        val unread: Boolean,
        /** 是否为本机发出的短信（ZTE tag 2/4/5） */
        val outgoing: Boolean,
        /**
         * SIM 槽位（通用版双卡）：0=卡1，1=卡2；-1=未知/单卡设备未标注。
         * 来自 /api/get_sms 的 sim_slot 字段。
         */
        val simSlot: Int = -1,
    )

    /**
     * 短信列表查询路径。UFI-TOOLS 官方前端用 mem_store=1&tags=100（U60Pro 用
     * mem_store=-1&tags=10 会返回空列表），保留旧参数作为兜底。
     */
    private fun smsListPath(memStore: Int = 1, tags: Int = 100): String {
        val orderBy = java.net.URLEncoder.encode("order by id desc", "UTF-8")
        return "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?cmd=sms_data_total&page=0&data_per_page=500&mem_store=$memStore&tags=$tags&order_by=$orderBy"
    }

    /**
     * 判定一条短信是否未读。ZTE 约定 `tag`：0=已读,1=未读,2=已发送,3=发送失败,4=发送中,5=草稿。
     * 兼容部分固件的 `read`/`flag` 字段（0/false=未读）。
     */
    private fun isUnread(m: JSONObject): Boolean {
        if (m.has("tag")) return m.optString("tag").trim() == "1"
        if (m.has("read")) {
            val r = m.optString("read").trim()
            return r == "0" || r.equals("false", ignoreCase = true)
        }
        if (m.has("flag")) return m.optString("flag").trim() == "1"
        return false
    }

    /** 是否为本机发出（ZTE tag：2=已发送,4=发送中,5=草稿） */
    private fun isOutgoing(m: JSONObject): Boolean =
        m.optString("tag").trim() in setOf("2", "4", "5")

    /**
     * 解析短信时间为 epoch 毫秒。无法解析返回 -1。
     *
     * ZTE / 3GPP 常见串：`"YY,MM,DD,HH,MM,SS,+TZ"`（`,` 或 `;` 分隔）。
     * 前 6 段为该时区下的墙上时间，第 7 段 TZ 为 **15 分钟刻度**（`+32` = UTC+8）。
     * goform 短信（F50 及大量同接口机型）与官方 Web 均按此语义；
     * **不要**再按 `model==F50` 强制当 UTC——会在东八区多显示 8 小时。
     * 无 TZ 时按手机本地时区；亦兼容 epoch 毫秒/秒与常见 ISO 串（UFI `/api/get_sms`）。
     */
    fun parseSmsTimestamp(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return -1L

        // 纯数字：epoch 毫秒或秒
        s.toLongOrNull()?.let { n ->
            return when {
                n >= 1_000_000_000_000L -> n
                n >= 1_000_000_000L -> n * 1000L
                else -> -1L
            }
        }

        // ISO-8601 类（通用版部分固件）
        parseIsoLikeSmsTime(s)?.let { return it }

        val p = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (p.size < 6) return -1L
        return try {
            val yy = p[0].toInt()
            val year = if (yy < 100) 2000 + yy else yy
            val zone = parseSctsTimeZone(p.getOrNull(6))
            val cal = java.util.Calendar.getInstance(zone)
            cal.clear()
            cal.set(
                year,
                p[1].toInt() - 1,
                p[2].toInt(),
                p[3].toInt(),
                p[4].toInt(),
                p[5].toInt(),
            )
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * 3GPP SCTS 时区：有符号整数，单位 15 分钟。
     * 例：`+32` / `32` → UTC+8；`-16` → UTC-4。缺省用本机时区。
     */
    private fun parseSctsTimeZone(raw: String?): java.util.TimeZone {
        if (raw.isNullOrBlank()) return java.util.TimeZone.getDefault()
        val t = raw.trim()
        val sign = when {
            t.startsWith('-') -> -1
            else -> 1
        }
        val digits = t.trimStart('+', '-').filter { it.isDigit() }.toIntOrNull()
            ?: return java.util.TimeZone.getDefault()
        val offsetMs = sign * digits * 15 * 60 * 1000
        return java.util.SimpleTimeZone(offsetMs, "SCTS")
    }

    /** 宽松解析 ISO 时间（含空格分隔、可选 Z/偏移） */
    private fun parseIsoLikeSmsTime(raw: String): Long? {
        val s = raw.trim()
        if (!s.contains('T') && !s.contains('-')) return null
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
        )
        for (p in patterns) {
            try {
                val fmt = java.text.SimpleDateFormat(p, Locale.US)
                // 无显式偏移的格式按本机时区（与设备墙上时间一致）
                if (!p.contains("XXX") && !p.contains("'Z'")) {
                    fmt.timeZone = java.util.TimeZone.getDefault()
                } else if (p.contains("'Z'")) {
                    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                fmt.isLenient = true
                return fmt.parse(s)?.time
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    /** 中文 12 小时制时间（如 "下午3:30"、"上午9:05"），仿 MIUI 短信 */
    private fun formatCnClock(cal: java.util.Calendar): String {
        val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (hour24 < 12) "上午" else "下午"
        var hour12 = hour24 % 12
        if (hour12 == 0) hour12 = 12
        return String.format(Locale.getDefault(), "%s%d:%02d", ampm, hour12, minute)
    }

    /** 列表用的简短日期（今天=下午3:30，昨天=昨天 下午3:30，今年=M月d日，其余=yyyy/M/d） */
    fun formatSmsDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = java.util.Calendar.getInstance()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameYear = now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
        val dayDiff = if (sameYear) {
            now.get(java.util.Calendar.DAY_OF_YEAR) - cal.get(java.util.Calendar.DAY_OF_YEAR)
        } else {
            Int.MAX_VALUE
        }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return when {
            dayDiff == 0 -> formatCnClock(cal)
            dayDiff == 1 -> "昨天 ${formatCnClock(cal)}"
            sameYear -> "${month}月${day}日"
            else -> "${cal.get(java.util.Calendar.YEAR)}/$month/$day"
        }
    }

    /** 会话气泡时间头（今天=下午3:30，昨天=昨天 下午3:30，今年=M月d日 下午3:30，跨年加年份） */
    fun formatSmsDateTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = java.util.Calendar.getInstance()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameYear = now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
        val dayDiff = if (sameYear) {
            now.get(java.util.Calendar.DAY_OF_YEAR) - cal.get(java.util.Calendar.DAY_OF_YEAR)
        } else {
            Int.MAX_VALUE
        }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val clock = formatCnClock(cal)
        return when {
            dayDiff == 0 -> clock
            dayDiff == 1 -> "昨天 $clock"
            sameYear -> "${month}月${day}日 $clock"
            else -> "${cal.get(java.util.Calendar.YEAR)}/$month/$day $clock"
        }
    }

    private val BASE64_RE = Regex("^[A-Za-z0-9+/]+={0,2}$")

    /**
     * 解码短信正文：设备正文一般为 Base64；纯数字内容原样返回。
     * Base64 解出的字节优先按 UTF-8 解释，若出现乱码再回退 UCS2(UTF-16BE)。
     */
    private fun decodeSmsBody(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        if (s.all { it.isDigit() }) return s
        if (s.length % 4 != 0 || !BASE64_RE.matches(s)) return s
        return try {
            val bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT)
            val utf8 = String(bytes, Charsets.UTF_8)
            val decoded = if (utf8.contains('\uFFFD')) String(bytes, Charsets.UTF_16BE) else utf8
            decoded.ifBlank { s }
        } catch (_: Exception) {
            s
        }
    }

    /** 解析短信 JSON 数组（goform 与 /api/get_sms：id/number/content/date/tag/sim_slot） */
    private fun parseSmsArray(context: Context, arr: org.json.JSONArray): List<SmsMessage> {
        val out = ArrayList<SmsMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val rawContent = m.optString("content")
                .ifEmpty { m.optString("body").ifEmpty { m.optString("message") } }
            val rawDate = m.optString("date").ifEmpty { m.optString("time") }
            out.add(
                SmsMessage(
                    id = m.optString("id").ifEmpty { m.optString("msg_id") },
                    number = m.optString("number")
                        .ifEmpty { m.optString("from").ifEmpty { m.optString("phone").ifEmpty { m.optString("address") } } },
                    content = decodeSmsBody(rawContent),
                    date = rawDate,
                    timestamp = parseSmsTimestamp(rawDate),
                    unread = isUnread(m),
                    outgoing = isOutgoing(m),
                    simSlot = parseSmsSimSlot(m),
                ),
            )
        }
        return out
    }

    /** 解析短信条目的 SIM 槽：sim_slot / simSlot / slot，支持 0/1 或 "1"/"2"（1 基） */
    private fun parseSmsSimSlot(m: JSONObject): Int {
        val raw = when {
            m.has("sim_slot") && !m.isNull("sim_slot") -> m.opt("sim_slot")
            m.has("simSlot") && !m.isNull("simSlot") -> m.opt("simSlot")
            m.has("slot") && !m.isNull("slot") -> m.opt("slot")
            else -> null
        } ?: return -1
        val n = when (raw) {
            is Number -> raw.toInt()
            else -> raw.toString().trim().filter { it.isDigit() || it == '-' }.toIntOrNull() ?: return -1
        }
        // 少数固件用 1/2 表示卡1/卡2
        return when (n) {
            in 0..3 -> n
            else -> -1
        }
    }

    /** 卡槽展示文案：0→卡1，1→卡2 */
    fun formatSimSlotLabel(slot: Int): String = when (slot) {
        0 -> "卡1"
        1 -> "卡2"
        2 -> "卡3"
        3 -> "卡4"
        else -> ""
    }

    /**
     * 是否展示通用版双卡 UI（收发短信选卡、列表卡标识）。
     * - 短信模式为 UFI `/api/get_sms` 时开启
     * - 或非 F50 且已缓存机型（通用版双卡机）
     */
    fun isDualSimUiEnabled(context: Context): Boolean {
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        if (DevicePrefs.getGoformCompatMode(context, baseUrl, "sms") == SMS_MODE_UFI_API) return true
        val model = DevicePrefs.getCachedModel(context)
        return model.isNotBlank() && !model.equals("F50", ignoreCase = true)
    }

    /** 当前数据业务卡槽（0/1），未知为 -1 */
    fun currentSimSlot(context: Context): Int =
        DevicePrefs.getActiveSimSlot(context).toIntOrNull()?.takeIf { it in 0..3 } ?: -1

    /**
     * UFI-TOOLS 私有短信接口（部分机型如 E5 无 goform 后端，官方前端走
     * GET /api/get_sms?page=&count=，返回 {success, data:[{id,number,content,date,tag,sim_slot}]}）。
     * 返回 null 表示接口不存在/失败。
     */
    private suspend fun fetchUfiApiSmsList(context: Context, t: Long, auth: String): List<SmsMessage>? {
        // E5 实测 page 从 1 开始；count/data_per_page 均可
        val resp = fetchApi(context, "/api/get_sms?page=1&data_per_page=500", t, auth)
            ?: fetchApi(context, "/api/get_sms?page=1&count=999", t, auth)
            ?: return null
        val arr = resp.optJSONArray("data")
            ?: resp.optJSONArray("messages")
            ?: return if (resp.optBoolean("success", false)) emptyList() else null
        // 应用本地已读状态（设备无已读接口，仅本机记录）
        return applyLocalSmsReadState(context, parseSmsArray(context, arr))
    }

    /** 叠加本地已读标记：服务端判定未读 && 未在本地已读集合中 */
    private fun applyLocalSmsReadState(
        context: Context,
        list: List<SmsMessage>,
    ): List<SmsMessage> {
        if (list.isEmpty()) return list
        val localRead = DevicePrefs.getLocalSmsReadIds(context)
        if (localRead.isEmpty()) return list
        return list.map { sms ->
            if (sms.unread && sms.id in localRead) sms.copy(unread = false) else sms
        }
    }

    /** 短信接口模式缓存值：UFI-TOOLS 私有 /api/get_sms（通用版，无删除接口） */
    private val SMS_MODE_UFI_API = "ufi_api"

    /**
     * 是否支持设备侧删除短信。
     * - goform 设备（F50 等）：支持 DELETE_SMS
     * - 通用版 UFI `/api/get_sms`：作者确认无删除接口 → false
     */
    fun isSmsDeleteSupported(context: Context): Boolean {
        val baseUrl = DevicePrefs.buildBaseUrl(context)
        return DevicePrefs.getGoformCompatMode(context, baseUrl, "sms") != SMS_MODE_UFI_API
    }

    /** 拉取全部短信列表 */
    suspend fun fetchSmsList(context: Context): List<SmsMessage>? = withContext(Dispatchers.IO) {
        // Android 17 未授予「本地网络」权限时连接只会静默等到超时，直接失败避免每次轮询白等
        if (LocalNetworkPermission.blocksCurrentDevice(context)) {
            lastError = LocalNetworkPermission.HINT
            return@withContext null
        }
        try {
            val t = System.currentTimeMillis()
            val auth = DevicePrefs.getAuthToken(context)
            ensureDeviceToken(t, context)
            val baseUrl = DevicePrefs.buildBaseUrl(context)

            // 已知走 /api/get_sms 的设备直接请求，避免每次都探测 goform
            if (DevicePrefs.getGoformCompatMode(context, baseUrl, "sms") == SMS_MODE_UFI_API) {
                fetchUfiApiSmsList(context, t, auth)?.let { return@withContext it }
            }

            var resp = fetchGoformApi(
                context,
                smsListPath(),
                t,
                auth,
                "messages", "sms_data_total",
            )
            if ((resp?.optJSONArray("messages")?.length() ?: -1) == 0) {
                fetchGoformApi(
                    context,
                    smsListPath(memStore = -1, tags = 10),
                    t,
                    auth,
                    "messages", "sms_data_total",
                )?.takeIf { (it.optJSONArray("messages")?.length() ?: 0) > 0 }
                    ?.let { resp = it }
            }
            val arr = resp?.optJSONArray("messages")
            if (arr == null || arr.length() == 0) {
                // goform 不可用或为空：尝试 UFI-TOOLS 私有 /api/get_sms（E5 等机型）
                fetchUfiApiSmsList(context, System.currentTimeMillis(), auth)?.let {
                    DevicePrefs.setGoformCompatMode(context, baseUrl, SMS_MODE_UFI_API, "sms")
                    return@withContext it
                }
            }
            if (resp == null) return@withContext null
            if (arr == null) return@withContext emptyList()
            applyLocalSmsReadState(context, parseSmsArray(context, arr))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** goform 写操作（x-www-form-urlencoded），成功判定 result=success */
    private suspend fun goformPost(context: Context, form: String): Boolean {
        val auth = DevicePrefs.getAuthToken(context)
        ensureDeviceToken(System.currentTimeMillis(), context)
        val baseUrl = DevicePrefs.buildBaseUrl(context)

        val direct = postGoformRaw(context, form, auth, "")
        if (direct.isGoformSuccess()) {
            DevicePrefs.setGoformCompatMode(context, baseUrl, "direct_v4", "write")
            return true
        }

        var cookie = DevicePrefs.getGoformCookie(context, baseUrl)
        if (cookie.isEmpty()) cookie = loginOfficialBackend(context, auth, "")
        var legacyForm = addLegacyWriteAuth(context, form, auth, cookie)
        var legacy = postGoformRaw(context, legacyForm, auth, cookie)
        if (legacy.isGoformSuccess()) {
            DevicePrefs.setGoformCompatMode(context, baseUrl, "multi_data_v4", "write")
            return true
        }

        cookie = loginOfficialBackend(context, auth, cookie)
        if (cookie.isEmpty()) return false
        legacyForm = addLegacyWriteAuth(context, form, auth, cookie)
        legacy = postGoformRaw(context, legacyForm, auth, cookie)
        if (legacy.isGoformSuccess()) {
            DevicePrefs.setGoformCompatMode(context, baseUrl, "multi_data_v4", "write")
            return true
        }
        return false
    }

    private fun HttpResponse?.isGoformSuccess(): Boolean =
        this != null && isSuccessful && runCatching {
            JSONObject(body).optString("result").let {
                it.equals("success", ignoreCase = true) || it == "0"
            }
        }.getOrDefault(body.contains("success", ignoreCase = true))

    private suspend fun addLegacyWriteAuth(
        context: Context,
        form: String,
        auth: String,
        cookie: String,
    ): String {
        val baseForm = if (form.contains("isTest=")) form else "$form&isTest=false"
        if (cookie.isEmpty()) return baseForm

        val versionPath = legacyGoformPath(
            "${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?cmd=wa_inner_version,cr_version",
        )
        val version = fetchApi(context, versionPath, System.currentTimeMillis(), auth, cookie)
            ?: return baseForm
        val innerVersion = version.optString("wa_inner_version")
        val crVersion = version.optString("cr_version")
        if (innerVersion.isBlank() || crVersion.isBlank()) return baseForm

        val rdPath = legacyGoformPath("${DevicePrefs.DEFAULT_GOFORM_COMMAND_PATH}?cmd=RD")
        val rd = fetchApi(context, rdPath, System.currentTimeMillis(), auth, cookie)
            ?.optString("RD")
            ?.takeIf { it.isNotBlank() }
            ?: return baseForm
        val versionHash = NetClient.sha256(innerVersion + crVersion).uppercase(Locale.US)
        val ad = NetClient.sha256(versionHash + rd).uppercase(Locale.US)
        return "$baseForm&AD=${java.net.URLEncoder.encode(ad, "UTF-8")}"
    }

    /** UCS2(UTF-16BE) 十六进制编码，ZTE SEND_SMS 的 MessageBody 用此格式 */
    private fun toUcs2Hex(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_16BE)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        return sb.toString()
    }

    /**
     * 生成 ZTE sms_time 串："YY;MM;DD;HH;MM;SS;+TZ"。
     * 与解析侧一致：前 6 段为本地墙上时间，TZ 为 15 分钟刻度（东八区 +32）。
     * 全 goform 机型通用，不区分 F50。
     */
    private fun currentSmsTime(): String {
        val cal = java.util.Calendar.getInstance()
        val tzQuarter =
            (cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)) /
                (15 * 60 * 1000)
        val sign = if (tzQuarter >= 0) "+" else "-"
        return String.format(
            Locale.US,
            "%02d;%02d;%02d;%02d;%02d;%02d;%s%d",
            cal.get(java.util.Calendar.YEAR) % 100,
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            sign,
            kotlin.math.abs(tzQuarter),
        )
    }

    private data class DiskUsage(val total: Long, val used: Long, val available: Long)
    private data class StorageVolumes(val internal: DiskUsage?, val external: DiskUsage?)

    /**
     * 将 goform 存储字段合并进 base（仅当 base 对应字段缺失/无效时）。
     * F50 随身 goform 返回 "784.4M" 字符串；通用版 base 常为字节数值。
     */
    private fun mergeStorageIntoBase(base: JSONObject, goform: JSONObject?) {
        if (goform == null) return
        val keys = listOf(
            "internal_total_storage", "internal_used_storage", "internal_available_storage",
            "external_total_storage", "external_used_storage", "external_available_storage",
        )
        for (key in keys) {
            if (parseStorageSize(base.opt(key)) != null) continue
            parseStorageSize(goform.opt(key))?.let { base.put(key, it) }
        }
    }

    /** 优先 base，其次 goform；解析失败返回 -1（UI 显示 --） */
    private fun firstPositiveStorage(base: JSONObject, goform: JSONObject?, key: String): Long {
        parseStorageSize(base.opt(key))?.let { return it }
        parseStorageSize(goform?.opt(key))?.let { return it }
        return -1L
    }

    /**
     * 解析存储大小：支持 Long/Double 字节，以及 goform 人类可读 "784.4M" / "1.2G" / "512K"。
     * 返回 null 表示缺失/无效；0 为合法（已用/可用可为 0）。
     */
    private fun parseStorageSize(raw: Any?): Long? {
        if (raw == null || raw == JSONObject.NULL) return null
        when (raw) {
            is Number -> {
                val v = raw.toLong()
                return if (v >= 0L) v else null
            }
            is String -> {
                val s = raw.trim()
                if (s.isEmpty() || s.equals("null", true) || s == "--" || s == "-1") return null
                s.toLongOrNull()?.let { return if (it >= 0L) it else null }
                s.toDoubleOrNull()?.let { d ->
                    return if (d >= 0) d.toLong() else null
                }
                val m = Regex(
                    """^([0-9]*\.?[0-9]+)\s*([KMGTP]i?B?)?$""",
                    RegexOption.IGNORE_CASE,
                ).matchEntire(s) ?: return null
                val num = m.groupValues[1].toDoubleOrNull() ?: return null
                if (num < 0) return null
                val unit = m.groupValues[2].uppercase(Locale.US)
                val mult = when {
                    unit.startsWith("K") -> 1024.0
                    unit.startsWith("M") -> 1024.0 * 1024.0
                    unit.startsWith("G") -> 1024.0 * 1024.0 * 1024.0
                    unit.startsWith("T") -> 1024.0 * 1024.0 * 1024.0 * 1024.0
                    unit.startsWith("P") -> 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
                    else -> 1.0
                }
                return (num * mult).toLong().coerceAtLeast(0L)
            }
            else -> return null
        }
    }

    /**
     * 通用版双卡：解析优先 slot，再探测 currentCellInfo。
     * 只插一张卡时自动选有信号/IMSI 的槽位（0 或 1）。
     */
    private suspend fun fetchBestCellInfo(
        context: Context,
        t: Long,
        auth: String,
        base: JSONObject,
    ): JSONObject? {
        val preferred = resolvePreferredSimSlot(context, t, auth, base)
        val order = linkedSetOf(preferred, "0", "1").toList()
        var best: JSONObject? = null
        var bestScore = -1
        var bestSlot = preferred
        for (slot in order) {
            val cell = fetchApi(context, "/api/currentCellInfo?slot=$slot", t, auth) ?: continue
            val score = cellInfoScore(cell)
            if (score > bestScore) {
                bestScore = score
                best = cell
                bestSlot = slot
            }
            // 足够可信则提前结束（减少双卡探测延迟）
            if (score >= 4) break
        }
        if (best != null && bestScore > 0) {
            DevicePrefs.setActiveSimSlot(context, bestSlot)
        } else if (preferred.isNotEmpty()) {
            DevicePrefs.setActiveSimSlot(context, preferred)
        }
        return best
    }

    /** base.sim_slot → /api/sim_slot → 上次缓存 → 0 */
    private suspend fun resolvePreferredSimSlot(
        context: Context,
        t: Long,
        auth: String,
        base: JSONObject,
    ): String {
        normalizeSimSlot(base.opt("sim_slot"))?.let { return it }
        // 通用版双卡接口
        runCatching {
            fetchApi(context, "/api/sim_slot", t, auth)
        }.getOrNull()?.let { o ->
            normalizeSimSlot(o.opt("sim_slot"))
                ?: normalizeSimSlot(o.opt("slot"))
                ?: normalizeSimSlot(o.opt("active_slot"))
                ?: normalizeSimSlot(o.opt("data"))
        }?.let { return it }
        return DevicePrefs.getActiveSimSlot(context).ifBlank { "0" }
    }

    private fun normalizeSimSlot(raw: Any?): String? {
        if (raw == null || raw == JSONObject.NULL) return null
        val s = when (raw) {
            is Number -> raw.toInt().toString()
            else -> raw.toString().trim()
        }
        if (s.isEmpty() || s.equals("null", true) || s == "-1") return null
        val digits = s.filter { it.isDigit() }
        val n = digits.toIntOrNull() ?: return null
        return if (n in 0..3) n.toString() else null
    }

    /** currentCellInfo 有效性评分：双卡只插一张时用于挑有卡槽位 */
    private fun cellInfoScore(cell: JSONObject): Int {
        if (cell.has("error") && cell.optString("error").isNotBlank()) return 0
        if (cell.optBoolean("error", false)) return 0
        var score = 0
        if (cell.optString("imsi").trim().let { it.isNotEmpty() && it != "null" }) score += 2
        if (cell.optString("iccid").trim().let { it.isNotEmpty() && it != "null" }) score += 1
        if (cell.optString("netMode").trim().let { it.isNotEmpty() && it != "null" }) score += 1
        val rsrpKeys = listOf("rsrp", "RSRP", "lte_rsrp", "nr_rsrp", "Z5g_rsrp")
        if (rsrpKeys.any { k ->
                cell.has(k) && cell.optString(k).let { it.isNotBlank() && it != "null" && it != "0" }
            }
        ) {
            score += 2
        }
        if (cell.optString("cellId").trim().let { it.isNotEmpty() && it != "null" }) score += 1
        // 空对象
        if (score == 0 && cell.length() <= 1) return 0
        return score
    }

    /**
     * 存储兜底：POST /api/root_shell 执行 df，分别识别内部(/data)与 SD 卡挂载点。
     * 通用版 base 存储字段常为 null；F50 一般走 goform，此处作兜底。
     */
    private suspend fun fetchRootShellStorageVolumes(context: Context): StorageVolumes? {
        val t = System.currentTimeMillis()
        val auth = DevicePrefs.getAuthToken(context)
        val path = "/api/root_shell"
        val sign = NetClient.generateKanoSign("POST", path, t, DevicePrefs.getSecretKey(context))
        // 一次拉全部分区，客户端侧分类；兼容无 SD 设备
        val cmd = "df -k 2>/dev/null | head -n 60"
        val json = JSONObject()
            .put("command", cmd)
            .put("timeout", 8000)
        val builder = Request.Builder()
            .url("${DevicePrefs.buildBaseUrl(context)}$path")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("kano-t", t.toString())
            .addHeader("kano-sign", sign)
            .addHeader("Authorization", auth)
        DevicePrefs.getDeviceToken(context).takeIf { it.isNotEmpty() }
            ?.let { builder.addHeader("X-Device-Token", it) }
        val resp = executeRequest(context, builder.build()) ?: return null
        if (!resp.isSuccessful) return null
        return runCatching {
            parseDfStorageVolumes(JSONObject(resp.body).optString("result"))
        }.getOrNull()
    }

    /** 解析 df -k：内部优先 /data；SD 识别 media_rw / UUID 挂载 / sdcard1 等 */
    private fun parseDfStorageVolumes(dfOutput: String): StorageVolumes? {
        if (dfOutput.isBlank()) return null
        data class Row(val totalKb: Long, val usedKb: Long, val availKb: Long, val mount: String)

        val rows = dfOutput.lineSequence().mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 6) return@mapNotNull null
            // Filesystem 1K-blocks Used Available Use% Mounted
            val total = p[1].toLongOrNull() ?: return@mapNotNull null
            val used = p[2].toLongOrNull() ?: return@mapNotNull null
            val avail = p[3].toLongOrNull() ?: return@mapNotNull null
            if (total <= 0L) return@mapNotNull null
            val mount = p.last()
            if (mount == "Mounted" || mount == "on") return@mapNotNull null
            Row(total, used, avail, mount)
        }.toList()
        if (rows.isEmpty()) return null

        fun toUsage(r: Row) = DiskUsage(
            total = r.totalKb * 1024,
            used = r.usedKb * 1024,
            available = r.availKb * 1024,
        )

        fun pick(predicate: (Row) -> Boolean): DiskUsage? =
            rows.filter(predicate).maxByOrNull { it.totalKb }?.let(::toUsage)

        val internal = pick { it.mount == "/data" || it.mount.startsWith("/data/") }
            ?: pick { it.mount == "/storage/emulated" || it.mount.startsWith("/storage/emulated/") }
            ?: pick { it.mount == "/" }

        val uuidMount = Regex("""^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$""")
        val external = pick { r ->
            val m = r.mount.lowercase(Locale.US)
            m.contains("media_rw") ||
                m.contains("sdcard1") ||
                m.contains("ext_sd") ||
                m.contains("external_sd") ||
                m.contains("usbotg") ||
                m.endsWith("/sdcard") && !m.contains("emulated") ||
                uuidMount.matches(r.mount)
        }
        // 排除与内部相同大小的“假外置”（有时 emulated 被标成 sdcard）
        val externalClean = external?.takeIf { ext ->
            val inn = internal ?: return@takeIf true
            // 完全一致则视为同一卷
            !(ext.total == inn.total && ext.used == inn.used)
        }

        return StorageVolumes(internal = internal, external = externalClean)
    }

    /**
     * UFI-TOOLS 私有发送接口：POST /api/send_sms，JSON {address, body, simSlot}，
     * 成功返回 result=success（E5 等无 goform 后端机型）。
     * @param simSlot 0=卡1，1=卡2；null 则用当前活动槽
     */
    private suspend fun sendSmsUfiApi(
        context: Context,
        number: String,
        body: String,
        simSlot: Int? = null,
    ): Boolean {
        val t = System.currentTimeMillis()
        val auth = DevicePrefs.getAuthToken(context)
        val path = "/api/send_sms"
        val sign = NetClient.generateKanoSign("POST", path, t, DevicePrefs.getSecretKey(context))
        val slot = (simSlot ?: DevicePrefs.getActiveSimSlot(context).toIntOrNull() ?: 0)
            .coerceIn(0, 3)
        val json = JSONObject()
            .put("address", number)
            .put("body", body)
            .put("simSlot", slot)
        val builder = Request.Builder()
            .url("${DevicePrefs.buildBaseUrl(context)}$path")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("kano-t", t.toString())
            .addHeader("kano-sign", sign)
            .addHeader("Authorization", auth)
        DevicePrefs.getDeviceToken(context).takeIf { it.isNotEmpty() }
            ?.let { builder.addHeader("X-Device-Token", it) }
        val resp = executeRequest(context, builder.build()) ?: return false
        if (!resp.isSuccessful) return false
        val ok = runCatching {
            val o = JSONObject(resp.body)
            o.optString("result").equals("success", ignoreCase = true) ||
                o.optBoolean("success", false)
        }.getOrDefault(false)
        if (ok) DevicePrefs.setActiveSimSlot(context, slot.toString())
        return ok
    }

    /**
     * 发送短信。
     * @param simSlot 通用版指定卡槽（0=卡1，1=卡2）；null 用当前活动槽。goform 机型忽略。
     */
    suspend fun sendSms(
        context: Context,
        number: String,
        body: String,
        simSlot: Int? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val num = number.trim()
            if (num.isEmpty() || body.isEmpty()) return@withContext false
            if (LocalNetworkPermission.blocksCurrentDevice(context)) {
                lastError = LocalNetworkPermission.HINT
                return@withContext false
            }
            val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
            val f50Form = "goformId=SEND_SMS" +
                "&Number=${enc(num)}" +
                "&MessageBody=${toUcs2Hex(body)}"
            try {
                val baseUrl = DevicePrefs.buildBaseUrl(context)
                if (DevicePrefs.getGoformCompatMode(context, baseUrl, "sms") == SMS_MODE_UFI_API) {
                    if (sendSmsUfiApi(context, num, body, simSlot)) return@withContext true
                }
                if (goformPost(context, f50Form)) return@withContext true
                val legacyForm = "$f50Form" +
                    "&notCallback=true" +
                    "&sms_time=${enc(currentSmsTime())}" +
                    "&ID=-1" +
                    "&encode_type=UNICODE"
                if (goformPost(context, legacyForm)) return@withContext true
                sendSmsUfiApi(context, num, body, simSlot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * 删除短信（goform DELETE_SMS）。
     * 通用版 UFI 私有接口无删除能力，应先用 [isSmsDeleteSupported] 隐藏入口。
     */
    suspend fun deleteSms(context: Context, ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!isSmsDeleteSupported(context)) return@withContext false
        if (LocalNetworkPermission.blocksCurrentDevice(context)) {
            lastError = LocalNetworkPermission.HINT
            return@withContext false
        }
        val clean = ids.filter { it.isNotBlank() }
        if (clean.isEmpty()) return@withContext false
        val idParam = java.net.URLEncoder.encode(clean.joinToString(";") + ";", "UTF-8")
        try {
            goformPost(context, "goformId=DELETE_SMS&msg_id=$idParam&notCallback=true")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 标记已读：本机记录（通用版无已读接口）；goform 设备额外尝试 SET_MSG_READ。
     */
    suspend fun markSmsRead(context: Context, ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        val clean = ids.filter { it.isNotBlank() }
        if (clean.isEmpty()) return@withContext false
        // 本地始终写入，保证 UI 未读角标立即更新
        DevicePrefs.addLocalSmsReadIds(context, clean)
        // 缺少本地网络权限时跳过服务端同步（否则每条都要白等超时），本地已读照旧生效
        if (isSmsDeleteSupported(context) && !LocalNetworkPermission.blocksCurrentDevice(context)) {
            // goform 设备同步尝试服务端已读（失败不影响本地）
            val idParam = java.net.URLEncoder.encode(clean.joinToString(";"), "UTF-8")
            try {
                goformPost(context, "goformId=SET_MSG_READ&msg_id=$idParam")
            } catch (_: Exception) {
                // ignore
            }
        }
        true
    }

    // ── 解析辅助 ──

    /**
     * 流量桶：总量与上下行展示值。
     * upload/download 为 -1 表示设备未提供可推导的拆分。
     */
    private data class TrafficBucket(
        val totalBytes: Long,
        val uploadBytes: Long,
        val downloadBytes: Long,
    )

    /**
     * 合并 baseDeviceInfo 总量与 goform 上下行。
     *
     * 真机约束（F50 + UFI 4.0.8）：
     * - 总量只用 base.daily_data / monthly_data（与 cellularUsage 对齐）
     * - goform 空串字段 = 不支持（忽略）
     * - goform monthly_tx 可能远大于 monthly_data（异常累计），不能当绝对值展示
     *
     * 处理顺序：
     * 1. 成对读取别名，不跨配对串扰
     * 2. 剔除「单边 > 总量×2」的异常方向
     * 3. 剩余值与总量一致则直接用；否则按比例归一到总量（上行+下行=总量）
     * 4. 无 base 总量时才用 goform 和
     */
    private fun resolveTrafficBucket(
        baseTotal: Long?,
        goform: JSONObject?,
        splitPairs: List<Pair<String, String>>,
    ): TrafficBucket {
        val candidates = ArrayList<Pair<Long?, Long?>>(splitPairs.size)
        for ((txKey, rxKey) in splitPairs) {
            val tx = readPresentTrafficBytes(goform, txKey)
            val rx = readPresentTrafficBytes(goform, rxKey)
            if (tx != null || rx != null) candidates.add(tx to rx)
        }

        val total = when {
            baseTotal != null && baseTotal >= 0L -> baseTotal
            else -> {
                val sum = candidates.maxOfOrNull { (tx, rx) -> (tx ?: 0L) + (rx ?: 0L) } ?: 0L
                sum
            }
        }

        if (candidates.isEmpty()) {
            return TrafficBucket(total, -1L, -1L)
        }

        // 选「归一化后信息量最大」的候选：先剔异常边，再评估
        var best: TrafficBucket? = null
        var bestScore = -1
        for ((rawTx, rawRx) in candidates) {
            val bucket = normalizeTrafficSplit(rawTx, rawRx, total)
            val score = (if (bucket.uploadBytes >= 0L) 2 else 0) +
                (if (bucket.downloadBytes >= 0L) 2 else 0) +
                (if (bucket.totalBytes > 0L) 1 else 0)
            if (score > bestScore) {
                bestScore = score
                best = bucket
            }
        }
        return best ?: TrafficBucket(total, -1L, -1L)
    }

    /**
     * 将单组 goform 上下行校正到可信总量。
     * 真机例：total=1.22GB, tx=17.6GB(异常), rx=1.52GB → 丢掉 tx，下行占满总量（或按 rx 封顶）。
     */
    private fun normalizeTrafficSplit(tx: Long?, rx: Long?, total: Long): TrafficBucket {
        if (total < 0L) return TrafficBucket(0L, -1L, -1L)

        var up = tx
        var down = rx

        if (total > 0L) {
            val outlierThreshold = total * 2L
            val upOut = up != null && up > outlierThreshold
            val downOut = down != null && down > outlierThreshold
            when {
                upOut && !downOut -> up = null // F50: monthly_tx 异常累计
                downOut && !upOut -> down = null
                upOut && downOut -> {
                    // 两边都离谱：保留比例缩放到总量
                    return scaleTrafficSplitToTotal(tx, rx, total)
                }
            }
        }

        if (up == null && down == null) {
            return TrafficBucket(total, -1L, -1L)
        }

        if (total <= 0L) {
            val sum = (up ?: 0L) + (down ?: 0L)
            return TrafficBucket(
                totalBytes = sum,
                uploadBytes = up ?: if (down != null) 0L else -1L,
                downloadBytes = down ?: if (up != null) 0L else -1L,
            )
        }

        // 仅一侧有效：该侧封顶到总量，另一侧为剩余
        if (up != null && down == null) {
            val u = up.coerceIn(0L, total)
            return TrafficBucket(total, u, total - u)
        }
        if (down != null && up == null) {
            val d = down.coerceIn(0L, total)
            return TrafficBucket(total, total - d, d)
        }

        // 两侧都有
        val u0 = up!!.coerceAtLeast(0L)
        val d0 = down!!.coerceAtLeast(0L)
        val sum = u0 + d0
        val slack = maxOf(1_048_576L, total / 20L)
        return if (u0 <= total + slack && d0 <= total + slack && sum <= total + slack) {
            // 与总量基本一致：若略超则按比例微缩，否则原样（差额视作统计误差不展示）
            if (sum <= total) {
                // 和小于总量：按比例把差额补上，避免详情里上+下 < 本月
                if (sum == 0L) {
                    TrafficBucket(total, 0L, 0L)
                } else {
                    scaleTrafficSplitToTotal(u0, d0, total)
                }
            } else {
                scaleTrafficSplitToTotal(u0, d0, total)
            }
        } else {
            scaleTrafficSplitToTotal(u0, d0, total)
        }
    }

    /**
     * 将上下行比例映射到可信总量，保证 up + down == total。
     */
    private fun scaleTrafficSplitToTotal(tx: Long?, rx: Long?, total: Long): TrafficBucket {
        if (total <= 0L) {
            return TrafficBucket(0L, tx ?: -1L, rx ?: -1L)
        }
        val up0 = (tx ?: 0L).coerceAtLeast(0L)
        val down0 = (rx ?: 0L).coerceAtLeast(0L)
        val sum0 = up0 + down0
        if (sum0 <= 0L) {
            return TrafficBucket(
                totalBytes = total,
                uploadBytes = if (tx != null) 0L else -1L,
                downloadBytes = if (rx != null) 0L else -1L,
            )
        }
        val up = ((up0.toDouble() / sum0.toDouble()) * total.toDouble())
            .toLong()
            .coerceIn(0L, total)
        val down = total - up
        return TrafficBucket(total, up, down)
    }

    /** 仅当 key 真实存在且非空串时解析；空串=固件不支持（F50 常见）。0 是合法值。 */
    private fun readPresentTrafficBytes(json: JSONObject?, vararg keys: String): Long? {
        if (json == null) return null
        for (key in keys) {
            if (!hasPresentField(json, key)) continue
            parseTrafficNumber(json, key)?.let { return it }
        }
        return null
    }

    private fun hasPresentField(json: JSONObject, key: String): Boolean {
        if (!json.has(key) || json.isNull(key)) return false
        val raw = json.optString(key, "").trim()
        if (raw.isEmpty()) return false
        if (raw.equals("null", ignoreCase = true)) return false
        if (raw == "--" || raw.equals("nil", ignoreCase = true)) return false
        return true
    }

    /**
     * 解析流量数值为字节（base *_data / goform *_bytes 均为字节，不做单位猜测）。
     */
    private fun parseTrafficNumber(json: JSONObject, key: String): Long? {
        val rawNumber: Double? = when (val v = json.opt(key)) {
            null -> null
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> json.optString(key, "").trim().toDoubleOrNull()
        }
        if (rawNumber == null || rawNumber < 0.0 || rawNumber.isNaN()) return null
        if (rawNumber > Long.MAX_VALUE.toDouble()) return null
        return rawNumber.toLong()
    }

    /**
     * 实时速率归一化（审计：realtime_*_thrpt / realtime_*_speed / real_*_speed）。
     * goform 优先，base 兜底；返回 bytes/s，缺失为 -1。
     */
    private fun resolveRealtimeSpeed(
        goform: JSONObject?,
        base: JSONObject?,
        vararg keys: String,
    ): Long {
        for (src in arrayOf(goform, base)) {
            if (src == null) continue
            for (key in keys) {
                if (!hasPresentField(src, key)) continue
                val v = when (val raw = src.opt(key)) {
                    null -> null
                    is Number -> raw.toLong()
                    is String -> raw.trim().toLongOrNull()
                    else -> src.optString(key, "").trim().toLongOrNull()
                }
                if (v != null && v >= 0L) return v
            }
        }
        return -1L
    }

    fun formatFlow(bytes: Long): String {
        if (bytes <= 0) return "0.00 GB"
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "--"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.getDefault(), "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * 温度原始值转摄氏度。
     * baseDeviceInfo 的 cpu_temp / cpu_temp_list.temp 常为毫摄氏度（如 37000 → 37.0℃），
     * 大于 1000 时按 milli 换算；已是 ℃ 的小数/两位数原样返回。
     */
    fun toCelsius(raw: Double): Double? {
        if (raw <= 0.0 || raw.isNaN()) return null
        return if (raw > 1000.0) raw / 1000.0 else raw
    }

    /** 从传感器列表取最高温（℃），忽略 battery 类型（电池温单独展示）。 */
    fun maxDeviceTempCelsius(items: List<CpuTempItem>): Double? =
        items.asSequence()
            .filter { !it.type.equals("battery", ignoreCase = true) }
            .mapNotNull { toCelsius(it.temp) }
            .maxOrNull()

    private fun formatTemp(raw: Double): String {
        val celsius = toCelsius(raw) ?: return "--"
        return String.format(Locale.getDefault(), "%.1f℃", celsius)
    }

    fun formatTempValue(raw: Double): String {
        val celsius = toCelsius(raw) ?: return "--"
        return String.format(Locale.getDefault(), "%.1f℃", celsius)
    }

    private fun formatBatteryTemp(raw: Double): String {
        val celsius = toCelsius(raw) ?: return "--"
        return String.format(Locale.getDefault(), "%.1f ℃", celsius)
    }

    private fun formatCurrent(currentUa: Int): String {
        if (currentUa == Int.MIN_VALUE) return "--"
        return String.format(Locale.getDefault(), "%.2fmA", currentUa / 1000.0)
    }

    private fun formatVoltage(voltageUv: Int): String {
        if (voltageUv < 0) return "--"
        return String.format(Locale.getDefault(), "%.2fV", voltageUv / 1_000_000.0)
    }

    private fun formatStorage(total: Long, used: Long): String {
        if (total <= 0) return "--"
        val safeUsed = used.coerceAtLeast(0L)
        // 小于 1GB 用 MB，避免 F50 约 800M 内部盘显示成 0.x GB 难读
        if (total < 1024L * 1024L * 1024L) {
            val usedMb = safeUsed / (1024.0 * 1024.0)
            val totalMb = total / (1024.0 * 1024.0)
            return String.format(Locale.getDefault(), "%.0f / %.0f MB", usedMb, totalMb)
        }
        val usedGb = safeUsed / (1024.0 * 1024.0 * 1024.0)
        val totalGb = total / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.1f / %.1f GB", usedGb, totalGb)
    }

    /** 网络制式：兼容老版数字代码（20=5G，13=4G，0=无服务） */
    private fun mapNetworkType(raw: String): String = when (raw) {
        "20" -> "5G"
        "13" -> "4G"
        "0" -> "无服务"
        else -> raw
    }

    /** 实时速率格式化（bytes/s） */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return "--"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB/s", mb)
            kb >= 1 -> String.format(Locale.getDefault(), "%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    /** 运营商名称中文化：识别设备返回的英文/缩写运营商名 */
    private fun translateCarrier(name: String): String {
        val n = name.trim().uppercase(Locale.ROOT)
            .replace(" ", "").replace("-", "").replace("_", "")
        return when {
            n.isEmpty() -> ""
            n.contains("中国") -> name.trim()
            n.contains("CMCC") || n.contains("CHINAMOBILE") || n.contains("CHNMOBILE") -> "中国移动"
            n.contains("UNICOM") || n.contains("CUCC") || n.contains("CHNCU") -> "中国联通"
            n.contains("TELECOM") || n.contains("CTCC") || n.contains("CHNCT") || n.contains("CHINANET") -> "中国电信"
            n.contains("CBN") || n.contains("BROADNET") || n.contains("CHINABROADCAST") -> "中国广电"
            else -> name.trim()
        }
    }

    /** 运营商识别 (MCC 460)：基于 IMSI 前缀 */
    private fun mapPlmnToCarrier(input: String): String {
        if (input.length < 5 || !input.startsWith("460")) return ""
        return when (input.substring(3, 5)) {
            "00", "02", "07", "08" -> "中国移动"
            "01", "06", "09" -> "中国联通"
            "03", "05", "11" -> "中国电信"
            "15" -> "中国广电"
            else -> ""
        }
    }
}
