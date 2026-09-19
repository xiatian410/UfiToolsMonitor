package com.xingyue.ufitools.monitor.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xingyue.ufitools.monitor.BuildConfig
import com.xingyue.ufitools.monitor.data.DevicePrefs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 设置主页：分类入口的二级菜单 */
@Composable
fun SettingsPage(
    padding: PaddingValues,
    onOpenSetup: () -> Unit,
    onOpenWidget: () -> Unit,
    onOpenAlert: () -> Unit,
    onOpenService: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLockAod: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
    ) {
        item {
            SmallTitle("连接")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "连接配置",
                    summary = run {
                        com.xingyue.ufitools.monitor.data.ConnectionProfiles.ensureMigrated(context)
                        val p = com.xingyue.ufitools.monitor.data.ConnectionProfiles.getActive(context)
                        val count = com.xingyue.ufitools.monitor.data.ConnectionProfiles.list(context).size
                        buildString {
                            append(p.name)
                            append(" · ")
                            append(p.address)
                            if (count > 1) append("（$count 个配置）")
                        }
                    },
                    onClick = onOpenSetup,
                )
            }
        }
        item {
            SmallTitle("功能")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "小组件与刷新",
                    summary = "按组件 DIY 外观与内容、刷新间隔",
                    onClick = onOpenWidget,
                )
                ArrowPreference(
                    title = "通知警报",
                    summary = if (DevicePrefs.isAlertEnabled(context)) "阈值警报已开启" else "阈值警报已关闭",
                    onClick = onOpenAlert,
                )
                ArrowPreference(
                    title = "后台服务",
                    summary = if (DevicePrefs.isKeepAliveEnabled(context)) "前台保活服务已开启" else "保活服务、监控间隔、流量记录",
                    onClick = onOpenService,
                )
                ArrowPreference(
                    title = "锁屏与息屏",
                    summary = "锁屏上显示界面、息屏显示与其参数",
                    onClick = onOpenLockAod,
                )
            }
        }
        item {
            SmallTitle("个性化")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "显示与个性化",
                    summary = "主题、莫奈取色、模糊、悬浮底栏",
                    onClick = onOpenDisplay,
                )
            }
        }
        item {
            SmallTitle("其他")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "关于",
                    summary = "版本 ${BuildConfig.VERSION_NAME}",
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
