package com.xingyue.ufitools.monitor.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.xingyue.ufitools.monitor.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 更新服务端对接：检查更新 / 强制更新 / 停用 / 公告 / 统计上报。
 * 传输采用 AES-256-GCM，密钥 = SHA-256(共享密钥)，报文 {"data": base64(nonce + 密文)}
 */
object UpdateApi {

    private const val SHARED_SECRET = "白毛红瞳白丝猫娘小萝莉.ufitools.monitor"

    /** 内置更新服务器地址（用户不可修改） */
    private const val UPDATE_SERVER = "https://monitor.ikuns.top"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class Notice(
        val title: String,
        val text: String,
        val link: String,
        val button: String,
    )

    data class Announcement(
        val title: String,
        val content: String,
        val link: String,
        val button: String,
    )

    data class CheckResult(
        val status: String, // ok / update / force_update / disabled
        val latestVersionName: String,
        val updateLog: String,
        val apkUrl: String,
        val notice: Notice?,
        val announcement: Announcement?,
        val statsEnabled: Boolean,
    )

    private fun aesKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(SHARED_SECRET.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(), GCMParameterSpec(128, nonce))
        return Base64.encodeToString(nonce + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    private fun decrypt(data: String): ByteArray {
        val raw = Base64.decode(data.trim(), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return cipher.doFinal(raw.copyOfRange(12, raw.size))
    }

    private fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    private fun baseUrl(): String = UPDATE_SERVER.trim().trimEnd('/')

    private fun post(url: String, payload: JSONObject): JSONObject {
        val envelope = JSONObject().put("data", encrypt(payload.toString().toByteArray(Charsets.UTF_8)))
        val request = Request.Builder()
            .url(url)
            .post(envelope.toString().toRequestBody(JSON_MEDIA))
            .build()
        NetClient.client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val outer = JSONObject(body)
            return JSONObject(String(decrypt(outer.getString("data")), Charsets.UTF_8))
        }
    }

    private fun clientPayload(ctx: Context): JSONObject = JSONObject()
        .put("version_code", BuildConfig.VERSION_CODE.toLong())
        .put("version_name", BuildConfig.VERSION_NAME)
        .put("device_id", deviceId(ctx))
        .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        .put("os_version", "Android ${Build.VERSION.RELEASE}")

    /** 检查更新，请求失败返回 null（静默跳过，不影响使用） */
    suspend fun check(ctx: Context): CheckResult? = withContext(Dispatchers.IO) {
        val base = baseUrl()
        runCatching {
            val json = post("$base/api/v1/check", clientPayload(ctx))
            val rawApk = json.optString("apk_url")
            val apkUrl = if (rawApk.startsWith("/")) base + rawApk else rawApk
            CheckResult(
                status = json.optString("status", "ok"),
                latestVersionName = json.optString("latest_version_name"),
                updateLog = json.optString("update_log"),
                apkUrl = apkUrl,
                notice = json.optJSONObject("notice")?.let {
                    val link = it.optString("link")
                    Notice(
                        title = it.optString("title"),
                        text = it.optString("text"),
                        link = if (link.startsWith("/")) base + link else link,
                        button = it.optString("button"),
                    )
                },
                announcement = json.optJSONObject("announcement")?.let {
                    Announcement(
                        title = it.optString("title"),
                        content = it.optString("content"),
                        link = it.optString("link"),
                        button = it.optString("button"),
                    )
                },
                statsEnabled = json.optBoolean("stats_enabled", false),
            )
        }.getOrNull()
    }

    /** 统计上报（服务端开启统计时由 check 结果触发），失败静默 */
    suspend fun reportStats(ctx: Context, event: String) = withContext(Dispatchers.IO) {
        val base = baseUrl()
        runCatching {
            post("$base/api/v1/stats", clientPayload(ctx).put("event", event))
        }
    }
}
