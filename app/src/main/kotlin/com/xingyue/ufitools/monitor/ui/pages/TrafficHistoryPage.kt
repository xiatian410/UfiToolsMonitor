package com.xingyue.ufitools.monitor.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.TrafficHistory
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 流量历史页：每日用量柱状图 + 列表（天数跟随设置中的保留天数） */
@Composable
fun TrafficHistoryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val retentionDays = remember { DevicePrefs.getTrafficRetentionDays(context) }
    var records by remember { mutableStateOf(TrafficHistory.getRecent(context, retentionDays)) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "流量历史",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            if (records.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = "暂无历史数据",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "应用会在每次刷新仪表盘或后台监控时自动记录每日流量",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                item {
                    SmallTitle("最近 $retentionDays 天（${records.size} 天有记录）")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        BarChart(records)
                    }
                }
                item {
                    SmallTitle("明细")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        records.asReversed().forEachIndexed { index, record ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = record.date,
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = DeviceApi.formatBytes(record.bytes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(records: List<TrafficHistory.DayRecord>) {
    val maxBytes = records.maxOf { it.bytes }.coerceAtLeast(1L)
    Column {
        Text(
            text = "单日最高 ${DeviceApi.formatBytes(maxBytes)}",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            records.forEach { record ->
                val fraction = (record.bytes.toFloat() / maxBytes).coerceIn(0.02f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = records.first().date.substring(5),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = records.last().date.substring(5),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
