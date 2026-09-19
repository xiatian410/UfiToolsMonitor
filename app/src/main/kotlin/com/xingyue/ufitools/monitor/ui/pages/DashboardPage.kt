package com.xingyue.ufitools.monitor.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.CpuFreqItem
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DeviceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 详情弹层类型 */
private enum class DetailSheet {
    NETWORK, TEMP, CPU, MEM, TRAFFIC, BATTERY, STORAGE, ADDRESS, FIRMWARE,
}

/** 网格磁贴数据 */
private data class TileSpec(
    val iconRes: Int,
    val label: String,
    val value: String,
    val sub: String,
    val progress: Float?,
    val sheet: DetailSheet,
)

@Composable
fun DashboardPage(
    padding: PaddingValues,
    status: DeviceStatus?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenTraffic: () -> Unit,
) {
    val context = LocalContext.current
    var detailSheet by remember { mutableStateOf<DetailSheet?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var lastSheetOpenTime by rememberSaveable { mutableStateOf(0L) }

    fun openSheet(sheet: DetailSheet) {
        val now = System.currentTimeMillis()
        if (now - lastSheetOpenTime < 300) return
        lastSheetOpenTime = now
        detailSheet = sheet
        sheetVisible = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
    ) {
        if (errorMessage != null && status == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight(0.75f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "无法连接设备",
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    TextButton(
                        text = "重新连接",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        text = "修改连接配置",
                        onClick = onOpenSetup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (errorMessage != null && status != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRetry,
                        ),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "连接已断开，显示的是上次数据",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "点击重试",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        if (status == null && errorMessage == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        InfiniteProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "正在连接设备…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        if (status != null) {
            item {
                // ── 设备头部 ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { openSheet(DetailSheet.FIRMWARE) },
                        ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = status.deviceModel.ifBlank { status.model }.ifBlank { "--" },
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("固件 ")
                            append(status.firmwareVer.ifBlank { "--" })
                            if (status.appVer.isNotBlank()) {
                                append("  ·  UFI-TOOLS ")
                                append(status.appVer)
                            }
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (status.carrier.isNotBlank() || status.netType.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                if (status.showSimSlot && status.activeSimSlot >= 0) {
                                    append(DeviceApi.formatSimSlotLabel(status.activeSimSlot))
                                    append("  ·  ")
                                }
                                if (status.carrier.isNotBlank()) {
                                    append(status.carrier)
                                    if (status.netType.isNotBlank() && status.netType != "--") {
                                        append("  ·  ")
                                        append(status.netType)
                                    }
                                } else {
                                    append(status.netType.ifBlank { "--" })
                                }
                            },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "更新于 " + SimpleDateFormat("HH:mm:ss", Locale.ROOT)
                            .format(Date(status.updateTime)) + if (isLoading) "（刷新中…）" else "",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            item {
                SmallTitle("实时状态")
                val tiles = buildTiles(status)
                tiles.chunked(2).forEach { rowTiles ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowTiles.forEach { tile ->
                            GridTile(
                                tile = tile,
                                modifier = Modifier.weight(1f),
                                onClick = { openSheet(tile.sheet) },
                            )
                        }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                SmallTitle("流量")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { openSheet(DetailSheet.TRAFFIC) },
                        ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "今日流量",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = status.dailyFlow,
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本月流量",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = status.monthlyFlow,
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                SmallTitle("功能")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    FeatureRow(
                        icon = MiuixIcons.Messages,
                        label = "短信收件箱",
                        summary = "查看设备接收的短信",
                        onClick = onOpenSms,
                    )
                    Spacer(Modifier.height(16.dp))
                    FeatureRow(
                        icon = MiuixIcons.Recent,
                        label = "流量历史",
                        summary = "最近 30 天每日用量图表",
                        onClick = onOpenTraffic,
                    )
                    Spacer(Modifier.height(16.dp))
                    FeatureRow(
                        icon = MiuixIcons.WorldClock,
                        label = "息屏显示",
                        summary = "黑底低亮度常亮展示时钟与关键指标",
                        onClick = {
                            if (!com.xingyue.ufitools.monitor.data.DevicePrefs.isAodGuideAcknowledged(context)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "首次使用请先到 设置 → 锁屏与息屏 查看说明",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                            context.startActivity(
                                android.content.Intent(context, com.xingyue.ufitools.monitor.AodActivity::class.java),
                            )
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    FeatureRow(
                        icon = MiuixIcons.Info,
                        label = "网络地址",
                        summary = "IP / MAC 详情",
                        onClick = { openSheet(DetailSheet.ADDRESS) },
                    )
                }
            }
        }
    }

    val sheet = detailSheet
    OverlayBottomSheet(
        show = sheetVisible && sheet != null && status != null,
        title = when (sheet) {
            DetailSheet.NETWORK -> "网络信号"
            DetailSheet.TEMP -> "温度详情"
            DetailSheet.CPU -> "CPU 详情"
            DetailSheet.MEM -> "内存详情"
            DetailSheet.TRAFFIC -> "流量详情"
            DetailSheet.BATTERY -> if (status?.hasBattery == false) "供电详情" else "电池详情"
            DetailSheet.STORAGE -> "存储详情"
            DetailSheet.ADDRESS -> "网络地址"
            DetailSheet.FIRMWARE -> "固件版本"
            null -> ""
        },
        onDismissRequest = { sheetVisible = false },
        content = {
            if (status != null && sheet != null) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    DetailContent(sheet, status)
                    Spacer(Modifier.height(20.dp))
                    TextButton(
                        text = "关闭",
                        onClick = { sheetVisible = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        },
    )
}

private fun parsePercent(s: String): Float? =
    Regex("-?\\d+(\\.\\d+)?").find(s)?.value?.toFloatOrNull()?.let { (it / 100f).coerceIn(0f, 1f) }

private fun buildTiles(s: DeviceStatus): List<TileSpec> {
    val signalProgress = s.rsrp?.let { ((it + 140f) / 96f).coerceIn(0f, 1f) }
    // 与警报一致：列表原始值可能是毫摄氏度，先换算到 ℃ 再算进度
    val tempValue = DeviceApi.maxDeviceTempCelsius(s.cpuTempList)
        ?: Regex("-?\\d+(\\.\\d+)?").find(s.temp)?.value?.toDoubleOrNull()
    val tempProgress = tempValue?.let { (it / 100f).toFloat().coerceIn(0f, 1f) }
    val batteryProgress = if (s.batteryPercent >= 0) s.batteryPercent / 100f else null
    val storageProgress = if (s.internalTotalStorage > 0 && s.internalUsedStorage >= 0) {
        (s.internalUsedStorage.toFloat() / s.internalTotalStorage).coerceIn(0f, 1f)
    } else null
    val hasSd = s.externalTotalStorage > 0
    val storageSubtitle = if (hasSd) {
        val sdAvail = DeviceApi.formatBytes(s.externalAvailableStorage)
        "含 SD · 可用 $sdAvail"
    } else {
        "内部存储"
    }

    return listOf(
        TileSpec(
            R.drawable.ic_widget_signal,
            "信号",
            // 主值：RSRP（信号强度）；格数在详情里单独展示，不与 RSRP 混写
            s.rsrp?.let { "${it}dBm" } ?: s.signal.takeUnless { it == "--" } ?: "--",
            listOf(
                if (s.signalBar in 0..5) "${s.signalBar}格" else null,
                s.netType.takeUnless { it.isBlank() || it == "--" },
                s.band.takeUnless { it.isBlank() || it == "--" },
                s.sinr.takeUnless { it.isBlank() || it == "--" }?.let { "SINR $it" },
            ).filterNotNull().joinToString(" · "),
            signalProgress, DetailSheet.NETWORK,
        ),
        TileSpec(R.drawable.ic_widget_temp, "温度", s.temp, "CPU 最高温", tempProgress, DetailSheet.TEMP),
        TileSpec(R.drawable.ic_widget_cpu, "CPU", s.cpu, "总体使用率", parsePercent(s.cpu), DetailSheet.CPU),
        TileSpec(R.drawable.ic_widget_memory, "内存", s.mem, "使用率", parsePercent(s.mem), DetailSheet.MEM),
        TileSpec(
            R.drawable.ic_widget_battery, if (s.hasBattery) "电量" else "供电",
            if (s.hasBattery) s.battery + if (s.charging) " ⚡" else "" else "外接供电",
            if (s.hasBattery) {
                if (s.charging) "充电中" else "放电中"
            } else {
                "F50 无内置电池"
            },
            batteryProgress, DetailSheet.BATTERY,
        ),
        TileSpec(
            R.drawable.ic_widget_storage, "存储", s.internalStorage,
            storageSubtitle, storageProgress, DetailSheet.STORAGE,
        ),
    )
}

@Composable
private fun GridTile(
    tile: TileSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(tile.iconRes),
                contentDescription = tile.label,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tile.label,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tile.value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tile.sub,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tile.progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = tile.progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    label: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(24.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailContent(sheet: DetailSheet, s: DeviceStatus) {
    when (sheet) {
        DetailSheet.NETWORK -> DetailRows(
            buildList {
                if (s.showSimSlot) {
                    add(
                        "当前 SIM" to DeviceApi.formatSimSlotLabel(s.activeSimSlot)
                            .ifBlank { "未识别" },
                    )
                }
                // 信号强度 = RSRP，只保留一行；格数是 UI 条数，单独一行
                add(
                    "RSRP" to (s.rsrp?.let { "${it}dBm" }
                        ?: s.signal.takeUnless { it == "--" }
                        ?: "--"),
                )
                add("信号格数" to if (s.signalBar in 0..5) "${s.signalBar} / 5" else "--")
                add("网络制式" to s.netType)
                add("运营商" to s.carrier.ifBlank { "--" })
                add("QCI" to s.qci)
                add("速率" to s.ambr)
                add("SINR" to s.sinr)
                add("频段" to s.band)
            },
        )

        DetailSheet.TEMP -> {
            if (s.cpuTempList.isEmpty()) {
                DetailRows(listOf("CPU 温度" to s.temp))
            } else {
                DetailRows(
                    s.cpuTempList.map { it.type to DeviceApi.formatTempValue(it.temp) },
                )
            }
        }

        DetailSheet.CPU -> {
            val rows = mutableListOf("总体使用率" to s.cpu)
            s.cpuUsageInfo.entries
                .filter { it.key != "cpu" }
                .sortedBy { it.key }
                .forEach { (core, usage) ->
                    val freq: CpuFreqItem? = s.cpuFreqInfo[core]
                    val freqText = if (freq != null) "  ${freq.cur}/${freq.max} MHz" else ""
                    rows.add(core to "$usage%$freqText")
                }
            DetailRows(rows)
        }

        DetailSheet.MEM -> DetailRows(
            listOf(
                "使用率" to s.mem,
                "总内存" to DeviceApi.formatBytes(s.memTotalKb * 1024),
                "已用" to DeviceApi.formatBytes(s.memUsedKb * 1024),
                "可用" to DeviceApi.formatBytes(s.memAvailableKb * 1024),
                "SWAP 总量" to DeviceApi.formatBytes(s.swapTotalKb * 1024),
                "SWAP 已用" to DeviceApi.formatBytes(s.swapUsedKb * 1024),
            ),
        )

        DetailSheet.TRAFFIC -> DetailRows(
            // F50 等：goform 上下行不可信，仅展示今日/本月总量
            if (s.showTrafficSplit) {
                listOf(
                    "今日流量" to s.dailyFlow,
                    "今日上行" to DeviceApi.formatBytes(s.dailyUploadBytes),
                    "今日下行" to DeviceApi.formatBytes(s.dailyDownloadBytes),
                    "本月流量" to s.monthlyFlow,
                    "本月上行" to DeviceApi.formatBytes(s.monthlyUploadBytes),
                    "本月下行" to DeviceApi.formatBytes(s.monthlyDownloadBytes),
                )
            } else {
                listOf(
                    "今日流量" to s.dailyFlow,
                    "本月流量" to s.monthlyFlow,
                )
            },
        )

        DetailSheet.BATTERY -> DetailRows(
            if (s.hasBattery) {
                listOf(
                    "电量" to s.battery,
                    "状态" to if (s.charging) "⚡ 充电中" else "放电中",
                    "电流" to s.batteryCurrent,
                    "电压" to s.batteryVoltage,
                    "电池温度" to s.batteryTemp,
                )
            } else {
                listOf(
                    "设备类型" to "F50 无电池设备",
                    "供电方式" to "外接供电",
                    "电池状态" to "无内置电池",
                )
            },
        )

        DetailSheet.STORAGE -> {
            val rows = mutableListOf(
                "内部已用/总量" to s.internalStorage,
                "内部可用" to DeviceApi.formatBytes(s.internalAvailableStorage),
            )
            if (s.externalTotalStorage > 0) {
                // SD 卡：F50 goform external_* / 通用版 base 或 root_shell df
                val sdUsedTotal = if (s.externalUsedStorage >= 0) {
                    "${DeviceApi.formatBytes(s.externalUsedStorage)} / ${DeviceApi.formatBytes(s.externalTotalStorage)}"
                } else {
                    DeviceApi.formatBytes(s.externalTotalStorage)
                }
                rows.add("SD 卡已用/总量" to sdUsedTotal)
                rows.add("SD 卡可用" to DeviceApi.formatBytes(s.externalAvailableStorage))
            } else {
                rows.add("SD 卡" to "未检测到")
            }
            DetailRows(rows)
        }

        DetailSheet.ADDRESS -> DetailRows(
            listOf(
                "客户端 IP" to s.clientIp,
                "WAN IPv4" to s.wanIp,
                "WAN IPv6" to s.wanIpv6,
                "MAC 地址" to s.macAddress,
            ),
        )

        DetailSheet.FIRMWARE -> DetailRows(
            listOf(
                "设备型号" to s.deviceModel.ifBlank { s.model }.ifBlank { "--" },
                "固件版本" to s.firmwareVer.ifBlank { "--" },
                "UFI-TOOLS" to s.appVer.ifBlank { "--" },
                "Web 版本" to s.webVersion,
                "硬件版本" to s.hardwareVersion,
            ),
        )
    }
}

@Composable
private fun DetailRows(rows: List<Pair<String, String>>) {
    Column {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            KeyValueRow(label, value)
        }
    }
}
