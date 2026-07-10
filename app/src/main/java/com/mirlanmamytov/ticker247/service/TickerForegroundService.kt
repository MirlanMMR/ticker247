package com.mirlanmamytov.ticker247.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mirlanmamytov.ticker247.DataBridge
import com.mirlanmamytov.ticker247.MainActivity
import com.mirlanmamytov.ticker247.R
import com.mirlanmamytov.ticker247.data.datastore.AppSettings
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.data.repository.CacheRepository
import com.mirlanmamytov.ticker247.network.ApiClient
import com.mirlanmamytov.ticker247.data.repository.FirebaseNewsRepository
import com.mirlanmamytov.ticker247.network.RSS_SOURCES
import com.mirlanmamytov.ticker247.network.RssParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.async
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import javax.inject.Inject

@AndroidEntryPoint
class TickerForegroundService : Service() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var cacheRepository: CacheRepository

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
        kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
            Log.e("Ticker247", "COROUTINE CRASH: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    )
    private var isFetchLoopRunning = false
    private var isRotationLoopRunning = false
    // Уже завибрировавшие URGENT-посты — не вибрируем повторно
    private val vibratedUrgentIds = mutableSetOf<String>()
    // Смахнутые пользователем уведомления — больше не показываем в ротации
    private val dismissedIds = mutableSetOf<String>()
    // При первой загрузке все срочные новости уже "старые" — вибрировать не нужно
    private var isInitialLoad = true
    // Rate-limit: не более 1 всплывающего алерта за 2 минуты
    private var lastAlertTime = 0L

    // BroadcastReceiver — ловит смахивание уведомления
    private val dismissReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val key = intent.getStringExtra("notification_key") ?: return
            dismissedIds.add(key)
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        val filter = android.content.IntentFilter(ACTION_DISMISSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }
        createNotificationChannels()
        com.mirlanmamytov.ticker247.data.repository.NewsBuffer.init(this)
        com.mirlanmamytov.ticker247.network.FuelPriceFetcher.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, 1001,
            buildNotification("Загрузка новостей…", "ticker_info", R.drawable.ic_lightning_white),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        // Pull-to-refresh — обновляем валюту и крипту напрямую из API (всегда актуально)
        if (intent?.action == ACTION_REFRESH) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Только финансовые данные — они меняются каждую минуту
                    val now = System.currentTimeMillis()
                    val newItems = DataBridge.newsItems.toMutableList()

                    // Обновляем валюту — база и набор зависят от локали (CurrencyProfile)
                    try {
                        val profile = com.mirlanmamytov.ticker247.util.CurrencyProfile.current()
                        val r = ApiClient.exchangeRate.getRates(profile.base)
                        val ratesText = if (r.result == "success" && r.rates != null)
                            com.mirlanmamytov.ticker247.util.CurrencyProfile.buildRatesText(r.rates) else null
                        if (ratesText != null) {
                            newItems.removeAll { it.category == "CURRENCY" }
                            newItems.add(0, NewsItem(
                                url = "", title = ratesText, summary = ratesText,
                                imageUrl = null, source = "ExchangeRate",
                                category = "CURRENCY", publishedAt = now, priority = 0
                            ))
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "Refresh currency: ${e.message}") }

                    // Обновляем крипту через CoinCap (бесплатный, без ключа)
                    try {
                        val coins = ApiClient.coinCap.getAssets().data
                        newItems.removeAll { it.category == "CRYPTO" }
                        coins.forEach { coin ->
                            val change = coin.change24h ?: 0.0
                            val price = coin.currentPrice ?: 0.0
                            newItems.add(NewsItem(
                                url = "https://coincap.io/assets/${coin.id}",
                                title = "${coin.name} (${coin.symbol})",
                                summary = "${coin.symbol} $${"%.0f".format(price)}",
                                imageUrl = coin.imageUrl,
                                source = "CoinCap", category = "CRYPTO",
                                publishedAt = now, priority = 0,
                                cryptoName = coin.name, cryptoSymbol = coin.symbol,
                                cryptoPrice = price, cryptoChange24h = change,
                                cryptoIconUrl = coin.imageUrl
                            ))
                        }
                        Log.d("Ticker247", "CoinCap: ${coins.size} coins loaded")
                    } catch (e: Exception) { Log.e("Ticker247", "Refresh crypto: ${e.message}") }

                    withContext(Dispatchers.Main) {
                        // Обновляем финансовые карточки в NewsBuffer, не трогая новости
                        com.mirlanmamytov.ticker247.data.repository.NewsBuffer.addItems(newItems)
                        val fullNews = com.mirlanmamytov.ticker247.data.repository.NewsBuffer.getSorted()
                        DataBridge.setNewsItems(fullNews)

                        // Пересобираем тикер: свежие финансы + существующие новостные заголовки
                        val tickerItems    = mutableListOf<String>()
                        val tickerCurrency = mutableListOf<String>()
                        val tickerFuel     = mutableListOf<String>()
                        val tickerCrypto   = mutableListOf<String>()
                        newItems.firstOrNull { it.category == "CURRENCY" }?.let { cur ->
                            val label = com.mirlanmamytov.ticker247.util.CurrencyProfile.current().label
                            val parts = cur.title.split(" | ")
                            parts.firstOrNull { it.startsWith("USD") }?.let { tickerItems.add("💵 $it $label") }
                            parts.firstOrNull { it.startsWith("EUR") }?.let { tickerItems.add("💶 $it $label") }
                        }
                        newItems.filter { it.category == "CRYPTO" }.take(5).forEach { coin ->
                            val price  = coin.cryptoPrice ?: 0.0
                            val change = coin.cryptoChange24h ?: 0.0
                            val arrow  = if (change >= 0) "▲" else "▼"
                            tickerItems.add("🪙 ${coin.cryptoSymbol} $${"%.0f".format(price)} $arrow${"%.1f".format(Math.abs(change))}%")
                        }
                        // Новостные заголовки — берём из текущего тикера (правильный разделитель)
                        DataBridge.tickerLine
                            .split("     ·     ")
                            .filter { it.isNotBlank() && !it.startsWith("🪙") && !it.startsWith("💵") && !it.startsWith("💶") }
                            .take(5)
                            .forEach { tickerItems.add(it) }
                        if (tickerItems.isNotEmpty()) {
                            DataBridge.tickerLine = tickerItems.joinToString("     ·     ")
                        }
                    }
                } catch (e: Exception) { Log.e("Ticker247", "Refresh: ${e.message}") }
            }
            return START_STICKY
        }

        if (!isFetchLoopRunning) startFetchLoop()
        if (!isRotationLoopRunning) startRotationLoop()
        return START_STICKY
    }

    // Загружаем данные каждые 5 минут
    private fun startFetchLoop() {
        isFetchLoopRunning = true

        serviceScope.launch(Dispatchers.IO) {
            // Загружаем spam patterns из Firebase один раз при старте
            try {
                val patterns = FirebaseNewsRepository.fetchSpamPatterns()
                if (patterns.isNotEmpty()) {
                    com.mirlanmamytov.ticker247.RemoteConfig.updateSpamPatterns(patterns)
                    Log.d("Ticker247", "RemoteConfig: ${patterns.size} spam patterns loaded")
                }
            } catch (e: Exception) {
                Log.w("Ticker247", "RemoteConfig load failed, using defaults: ${e.message}")
            }

            while (isActive) {
                try {
                    val allItems = mutableListOf<NewsItem>()
                    val tickerItems    = mutableListOf<String>()
                    val tickerCurrency = mutableListOf<String>()
                    val tickerFuel     = mutableListOf<String>()
                    val tickerCrypto   = mutableListOf<String>()
                    val tickerIndices  = mutableListOf<String>()
                    val now = System.currentTimeMillis()

                    // 1. Валюта — ExchangeRate → fallback: кэш из буфера
                    // База и набор валют зависят от локали (CurrencyProfile)
                    try {
                        val profile = com.mirlanmamytov.ticker247.util.CurrencyProfile.current()
                        val r = ApiClient.exchangeRate.getRates(profile.base)
                        val ratesText = if (r.result == "success" && r.rates != null)
                            com.mirlanmamytov.ticker247.util.CurrencyProfile.buildRatesText(r.rates) else null
                        if (ratesText != null) {
                            com.mirlanmamytov.ticker247.util.CurrencyProfile.buildTickerEntries(r.rates!!)
                                .forEach { tickerCurrency.add(it) }
                            allItems.add(NewsItem(
                                url = "", title = ratesText, summary = ratesText,
                                imageUrl = null, source = "ExchangeRate",
                                category = "CURRENCY", publishedAt = now, priority = 0
                            ))
                            Log.d("Ticker247", "Currency: OK (base=${profile.base})")
                        }
                    } catch (e: Exception) {
                        Log.w("Ticker247", "Currency ExchangeRate failed: ${e.message}, using cache")
                        // Fallback: берём последние курсы из буфера
                        com.mirlanmamytov.ticker247.data.repository.NewsBuffer.getSorted()
                            .firstOrNull { it.category == "CURRENCY" }
                            ?.let { allItems.add(it.copy(source = "ExchangeRate (кэш)")) }
                    }

                    // 2. Цены на топливо — только реальные данные, не fallback
                    try {
                        val fuel = com.mirlanmamytov.ticker247.network.FuelPriceFetcher.fetch()
                        if (fuel.isReal) {
                            com.mirlanmamytov.ticker247.network.FuelPriceFetcher.toTickerItems(fuel)
                                .forEach { tickerFuel.add(it) }
                            Log.d("Ticker247", "Fuel real: А-92=${fuel.a92}, А-95=${fuel.a95}, ДТ=${fuel.diesel}")
                        } else {
                            Log.d("Ticker247", "Fuel: нет реальных данных, пропускаем")
                        }
                    } catch (e: Exception) {
                        Log.w("Ticker247", "Fuel prices failed: ${e.message}")
                    }

                    // 3. Крипта — CoinCap → fallback: CoinGecko → fallback: кэш
                    try {
                        val cryptoOrder = listOf("BTC", "ETH", "SOL", "BNB", "XRP", "DOGE")
                        val rawCoins = ApiClient.coinCap.getAssets().data
                        if (rawCoins.isEmpty()) throw Exception("empty response")
                        val coins = (cryptoOrder.mapNotNull { sym -> rawCoins.firstOrNull { it.symbol == sym } } +
                                     rawCoins.filter { it.symbol !in cryptoOrder }).take(rawCoins.size)
                        coins.forEach { coin ->
                            val change = coin.change24h ?: 0.0
                            val price = coin.currentPrice ?: 0.0
                            val arrow = if (change >= 0) "▲" else "▼"
                            tickerCrypto.add("🪙 ${coin.symbol} $${"%.0f".format(price)} $arrow${"%.1f".format(Math.abs(change))}%")
                            allItems.add(NewsItem(
                                url = "https://coincap.io/assets/${coin.id}",
                                title = "${coin.name} (${coin.symbol})",
                                summary = "${coin.symbol} $${"%.0f".format(price)}",
                                imageUrl = coin.imageUrl,
                                source = "CoinCap", category = "CRYPTO",
                                publishedAt = now, priority = 0,
                                cryptoName = coin.name, cryptoSymbol = coin.symbol,
                                cryptoPrice = price, cryptoChange24h = change,
                                cryptoIconUrl = coin.imageUrl
                            ))
                        }
                        Log.d("Ticker247", "CoinCap: ${coins.size} coins")
                    } catch (e1: Exception) {
                        Log.w("Ticker247", "CoinCap failed: ${e1.message}, trying CoinGecko")
                        try {
                            val coins = ApiClient.coinGecko.getMarkets()
                            coins.forEach { coin ->
                                val change = coin.change24h ?: 0.0
                                val price = coin.currentPrice ?: 0.0
                                val arrow = if (change >= 0) "▲" else "▼"
                                tickerCrypto.add("🪙 ${coin.symbol.uppercase()} $${"%.0f".format(price)} $arrow${"%.1f".format(Math.abs(change))}%")
                                allItems.add(NewsItem(
                                    url = "https://www.coingecko.com/en/coins/${coin.id}",
                                    title = "${coin.name} (${coin.symbol.uppercase()})",
                                    summary = "${coin.symbol.uppercase()} $${"%.0f".format(price)}",
                                    imageUrl = coin.imageUrl,
                                    source = "CoinGecko", category = "CRYPTO",
                                    publishedAt = now, priority = 0,
                                    cryptoName = coin.name, cryptoSymbol = coin.symbol.uppercase(),
                                    cryptoPrice = price, cryptoChange24h = change,
                                    cryptoIconUrl = coin.imageUrl
                                ))
                            }
                            Log.d("Ticker247", "CoinGecko fallback: ${coins.size} coins")
                        } catch (e2: Exception) {
                            Log.w("Ticker247", "CoinGecko failed too: ${e2.message}, using cache")
                            // Fallback: последние данные из буфера
                            com.mirlanmamytov.ticker247.data.repository.NewsBuffer.getSorted()
                                .filter { it.category == "CRYPTO" }
                                .forEach { allItems.add(it.copy(source = "${it.source} (кэш)")) }
                        }
                    }

                    // 3. Вирусные видео — из Firebase
                    try {
                        val viralItems = FirebaseNewsRepository.fetchViral()
                        allItems.addAll(viralItems)
                        Log.d("Ticker247", "Viral: ${viralItems.size} videos")
                    } catch (e: Exception) { Log.e("Ticker247", "Viral: ${e.message}") }

                    // 4. Биржевые индексы из Firebase
                    try {
                        val indices = FirebaseNewsRepository.fetchIndices()
                        tickerIndices.addAll(indices)
                        Log.d("Ticker247", "Indices: ${indices.size}")
                    } catch (e: Exception) { Log.e("Ticker247", "Indices: ${e.message}") }

                    // 5. Новости — из Firebase
                    try {
                        val firebaseItems = FirebaseNewsRepository.fetchNews()
                        allItems.addAll(firebaseItems)
                        // Бегущая строка — только на языке интерфейса (мировые новости в пуле на английском)
                        val tickerCandidates = firebaseItems.filter { matchesUiLanguage(it.title) }
                        // URGENT — всегда первым в тикере
                        val urgentItem = tickerCandidates.firstOrNull { it.category == "URGENT" }
                        if (urgentItem != null) {
                            tickerItems.add(0, "🚨 " + urgentItem.title.trimStart { it == ' ' }.removePrefix("🚨").removePrefix("⚡").trimStart())
                        }
                        // Остальные важные (priority >= 2, не URGENT) — после urgent
                        tickerCandidates.filter { it.priority >= 2 && it.category != "URGENT" }.take(3)
                            .forEach { tickerItems.add("⚡ " + it.title.trimStart { it == ' ' }.removePrefix("⚡").trimStart()) }
                        Log.d("Ticker247", "Firebase: ${firebaseItems.size} items, urgent=${urgentItem != null}")
                    } catch (e: Exception) { Log.e("Ticker247", "Firebase: ${e.message}") }

                    // 5. Редакторский Telegram-канал — у каждого языкового пула свой
                    try {
                        val parser = com.mirlanmamytov.ticker247.network.TelegramParser
                        val editorialChannel = when (java.util.Locale.getDefault().language) {
                            in setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk") -> "t247feed"
                            "es" -> "ticker247feed_es"
                            "pt" -> "t247feed_pt"
                            else -> "t247feed_en"
                        }
                        val editorialSource = com.mirlanmamytov.ticker247.network.TelegramParser
                            .TelegramSource(editorialChannel, "KG", 10)
                        run {
                            val editorialItems = withContext(Dispatchers.IO) {
                                parser.fetchChannel(editorialSource)
                            }
                            allItems.addAll(editorialItems)
                            // Все посты идут в ленту, а в тикер — только помеченные редактором:
                            // СРОЧНО / BREAKING / ⚡ / #важно / #urgent в тексте поста
                            editorialItems.filter { it.category == "URGENT" }
                                .sortedByDescending { it.publishedAt }.take(2)
                                .forEach { tickerItems.add(0, "⚡ ${it.title.substringBefore('\n').trim()}") }
                            Log.d("Ticker247", "@t247feed: ${editorialItems.size} items")
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "@t247feed: ${e.message}") }


                    // Fuzzy-дедуп: убираем дубли между Telegram и Google News
                    val dedupedItems = com.mirlanmamytov.ticker247.util.FuzzyDedup.deduplicate(allItems)
                    Log.d("Ticker247", "After FuzzyDedup: ${allItems.size} → ${dedupedItems.size} items")

                    // Обновляем буфер (без повторов)
                    com.mirlanmamytov.ticker247.data.repository.NewsBuffer.addItems(dedupedItems)

                    Log.d("Ticker247", "allItems=${allItems.size} tickerItems=${tickerItems.size} buffer=${com.mirlanmamytov.ticker247.data.repository.NewsBuffer.size()}")
                    tickerItems.forEachIndexed { i, s -> Log.d("TICKER_ITEM", "$i: ${s.take(50)}") }
                    if (allItems.isEmpty()) {
                        Log.w("Ticker247", "allItems empty, skipping update")
                        delay(30_000L) // подождём 30 сек и попробуем снова
                        continue
                    }

                    withContext(Dispatchers.Main) {
                        // Сортируем: непрочитанные первыми (как Instagram)
                        val sortedForDisplay = com.mirlanmamytov.ticker247.data.repository.NewsBuffer.getSorted()
                        Log.d("Ticker247", "Updating DataBridge: ${sortedForDisplay.size} items (${com.mirlanmamytov.ticker247.data.repository.NewsBuffer.unseenCount()} unseen)")
                        // Группируем: валюта · · · крипта · · · гсм · · · новости
                        val SEP = "     ·     "
                        val GROUP_SEP = "          ·          "
                        val groups = mutableListOf<List<String>>()
                        if (tickerCurrency.isNotEmpty()) groups.add(tickerCurrency)
                        if (tickerCrypto.isNotEmpty())   groups.add(tickerCrypto)
                        if (tickerIndices.isNotEmpty())  groups.add(tickerIndices)
                        if (tickerFuel.isNotEmpty())     groups.add(tickerFuel)
                        val newsTicker = tickerItems.filter { !it.startsWith("💵") && !it.startsWith("💶") && !it.startsWith("🪙") && !it.startsWith("⛽") }
                        if (newsTicker.isNotEmpty()) groups.add(newsTicker)
                        val finalTicker = groups.joinToString(GROUP_SEP) { it.joinToString(SEP) }
                        if (finalTicker.isNotEmpty()) DataBridge.tickerLine = finalTicker
                        DataBridge.setNewsItems(sortedForDisplay)
                    }
                } catch (e: Exception) {
                    Log.e("Ticker247", "Fetch error: ${e.javaClass.simpleName}: ${e.message}", e)
                }
                delay(5 * 60_000L)
            }
        }
    }

    // Крутим уведомление каждые 8 секунд — только цифры и срочные
    private fun startRotationLoop() {
        isRotationLoopRunning = true
        serviceScope.launch {
            var index = 0
            while (isActive) {
                try {
                    // Смахнутые уведомления чистим раз в час (новости устаревают)
                    val allLines = buildRotationLines()
                    val lines = allLines.filter { l -> dismissedIds.none { l.startsWith(it) } }
                    if (lines.isEmpty()) {
                        delay(3000L)
                        continue
                    }
                    run {
                        val line = lines[index % lines.size]
                        index++
                        val urgentPrefixes = setOf("⚡","💧","🔌","⛽","♨️","🚧","🚗","🌍","🌊","🔥","🌪️","🚨","🆘","🏥","📈","✈️","📵","📅","🥊","⚠️")
                        val isUrgent = urgentPrefixes.any { line.startsWith(it) }
                        val isImportant = line.startsWith("🏆") || line.startsWith("📰")

                        // ВИБРАЦИЯ: только первый раз на каждый уникальный URGENT-пост
                        val lineKey = line.take(40)
                        val shouldVibrate = isUrgent && lineKey !in vibratedUrgentIds
                        if (shouldVibrate) {
                            vibratedUrgentIds.add(lineKey)
                            // Задержка между вибрациями — не больше одной за 30 секунд
                            delay(500L)
                        }

                        val iconRes = when {
                            isUrgent -> R.drawable.ic_lightning_urgent
                            isImportant -> R.drawable.ic_lightning_blue
                            else -> R.drawable.ic_lightning_white
                        }

                        // Фильтр давности: срочные новости старше 3 часов не показываем
                        val threeHoursAgo = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
                        val urgentItem = if (isUrgent || isImportant) {
                            DataBridge.newsItems.firstOrNull {
                                (it.category == "URGENT" || it.priority >= 2) &&
                                line.contains(it.title.take(30)) &&
                                it.publishedAt >= threeHoursAgo
                            }
                        } else null

                        // Срочная новость старше 3 часов — пропускаем
                        if (isUrgent && urgentItem == null) return@run

                        val now2 = System.currentTimeMillis()
                        val canAlert = isUrgent && shouldVibrate && now2 - lastAlertTime >= 2 * 60_000L

                        // Срочная первый раз → используем канал ticker_urgent (heads-up + вибрация)
                        // Остальные → ticker_important или ticker_info
                        // Всегда только одно уведомление (1001) — нет дублей в шторке
                        val channelId = when {
                            canAlert -> "ticker_urgent"
                            isUrgent || isImportant -> "ticker_important"
                            else -> "ticker_info"
                        }
                        if (canAlert) lastAlertTime = now2

                        val foregroundNotif = buildNotificationWithUrl(
                            line, channelId, iconRes, urgentItem?.url ?: ""
                        )
                        if (!com.mirlanmamytov.ticker247.DataBridge.isAppVisible) {
                            getSystemService(NotificationManager::class.java)?.notify(1001, foregroundNotif)
                        }
                    } // конец run
                } catch (e: Exception) {
                    Log.e("Ticker247", "Rotation error: ${e.message}", e)
                }
                delay(6000L)
            }
        }
    }

    private fun buildRotationLines(): List<String> {
        val lines = mutableListOf<String>()
        val cyrillicLangs = setOf("ru", "ky", "uk", "be", "bg", "sr", "mk")
        val userLang = com.mirlanmamytov.ticker247.data.repository.NewsBuffer.deviceLanguage
        val items = DataBridge.newsItems.filter { item ->
            val lang = item.language ?: ""
            when {
                item.category in setOf("CURRENCY", "CRYPTO") -> true
                lang.isEmpty() || lang == "unknown" || lang == "other" -> true
                userLang in cyrillicLangs -> lang in cyrillicLangs
                else -> lang == userLang
            }
        }

        // ── 1. Валюта — всегда первой строкой ────────────────────────────────
        items.firstOrNull { it.category == "CURRENCY" }?.let {
            val rates = it.title.split("|").take(3).joinToString("  ·  ") { r -> r.trim() }
            if (rates.isNotEmpty()) lines.add("💱 $rates")
        }

        // ── 2. Крипта — одна строка: BTC · ETH · SOL ────────────────────────
        val cryptoOrder = listOf("BTC","ETH","SOL","BNB","XRP","DOGE")
        val allCryptos = items.filter { it.category == "CRYPTO" }
        val sortedCryptos = (cryptoOrder.mapNotNull { sym -> allCryptos.firstOrNull { it.cryptoSymbol == sym } } +
                             allCryptos.filter { it.cryptoSymbol !in cryptoOrder }).take(3)
        val cryptoLine = sortedCryptos.mapNotNull { coin ->
            val sym   = coin.cryptoSymbol ?: return@mapNotNull null
            val price = coin.cryptoPrice ?: return@mapNotNull null
            val chg   = coin.cryptoChange24h ?: 0.0
            val arrow = if (chg >= 0) "▲" else "▼"
            "$sym \$${"%,.0f".format(price)} $arrow${"%.1f".format(Math.abs(chg))}%"
        }.joinToString("  ·  ")
        if (cryptoLine.isNotEmpty()) lines.add("₿ $cryptoLine")

        // ── 3. Срочные новости — по мере появления, поверх валюты ────────────
        // Только на языке интерфейса — непереведённые из пула не показываем
        fun titleOnly(s: String) = s.substringBefore('\n').trim().take(160)
        items.filter { it.priority >= 3 && matchesUiLanguage(it.title) }.take(2).forEach {
            lines.add(0, "🏆 ${titleOnly(it.title)}")
        }
        val cryptoSources = setOf("coingecko", "coincap", "coinmarketcap")
        items.filter { item ->
            (item.category == "URGENT" || item.priority >= 1) &&
            item.cryptoSymbol == null &&
            item.category !in setOf("CURRENCY", "CRYPTO") &&
            item.source.lowercase() !in cryptoSources &&
            item.summary.length > 20 &&
            matchesUiLanguage(item.title)
        }
            .distinctBy { it.url }
            .take(5)
            .forEach { item ->
                val t = titleOnly(item.title).lowercase()
                val prefix = when {
                    Regex("вод|водоснаб|водопровод").containsMatchIn(t) && Regex("отключ|нет|авари|перебо").containsMatchIn(t) -> "💧"
                    Regex("электр|свет|обесточ|электроэнерг").containsMatchIn(t) -> "🔌"
                    Regex("газ|газоснаб").containsMatchIn(t) && Regex("отключ|нет|авари").containsMatchIn(t) -> "⛽"
                    Regex("тепл|отоплен|горяч").containsMatchIn(t) -> "♨️"
                    Regex("перекрыт|перекрыли|перевал.{0,15}закрыт|закрыт.{0,15}перевал|дорог.{0,15}(закрыт|блокир)").containsMatchIn(t) -> "🚧"
                    Regex("пробк|затор|дтп.{0,20}(погиб|жертв)").containsMatchIn(t) -> "🚗"
                    Regex("землетрясен|сейсм|толчк").containsMatchIn(t) -> "🌍"
                    Regex("наводнен|паводк|затоплен|сель |оползен").containsMatchIn(t) -> "🌊"
                    Regex("пожар.{0,15}(погиб|жил|крупн)|крупный пожар").containsMatchIn(t) -> "🔥"
                    Regex("ураган|лавин|буря|снежн.{0,10}занос").containsMatchIn(t) -> "🌪️"
                    Regex("взрыв|теракт|стрельб").containsMatchIn(t) -> "🚨"
                    Regex("чс|чрезвычайн|эвакуац|комендантск").containsMatchIn(t) -> "🆘"
                    Regex("вспышк|эпидем|карантин|массов.{0,10}отравлен").containsMatchIn(t) -> "🏥"
                    Regex("бензин.{0,20}(дорож|дефицит)|хлеб.{0,15}(цен|дорож)").containsMatchIn(t) -> "📈"
                    Regex("аэропорт.{0,15}закрыт|рейс.{0,10}отменён").containsMatchIn(t) -> "✈️"
                    Regex("интернет.{0,15}(отключ|заблокир)|блокировк.{0,10}(telegram|whatsapp|instagram)").containsMatchIn(t) -> "📵"
                    Regex("нерабочий день|внеплановый выходн|школ.{0,15}закрыт").containsMatchIn(t) -> "📅"
                    Regex("(кыргыз|кырг).{0,40}(победил|нокаут|чемпион|золото|медаль)|джумагулов|досмагамбетов|сидаков").containsMatchIn(t) -> "🥊"
                    else -> "⚡"
                }
                // Срочные вставляем после победных но перед валютой — индекс 0 или после 🏆
                val insertAt = lines.indexOfFirst { !it.startsWith("🏆") }.takeIf { it >= 0 } ?: 0
                lines.add(insertAt, "$prefix ${titleOnly(item.title)}")
            }

        return lines
    }

    private fun buildTickerLine(): String {
        val parts = mutableListOf<String>()
        val items = DataBridge.newsItems

        // Валюта — первые 3 курса
        items.firstOrNull { it.category == "CURRENCY" }?.let {
            val rates = it.title.split("|").take(3).joinToString(" | ") { r -> r.trim() }
            if (rates.isNotEmpty()) parts.add(rates)
        }

        // Крипта — топ 2
        items.filter { it.category == "CRYPTO" }.take(2).forEach { coin ->
            val sym = coin.cryptoSymbol ?: return@forEach
            val price = coin.cryptoPrice?.let { p -> "\$${"%,.0f".format(p)}" } ?: return@forEach
            val chg = coin.cryptoChange24h?.let { c ->
                "${if (c >= 0) "▲" else "▼"}${"%.1f".format(Math.abs(c))}%"
            } ?: ""
            parts.add("$sym $price $chg".trim())
        }

        // Срочная новость если есть — только на языке интерфейса
        items.firstOrNull { it.category == "URGENT" && matchesUiLanguage(it.title) }?.let {
            parts.add("⚡ ${it.title}")
        }

        return parts.joinToString("  |  ")
    }

    // Заголовок подходит для бегущей строки только если он на языке интерфейса:
    // кириллические локали (ru/ky/kk и др.) → кириллица, остальные → латиница
    private fun matchesUiLanguage(title: String): Boolean {
        if (title.isBlank()) return false
        val cyrillic = title.count { it in 'Ѐ'..'ӿ' }
        val latin = title.count { it.lowercaseChar() in 'a'..'z' }
        val cyrillicLocales = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk")
        return if (java.util.Locale.getDefault().language in cyrillicLocales)
            cyrillic > latin else latin > cyrillic
    }

    private fun timeAgoShort(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val min = diff / 60_000
        return when {
            min < 1   -> "только что"
            min < 60  -> "$min мин назад"
            else      -> "${min / 60} ч назад"
        }
    }

    private suspend fun fetchData() = coroutineScope {
        val now = System.currentTimeMillis()

        // Обновляем Google Trends KG параллельно с остальными запросами
        launch(Dispatchers.IO) {
            try { com.mirlanmamytov.ticker247.network.TrendingFetcher.refresh() }
            catch (e: Exception) { Log.w("Ticker247", "TrendingFetcher: ${e.message}") }
        }

        // Параллельно: валюта — база и набор зависят от локали (CurrencyProfile)
        val currencyDeferred = async(Dispatchers.IO) {
            try {
                val profile = com.mirlanmamytov.ticker247.util.CurrencyProfile.current()
                val r = ApiClient.exchangeRate.getRates(profile.base)
                if (r.result == "success" && r.rates != null)
                    com.mirlanmamytov.ticker247.util.CurrencyProfile.buildRatesText(r.rates)
                else null
            } catch (e: Exception) { Log.e("Ticker247", "Currency: ${e.message}"); null }
        }

        // Параллельно: крипта с изменением за 24ч
        val cryptoDeferred = async(Dispatchers.IO) {
            try {
                ApiClient.coinGecko.getMarkets()
            } catch (e: Exception) { Log.e("Ticker247", "Crypto: ${e.message}"); emptyList() }
        }

        // Параллельно: все RSS источники
        val rssDeferred = RSS_SOURCES.map { source ->
            async(Dispatchers.IO) { RssParser.fetchFeed(source) }
        }

        // Параллельно: EV зарядка
        val evDeferred = async(Dispatchers.IO) {
            try {
                val ev = ApiClient.openChargeMap.getChargingPoints(42.8746, 74.5698, 10)
                if (ev.isNotEmpty()) ev else null
            } catch (e: Exception) { Log.e("Ticker247", "EV: ${e.message}"); null }
        }

        // Собираем
        val tickerItems = mutableListOf<String>()
        val newsItems = mutableListOf<NewsItem>()

        currencyDeferred.await()?.let { rates ->
            tickerItems.add(rates)
            newsItems.add(NewsItem(
                url = "", title = rates, summary = rates,
                imageUrl = null, source = "НБКР / ExchangeRate",
                category = "CURRENCY", publishedAt = now, priority = 0
            ))
        }

        // Крипта — отдельная карточка на каждую монету
        val cryptoList = cryptoDeferred.await()
        cryptoList.forEach { coin ->
            val change = coin.change24h ?: 0.0
            val arrow = if (change >= 0) "▲" else "▼"
            val tickerEntry = "${coin.symbol.uppercase()} $${"%,.0f".format(coin.currentPrice ?: 0.0)} $arrow${"%.1f".format(Math.abs(change))}%"
            tickerItems.add(tickerEntry)
            newsItems.add(NewsItem(
                url = "https://www.coingecko.com/en/coins/${coin.id}",
                title = "${coin.name} (${coin.symbol.uppercase()})",
                summary = tickerEntry,
                imageUrl = coin.imageUrl,
                source = "CoinGecko",
                category = "CRYPTO",
                publishedAt = now,
                priority = 0,
                cryptoName = coin.name,
                cryptoSymbol = coin.symbol.uppercase(),
                cryptoPrice = coin.currentPrice,
                cryptoChange24h = coin.change24h,
                cryptoIconUrl = coin.imageUrl
            ))
        }

        // RSS новости
        val allRssItems = rssDeferred.flatMap { it.await() }
            .sortedByDescending { it.publishedAt }
        newsItems.addAll(allRssItems)
        allRssItems.take(3).forEach { tickerItems.add(it.title) }

        // EV
        evDeferred.await()?.let { ev ->
            val evText = "EV: ${ev.size} точек зарядки | ${ev.take(2).mapNotNull { it.AddressInfo?.Title }.joinToString(" · ")}"
            tickerItems.add(evText)
            newsItems.add(NewsItem(
                url = "", title = evText, summary = evText,
                imageUrl = null, source = "OpenChargeMap",
                category = "EV", publishedAt = now, priority = 0
            ))
        }

        if (newsItems.isEmpty()) return@coroutineScope

        withContext(Dispatchers.Main) {
            DataBridge.setTickerAndNews(tickerItems, newsItems)
        }

        // Первая загрузка: молча помечаем все срочные как уже виденные
        // чтобы при старте приложения не сыпались вибрации за накопившиеся новости
        if (isInitialLoad) {
            isInitialLoad = false
            val urgentLines = buildRotationLines().filter {
                it.startsWith("⚡") || it.startsWith("🏆")
            }
            urgentLines.forEach { vibratedUrgentIds.add(it.take(40)) }
        } else {
            // При каждом новом цикле новостей — сбрасываем смахнутые:
            // пришли новые данные → старые уведомления больше не актуальны
            dismissedIds.clear()
        }
    }

    private fun urgencyChannel(category: String): String = when (category) {
        "URGENT" -> "ticker_urgent"
        "IMPORTANT", "SPORT" -> "ticker_important"
        else -> "ticker_info"
    }

    private fun urgencyIcon(category: String): Int = when (category) {
        "URGENT" -> R.drawable.ic_lightning_urgent
        "IMPORTANT", "SPORT" -> R.drawable.ic_lightning_blue
        else -> R.drawable.ic_lightning_white
    }

    private fun parseNbkrXml(xml: String): String {
        val rates = mutableListOf<String>()
        val targetCurrencies = setOf("USD", "EUR", "RUB", "CNY")
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())
            var isoCode = ""
            var value = ""
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "ISOCode" -> isoCode = parser.nextText()
                        "Value" -> value = parser.nextText().replace(",", ".")
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Currency" && isoCode in targetCurrencies) {
                            value.toDoubleOrNull()?.let {
                                rates.add("$isoCode ${"%.2f".format(it)}")
                            }
                            isoCode = ""; value = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("Ticker247", "XML: ${e.message}")
        }
        return rates.joinToString(" | ")
    }

    private fun buildNotification(text: String, channelId: String, iconRes: Int): Notification =
        buildNotificationWithUrl(text, channelId, iconRes, "")

    private fun buildNotificationWithUrl(
        text: String, channelId: String, iconRes: Int, articleUrl: String
    ): Notification {
        // Intent с URL статьи для открытия при тапе
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (articleUrl.isNotEmpty()) putExtra("article_url", articleUrl)
            if (channelId == "ticker_urgent") putExtra("open_tab", "URGENT")
        }

        // deleteIntent — срабатывает когда пользователь смахивает уведомление
        // Добавляем ключ в dismissedIds → это уведомление больше не появится в ротации
        val notificationKey = text.take(40)
        val dismissIntent = PendingIntent.getBroadcast(
            this,
            notificationKey.hashCode(),
            Intent(ACTION_DISMISSED).putExtra("notification_key", notificationKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, articleUrl.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Умный заголовок уведомления — пользователь сразу понимает тип новости
        val subText = when {
            text.startsWith("💧") -> "💧 Отключение воды"
            text.startsWith("🔌") -> "🔌 Отключение электричества"
            text.startsWith("⛽") -> "⛽ Отключение газа"
            text.startsWith("♨️") -> "♨️ Отключение отопления"
            text.startsWith("🚧") -> "🚧 Перекрытие дороги"
            text.startsWith("🚗") -> "🚗 Затор / ДТП"
            text.startsWith("🌍") -> "🌍 Землетрясение"
            text.startsWith("🌊") -> "🌊 Наводнение / Сель"
            text.startsWith("🔥") -> "🔥 Пожар"
            text.startsWith("🌪️") -> "🌪️ Стихийное бедствие"
            text.startsWith("🚨") -> "🚨 Чрезвычайное происшествие"
            text.startsWith("🆘") -> "🆘 Режим ЧС"
            text.startsWith("🏥") -> "🏥 Угроза здоровью"
            text.startsWith("📈") -> "📈 Резкий рост цен"
            text.startsWith("✈️") -> "✈️ Сбой в авиасообщении"
            text.startsWith("📵") -> "📵 Отключение интернета"
            text.startsWith("📅") -> "📅 Нерабочий день"
            text.startsWith("🥊") -> "🥊 Наш победил!"
            text.startsWith("⚠️") -> "⚠️ Важное предупреждение"
            channelId == "ticker_urgent"    -> "⚡ Срочно"
            channelId == "ticker_important" -> "📰 Важное"
            else                            -> ""
        }
        // Убираем emoji-префикс из текста — он уже в заголовке уведомления
        val allPrefixes = listOf("💧","🔌","⛽","♨️","🚧","🚗","🌍","🌊","🔥","🌪️","🚨","🆘","🏥","📈","✈️","📵","📅","🥊","⚠️","⚡","🏆","📰","💱","₿")
        var cleanText = text
        allPrefixes.forEach { cleanText = cleanText.removePrefix(it) }
        cleanText = cleanText.trimStart()

        // Убираем хвосты вида "По данным SHOT," / "Источник:" / "Подробнее:"
        // которые попадают из body новости и обрываются на полуслове
        cleanText = cleanText
            .replace(Regex("\nПо данным .{0,30}$", RegexOption.MULTILINE), "")
            .replace(Regex("\nИсточник:.{0,60}$", RegexOption.MULTILINE), "")
            .replace(Regex("\nПодробнее:.{0,60}$", RegexOption.MULTILINE), "")
            .trimEnd()

        // BigTextStyle без setContentText — Android показывает до 5 строк без обрезки.
        // setContentText дублирует и обрезает, поэтому не используем.
        val bigStyle = NotificationCompat.BigTextStyle()
            .bigText(cleanText)
            // setBigContentTitle убран — иначе дублирует "Ticker 24/7 / Ticker 24/7"

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(subText)      // "⚡ Срочно" / "💧 Отключение воды" и т.д.
            .setContentText(cleanText)     // одна строка в свёрнутом виде
            .setStyle(bigStyle)            // полный текст в развёрнутом виде
            .setOngoing(channelId == "ticker_info")
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(channelId != "ticker_info")
            .setShowWhen(channelId != "ticker_info")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(
                when (channelId) {
                    "ticker_urgent"    -> NotificationCompat.PRIORITY_HIGH
                    "ticker_important" -> NotificationCompat.PRIORITY_DEFAULT
                    else               -> NotificationCompat.PRIORITY_MIN
                }
            )
            .setContentIntent(contentIntent)
            .apply {
                // Только срочные/важные уведомления можно смахнуть — foreground нельзя
                if (channelId != "ticker_info") setDeleteIntent(dismissIntent)
            }
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Пересоздаём urgent канал чтобы применить новые настройки звука/вибрации
            manager.deleteNotificationChannel("ticker_urgent")
            NotificationChannel("ticker_info", "Информация", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }.also { manager.createNotificationChannel(it) }
            NotificationChannel("ticker_important", "Важное", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                lightColor = android.graphics.Color.BLUE; enableLights(true)
            }.also { manager.createNotificationChannel(it) }
            NotificationChannel("ticker_urgent", "Срочно", NotificationManager.IMPORTANCE_HIGH).apply {
                val urgentSound = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION
                )
                val audioAttr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(urgentSound, audioAttr)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                lightColor = android.graphics.Color.RED; enableLights(true)
            }.also { manager.createNotificationChannel(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try { unregisterReceiver(dismissReceiver) } catch (e: Exception) { /* ignore */ }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_REFRESH    = "com.mirlanmamytov.ticker247.REFRESH"
        const val ACTION_DISMISSED  = "com.mirlanmamytov.ticker247.NOTIFICATION_DISMISSED"

        fun startService(context: Context) {
            val intent = Intent(context, TickerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, TickerForegroundService::class.java)
            intent.action = ACTION_REFRESH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
