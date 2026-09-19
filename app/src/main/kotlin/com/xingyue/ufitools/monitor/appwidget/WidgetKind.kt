package com.xingyue.ufitools.monitor.appwidget

/**
 * 桌面小组件类型：设置页按组件识别，DIY 选项随样式变化。
 */
enum class WidgetKind(
    val id: String,
    val title: String,
    val sizeLabel: String,
    val summary: String,
    /** 顶部状态栏开关（全量卡） */
    val hasHeader: Boolean = false,
    /** 中部数据卡片区开关（全量卡） */
    val hasFlowCardsToggle: Boolean = false,
    /** 可配置数据卡片槽位数量（0=无） */
    val cardSlotCount: Int = 0,
    /** 卡片槽位标题：左/右 或 上/下 */
    val cardSlotTitles: List<String> = emptyList(),
    /** 指标条可选 key 目录（空=不支持） */
    val metricKeys: List<String> = emptyList(),
    val metricMax: Int = 0,
    val metricDefaultOn: Set<String> = emptySet(),
    /** 状态条固定 4 列内容 DIY */
    val hasStatusSlots: Boolean = false,
    /** 信号图标/文字切换 */
    val hasSignalIcon: Boolean = false,
) {
    MAIN(
        id = "main",
        title = "状态卡",
        sizeLabel = "4×2",
        summary = "顶部状态 + 流量双卡 + 指标条",
        hasHeader = true,
        hasFlowCardsToggle = true,
        cardSlotCount = 2,
        cardSlotTitles = listOf("左侧卡片", "右侧卡片"),
        metricKeys = listOf("qci", "rate", "rsrp", "sinr", "band", "wifi", "btemp", "ctemp", "current"),
        metricMax = 5,
        metricDefaultOn = setOf("qci", "rate", "rsrp", "sinr", "band"),
        hasSignalIcon = true,
    ),
    TRAFFIC(
        id = "traffic",
        title = "流量条",
        sizeLabel = "4×1",
        summary = "横向双卡对照",
        cardSlotCount = 2,
        cardSlotTitles = listOf("左侧卡片", "右侧卡片"),
    ),
    SIGNAL(
        id = "signal",
        title = "信号卡",
        sizeLabel = "2×2",
        summary = "信号格 + 底部指标",
        metricKeys = listOf("rsrp", "sinr", "band", "qci", "rate", "wifi", "btemp", "ctemp"),
        metricMax = 3,
        metricDefaultOn = setOf("rsrp", "sinr", "band"),
        hasSignalIcon = true,
    ),
    SPEED(
        id = "speed",
        title = "流量卡",
        sizeLabel = "2×2",
        summary = "上下双卡 + 底部指标",
        cardSlotCount = 2,
        cardSlotTitles = listOf("上方卡片", "下方卡片"),
        metricKeys = listOf("qci", "rate", "rsrp", "sinr", "band", "wifi", "btemp", "ctemp", "current"),
        metricMax = 2,
        metricDefaultOn = setOf("qci", "rate"),
    ),
    STATUS_BAR(
        id = "status_bar",
        title = "状态条",
        sizeLabel = "4×1",
        summary = "四列状态指标",
        hasStatusSlots = true,
        hasSignalIcon = true,
    );

    /** 设置列表/标题：仅卡/条名称，不带尺寸等多余字样 */
    val displayTitle: String get() = title

    val hasCardSlots: Boolean get() = cardSlotCount > 0
    val hasMetrics: Boolean get() = metricKeys.isNotEmpty() && metricMax > 0

    companion object {
        fun fromId(id: String?): WidgetKind? = entries.firstOrNull { it.id == id }

        /** 状态条可选列内容 */
        val STATUS_SLOT_KEYS = listOf(
            "signal", "battery", "today", "month", "model", "network",
            "rsrp", "sinr", "band", "qci", "rate", "wifi", "btemp", "ctemp", "current",
        )
        val STATUS_SLOT_DEFAULTS = listOf("signal", "battery", "today", "model")
        const val STATUS_SLOT_COUNT = 4
    }
}
