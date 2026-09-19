package com.xingyue.ufitools.monitor.ui.pages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.xingyue.ufitools.monitor.BuildConfig
import com.xingyue.ufitools.monitor.data.UpdateApi
import com.xingyue.ufitools.monitor.ui.pages.effect.BgEffectBackground
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope

/** 酷安个人主页（App 内跳转） */
private const val URL_COOLAPK_APP = "coolmarket://www.coolapk.com/u/30205348"

/** 酷安个人主页（浏览器兜底） */
private const val URL_COOLAPK_WEB = "https://www.coolapk.com/u/30205348"

@Composable
fun AboutPage(
    onBack: () -> Unit,
    enableBlur: Boolean,
    onOpenSponsor: () -> Unit = {},
) {
    // 需 AGSL（API 33）；判断口径必须与下方 blurEnabled 一致，否则 Android 12 会创建着色器崩溃
    val blurBackdrop = if (enableBlur && isRuntimeShaderSupported()) {
        val surfaceColor = colorScheme.surface
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else null
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
                scrollBehavior = topAppBarScrollBehavior,
                modifier =
                    if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop)
                    else Modifier,
                color =
                    if (blurBackdrop != null) Color.Transparent
                    else colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 20.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            enableBlur = enableBlur,
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            onLogoHeightChanged = { logoHeightPx = it },
            onOpenSponsor = onOpenSponsor,
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    enableBlur: Boolean,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
    onOpenSponsor: () -> Unit,
) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    var isOs3Effect by remember { mutableStateOf(true) }
    val blurEnabled = remember(enableBlur) { enableBlur && isRuntimeShaderSupported() }
    val isDark = colorScheme.background.luminance() < 0.5f
    val heroBlendColors = remember(isDark) { heroBlendColors(isDark) }
    val cardBlendColors = remember(isDark) { aboutCardBlendToken(isDark) }

    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    val openUrl = { url: String ->
        openExternalUrl(context, url)
    }
    val openCoolapk = {
        // 优先拉起酷安 App，失败则用浏览器
        if (!openExternalUrl(context, URL_COOLAPK_APP, silent = true)) {
            openExternalUrl(context, URL_COOLAPK_WEB)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    projectNameProgress = 1f
                    versionCodeProgress = 1f
                    return@onEach
                }
                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY
                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress =
                    ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay)
                        .coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                projectNameProgress =
                    ((offset.toFloat() - stage1TotalLength) / stage2TotalLength
                        .coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
            }
            .collect {}
    }

    @Composable
    fun AboutCard(
        modifier: Modifier = Modifier.padding(horizontal = 12.dp),
        content: @Composable () -> Unit,
    ) {
        Card(
            modifier = modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 60f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors(blendColors = cardBlendColors),
                enabled = blurEnabled,
            ),
            colors = CardDefaults.defaultColors(
                color =
                    if (blurEnabled) Color.Transparent
                    else colorScheme.surfaceContainer,
                contentColor = Color.Transparent,
            ),
        ) {
            content()
        }
    }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier =
            if (blurEnabled) Modifier.layerBackdrop(backdrop)
            else Modifier,
        effectBackground = true,
        isOs3Effect = isOs3Effect,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = padding.calculateTopPadding() + 92.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val baseTitleFontSize = 32.sp
                val titleLayout = remember(textMeasurer) {
                    textMeasurer.measure(
                        text = "UFI-TOOLS-Monitor",
                        style = TextStyle(
                            fontWeight = FontWeight.Black,
                            fontSize = baseTitleFontSize,
                        ),
                        softWrap = false,
                    )
                }
                val titleFontSize = with(density) {
                    val availableWidthPx = maxWidth.roundToPx().toFloat()
                    val measuredWidthPx = titleLayout.size.width.toFloat().coerceAtLeast(1f)
                    val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)
                    (baseTitleFontSize.value * scale).coerceAtLeast(24f).sp
                }
                Text(
                    text = "UFI-TOOLS-Monitor",
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .onGloballyPositioned { coordinates ->
                            if (projectNameY != 0f) return@onGloballyPositioned
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            projectNameY = y + size.height
                        }
                        .graphicsLayer {
                            alpha = 1f - projectNameProgress
                            scaleX = 1f - (projectNameProgress * 0.05f)
                            scaleY = 1f - (projectNameProgress * 0.05f)
                        }
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = heroBlendColors),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = blurEnabled,
                        ),
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleFontSize,
                )
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        " · ${BuildConfig.BUILD_TYPE.uppercase(Locale.ROOT)} BUILD",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.05f)
                        scaleY = 1f - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(top = 36.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    isOs3Effect = !isOs3Effect
                                },
                            )
                        }
                        .onSizeChanged { onLogoHeightChanged(it.height) }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {}
            }
            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = padding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AboutCard {
                        val scope = rememberCoroutineScope()
                        var checking by remember { mutableStateOf(false) }
                        var checkText by remember { mutableStateOf("点击检查") }
                        var downloadUrl by remember { mutableStateOf("") }
                        ArrowPreference(
                            title = "检查更新",
                            endActions = {
                                Text(
                                    text = checkText,
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                if (downloadUrl.isNotEmpty()) {
                                    openUrl(downloadUrl)
                                } else if (!checking) {
                                    checking = true
                                    checkText = "检查中…"
                                    scope.launch {
                                        val result = UpdateApi.check(context)
                                        checking = false
                                        when {
                                            result == null -> checkText = "检查失败，请稍后重试"
                                            result.status == "update" || result.status == "force_update" -> {
                                                downloadUrl = result.apkUrl
                                                checkText = "发现新版本 v${result.latestVersionName}，点击下载"
                                            }
                                            else -> checkText = "已是最新版本"
                                        }
                                    }
                                }
                            },
                        )
                    }
                    AboutCard {
                        ArrowPreference(
                            title = "赞助支持",
                            summary = "微信 / 支付宝扫码支持",
                            endActions = {
                                Text(
                                    text = "去赞助",
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = onOpenSponsor,
                        )
                        ArrowPreference(
                            title = "酷安",
                            summary = "点点关注喵",
                            endActions = {
                                Text(
                                    text = "@星之月喵",
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { openCoolapk() },
                        )
                    }
                    AboutCard {
                        ArrowPreference(
                            title = "Miuix",
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { openUrl("https://github.com/compose-miuix-ui/miuix") },
                        )
                    }
                }
            }
        }
    }
}

/** 打开外部链接；[silent] 为 true 时失败不提示（用于酷安 App 兜底） */
private fun openExternalUrl(
    context: Context,
    url: String,
    silent: Boolean = false,
): Boolean {
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        if (!silent) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
        false
    } catch (_: Exception) {
        if (!silent) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
        false
    }
}

private fun heroBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) listOf(
        BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
    )
    else listOf(
        BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
    )

private fun aboutCardBlendToken(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) listOf(
        BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
    )
    else listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )
