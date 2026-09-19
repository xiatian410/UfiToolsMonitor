package com.xingyue.ufitools.monitor.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingyue.ufitools.monitor.data.UpdateApi
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private fun openLink(ctx: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * 启动时对接更新服务端：
 * - status=disabled：全屏停用页，无法进入应用
 * - status=force_update：强制更新弹窗，无法关闭
 * - status=update：可选更新弹窗
 * - announcement：公告弹窗（每次启动最多一次）
 * - 服务端开启统计时上报 app_open
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<UpdateApi.CheckResult?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var announcementDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = UpdateApi.check(context) ?: return@LaunchedEffect
        result = r
        if (r.statsEnabled) {
            UpdateApi.reportStats(context, "app_open")
        }
    }

    val r = result

    // 停用：全屏拦截，不显示应用内容
    if (r != null && r.status == "disabled") {
        val notice = r.notice
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    text = notice?.title?.ifBlank { null } ?: "当前版本已停用",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notice?.text?.ifBlank { null } ?: "请更新到最新版本后继续使用。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                TextButton(
                    text = notice?.button?.ifBlank { null } ?: "获取新版本",
                    onClick = { openLink(context, notice?.link ?: r.apkUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }

    if (r != null) {
        // 强制更新：无法关闭；可选更新：可稍后
        val isForce = r.status == "force_update"
        val showUpdate = isForce || (r.status == "update" && !updateDismissed)
        WindowDialog(
            show = showUpdate,
            title = if (isForce) "需要更新" else "发现新版本",
            summary = buildString {
                append("最新版本 ")
                append(r.latestVersionName)
                if (r.updateLog.isNotBlank()) {
                    append("\n\n")
                    append(r.updateLog)
                }
                if (isForce) append("\n\n当前版本过低，必须更新后才能继续使用。")
            },
            onDismissRequest = { if (!isForce) updateDismissed = true },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isForce) {
                    TextButton(
                        text = "稍后",
                        onClick = { updateDismissed = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = "立即更新",
                    onClick = { openLink(context, r.apkUrl) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 公告
        val ann = r.announcement
        if (ann != null && !showUpdate) {
            WindowDialog(
                show = !announcementDismissed,
                title = ann.title.ifBlank { "公告" },
                summary = ann.content,
                onDismissRequest = { announcementDismissed = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "知道了",
                        onClick = { announcementDismissed = true },
                        modifier = Modifier.weight(1f),
                    )
                    if (ann.link.isNotBlank()) {
                        TextButton(
                            text = ann.button.ifBlank { "查看详情" },
                            onClick = { openLink(context, ann.link) },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
