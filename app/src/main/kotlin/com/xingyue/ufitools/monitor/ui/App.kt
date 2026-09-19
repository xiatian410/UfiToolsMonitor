package com.xingyue.ufitools.monitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop as glassLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberGlassBackdrop
import com.xingyue.ufitools.monitor.ui.component.FloatingBottomBar
import com.xingyue.ufitools.monitor.ui.component.FloatingBottomBarItem
import com.xingyue.ufitools.monitor.ui.component.LocalNetworkPermissionDialog
import com.xingyue.ufitools.monitor.data.DeviceApi
import com.xingyue.ufitools.monitor.data.DevicePrefs
import com.xingyue.ufitools.monitor.data.DeviceStatus
import com.xingyue.ufitools.monitor.data.LocalNetworkPermission
import com.xingyue.ufitools.monitor.data.StatusRepository
import com.xingyue.ufitools.monitor.service.MonitorService
import com.xingyue.ufitools.monitor.ui.pages.AboutPage
import com.xingyue.ufitools.monitor.ui.pages.AlertSettingsPage
import com.xingyue.ufitools.monitor.ui.pages.ConnectionProfilesPage
import com.xingyue.ufitools.monitor.ui.pages.DashboardPage
import com.xingyue.ufitools.monitor.ui.pages.DisplaySettingsPage
import com.xingyue.ufitools.monitor.ui.pages.LockAodSettingsPage
import com.xingyue.ufitools.monitor.ui.pages.ServiceSettingsPage
import com.xingyue.ufitools.monitor.ui.pages.SettingsPage
import com.xingyue.ufitools.monitor.ui.pages.SetupPage
import com.xingyue.ufitools.monitor.ui.pages.SponsorPage
import com.xingyue.ufitools.monitor.ui.pages.WidgetKindDiyPage
import com.xingyue.ufitools.monitor.ui.pages.WidgetSettingsPage
import com.xingyue.ufitools.monitor.ui.pages.SmsPage
import com.xingyue.ufitools.monitor.appwidget.WidgetKind
import com.xingyue.ufitools.monitor.ui.pages.TrafficHistoryPage
import com.xingyue.ufitools.monitor.ui.theme.ThemeSettings
import com.xingyue.ufitools.monitor.ui.theme.createThemeController
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
)

sealed interface RootScreen : NavKey {
    data object Home : RootScreen
    data object About : RootScreen
    data object Sponsor : RootScreen
    /** 首次引导 / 编辑当前连接 */
    data object Setup : RootScreen
    /** 多连接配置列表与切换 */
    data object ConnectionProfiles : RootScreen
    data object Sms : RootScreen
    data object Traffic : RootScreen
    data object SettingsWidget : RootScreen
    /** 单个桌面小组件的 DIY 设置（kindId 见 WidgetKind.id） */
    data class SettingsWidgetDiy(val kindId: String) : RootScreen
    data object SettingsAlert : RootScreen
    data object SettingsService : RootScreen
    data object SettingsDisplay : RootScreen
    data object SettingsLockAod : RootScreen
}

