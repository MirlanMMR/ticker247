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
        // Android 15+ запрещает запуск dataSync-сервиса из BOOT_COMPLETED —
        // не пытаемся, сервис поднимется при первом открытии приложения
        if (android.os.Build.VERSION.SDK_INT >= 35) return

        // Запускаем СРАЗУ, синхронно: окно разрешения после загрузки короткое,
        // асинхронное чтение настроек его упускало (ForegroundServiceStartNotAllowed).
        // Быстрая проверка первого запуска — по SharedPreferences (синхронно)
        val firstLaunchDone = context
            .getSharedPreferences("ticker247_prefs", Context.MODE_PRIVATE)
            .getBoolean("first_launch_done", false)
        if (firstLaunchDone) {
            TickerForegroundService.startService(context)
        }
    }
}