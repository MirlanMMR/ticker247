package com.mirlanmamytov.ticker247.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mirlanmamytov.ticker247.data.datastore.AppSettings
import com.mirlanmamytov.ticker247.data.repository.CacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "247_app_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appDataStore

    @Provides
    @Singleton
    fun provideAppSettings(
        dataStore: DataStore<Preferences>
    ): AppSettings = AppSettings(dataStore)

    @Provides
    @Singleton
    fun provideCacheRepository(dataStore: DataStore<Preferences>): CacheRepository =
        CacheRepository(dataStore)
}
