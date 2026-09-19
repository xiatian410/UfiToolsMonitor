package com.xingyue.ufitools.monitor.data

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** 一条连接配置档案（地址 + 密码哈希 + 名称） */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val address: String,
    val authToken: String = "",
    val lastModel: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 多连接配置档案。
 *
 * 活动档案会同步到 [DevicePrefs] 的 `device_address` / `auth_token`，
 * 现有接口逻辑无需改动即可生效。
 */
object ConnectionProfiles {

    private const val KEY_JSON = "conn_profiles_json"
    private const val KEY_ACTIVE = "conn_active_profile_id"
    private const val MAX_PROFILES = 12

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("ufi_tools_prefs", Context.MODE_PRIVATE)

    /**
     * 从旧版单配置迁移，或保证至少有一条配置。
     * 仅在列表为空时创建「默认」配置，不会在已有配置时额外新建。
     */
    fun ensureMigrated(ctx: Context) {
        val prefs = sp(ctx)
        val raw = prefs.getString(KEY_JSON, null)
        val list = if (raw.isNullOrBlank()) emptyList() else decode(raw)
        if (list.isEmpty()) {
            createBootstrap(ctx)
            return
        }
        val active = prefs.getString(KEY_ACTIVE, null)
        if (active.isNullOrBlank() || list.none { it.id == active }) {
            prefs.edit().putString(KEY_ACTIVE, list.first().id).apply()
            applyToRuntime(ctx, list.first(), clearCache = false)
        }
    }

    private fun createBootstrap(ctx: Context) {
        // 双重保险：若已有配置则绝不新建
        val existing = decode(sp(ctx).getString(KEY_JSON, null).orEmpty())
        if (existing.isNotEmpty()) return
        val address = DevicePrefs.getDeviceAddress(ctx)
        val token = DevicePrefs.getAuthToken(ctx)
        val model = DevicePrefs.getCachedModel(ctx)
        val profile = ConnectionProfile(
            id = newId(),
            name = "默认",
            address = address.ifBlank { DevicePrefs.DEFAULT_DEVICE_ADDRESS },
            authToken = token,
            lastModel = model,
        )
        persistAll(ctx, listOf(profile), profile.id)
        applyToRuntime(ctx, profile, clearCache = false)
    }

    fun list(ctx: Context): List<ConnectionProfile> {
        ensureMigrated(ctx)
        return decode(sp(ctx).getString(KEY_JSON, "[]") ?: "[]")
    }

    /** 是否允许删除（至少保留 1 个） */
    fun canDelete(ctx: Context): Boolean = list(ctx).size > 1

    fun getActiveId(ctx: Context): String {
        ensureMigrated(ctx)
        return sp(ctx).getString(KEY_ACTIVE, "") ?: ""
    }

    fun getActive(ctx: Context): ConnectionProfile {
        val profiles = list(ctx)
        val id = getActiveId(ctx)
        return profiles.firstOrNull { it.id == id } ?: profiles.first()
    }

    /**
     * 切换活动档案。
     * @return 是否切换成功（含已是当前档案）
     */
    fun switchTo(ctx: Context, id: String): Boolean {
        val profiles = list(ctx)
        val target = profiles.firstOrNull { it.id == id } ?: return false
        val oldAddress = DevicePrefs.getDeviceAddress(ctx)
        sp(ctx).edit().putString(KEY_ACTIVE, target.id).apply()
        applyToRuntime(ctx, target, clearCache = true, oldAddress = oldAddress)
        return true
    }

    /**
     * 用表单内容更新当前活动档案（保存连接时调用）。
     * [passwordHashOrNull] 为 null 表示保持原密码；空串表示清空密码。
     */
    fun updateActive(
        ctx: Context,
        name: String? = null,
        address: String,
        passwordHashOrNull: String? = null,
        lastModel: String? = null,
    ): ConnectionProfile {
        val profiles = list(ctx).toMutableList()
        val activeId = getActiveId(ctx)
        val index = profiles.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: 0
        val old = profiles[index]
        val oldAddress = DevicePrefs.getDeviceAddress(ctx)
        val updated = old.copy(
            name = name?.trim()?.take(24)?.ifBlank { old.name } ?: old.name,
            address = address.trim().ifBlank { DevicePrefs.DEFAULT_DEVICE_ADDRESS },
            authToken = passwordHashOrNull ?: old.authToken,
            lastModel = lastModel?.trim()?.takeIf { it.isNotEmpty() } ?: old.lastModel,
            updatedAt = System.currentTimeMillis(),
        )
        profiles[index] = updated
        persistAll(ctx, profiles, updated.id)
        applyToRuntime(ctx, updated, clearCache = true, oldAddress = oldAddress)
        return updated
    }

