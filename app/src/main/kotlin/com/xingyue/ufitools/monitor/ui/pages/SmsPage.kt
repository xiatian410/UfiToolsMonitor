package com.xingyue.ufitools.monitor.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xingyue.ufitools.monitor.R
import com.xingyue.ufitools.monitor.data.DeviceApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

/** 未读红点颜色（MIUI 风格） */
private val UnreadRed = Color(0xFFFA5151)

/** 发出气泡/悬浮按钮绿色（取自 MIUI 短信设计稿） */
private val SendGreen = Color(0xFF36D267)

/** 禁用发送按钮垫底色（素材为白色半透明，需灰底托起） */
private val SendDisabledBg = Color(0xFFB8B8B8)

/** 一个号码下聚合的会话 */
private data class SmsConversation(
    val number: String,
    /** 按时间升序（最旧在前）排列的全部短信 */
    val messages: List<DeviceApi.SmsMessage>,
    val last: DeviceApi.SmsMessage,
    val unreadCount: Int,
    /** 会话主用卡槽：最新一条有标注的 simSlot，-1 未知 */
    val primarySimSlot: Int = -1,
)

private fun sameSmsNumber(first: String, second: String): Boolean {
    val a = first.filter(Char::isDigit)
    val b = second.filter(Char::isDigit)
    if (a == b) return true
    return a.length >= 11 && b.length >= 11 && a.takeLast(11) == b.takeLast(11)
}

/** 归一化短信号码：去掉 +86 等前缀，取末 11 位数字作为会话键 */
private fun canonicalSmsKey(number: String): String {
    val digits = number.filter(Char::isDigit)
    if (digits.isEmpty()) return number.trim()
    return if (digits.length >= 11) digits.takeLast(11) else digits
}

/** 把扁平短信列表按号码聚合成会话，并按最新时间倒序 */
private fun buildConversations(all: List<DeviceApi.SmsMessage>): List<SmsConversation> {
    if (all.isEmpty()) return emptyList()
    val order = ArrayList<String>()
    val groups = LinkedHashMap<String, MutableList<DeviceApi.SmsMessage>>()
    val displayNumbers = HashMap<String, String>()
    for (m in all) {
        val key = if (m.number.isBlank()) "未知号码" else canonicalSmsKey(m.number)
        if (key !in groups) {
            groups[key] = ArrayList()
            order.add(key)
        }
        groups.getValue(key).add(m)
        val raw = m.number.trim()
        if (raw.isNotEmpty()) {
            val existing = displayNumbers[key]
            // 优先展示不带国家码前缀的写法（更短的原始号码）
            if (existing == null || raw.length < existing.length) displayNumbers[key] = raw
        }
    }
    val convos = order.map { key ->
        val msgs = groups.getValue(key).sortedWith(
            compareBy({ it.timestamp }, { it.id.toLongOrNull() ?: 0L }),
        )
        val primarySlot = msgs.asReversed().firstOrNull { it.simSlot >= 0 }?.simSlot ?: -1
        SmsConversation(
            number = displayNumbers[key] ?: key,
            messages = msgs,
            last = msgs.last(),
            unreadCount = msgs.count { it.unread },
            primarySimSlot = primarySlot,
        )
    }
    return convos.sortedByDescending { it.last.timestamp }
}

