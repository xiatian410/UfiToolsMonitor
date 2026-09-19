package com.xingyue.ufitools.monitor.data

import org.json.JSONObject

data class CpuTempItem(val type: String, val temp: Double)

data class CpuFreqItem(val cur: Int, val max: Int)

/** 设备完整运行状态（一次刷新采集的全部数据） */
data class DeviceStatus(
    val model: String = "--",
    val deviceModel: String = "--",
    val firmwareVer: String = "--",
    val appVer: String = "--",
    val webVersion: String = "--",
    val hardwareVersion: String = "--",
    // 网络信号
    val signal: String = "--",
    val netType: String = "--",
    val carrier: String = "",
    /**
     * 当前数据业务 SIM 槽：0=卡1，1=卡2，-1=未知。
     * 通用版双卡展示用；F50 单卡一般为 -1。
     */
    val activeSimSlot: Int = -1,
    /** 是否在 UI 中展示卡1/卡2（通用版） */
    val showSimSlot: Boolean = false,
    val rsrp: Int? = null,
    val signalBar: Int = -1,
    val qci: String = "--",
    val ambr: String = "--",
    val sinr: String = "--",
    val band: String = "--",
    val caStatus: String = "--",
    // 实时速率（bytes/s，-1 表示未知）
    val rxSpeed: Long = -1,
    val txSpeed: Long = -1,
    // 温度
    val temp: String = "--",
    val cpuTempList: List<CpuTempItem> = emptyList(),
    // CPU
    val cpu: String = "--",
    val cpuUsageInfo: Map<String, String> = emptyMap(),
    val cpuFreqInfo: Map<String, CpuFreqItem> = emptyMap(),
    // 内存
    val mem: String = "--",
    val memTotalKb: Long = 0,
    val memAvailableKb: Long = 0,
    val memUsedKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapUsedKb: Long = 0,
    // 流量
    val dailyFlow: String = "--",
    val monthlyFlow: String = "--",
    val dailyRawBytes: Long = 0,
    val monthlyRawBytes: Long = 0,
    val dailyUploadBytes: Long = 0,
    val dailyDownloadBytes: Long = 0,
    val monthlyUploadBytes: Long = 0,
    val monthlyDownloadBytes: Long = 0,
    /**
     * 是否展示上下行拆分。
     * F50 等机型 goform 日/月上下行不可信或为空，仅保留今日/本月总量。
     */
    val showTrafficSplit: Boolean = true,
    // 电池
    val battery: String = "--",
    val batteryPercent: Int = -1,
    val batteryCurrent: String = "--",
    val batteryVoltage: String = "--",
    val batteryTemp: String = "--",
    val hasBattery: Boolean = true,
    val charging: Boolean = false,
    // WiFi 连接数（-1 表示未知）
    val wifiCount: Int = -1,
    // 存储
    val internalStorage: String = "--",
    val internalTotalStorage: Long = -1,
    val internalUsedStorage: Long = -1,
    val internalAvailableStorage: Long = -1,
    val externalTotalStorage: Long = -1,
    val externalUsedStorage: Long = -1,
    val externalAvailableStorage: Long = -1,
    // 网络地址
    val clientIp: String = "--",
    val wanIp: String = "--",
    val wanIpv6: String = "--",
    val macAddress: String = "--",
    // 采集时间
    val updateTime: Long = 0,
) {

    /** 序列化小组件所需的核心字段 */
    fun toWidgetJson(): String = JSONObject().apply {
        put("model", model)
        put("deviceModel", deviceModel)
        put("firmwareVer", firmwareVer)
        put("appVer", appVer)
        put("signal", signal)
        put("netType", netType)
        put("temp", temp)
        put("cpu", cpu)
        put("mem", mem)
        put("dailyFlow", dailyFlow)
        put("monthlyFlow", monthlyFlow)
        put("battery", battery)
        put("batteryPercent", batteryPercent)
        put("hasBattery", hasBattery)
        put("charging", charging)
        put("signalBar", signalBar)
        put("qci", qci)
        put("ambr", ambr)
        put("sinr", sinr)
        put("band", band)
        put("caStatus", caStatus)
        rsrp?.let { put("rsrp", it) }
        put("rxSpeed", rxSpeed)
        put("txSpeed", txSpeed)
        put("carrier", carrier)
        put("activeSimSlot", activeSimSlot)
        put("showSimSlot", showSimSlot)
        put("batteryTemp", batteryTemp)
        put("batteryCurrent", batteryCurrent)
        put("batteryVoltage", batteryVoltage)
        put("wifiCount", wifiCount)
        put("dailyUploadBytes", dailyUploadBytes)
        put("dailyDownloadBytes", dailyDownloadBytes)
        put("monthlyUploadBytes", monthlyUploadBytes)
        put("monthlyDownloadBytes", monthlyDownloadBytes)
        put("showTrafficSplit", showTrafficSplit)
        put("updateTime", updateTime)
    }.toString()

    companion object {
        fun fromWidgetJson(json: String): DeviceStatus? = try {
            val o = JSONObject(json)
            DeviceStatus(
                model = o.optString("model", "--"),
                deviceModel = o.optString("deviceModel", "--"),
                firmwareVer = o.optString("firmwareVer", "--"),
                appVer = o.optString("appVer", "--"),
                signal = o.optString("signal", "--"),
                netType = o.optString("netType", "--"),
                temp = o.optString("temp", "--"),
                cpu = o.optString("cpu", "--"),
                mem = o.optString("mem", "--"),
                dailyFlow = o.optString("dailyFlow", "--"),
                monthlyFlow = o.optString("monthlyFlow", "--"),
                battery = o.optString("battery", "--"),
                batteryPercent = o.optInt("batteryPercent", -1),
                hasBattery = o.optBoolean("hasBattery", true),
                charging = o.optBoolean("charging", false),
                signalBar = o.optInt("signalBar", -1),
                qci = o.optString("qci", "--"),
                ambr = o.optString("ambr", "--"),
                sinr = o.optString("sinr", "--"),
                band = o.optString("band", "--"),
                caStatus = o.optString("caStatus", "--"),
                rsrp = if (o.has("rsrp")) o.optInt("rsrp") else null,
                rxSpeed = o.optLong("rxSpeed", -1L),
                txSpeed = o.optLong("txSpeed", -1L),
                carrier = o.optString("carrier", ""),
                activeSimSlot = o.optInt("activeSimSlot", -1),
                showSimSlot = o.optBoolean("showSimSlot", false),
                batteryTemp = o.optString("batteryTemp", "--"),
                batteryCurrent = o.optString("batteryCurrent", "--"),
                batteryVoltage = o.optString("batteryVoltage", "--"),
                wifiCount = o.optInt("wifiCount", -1),
                dailyUploadBytes = o.optLong("dailyUploadBytes", 0L),
                dailyDownloadBytes = o.optLong("dailyDownloadBytes", 0L),
                monthlyUploadBytes = o.optLong("monthlyUploadBytes", 0L),
                monthlyDownloadBytes = o.optLong("monthlyDownloadBytes", 0L),
                showTrafficSplit = o.optBoolean("showTrafficSplit", true),
                updateTime = o.optLong("updateTime", 0L),
            )
        } catch (_: Exception) {
            null
        }
    }
}
