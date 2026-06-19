package com.mirlanmamytov.ticker247.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_CONTENT_REGION  = stringPreferencesKey("content_region")
    }

    val onboardingDoneFlow: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }

    suspend fun saveContentRegion(region: String) {
        dataStore.edit { prefs -> prefs[KEY_CONTENT_REGION] = region }
    }

    suspend fun setOnboardingDone(isDone: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = isDone }
    }
}
