package com.xingyue.ufitools.monitor.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.ConnectionProfiles
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.LocalNetworkPermission
import com.xingyue.ufitools.monitor.ui.component.LocalNetworkPermissionDialog
import com.xingyue.ufitools.monitor.data.NetClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/**
 * 连接编辑页：仅编辑当前配置的名称 / 地址 / 密码。
 * 多配置切换请使用 [ConnectionProfilesPage]。
 * 反馈使用 Miuix Snackbar。
 */
@Composable
fun SetupPage(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val isFirstRun = onBack == null

    ConnectionProfiles.ensureMigrated(context)
    val active = remember { ConnectionProfiles.getActive(context) }

    var profileName by remember { mutableStateOf(active.name) }
    // 地址栏只显示 host:port；协议单独选（手动填写时）
    var address by remember { mutableStateOf(DevicePrefs.hostPortOf(active.address)) }
    var scheme by remember {
        mutableStateOf(DevicePrefs.parseScheme(active.address))
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hasSavedPassword by remember { mutableStateOf(active.authToken.isNotEmpty()) }
    var testing by remember { mutableStateOf(false) }

    val normalized = remember(address) { DevicePrefs.sanitizeHostPort(address) }
    val livePreview = remember(scheme, normalized) {
        if (normalized.isBlank()) "" else DevicePrefs.composeEndpoint(scheme, normalized)
    }
    val addressValid = remember(normalized) {
        normalized.isNotBlank() && DevicePrefs.isValidHostPort(normalized)
    }

    val presetLabels = remember {
        listOf("手动填写", "192.168.0.1:2333", "192.168.1.1:2333", "192.168.100.1:2333")
    }
    val schemeLabels = remember { listOf("http", "https") }
    val schemeIndex = if (scheme.equals("https", ignoreCase = true)) 1 else 0
    val presetIndex = remember(normalized) {
        when (normalized) {
            "192.168.0.1:2333" -> 1
            "192.168.1.1:2333" -> 2
            "192.168.100.1:2333" -> 3
            else -> 0
        }
    }
    val isManualAddress = presetIndex == 0

    fun toast(message: String, long: Boolean = false) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    // Android 17：局域网地址需「本地网络」权限，未授权时连接只会超时。
    // 说明用弹窗给出，不占用页面内容；授权结束后继续执行原本的动作（保存并测试）。
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocalNetworkDialog by remember { mutableStateOf(false) }

    fun runPendingPermissionAction() {
        val action = pendingPermissionAction
        pendingPermissionAction = null
        action?.invoke()
    }

    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val hasAction = pendingPermissionAction != null
        // 被拒绝时也照常继续：配置仍会保存，失败原因由连接测试的提示给出
        if (!granted && !hasAction) {
            toast("未授予「本地网络」权限，无法连接局域网设备", long = true)
        }
        runPendingPermissionAction()
    }

    fun persistConfig(normalizedAddress: String, pwd: String) {
        val hashOrNull = when {
            pwd.isNotEmpty() -> {
                hasSavedPassword = true
                NetClient.sha256(pwd)
            }
            else -> null
        }
        ConnectionProfiles.updateActive(
            ctx = context,
            name = profileName,
            address = normalizedAddress,
            passwordHashOrNull = hashOrNull,
        )
        profileName = ConnectionProfiles.getActive(context).name
    }

    fun finishSuccess(fromTest: Boolean) {
        scope.launch {
            if (!isFirstRun && fromTest) delay(380)
            onDone()
        }
    }

    fun persistAndTest() {
        testing = true
        val pwd = password.trim()
        // 保存/请求都不补端口：用户写了才带，没写就是纯 host
        val endpoint = DevicePrefs.composeEndpoint(scheme, normalized)
        persistConfig(endpoint, pwd)
        address = DevicePrefs.hostPortOf(normalized)

        scope.launch {
            val result = DeviceApi.testConnectionDetailed(context)
            testing = false
            if (result.ok) {
                DevicePrefs.setConfigured(context, true)
                if (result.model.isNotBlank()) {
                    ConnectionProfiles.updateActiveModel(context, result.model)
                }
                password = ""
                toast(result.message.ifBlank { "连接成功" })
                finishSuccess(fromTest = true)
            } else {
                toast(
                    result.message.ifBlank {
                        "无法连接设备，请检查 Wi‑Fi、地址与密码"
                    },
                    long = true,
                )
            }
        }
    }

    fun saveAndTest() {
        if (testing) return
        keyboard?.hide()
        if (normalized.isBlank()) {
            toast("请填写设备地址")
            return
        }
        if (!addressValid) {
            toast(addressErrorHint(normalized), long = true)
            return
        }
        // Android 17：先用弹窗说明并申请本地网络权限，否则整次连接只会等到超时
        if (LocalNetworkPermission.isMissing(context) &&
            LocalNetworkPermission.isRestrictedHost(DevicePrefs.parseAddress(normalized).first)
        ) {
            pendingPermissionAction = { persistAndTest() }
            showLocalNetworkDialog = true
            return
        }
        persistAndTest()
    }

    fun saveWithoutTest() {
        if (testing) return
        keyboard?.hide()
        val hostPort = normalized.ifBlank {
            // 仅「仅保存」且未填写时，回落到默认常用地址（http）
            DevicePrefs.hostPortOf(DevicePrefs.DEFAULT_DEVICE_ADDRESS)
        }
        if (!DevicePrefs.isValidHostPort(hostPort)) {
            toast(addressErrorHint(hostPort), long = true)
            return
        }
        val useScheme = if (normalized.isBlank()) "http" else scheme
        val cleaned = DevicePrefs.hostPortOf(hostPort)
        val endpoint = DevicePrefs.composeEndpoint(useScheme, cleaned)
        persistConfig(endpoint, password.trim())
        address = cleaned
        scheme = useScheme
        DevicePrefs.setConfigured(context, true)
        password = ""
        toast("已保存到「${ConnectionProfiles.getActive(context).name}」")
        finishSuccess(fromTest = false)
    }

    LocalNetworkPermissionDialog(
        show = showLocalNetworkDialog,
        host = DevicePrefs.parseAddress(normalized).first,
        onDismiss = {
            showLocalNetworkDialog = false
            // 选择稍后：配置照旧保存，连接失败的原因由提示给出
            runPendingPermissionAction()
        },
        onGrant = {
            showLocalNetworkDialog = false
            localNetworkLauncher.launch(LocalNetworkPermission.NAME)
        },
        onOpenSystemSettings = {
            showLocalNetworkDialog = false
            LocalNetworkPermission.openAppSettings(context)
            runPendingPermissionAction()
        },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isFirstRun) {
                TopAppBar(
                    title = "连接配置",
                    largeTitle = "连接配置",
                    scrollBehavior = scrollBehavior,
                )
            } else {
                SmallTopAppBar(
                    title = "编辑连接",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        val back = onBack
                        if (back != null) {
                            IconButton(
                                onClick = back,
                                modifier = Modifier.padding(start = 12.dp),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    tint = colors.onSurface,
                                )
                            }
                        }
                    },
                )
            }
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
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
        ) {
            if (isFirstRun) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "连接你的随身 Wi‑Fi",
                            style = textStyles.title3,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "先连上设备热点，再填写管理地址。完成后可在「连接配置」中管理多台设备。",
                            style = textStyles.body2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item {
                SmallTitle("当前配置 · ${ConnectionProfiles.getActive(context).name}")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    TextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(24) },
                        label = "配置名称",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = address,
                        onValueChange = { raw ->
                            // 粘贴完整 URL 时拆出协议；输入中实时把全角冒号换成半角，避免误判格式
                            val t = raw.trim()
                            when {
                                t.startsWith("https://", ignoreCase = true) -> {
                                    scheme = "https"
                                    address = DevicePrefs.sanitizeHostPort(t)
                                }
                                t.startsWith("http://", ignoreCase = true) -> {
                                    scheme = "http"
                                    address = DevicePrefs.sanitizeHostPort(t)
                                }
                                else -> {
                                    // 保留用户正在输入的内容，仅替换全角符号
                                    address = raw
                                        .replace('：', ':')
                                        .replace('．', '.')
                                }
                            }
                        },
                        label = "设备地址（如 192.168.0.1 或 192.168.0.1:8080）",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        enabled = !testing,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (livePreview.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "将访问 $livePreview",
                            style = textStyles.footnote1,
                            color = if (addressValid) colors.onSurfaceVariantSummary else colors.error,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    OverlayDropdownPreference(
                        items = presetLabels,
                        selectedIndex = presetIndex,
                        onSelectedIndexChange = { index ->
                            when (index) {
                                // 手动填写：清空，端口由用户自行输入，不默认带 :2333
                                0 -> {
                                    address = ""
                                    // 协议保留当前选择，便于改地址时继续用 https
                                }
                                in 1..3 -> {
                                    address = presetLabels[index]
                                    scheme = "http" // 常用地址固定 http
                                }
                            }
                        },
                        title = "常用地址",
                        summary = if (presetIndex == 0) "手动填写" else presetLabels[presetIndex],
                        enabled = !testing,
                    )
                    // 仅手动填写时显示协议选择
                    if (isManualAddress) {
                        OverlayDropdownPreference(
                            items = schemeLabels,
                            selectedIndex = schemeIndex,
                            onSelectedIndexChange = { index ->
                                scheme = if (index == 1) "https" else "http"
                            },
                            title = "协议",
                            summary = scheme.lowercase(),
                            enabled = !testing,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = if (hasSavedPassword) "访问密码（留空保持不变）" else "访问密码（可留空）",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        enabled = !testing,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { saveAndTest() }),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !testing,
                            ) {
                                Image(
                                    painter = painterResource(
                                        if (passwordVisible) R.drawable.ic_visibility
                                        else R.drawable.ic_visibility_off,
                                    ),
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    modifier = Modifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(colors.onSurfaceVariantActions),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (hasSavedPassword) "此配置已保存密码" else "多数设备无需密码",
                        style = textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SmallTitle("操作")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Button(
                        onClick = { saveAndTest() },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        if (testing) {
                            InfiniteProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在连接…")
                        } else {
                            Text("保存并连接")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = "仅保存",
                        onClick = { saveWithoutTest() },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 校验失败提示。端口可选，仅格式错误时提示。 */
private fun addressErrorHint(hostPort: String): String {
    val s = DevicePrefs.sanitizeHostPort(hostPort)
    if (s.isBlank()) return "请填写设备地址"
    return "地址格式不正确，示例：192.168.0.1 或 192.168.0.1:8080"
}
