package com.mirlanmamytov.ticker247.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    data class TickerItem(
        val label: String,
        val value: String,
        val isEmergency: Boolean = false
    )

    data class TickerData(
        val items: List<TickerItem> = emptyList(),
        val hasEmergency: Boolean = false,
        val isJustUpdated: Boolean = false
    )

    companion object {
        private val KEY_TICKER_JSON    = stringPreferencesKey("ticker_cache_json")
        private val KEY_HAS_EMERGENCY  = booleanPreferencesKey("ticker_has_emergency")
        private val KEY_IS_FRESH       = booleanPreferencesKey("ticker_is_fresh")
    }

    val tickerTextFlow: Flow<TickerData> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            TickerData(
                items        = parseTickerJson(prefs[KEY_TICKER_JSON] ?: ""),
                hasEmergency = prefs[KEY_HAS_EMERGENCY] ?: false,
                isJustUpdated = prefs[KEY_IS_FRESH] ?: false
            )
        }

    suspend fun updateTickerData(
        items: List<TickerItem>,
        hasEmergency: Boolean,
        isJustUpdated: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TICKER_JSON]   = serializeItems(items)
            prefs[KEY_HAS_EMERGENCY] = hasEmergency
            prefs[KEY_IS_FRESH]      = isJustUpdated
        }
    }

    suspend fun updateEmergencyData(
        emergencyText: String?,
        utilityText: String?,
        isJustUpdated: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_EMERGENCY] = (emergencyText != null || utilityText != null)
            prefs[KEY_IS_FRESH]      = isJustUpdated
        }
    }

    suspend fun resetFreshFlag() {
        dataStore.edit { prefs -> prefs[KEY_IS_FRESH] = false }
    }

    // ─── Сериализация (без сторонних библиотек) ───────────────

    private fun serializeItems(items: List<TickerItem>): String =
        items.joinToString("|") { "${it.label}::${it.value}::${it.isEmergency}" }

    private fun parseTickerJson(raw: String): List<TickerItem> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { part ->
            val s = part.split("::")
            if (s.size >= 2) TickerItem(
                label       = s[0],
                value       = s[1],
                isEmergency = s.getOrNull(2)?.toBoolean() ?: false
            ) else null
        }
    }
}