package com.mirlanmamytov.ticker247.util

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Определяет физическую страну устройства.
 * Locale.getDefault().country — это регион СИСТЕМНОГО ЯЗЫКА, а не факт
 * местонахождения: на Samsung и др. он часто отличается от реальной страны
 * (наследуется от региона при покупке, ручных настроек языка и т.п.).
 * Приоритет: SIM-карта → сеть оператора → системная локаль (фолбэк).
 */
object DeviceCountry {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): String {
        val ctx = appContext
        if (ctx != null) {
            try {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val simCountry = tm?.simCountryIso?.uppercase()?.takeIf { it.length == 2 }
                if (simCountry != null) return simCountry
                val networkCountry = tm?.networkCountryIso?.uppercase()?.takeIf { it.length == 2 }
                if (networkCountry != null) return networkCountry
            } catch (_: Exception) {}
        }
        // WiFi-only устройство без SIM — только тогда системная локаль
        return java.util.Locale.getDefault().country.uppercase()
    }
}
