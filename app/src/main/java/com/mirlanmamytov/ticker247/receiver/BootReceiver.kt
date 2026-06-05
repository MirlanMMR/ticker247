package com.mirlanmamytov.ticker247.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirlanmamytov.ticker247.data.datastore.AppSettings
import com.mirlanmamytov.ticker247.service.TickerForegroundService // ВОТ ЭТОТ ИМПОРТ
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appSettings: AppSettings

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wasRunning = appSettings.onboardingDoneFlow.first()
                if (wasRunning) {
                    TickerForegroundService.startService(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}