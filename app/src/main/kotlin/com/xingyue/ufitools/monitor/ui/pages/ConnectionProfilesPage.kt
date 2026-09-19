package com.xingyue.ufitools.monitor.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xingyue.ufitools.monitor.data.ConnectionProfiles
import com.xingyue.ufitools.monitor.data.DevicePrefs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 连接配置列表：切换 / 新建 / 删除 / 进入编辑。
 * 底部用按钮操作，不另起「管理」分组。
 */
@Composable
fun ConnectionProfilesPage(
    onBack: () -> Unit,
    onEditCurrent: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    var tick by remember { mutableIntStateOf(0) }
    val profiles = remember(tick) { ConnectionProfiles.list(context) }
    val activeId = remember(tick) { ConnectionProfiles.getActiveId(context) }
    val active = remember(tick) { ConnectionProfiles.getActive(context) }
    val canDelete = profiles.size > 1
    val canCreate = ConnectionProfiles.canCreateMore(context)

    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun toast(message: String, long: Boolean = false) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "连接配置",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colors.onSurface,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SmallTitle("选择配置")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    profiles.forEach { profile ->
                        val summary = buildList {
                            add(profile.address)
                            if (profile.lastModel.isNotBlank()) add(profile.lastModel)
                            if (profile.authToken.isNotEmpty()) add("已设密码")
                        }.joinToString(" · ")
                        RadioButtonPreference(
                            title = profile.name,
                            summary = summary,
                            selected = profile.id == activeId,
                            onClick = {
                                if (profile.id == activeId) return@RadioButtonPreference
                                ConnectionProfiles.switchTo(context, profile.id)
                                tick++
                                toast("已切换到「${profile.name}」")
                            },
                        )
                    }
                }
            }

            // 操作按钮直接放在列表后面
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    TextButton(
                        text = "编辑当前连接",
                        onClick = onEditCurrent,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = if (canCreate) {
                            "新建配置（${profiles.size}/${ConnectionProfiles.maxCount()}）"
                        } else {
                            "已达上限（${ConnectionProfiles.maxCount()}）"
                        },
                        onClick = {
                            createName = "配置 ${profiles.size + 1}"
                            showCreateDialog = true
                        },
                        enabled = canCreate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canDelete) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "删除当前配置",
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "至少保留一个配置，无法删除最后一个",
                            style = textStyles.footnote1,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SmallTitle("说明")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "点选即可切换设备。地址与密码在「编辑当前连接」中修改。切换时会清理接口探测缓存，避免不同机型模式串用。",
                        style = textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    WindowDialog(
        show = showCreateDialog,
        title = "新建配置",
        summary = "创建后将自动切换，并进入编辑连接",
        onDismissRequest = { showCreateDialog = false },
    ) {
        TextField(
            value = createName,
            onValueChange = { createName = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            label = "配置名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showCreateDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "创建",
                onClick = {
                    val name = createName.trim().ifBlank { "配置 ${profiles.size + 1}" }
                    val created = ConnectionProfiles.create(
                        ctx = context,
                        name = name,
                        address = DevicePrefs.DEFAULT_DEVICE_ADDRESS,
                        authToken = "",
                        switchToNew = true,
                    )
                    showCreateDialog = false
                    createName = ""
                    if (created == null) {
                        toast("最多保存 ${ConnectionProfiles.maxCount()} 个配置")
                    } else {
                        tick++
                        toast("已新建「${created.name}」")
                        onEditCurrent()
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }

    WindowDialog(
        show = showDeleteConfirm && canDelete,
        title = "删除当前配置",
        summary = "将删除「${active.name}」，此操作不可恢复",
        onDismissRequest = { showDeleteConfirm = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "删除",
                onClick = {
                    // 再次校验：只剩一个时绝不删除，也不会新建
                    if (ConnectionProfiles.list(context).size <= 1) {
                        showDeleteConfirm = false
                        toast("至少保留一个配置")
                        tick++
                        return@TextButton
                    }
                    val name = active.name
                    val ok = ConnectionProfiles.delete(context, activeId)
                    showDeleteConfirm = false
                    tick++
                    if (!ok) {
                        toast("至少保留一个配置")
                    } else {
                        toast("已删除「$name」")
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