@Composable
fun App(
    themeSettings: ThemeSettings,
    onThemeSettingsChange: (ThemeSettings) -> Unit,
) {
    val themeController = remember(themeSettings) { themeSettings.createThemeController() }

    MiuixTheme(
        controller = themeController,
    ) {
        val context = LocalContext.current
        val backStack = remember {
            mutableStateListOf<NavKey>(
                if (DevicePrefs.isConfigured(context)) RootScreen.Home else RootScreen.Setup,
            )
        }
        val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        LaunchedEffect(Unit) {
            if (DevicePrefs.isConfigured(context) && DevicePrefs.isKeepAliveEnabled(context)) {
                MonitorService.start(context)
            }
        }
        var blurEnabled by remember { mutableStateOf(DevicePrefs.isBlurEnabled(context)) }
        var floatingBottomBar by remember { mutableStateOf(DevicePrefs.isFloatingBottomBar(context)) }
        var floatingBottomBarBlur by remember { mutableStateOf(DevicePrefs.isFloatingBottomBarBlur(context)) }

        val entryProvider = entryProvider<NavKey> {
            entry<RootScreen.Home> {
                HomeScreen(
                    blurEnabled = blurEnabled,
                    floatingBottomBar = floatingBottomBar,
                    floatingBottomBarBlur = floatingBottomBarBlur,
                    onOpenAbout = { backStack.add(RootScreen.About) },
                    onOpenSetup = { backStack.add(RootScreen.ConnectionProfiles) },
                    onOpenSms = { backStack.add(RootScreen.Sms) },
                    onOpenTraffic = { backStack.add(RootScreen.Traffic) },
                    onOpenSettingsWidget = { backStack.add(RootScreen.SettingsWidget) },
                    onOpenSettingsAlert = { backStack.add(RootScreen.SettingsAlert) },
                    onOpenSettingsService = { backStack.add(RootScreen.SettingsService) },
                    onOpenSettingsDisplay = { backStack.add(RootScreen.SettingsDisplay) },
                    onOpenSettingsLockAod = { backStack.add(RootScreen.SettingsLockAod) },
                )
            }
            entry<RootScreen.SettingsWidget> {
                WidgetSettingsPage(
                    onBack = pop,
                    onOpenKind = { kind ->
                        backStack.add(RootScreen.SettingsWidgetDiy(kind.id))
                    },
                )
            }
            entry<RootScreen.SettingsWidgetDiy> { key ->
                val kind = WidgetKind.fromId(key.kindId) ?: WidgetKind.MAIN
                WidgetKindDiyPage(kind = kind, onBack = pop)
            }
            entry<RootScreen.SettingsAlert> {
                AlertSettingsPage(onBack = pop)
            }
            entry<RootScreen.SettingsService> {
                ServiceSettingsPage(onBack = pop)
            }
            entry<RootScreen.SettingsDisplay> {
                DisplaySettingsPage(
                    onBack = pop,
                    themeSettings = themeSettings,
                    onThemeSettingsChange = onThemeSettingsChange,
                    blurEnabled = blurEnabled,
                    onBlurEnabledChange = {
                        blurEnabled = it
                        DevicePrefs.setBlurEnabled(context, it)
                    },
                    floatingBottomBar = floatingBottomBar,
                    onFloatingBottomBarChange = {
                        floatingBottomBar = it
                        DevicePrefs.setFloatingBottomBar(context, it)
                    },
                    floatingBottomBarBlur = floatingBottomBarBlur,
                    onFloatingBottomBarBlurChange = {
                        floatingBottomBarBlur = it
                        DevicePrefs.setFloatingBottomBarBlur(context, it)
                    },
                )
            }
            entry<RootScreen.SettingsLockAod> {
                LockAodSettingsPage(onBack = pop)
            }
            entry<RootScreen.About> {
                AboutPage(
                    onBack = pop,
                    enableBlur = blurEnabled,
                    onOpenSponsor = { backStack.add(RootScreen.Sponsor) },
                )
            }
            entry<RootScreen.Sponsor> {
                SponsorPage(onBack = pop)
            }
            entry<RootScreen.Sms> {
                SmsPage(onBack = pop)
            }
            entry<RootScreen.Traffic> {
                TrafficHistoryPage(onBack = pop)
            }
            entry<RootScreen.ConnectionProfiles> {
                ConnectionProfilesPage(
                    onBack = pop,
                    onEditCurrent = { backStack.add(RootScreen.Setup) },
                )
            }
            entry<RootScreen.Setup> {
                SetupPage(
                    onDone = {
                        if (backStack.size > 1) {
                            pop()
                        } else {
                            backStack.clear()
                            backStack.add(RootScreen.Home)
                        }
                    },
                    onBack = if (backStack.size > 1) pop else null,
                )
            }
        }

        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        )

        UpdateGate {
            NavDisplay(
                entries = entries,
                onBack = pop,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    blurEnabled: Boolean,
    floatingBottomBar: Boolean,
    floatingBottomBarBlur: Boolean,
    onOpenAbout: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenTraffic: () -> Unit,
    onOpenSettingsWidget: () -> Unit,
    onOpenSettingsAlert: () -> Unit,
    onOpenSettingsService: () -> Unit,
    onOpenSettingsDisplay: () -> Unit,
    onOpenSettingsLockAod: () -> Unit,
) {
    val tabs = remember {
        listOf(
            TabSpec("仪表盘", MiuixIcons.GridView),
            TabSpec("设置", MiuixIcons.Settings),
        )
    }
    val context = LocalContext.current
    var deviceStatus by remember { mutableStateOf<DeviceStatus?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    // Android 17：未授予「本地网络」权限时局域网请求只会超时，用弹窗说明并引导授权
    var showLocalNetworkDialog by remember { mutableStateOf(false) }
    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showLocalNetworkDialog = false
            refreshTrigger++
        } else if (!LocalNetworkPermission.canShowRationale(context)) {
            // 系统已不再弹窗（永久拒绝），改由弹窗指引去系统设置手动开启
            showLocalNetworkDialog = true
        }
    }
    LaunchedEffect(Unit) {
        if (DevicePrefs.isConfigured(context) && LocalNetworkPermission.blocksCurrentDevice(context)) {
            showLocalNetworkDialog = true
        }
    }

    LaunchedEffect(refreshTrigger) {
        while (true) {
            if (DevicePrefs.isConfigured(context)) {
                isLoading = true
                when (val result = StatusRepository.refresh(context)) {
                    is DeviceApi.FetchResult.Success -> {
                        deviceStatus = result.status
                        errorMessage = null
                    }

                    is DeviceApi.FetchResult.Failure -> {
                        errorMessage = when (result.reason) {
                            DeviceApi.FetchResult.Reason.NETWORK ->
                                "设备无响应，请确认设备已开机并与手机处于同一网络"
                            DeviceApi.FetchResult.Reason.API ->
                                "设备返回异常，请检查访问密码或稍后重试"
                            DeviceApi.FetchResult.Reason.PERMISSION ->
                                "${LocalNetworkPermission.HINT}，授权后即可连接 ${LocalNetworkPermission.currentHost(context)}"
                        }
                    }
                }
                isLoading = false
            }
            delay(DevicePrefs.getRefreshIntervalSec(context).coerceAtLeast(1) * 1000L)
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = selectedTab) { tabs.size }
    val scope = rememberCoroutineScope()
    var pagerNavigationJob by remember { mutableStateOf<Job?>(null) }
    var isPagerNavigating by remember { mutableStateOf(false) }

    fun navigateToTab(targetIndex: Int) {
        if (targetIndex == selectedTab) return
        pagerNavigationJob?.cancel()
        selectedTab = targetIndex
        isPagerNavigating = true
        scope.launch {
            val job = coroutineContext[Job]
            pagerNavigationJob = job
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                )
            } finally {
                if (pagerNavigationJob == job) {
                    isPagerNavigating = false
                    pagerNavigationJob = null
                    if (pagerState.currentPage != targetIndex) {
                        selectedTab = pagerState.currentPage
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isPagerNavigating && selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }
    val scrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // 模糊/液态玻璃依赖 AGSL（android.graphics.RuntimeShader），Android 13（API 33）起才有；
    // Android 12 上只能走不带模糊的普通外观，否则创建着色器会直接崩溃。
    val blurSupported = isRuntimeShaderSupported()
    val blurActive = blurEnabled && blurSupported
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (blurActive) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else null
    val floatingBarBlurActive = blurActive && floatingBottomBarBlur
    val glassBackdrop = rememberGlassBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    LocalNetworkPermissionDialog(
        show = showLocalNetworkDialog,
        host = LocalNetworkPermission.currentHost(context),
        onDismiss = { showLocalNetworkDialog = false },
        onGrant = {
            showLocalNetworkDialog = false
            localNetworkLauncher.launch(LocalNetworkPermission.NAME)
        },
        onOpenSystemSettings = {
            showLocalNetworkDialog = false
            LocalNetworkPermission.openAppSettings(context)
        },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(backdrop = backdrop) {
                TopAppBar(
                    title = tabs[selectedTab].label,
                    largeTitle = "UFI TOOLS",
                    subtitle = deviceStatus?.deviceModel
                        ?.ifBlank { deviceStatus?.model }
                        ?.ifBlank { null }
                        ?: "随身 WiFi 状态监控",
                    color = if (backdrop != null) Color.Transparent else surfaceColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (!floatingBottomBar) {
                BlurredBar(backdrop = backdrop) {
                    NavigationBar(
                        color = if (backdrop != null) Color.Transparent else surfaceColor,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { navigateToTab(index) },
                                icon = tab.icon,
                                label = tab.label,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { padding ->
        val contentPadding = if (floatingBottomBar) {
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 12.dp + 64.dp + padding.calculateBottomPadding(),
            )
        } else padding

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .then(
                            if (floatingBottomBar && floatingBarBlurActive) {
                                Modifier.glassLayerBackdrop(glassBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                    beyondViewportPageCount = 1,
                ) { tab ->
                    when (tab) {
                        0 -> DashboardPage(
                            padding = contentPadding,
                            status = deviceStatus,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRetry = {
                                // 缺权限时重试只会再超时一次，直接把说明弹窗拉起来
                                if (LocalNetworkPermission.blocksCurrentDevice(context)) {
                                    showLocalNetworkDialog = true
                                } else {
                                    refreshTrigger++
                                }
                            },
                            onOpenSetup = onOpenSetup,
                            onOpenSms = onOpenSms,
                            onOpenTraffic = onOpenTraffic,
                        )
                        else -> SettingsPage(
                            padding = contentPadding,
                            onOpenSetup = onOpenSetup,
                            onOpenWidget = onOpenSettingsWidget,
                            onOpenAlert = onOpenSettingsAlert,
                            onOpenService = onOpenSettingsService,
                            onOpenDisplay = onOpenSettingsDisplay,
                            onOpenLockAod = onOpenSettingsLockAod,
                            onOpenAbout = onOpenAbout,
                        )
                    }
                }
            }

            if (floatingBottomBar) {
                FloatingBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp + padding.calculateBottomPadding())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    selectedIndex = { selectedTab },
                    onSelected = { index -> navigateToTab(index) },
                    backdrop = glassBackdrop,
                    tabsCount = tabs.size,
                    isBlurEnabled = floatingBarBlurActive,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        FloatingBottomBarItem(
                            onClick = { navigateToTab(index) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f * LocalDensity.current.density,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    ),
                ),
            )
        } else Modifier,
    ) {
        content()
    }
}
