package com.mirlanmamytov.ticker247.util

import android.content.Context
import com.mirlanmamytov.ticker247.data.model.DigestItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище дайджеста (SharedPreferences — переживает перезапуск
 * процесса, важно т.к. Worker и UI могут выполняться в разных процессах жизни).
 *
 * Хранит:
 * 1. Топ-3 новости за каждый из последних 7 дней (для сборки недельного дайджеста)
 * 2. Последний показанный дайджест (для карточки в ленте)
 */
object DigestStore {
    private const val PREFS = "ticker247_digest"
    private const val KEY_DAY_KEYS = "day_keys"       // "2026-08-01,2026-08-02,..."
    private const val KEY_DAY_PREFIX = "day_"
    private const val KEY_LATEST = "latest_digest"
    private const val KEY_DISMISSED_TS = "dismissed_ts"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun itemsToJson(items: List<DigestItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("title", item.title)
                put("url", item.url)
                put("source", item.source)
            })
        }
        return arr.toString()
    }

    private fun itemsFromJson(json: String?): List<DigestItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DigestItem(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    source = o.optString("source")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Сохраняет топ-3 за конкретный день (dateKey формата "yyyy-MM-dd"), храним последние 7 */
    fun saveDailyTop(context: Context, dateKey: String, items: List<DigestItem>) {
        val p = prefs(context)
        val keys = (p.getString(KEY_DAY_KEYS, "") ?: "")
            .split(",").filter { it.isNotBlank() }.toMutableList()
        if (dateKey !in keys) keys.add(dateKey)
        val trimmed = keys.sorted().takeLast(7)
        val editor = p.edit()
        // Чистим записи дней, которые выпали из окна в 7 дней
        (keys - trimmed.toSet()).forEach { editor.remove(KEY_DAY_PREFIX + it) }
        editor.putString(KEY_DAY_KEYS, trimmed.joinToString(","))
        editor.putString(KEY_DAY_PREFIX + dateKey, itemsToJson(items))
        editor.apply()
    }

    /** Топ-3 за каждый из последних 7 дней, одним списком (для недельного дайджеста) */
    fun getLast7DaysTop(context: Context): List<DigestItem> {
        val p = prefs(context)
        val keys = (p.getString(KEY_DAY_KEYS, "") ?: "").split(",").filter { it.isNotBlank() }
        return keys.sorted().flatMap { itemsFromJson(p.getString(KEY_DAY_PREFIX + it, null)) }
    }

    data class LatestDigest(val type: String, val timestampMs: Long, val items: List<DigestItem>)

    fun saveLatestDigest(context: Context, type: String, items: List<DigestItem>) {
        val obj = JSONObject().apply {
            put("type", type)
            put("ts", System.currentTimeMillis())
            put("items", JSONArray(itemsToJson(items)))
        }
        prefs(context).edit().putString(KEY_LATEST, obj.toString()).apply()
    }

    /** Последний дайджест — null если его нет, он старше 2 суток, или пользователь его закрыл */
    fun getLatestDigest(context: Context): LatestDigest? {
        val p = prefs(context)
        val json = p.getString(KEY_LATEST, null) ?: return null
        return try {
            val o = JSONObject(json)
            val ts = o.optLong("ts", 0L)
            val dismissedTs = p.getLong(KEY_DISMISSED_TS, 0L)
            if (ts <= dismissedTs) return null
            if (System.currentTimeMillis() - ts > 48 * 3600_000L) return null
            LatestDigest(
                type = o.optString("type", "daily"),
                timestampMs = ts,
                items = itemsFromJson(o.optJSONArray("items")?.toString())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun dismissLatest(context: Context) {
        val current = getLatestDigest(context) ?: return
        prefs(context).edit().putLong(KEY_DISMISSED_TS, current.timestampMs).apply()
    }
}
