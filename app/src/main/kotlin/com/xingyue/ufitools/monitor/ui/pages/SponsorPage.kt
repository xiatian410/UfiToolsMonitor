package com.xingyue.ufitools.monitor.ui.pages

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingyue.ufitools.monitor.data.NetClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val URL_WECHAT_PAY = "https://api.ikuns.top/z.php?WeChatPay"
private const val URL_ALIPAY = "https://api.ikuns.top/z.php?Alipay"

/** 赞助支持：应用内展示微信 / 支付宝收款码（Miuix 风格） */
@Composable
fun SponsorPage(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val colors = MiuixTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "赞助支持",
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "感谢你的支持",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "如果本应用对你有帮助，欢迎通过微信或支付宝赞助。所得将用于服务器与后续维护，十分感谢。",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariantSummary,
                        lineHeight = 20.sp,
                    )
                }
            }

            item {
                SmallTitle("微信支付")
                PayQrCard(
                    title = "微信扫一扫",
                    subtitle = "打开微信扫码完成赞助",
                    accent = Color(0xFF07C160),
                    imageUrl = URL_WECHAT_PAY,
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SmallTitle("支付宝")
                PayQrCard(
                    title = "支付宝扫一扫",
                    subtitle = "打开支付宝扫码完成赞助",
                    accent = Color(0xFF1677FF),
                    imageUrl = URL_ALIPAY,
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "扫码即可支持 · 感谢你的每一份心意",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun PayQrCard(
    title: String,
    subtitle: String,
    accent: Color,
    imageUrl: String,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 4.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            RemoteQrImage(
                url = imageUrl,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun RemoteQrImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf(false) }
    var reloadKey by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url, reloadKey) {
        loading = true
        error = false
        bitmap = null
        val loaded = withContext(Dispatchers.IO) {
            loadBitmapFromUrl(url)
        }
        bitmap = loaded
        loading = false
        error = loaded == null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> {
                InfiniteProgressIndicator(modifier = Modifier.size(28.dp))
            }
            error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "收款码加载失败",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        text = "重新加载",
                        onClick = { reloadKey++ },
                    )
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "收款码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun loadBitmapFromUrl(url: String): ImageBitmap? {
    return try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "UFI-TOOLS-Monitor")
            .get()
            .build()
        NetClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
