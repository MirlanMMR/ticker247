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
        // Автозапуск после перезагрузки ОТКЛЮЧЁН: в момент загрузки система
        // перегружена, startForeground не успевает за 5 секунд →
        // ForegroundServiceDidNotStartInTime (главный краш 1.3.x в Vitals).
        // Сервис поднимется при первом открытии приложения.
    }
}