    /** 新建档案并切换为活动；默认复制当前地址 */
    fun create(
        ctx: Context,
        name: String,
        address: String = DevicePrefs.DEFAULT_DEVICE_ADDRESS,
        authToken: String = "",
        switchToNew: Boolean = true,
    ): ConnectionProfile? {
        val profiles = list(ctx)
        if (profiles.size >= MAX_PROFILES) return null
        val profile = ConnectionProfile(
            id = newId(),
            name = name.trim().take(24).ifBlank { "配置 ${profiles.size + 1}" },
            address = address.trim().ifBlank { DevicePrefs.DEFAULT_DEVICE_ADDRESS },
            authToken = authToken,
        )
        val next = profiles + profile
        val activeId = if (switchToNew) profile.id else getActiveId(ctx)
        persistAll(ctx, next, activeId)
        if (switchToNew) {
            val oldAddress = DevicePrefs.getDeviceAddress(ctx)
            applyToRuntime(ctx, profile, clearCache = true, oldAddress = oldAddress)
        }
        return profile
    }

    fun rename(ctx: Context, id: String, name: String): Boolean {
        val profiles = list(ctx).toMutableList()
        val index = profiles.indexOfFirst { it.id == id }
        if (index < 0) return false
        val n = name.trim().take(24).ifBlank { return false }
        profiles[index] = profiles[index].copy(name = n, updatedAt = System.currentTimeMillis())
        persistAll(ctx, profiles, getActiveId(ctx))
        return true
    }

    /**
     * 删除配置。
     * 只剩一个时直接返回 false，**不会**清空列表，也**不会**触发新建。
     */
    fun delete(ctx: Context, id: String): Boolean {
        val profiles = list(ctx)
        if (profiles.size <= 1) return false
        val next = profiles.filter { it.id != id }
        if (next.isEmpty() || next.size == profiles.size) return false
        val activeId = getActiveId(ctx)
        val newActive = next.firstOrNull { it.id == activeId } ?: next.first()
        persistAll(ctx, next, newActive.id)
        if (activeId == id || activeId != newActive.id) {
            val oldAddress = DevicePrefs.getDeviceAddress(ctx)
            applyToRuntime(ctx, newActive, clearCache = true, oldAddress = oldAddress)
        }
        return true
    }

    /** 同步最近探测到的型号到活动档案 */
    fun updateActiveModel(ctx: Context, model: String) {
        val m = model.trim()
        if (m.isEmpty()) return
        ensureMigrated(ctx)
        val profiles = list(ctx).toMutableList()
        val activeId = getActiveId(ctx)
        val index = profiles.indexOfFirst { it.id == activeId }
        if (index < 0) return
        if (profiles[index].lastModel == m) return
        profiles[index] = profiles[index].copy(lastModel = m, updatedAt = System.currentTimeMillis())
        persistAll(ctx, profiles, activeId)
    }

    fun canCreateMore(ctx: Context): Boolean = list(ctx).size < MAX_PROFILES

    fun maxCount(): Int = MAX_PROFILES

    private fun applyToRuntime(
        ctx: Context,
        profile: ConnectionProfile,
        clearCache: Boolean,
        oldAddress: String? = null,
    ) {
        // 直接写 SharedPreferences 字段，避免再回写档案造成循环
        sp(ctx).edit()
            .putString("device_address", profile.address.trim())
            .putString("auth_token", profile.authToken)
            .apply()
        if (profile.lastModel.isNotBlank()) {
            sp(ctx).edit().putString("cached_model", profile.lastModel).apply()
        }
        if (clearCache) {
            val old = oldAddress ?: ""
            if (old.isNotBlank() && old != profile.address) {
                DevicePrefs.clearDeviceCache(ctx)
            } else {
                DevicePrefs.clearProbeCache(ctx)
            }
        }
    }

    private fun persistAll(ctx: Context, profiles: List<ConnectionProfile>, activeId: String) {
        sp(ctx).edit()
            .putString(KEY_JSON, encode(profiles))
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    private fun encode(profiles: List<ConnectionProfile>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("address", p.address)
                    .put("authToken", p.authToken)
                    .put("lastModel", p.lastModel)
                    .put("updatedAt", p.updatedAt),
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<ConnectionProfile> {
        if (raw.isBlank() || raw == "null") return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val name = o.optString("name").ifBlank { "配置" }
                    val address = o.optString("address").ifBlank { DevicePrefs.DEFAULT_DEVICE_ADDRESS }
                    add(
                        ConnectionProfile(
                            id = id,
                            name = name,
                            address = address,
                            authToken = o.optString("authToken"),
                            lastModel = o.optString("lastModel"),
                            updatedAt = o.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
}