/** 短信页 —— 会话列表 + 二级会话详情，仿 MIUI 短信应用 */
@Composable
fun SmsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<DeviceApi.SmsMessage>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isMarking by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openedNumber by remember { mutableStateOf<String?>(null) }
    var convoNumber by remember { mutableStateOf("") }
    var composeOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var actionBusy by remember { mutableStateOf(false) }
    var markAllDialogOpen by remember { mutableStateOf(false) }
    // 通用版 UFI 短信接口无删除；goform 设备保留删除
    val smsDeleteSupported = remember(messages) {
        DeviceApi.isSmsDeleteSupported(context)
    }
    // 通用版双卡：列表/气泡/发送选卡
    val dualSimUi = remember(messages) {
        DeviceApi.isDualSimUiEnabled(context) ||
            messages.orEmpty().any { it.simSlot >= 0 }
    }
    val defaultSendSlot = remember(messages, dualSimUi) {
        when {
            !dualSimUi -> 0
            DeviceApi.currentSimSlot(context) >= 0 -> DeviceApi.currentSimSlot(context)
            else -> messages.orEmpty().asReversed().firstOrNull { it.simSlot >= 0 }?.simSlot ?: 0
        }
    }

    WindowDialog(
        show = markAllDialogOpen,
        title = "全部已读",
        summary = "确认将所有的短信标为已读吗？",
        onDismissRequest = { markAllDialogOpen = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { markAllDialogOpen = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确定",
                onClick = {
                    markAllDialogOpen = false
                    val unreadIds = messages.orEmpty().filter { it.unread }.map { it.id }
                    if (unreadIds.isNotEmpty() && !isMarking) {
                        isMarking = true
                        scope.launch {
                            DeviceApi.markSmsRead(context, unreadIds)
                            isMarking = false
                            refreshTrigger++
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }

    LaunchedEffect(refreshTrigger) {
        var firstLoad = true
        while (true) {
            if (firstLoad) isLoading = true
            val list = DeviceApi.fetchSmsList(context)
            if (list == null) {
                if (messages == null) loadError = true
            } else {
                messages = list
                loadError = false
            }
            isLoading = false
            firstLoad = false
            delay(3_000)
        }
    }

    val all = messages.orEmpty()
    val conversations = remember(all) { buildConversations(all) }

    val opened = openedNumber
    // 进入会话即把该号码未读标记为已读
    LaunchedEffect(opened, conversations) {
        if (opened == null) return@LaunchedEffect
        val convo = conversations.firstOrNull { sameSmsNumber(it.number, opened) }
        val ids = convo?.messages?.filter { it.unread }?.map { it.id }.orEmpty()
        if (ids.isNotEmpty()) {
            DeviceApi.markSmsRead(context, ids)
            refreshTrigger++
        }
    }

    BackHandler(enabled = composeOpen || opened != null || selectionMode) {
        when {
            composeOpen -> composeOpen = false
            opened != null -> openedNumber = null
            else -> {
                selectionMode = false
                selected.clear()
            }
        }
    }

    val q = query.trim()
    val filtered = if (q.isEmpty()) {
        conversations
    } else {
        conversations.filter { c ->
            c.number.contains(q, ignoreCase = true) ||
                c.messages.any { it.content.contains(q, ignoreCase = true) }
        }
    }
    val unreadTotal = conversations.sumOf { it.unreadCount }
    val scrollBehavior = MiuixScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = if (selectionMode) "已选择${selected.size}项" else "短信",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selected.clear()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (selectionMode) MiuixIcons.Close else MiuixIcons.Back,
                            contentDescription = if (selectionMode) "退出多选" else "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        val allSelected = filtered.isNotEmpty() && filtered.all { it.number in selected }
                        IconButton(
                            onClick = {
                                if (allSelected) {
                                    selected.clear()
                                } else {
                                    selected.clear()
                                    selected.addAll(filtered.map { it.number })
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选",
                                tint = if (allSelected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (!selectionMode && unreadTotal > 0) {
                        IconButton(
                            onClick = { if (!isMarking) markAllDialogOpen = true },
                            enabled = !isMarking,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Clear,
                                contentDescription = "全部已读",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (!selectionMode) {
                        IconButton(
                            onClick = { if (!isLoading) refreshTrigger++ },
                            enabled = !isLoading,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
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
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )
            }

            if (isLoading && messages == null) {
                item { CenterHint(loading = true, text = "正在读取短信…") }
            } else if (loadError && messages == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "读取短信失败", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "请确认设备在线且固件支持短信功能",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    CenterHint(
                        loading = false,
                        text = if (q.isNotEmpty()) "未找到匹配短信" else "暂无短信",
                    )
                }
            } else {
                items(filtered.size) { index ->
                    val convo = filtered[index]
                    ConversationRow(
                        convo = convo,
                        selectionMode = selectionMode,
                        isSelected = convo.number in selected,
                        menuOpen = menuFor == convo.number,
                        showDelete = smsDeleteSupported,
                        showSimSlot = dualSimUi,
                        onClick = {
                            if (selectionMode) {
                                if (convo.number in selected) selected.remove(convo.number)
                                else selected.add(convo.number)
                            } else {
                                convoNumber = convo.number
                                openedNumber = convo.number
                            }
                        },
                        onLongClick = { if (!selectionMode) menuFor = convo.number },
                        onDismissMenu = { menuFor = null },
                        onMenuDelete = {
                            menuFor = null
                            if (!actionBusy) {
                                actionBusy = true
                                val ids = convo.messages.map { it.id }
                                scope.launch {
                                    DeviceApi.deleteSms(context, ids)
                                    actionBusy = false
                                    refreshTrigger++
                                }
                            }
                        },
                        onMenuMultiSelect = {
                            menuFor = null
                            selected.clear()
                            selected.add(convo.number)
                            selectionMode = true
                        },
                    )
                    if (index < filtered.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 78.dp)
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.dividerLine),
                        )
                    }
                }
            }
        }
    }

    // 多选底栏：标为已读；删除仅 goform 设备显示（通用版 UFI 无删除接口）
    AnimatedVisibility(
        visible = selectionMode,
        enter = slideInVertically(animationSpec = tween(250)) { it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val hasUnreadSelected = conversations.any { it.number in selected && it.unreadCount > 0 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SelectionAction(
                icon = MiuixIcons.Ok,
                label = "标为已读",
                enabled = hasUnreadSelected && !actionBusy,
                onClick = {
                    val ids = conversations
                        .filter { it.number in selected }
                        .flatMap { c -> c.messages.filter { it.unread }.map { it.id } }
                    if (ids.isEmpty()) return@SelectionAction
                    actionBusy = true
                    scope.launch {
                        DeviceApi.markSmsRead(context, ids)
                        actionBusy = false
                        selectionMode = false
                        selected.clear()
                        refreshTrigger++
                    }
                },
            )
            if (smsDeleteSupported) {
                SelectionAction(
                    icon = MiuixIcons.Delete,
                    label = "删除",
                    enabled = selected.isNotEmpty() && !actionBusy,
                    onClick = {
                        val ids = conversations
                            .filter { it.number in selected }
                            .flatMap { c -> c.messages.map { it.id } }
                        if (ids.isEmpty()) return@SelectionAction
                        actionBusy = true
                        scope.launch {
                            DeviceApi.deleteSms(context, ids)
                            actionBusy = false
                            selectionMode = false
                            selected.clear()
                            refreshTrigger++
                        }
                    },
                )
            }
        }
    }

    // 右下角悬浮「新建短信」按钮
    AnimatedVisibility(
        visible = !composeOpen && opened == null && !selectionMode,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 24.dp, bottom = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SendGreen)
                .clickable { composeOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = "新建短信",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }

    AnimatedVisibility(
        visible = opened != null || composeOpen,
        enter = fadeIn(animationSpec = tween(1)),
        exit = fadeOut(animationSpec = tween(320)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        )
    }

    // 独立二级会话页：从右侧滑入/滑出
    AnimatedVisibility(
        visible = opened != null,
        enter = slideInHorizontally(animationSpec = tween(300)) { it },
        exit = slideOutHorizontally(animationSpec = tween(300)) { it },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f),
    ) {
        val number = convoNumber
        val convo = conversations.firstOrNull { sameSmsNumber(it.number, number) }
        ConversationPage(
            number = number,
            messages = convo?.messages.orEmpty(),
            showSimPicker = dualSimUi,
            defaultSimSlot = convo?.primarySimSlot?.takeIf { it >= 0 } ?: defaultSendSlot,
            onBack = { openedNumber = null },
            onSend = { text, simSlot, done ->
                scope.launch {
                    val ok = DeviceApi.sendSms(context, number, text, simSlot)
                    if (ok) refreshTrigger++
                    done(ok)
                }
            },
        )
    }

    // 新建短信页：从右下角悬浮按钮处展开，返回时收回成按钮
    AnimatedVisibility(
        visible = composeOpen,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f),
    ) {
        val pageScale by transition.animateFloat(
            transitionSpec = {
                tween(durationMillis = if (targetState == EnterExitState.Visible) 320 else 280)
            },
            label = "composePageScale",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0.1f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pageScale
                        scaleY = pageScale
                        transformOrigin = TransformOrigin(0.9f, 0.94f)
                    },
            ) {
                ComposePage(
                    showSimPicker = dualSimUi,
                    defaultSimSlot = defaultSendSlot,
                    onBack = { composeOpen = false },
                    onSend = { number, text, simSlot, done ->
                        scope.launch {
                            val ok = DeviceApi.sendSms(context, number, text, simSlot)
                            if (ok) {
                                refreshTrigger++
                                composeOpen = false
                                convoNumber = number
                                openedNumber = number
                            }
                            done(ok)
                        }
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Search,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索短信",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CenterHint(loading: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            InfiniteProgressIndicator()
            Spacer(Modifier.width(12.dp))
        }
        Text(text = text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 会话列表项：头像 + 号码 + 最新内容预览 + 右侧日期 + 未读数角标，支持长按菜单与多选 */
@Composable
private fun ConversationRow(
    convo: SmsConversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    menuOpen: Boolean,
    showDelete: Boolean,
    showSimSlot: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuDelete: () -> Unit,
    onMenuMultiSelect: () -> Unit,
) {
    val name = convo.number
    val hasUnread = convo.unreadCount > 0
    val dateLabel = DeviceApi.formatSmsDate(convo.last.timestamp)
        .ifEmpty { convo.last.date }
    val simSlot = convo.primarySimSlot.takeIf { it >= 0 } ?: convo.last.simSlot
    var pressOffset by remember { mutableStateOf(IntOffset.Zero) }
    val onLongPressAt: (IntOffset) -> Unit = {
        pressOffset = it
        onLongClick()
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selectionMode && isSelected) MiuixTheme.colorScheme.surfaceContainer
                    else Color.Transparent,
                )
                .pointerInput(selectionMode) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { offset ->
                            onLongPressAt(IntOffset(offset.x.roundToInt(), offset.y.roundToInt()))
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (showSimSlot && simSlot >= 0) {
                        Spacer(Modifier.width(6.dp))
                        SimSlotBadge(slot = simSlot)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dateLabel,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = convo.last.content.ifBlank { "（空内容）" },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!selectionMode && hasUnread) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(convo.unreadCount)
                    }
                }
            }
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(animationSpec = tween(180)) + scaleIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Checkbox(
                        state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                        onClick = onClick,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { pressOffset }
                .size(1.dp),
        ) {
        WindowListPopup(
            show = menuOpen,
            onDismissRequest = onDismissMenu,
        ) {
            ListPopupColumn {
                if (showDelete) {
                    DropdownImpl(
                        text = "删除",
                        optionSize = 2,
                        isSelected = false,
                        index = 0,
                        onSelectedIndexChange = { onMenuDelete() },
                    )
                    DropdownImpl(
                        text = "多选",
                        optionSize = 2,
                        isSelected = false,
                        index = 1,
                        onSelectedIndexChange = { onMenuMultiSelect() },
                    )
                } else {
                    // 通用版无删除接口：长按仅多选
                    DropdownImpl(
                        text = "多选",
                        optionSize = 1,
                        isSelected = false,
                        index = 0,
                        onSelectedIndexChange = { onMenuMultiSelect() },
                    )
                }
            }
        }
        }
    }
}

/** 多选模式底部操作项：图标 + 文字 */
@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MiuixTheme.colorScheme.onSurface
    else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(UnreadRed),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else "$count",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Avatar(name: String) {
    Image(
        painter = painterResource(
            if (isSystemInDarkTheme()) R.drawable.ic_avatar_default else R.drawable.ic_avatar_default_light,
        ),
        contentDescription = name,
        modifier = Modifier.size(46.dp),
    )
}

/** 二级会话详情页：气泡列表 + 底部输入发送 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationPage(
    number: String,
    messages: List<DeviceApi.SmsMessage>,
    showSimPicker: Boolean,
    defaultSimSlot: Int,
    onBack: () -> Unit,
    onSend: (String, Int, (Boolean) -> Unit) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var simSlot by remember(defaultSimSlot) { mutableIntStateOf(defaultSimSlot.coerceIn(0, 1)) }
    val listState = rememberLazyListState()
    val showBubbleSim = showSimPicker || messages.any { it.simSlot >= 0 }

    // 进入会话、有新消息或键盘弹起时自动滚到最底部，最新短信始终可见
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(messages.size, imeVisible) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        topBar = {
            SmallTopAppBar(
                title = number,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(messages.size) { index ->
                    val msg = messages[index]
                    val prev = messages.getOrNull(index - 1)
                    val showTime = prev == null ||
                        msg.timestamp <= 0L ||
                        prev.timestamp <= 0L ||
                        msg.timestamp - prev.timestamp >= 5 * 60 * 1000L
                    if (showTime) {
                        val label = DeviceApi.formatSmsDateTime(msg.timestamp).ifEmpty { msg.date }
                        if (label.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    MessageBubble(msg, showSimSlot = showBubbleSim)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (sendFailed) {
                Text(
                    text = "发送失败，请稍后重试",
                    fontSize = 12.sp,
                    color = UnreadRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SendBar(
                input = input,
                sending = sending,
                showSimPicker = showSimPicker,
                simSlot = simSlot,
                onSimSlotChange = { simSlot = it },
                onInputChange = {
                    input = it
                    sendFailed = false
                },
                onSend = {
                    val text = input.trim()
                    if (text.isEmpty() || sending) return@SendBar
                    sending = true
                    sendFailed = false
                    onSend(text, simSlot) { ok ->
                        sending = false
                        if (ok) input = "" else sendFailed = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: DeviceApi.SmsMessage, showSimSlot: Boolean = false) {
    val outgoing = msg.outgoing
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (showSimSlot && msg.simSlot >= 0) {
            SimSlotBadge(
                slot = msg.simSlot,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (outgoing) SendGreen else MiuixTheme.colorScheme.surfaceContainer,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SelectionContainer {
                Text(
                    text = msg.content.ifBlank { "（空内容）" },
                    fontSize = 15.sp,
                    color = if (outgoing) Color.White else MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 卡1 / 卡2 小标签 */
@Composable
private fun SimSlotBadge(slot: Int, modifier: Modifier = Modifier) {
    val label = DeviceApi.formatSimSlotLabel(slot)
    if (label.isEmpty()) return
    val bg = if (slot == 0) Color(0xFF3B82F6) else Color(0xFF8B5CF6)
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 发送前选卡：卡1 / 卡2 */
@Composable
private fun SimSlotPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "发送卡",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        listOf(0 to "卡1", 1 to "卡2").forEach { (slot, label) ->
            val selectedNow = selected == slot
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selectedNow) Color.White else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selectedNow) {
                            if (slot == 0) Color(0xFF3B82F6) else Color(0xFF8B5CF6)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onSelect(slot) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** 底部输入发送栏（MIUI 短信样式）：可选卡1/卡2 + 圆角输入框 + 绿色发送 */
@Composable
private fun SendBar(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    showSimPicker: Boolean = false,
    simSlot: Int = 0,
    onSimSlotChange: (Int) -> Unit = {},
) {
    val canSend = !sending && input.trim().isNotEmpty()
    val inputFill = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFF2F3F5)
    Column(modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer)) {
        if (showSimPicker) {
            SimSlotPicker(
                selected = simSlot.coerceIn(0, 1),
                onSelect = onSimSlotChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 128.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(inputFill)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "输入短信内容",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .padding(bottom = 1.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canSend || sending) Color.Transparent else SendDisabledBg)
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            if (sending) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    size = 18.dp,
                )
            } else {
                Image(
                    painter = painterResource(
                        if (canSend) R.drawable.ic_sms_send_enabled else R.drawable.ic_sms_send_disabled,
                    ),
                    contentDescription = "发送",
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
    }
}

/** 新建短信页：收信人输入 + 底部编辑发送，从悬浮按钮展开的独立页面 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposePage(
    showSimPicker: Boolean,
    defaultSimSlot: Int,
    onBack: () -> Unit,
    onSend: (String, String, Int, (Boolean) -> Unit) -> Unit,
) {
    var recipient by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var simSlot by remember(defaultSimSlot) { mutableIntStateOf(defaultSimSlot.coerceIn(0, 1)) }
    val recipientFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        recipientFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        topBar = {
            SmallTopAppBar(
                title = "新建短信",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "收信人:",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        sendFailed = false
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(recipientFocusRequester),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            innerTextField()
                        }
                    },
                )
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.dividerLine),
            )

            Spacer(Modifier.weight(1f))

            if (sendFailed) {
                Text(
                    text = "发送失败，请检查设备连接或短信登录状态后重试",
                    fontSize = 12.sp,
                    color = UnreadRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SendBar(
                input = input,
                sending = sending,
                showSimPicker = showSimPicker,
                simSlot = simSlot,
                onSimSlotChange = { simSlot = it },
                onInputChange = {
                    input = it
                    sendFailed = false
                },
                onSend = {
                    val number = recipient.trim()
                    val text = input.trim()
                    if (number.isEmpty() || text.isEmpty() || sending) return@SendBar
                    sending = true
                    sendFailed = false
                    onSend(number, text, simSlot) { ok ->
                        sending = false
                        if (ok) input = "" else sendFailed = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }
    }
}
