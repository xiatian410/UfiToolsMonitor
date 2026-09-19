package com.xingyue.ufitools.monitor.appwidget

import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.DeviceStatus

/** 数据卡片 / 指标条 / 状态列 的内容解析，供各小组件共用 */

data class FlowCardSpec(val iconRes: Int, val title: String, val value: String)

data class MetricSpec(val iconRes: Int, val label: String, val value: String)

/** 数据卡片可选内容（与设置下拉一致） */
val WIDGET_CARD_OPTION_LABELS = listOf(
    "今日流量", "本月流量", "今日上下行", "本月上下行",
    "电池温度", "CPU温度", "放电电流", "WiFi连接数",
)

/** 指标 key → 标题 */
val WIDGET_METRIC_TITLES = mapOf(
    "qci" to "QCI",
    "rate" to "速率",
    "rsrp" to "RSRP",
    "sinr" to "SINR",
    "band" to "频段",
    "wifi" to "WiFi 连接数",
    "btemp" to "电池温度",
    "ctemp" to "CPU 温度",
    "current" to "放电电流",
)

/** 状态条列可选 key → 标题 */
val STATUS_SLOT_TITLES = mapOf(
    "signal" to "信号",
    "battery" to "电量",
    "today" to "今日流量",
    "month" to "本月流量",
    "model" to "机型",
    "network" to "网络",
    "rsrp" to "RSRP",
    "sinr" to "SINR",
    "band" to "频段",
    "qci" to "QCI",
    "rate" to "速率",
    "wifi" to "WiFi",
    "btemp" to "电池温度",
    "ctemp" to "CPU温度",
    "current" to "电流",
)

private fun updownText(downBytes: Long, upBytes: Long): String =
    "↓${shortBytes(downBytes)} ↑${shortBytes(upBytes)}"

/**
 * 流量卡片内容：
 * 0=今日流量 1=本月流量 2=今日上下行 3=本月上下行
 * 4=电池温度 5=CPU温度 6=放电电流 7=WiFi连接数
 */
fun resolveFlowCard(content: Int, s: DeviceStatus): FlowCardSpec = when (content) {
    1 -> FlowCardSpec(R.drawable.ic_widget_month, "本月流量", s.monthlyFlow)
    2 -> if (s.showTrafficSplit) {
        FlowCardSpec(R.drawable.ic_widget_today, "今日上下行", updownText(s.dailyDownloadBytes, s.dailyUploadBytes))
    } else {
        FlowCardSpec(R.drawable.ic_widget_today, "今日流量", s.dailyFlow)
    }
    3 -> if (s.showTrafficSplit) {
        FlowCardSpec(R.drawable.ic_widget_month, "本月上下行", updownText(s.monthlyDownloadBytes, s.monthlyUploadBytes))
    } else {
        FlowCardSpec(R.drawable.ic_widget_month, "本月流量", s.monthlyFlow)
    }
    4 -> if (s.hasBattery) {
        FlowCardSpec(R.drawable.ic_widget_btemp, "电池温度", s.batteryTemp)
    } else {
        FlowCardSpec(R.drawable.ic_widget_btemp, "供电方式", "外接供电")
    }
    5 -> FlowCardSpec(R.drawable.ic_widget_temp, "CPU温度", s.temp)
    6 -> if (s.hasBattery) {
        FlowCardSpec(R.drawable.ic_widget_current, "放电电流", s.batteryCurrent)
    } else {
        FlowCardSpec(R.drawable.ic_widget_current, "电池状态", "无电池")
    }
    7 -> FlowCardSpec(R.drawable.ic_widget_wifi, "WiFi连接数", wifiText(s.wifiCount))
    else -> FlowCardSpec(R.drawable.ic_widget_today, "今日流量", s.dailyFlow)
}

fun resolveMetric(key: String, s: DeviceStatus): MetricSpec? = when (key) {
    "qci" -> MetricSpec(R.drawable.ic_widget_qci, "QCI", s.qci)
    "rate" -> MetricSpec(R.drawable.ic_widget_rate, "速率", s.ambr)
    "rsrp" -> MetricSpec(R.drawable.ic_widget_rsrp, "RSRP", formatRsrpDbm(s.rsrp))
    "sinr" -> MetricSpec(R.drawable.ic_widget_sinr, "SINR", s.sinr)
    "band" -> MetricSpec(R.drawable.ic_widget_band, "频段", s.band)
    "wifi" -> MetricSpec(R.drawable.ic_widget_wifi, "WiFi", wifiText(s.wifiCount))
    "btemp" -> MetricSpec(
        R.drawable.ic_widget_btemp,
        if (s.hasBattery) "电池温度" else "供电",
        if (s.hasBattery) s.batteryTemp else "外接供电",
    )
    "ctemp" -> MetricSpec(R.drawable.ic_widget_temp, "CPU温度", s.temp)
    "current" -> MetricSpec(
        R.drawable.ic_widget_current,
        if (s.hasBattery) "电流" else "电池",
        if (s.hasBattery) s.batteryCurrent else "无电池",
    )
    else -> null
}

/** 状态条非「信号」列的展示（信号列单独处理） */
fun resolveStatusSlotMetric(key: String, s: DeviceStatus): MetricSpec? {
    when (key) {
        "signal" -> return null // 调用方用信号专用 UI
        "battery" -> {
            val value = if (s.hasBattery) {
                val pct = if (s.batteryPercent in 0..100) "${s.batteryPercent}%" else "--"
                if (s.charging) "$pct↑" else pct
            } else {
                "外接"
            }
            return MetricSpec(R.drawable.ic_widget_battery, "电量", value)
        }
        "today" -> return MetricSpec(
            R.drawable.ic_widget_today,
            "今日",
            s.dailyFlow.ifBlank { "--" },
        )
        "month" -> return MetricSpec(
            R.drawable.ic_widget_month,
            "本月",
            s.monthlyFlow.ifBlank { "--" },
        )
        "model" -> {
            val model = s.deviceModel.ifBlank { s.model.ifBlank { "UFI" } }
                .let { if (it.length > 12) it.take(11) + "…" else it }
            return MetricSpec(R.drawable.ic_widget_network, "机型", model)
        }
        "network" -> return MetricSpec(
            R.drawable.ic_widget_network,
            "网络",
            shortNetworkMode(s.netType),
        )
        else -> return resolveMetric(key, s)?.let {
            // 状态条标签略短
            val shortLabel = when (it.label) {
                "电池温度" -> "电温"
                "CPU温度" -> "温度"
                "放电电流" -> "电流"
                "WiFi 连接数", "WiFi" -> "WiFi"
                else -> it.label
            }
            it.copy(label = shortLabel)
        }
    }
}
