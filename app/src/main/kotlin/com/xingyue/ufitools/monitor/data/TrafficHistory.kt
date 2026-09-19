package com.xingyue.ufitools.monitor.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** 每日流量记录（存 SharedPreferences，按天保留当日最大值，保留天数可在设置中配置） */
object TrafficHistory {

    data class DayRecord(val date: String, val bytes: Long)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    /** 记录一次采集到的当日流量（取当日最大值，跨日自动新增记录） */
    fun record(ctx: Context, dailyBytes: Long) {
        if (dailyBytes < 0) return
        val json = try {
            JSONObject(DevicePrefs.getTrafficHistoryJson(ctx).ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
        val key = today()
        val prev = json.optLong(key, 0L)
        if (dailyBytes > prev) json.put(key, dailyBytes)

        // 清理超期数据
        val maxDays = DevicePrefs.getTrafficRetentionDays(ctx)
        val keys = json.keys().asSequence().toList().sorted()
        if (keys.size > maxDays) {
            keys.take(keys.size - maxDays).forEach { json.remove(it) }
        }
        DevicePrefs.setTrafficHistoryJson(ctx, json.toString())
    }

    /** 最近 [days] 天的记录，按日期升序（无数据的天不补零） */
    fun getRecent(ctx: Context, days: Int = 30): List<DayRecord> {
        val json = try {
            JSONObject(DevicePrefs.getTrafficHistoryJson(ctx).ifBlank { "{}" })
        } catch (_: Exception) {
            return emptyList()
        }
        return json.keys().asSequence()
            .map { DayRecord(it, json.optLong(it, 0L)) }
            .sortedBy { it.date }
            .toList()
            .takeLast(days)
    }

    fun clear(ctx: Context) = DevicePrefs.setTrafficHistoryJson(ctx, "")
}
