package com.xingyue.ufitools.monitor.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「本地网络」权限说明弹窗。
 *
 * Android 17（API 37）起访问局域网地址需要 ACCESS_LOCAL_NETWORK，未授权时连接不会报错、
 * 只会静默等到超时，所以必须主动向用户解释。以弹窗形式提示，不占用页面内容区域。
 *
 * @param host 当前配置的设备地址，用于让提示更具体；为空时省略
 */
@Composable
fun LocalNetworkPermissionDialog(
    show: Boolean,
    host: String,
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "需要「本地网络」权限",
        summary = "Android 17 起访问局域网设备必须先授权",
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "应用需要通过局域网地址${if (host.isBlank()) "" else "（$host）"}与随身 Wi‑Fi 通信。" +
                "未授予该权限时连接不会报错，只会一直等到超时。",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "若系统没有弹出授权窗口，请用「打开系统设置」→ 权限 → 附近的设备，手动允许本地网络访问。",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            text = "授予权限",
            onClick = onGrant,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "稍后",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "打开系统设置",
                onClick = onOpenSystemSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
