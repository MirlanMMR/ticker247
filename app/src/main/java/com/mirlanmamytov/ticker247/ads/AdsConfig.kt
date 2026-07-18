package com.mirlanmamytov.ticker247.ads

import com.mirlanmamytov.ticker247.BuildConfig

/**
 * Идентификаторы AdMob. В debug-сборках — тестовые объявления Google
 * (клики по своим живым объявлениям = бан аккаунта AdMob).
 */
object AdsConfig {
    // Тестовый Native Advanced блок Google — безопасен для разработки
    private const val NATIVE_TEST = "ca-app-pub-3940256099942544/2247696110"

    val FEED_SLOT_1: String =
        if (BuildConfig.DEBUG) NATIVE_TEST else "ca-app-pub-6966517821505726/6964560253"

    val FEED_SLOT_2: String =
        if (BuildConfig.DEBUG) NATIVE_TEST else "ca-app-pub-6966517821505726/5001310117"
}
