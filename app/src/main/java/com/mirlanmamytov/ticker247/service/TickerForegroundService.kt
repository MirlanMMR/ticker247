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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        com.mirlanmamytov.ticker247.data.repository.NewsBuffer.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, 1001,
            buildNotification("Ticker 24/7", "ticker_info", R.drawable.ic_lightning_white),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        // Pull-to-refresh — обновляем валюту и крипту напрямую из API (всегда актуально)
        if (intent?.action == ACTION_REFRESH) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Только финансовые данные — они меняются каждую минуту
                    val now = System.currentTimeMillis()
                    val newItems = DataBridge.newsItems.toMutableList()

                    // Обновляем валюту
                    try {
                        val r = ApiClient.exchangeRate.getRates("USD")
                        if (r.result == "success" && r.rates != null) {
                            val kgs = r.rates["KGS"] ?: 0.0
                            fun toSom(code: String) = r.rates[code]?.let { kgs / it }
                            val parts = mutableListOf("USD ${"%.2f".format(kgs)}")
                            toSom("EUR")?.let { parts.add("EUR ${"%.2f".format(it)}") }
                            toSom("RUB")?.let { parts.add("RUB ${"%.4f".format(it)}") }
                            toSom("KZT")?.let { parts.add("KZT ${"%.4f".format(it)}") }
                            val ratesText = parts.joinToString(" | ")
                            newItems.removeAll { it.category == "CURRENCY" }
                            newItems.add(0, NewsItem(
                                url = "", title = ratesText, summary = ratesText,
                                imageUrl = null, source = "ExchangeRate",
                                category = "CURRENCY", publishedAt = now, priority = 0
                            ))
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "Refresh currency: ${e.message}") }

                    // Обновляем крипту
                    try {
                        val coins = ApiClient.coinGecko.getMarkets()
                        newItems.removeAll { it.category == "CRYPTO" }
                        coins.forEach { coin ->
                            val change = coin.change24h ?: 0.0
                            val arrow = if (change >= 0) "▲" else "▼"
                            val text = "${coin.symbol.uppercase()} $${"%.0f".format(coin.currentPrice ?: 0.0)} $arrow${"%.1f".format(Math.abs(change))}%"
                            newItems.add(NewsItem(
                                url = "https://www.coingecko.com/en/coins/${coin.id}",
                                title = "${coin.name} (${coin.symbol.uppercase()})",
                                summary = text, imageUrl = coin.imageUrl,
                                source = "CoinGecko", category = "CRYPTO",
                                publishedAt = now, priority = 0,
                                cryptoName = coin.name, cryptoSymbol = coin.symbol.uppercase(),
                                cryptoPrice = coin.currentPrice, cryptoChange24h = coin.change24h,
                                cryptoIconUrl = coin.imageUrl
                            ))
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "Refresh crypto: ${e.message}") }

                    withContext(Dispatchers.Main) {
                        DataBridge.setNewsItems(newItems)
                        DataBridge.tickerLine = newItems
                            .filter { it.category in setOf("CURRENCY", "CRYPTO") }
                            .take(5).joinToString("  ·  ") { it.title }
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
            while (isActive) {
                try {
                    val allItems = mutableListOf<NewsItem>()
                    val tickerItems = mutableListOf<String>()
                    val now = System.currentTimeMillis()

                    // 1. Валюта — всегда из прямого API
                    try {
                        val r = ApiClient.exchangeRate.getRates("USD")
                        if (r.result == "success" && r.rates != null) {
                            val kgs = r.rates["KGS"] ?: 0.0
                            fun toSom(code: String) = r.rates[code]?.let { kgs / it }
                            val parts = mutableListOf("USD ${"%.2f".format(kgs)}")
                            toSom("EUR")?.let { parts.add("EUR ${"%.2f".format(it)}") }
                            toSom("RUB")?.let { parts.add("RUB ${"%.4f".format(it)}") }
                            toSom("KZT")?.let { parts.add("KZT ${"%.4f".format(it)}") }
                            toSom("UZS")?.let { parts.add("UZS ${"%.6f".format(it)}") }
                            toSom("TRY")?.let { parts.add("TRY ${"%.3f".format(it)}") }
                            toSom("AED")?.let { parts.add("AED ${"%.2f".format(it)}") }
                            val ratesText = parts.joinToString(" | ")
                            tickerItems.add("💱 $ratesText")
                            allItems.add(NewsItem(
                                url = "", title = ratesText, summary = ratesText,
                                imageUrl = null, source = "ExchangeRate",
                                category = "CURRENCY", publishedAt = now, priority = 0
                            ))
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "Currency: ${e.message}") }

                    // 2. Крипта — всегда из CoinGecko
                    try {
                        val coins = ApiClient.coinGecko.getMarkets()
                        coins.forEach { coin ->
                            val change = coin.change24h ?: 0.0
                            val arrow = if (change >= 0) "▲" else "▼"
                            val text = "${coin.symbol.uppercase()} $${"%.0f".format(coin.currentPrice ?: 0.0)} $arrow${"%.1f".format(Math.abs(change))}%"
                            tickerItems.add("🪙 $text")
                            allItems.add(NewsItem(
                                url = "https://www.coingecko.com/en/coins/${coin.id}",
                                title = "${coin.name} (${coin.symbol.uppercase()})",
                                summary = text, imageUrl = coin.imageUrl,
                                source = "CoinGecko", category = "CRYPTO",
                                publishedAt = now, priority = 0,
                                cryptoName = coin.name, cryptoSymbol = coin.symbol.uppercase(),
                                cryptoPrice = coin.currentPrice, cryptoChange24h = coin.change24h,
                                cryptoIconUrl = coin.imageUrl
                            ))
                        }
                    } catch (e: Exception) { Log.e("Ticker247", "Crypto: ${e.message}") }

                    // 3. Вирусные видео — из Firebase
                    try {
                        val viralItems = FirebaseNewsRepository.fetchViral()
                        allItems.addAll(viralItems)
                        Log.d("Ticker247", "Viral: ${viralItems.size} videos")
                    } catch (e: Exception) { Log.e("Ticker247", "Viral: ${e.message}") }

                    // 4. Новости — из Firebase
                    try {
                        val firebaseItems = FirebaseNewsRepository.fetchNews()
                        allItems.addAll(firebaseItems)
                        firebaseItems.filter { it.priority >= 1 }.take(3)
                            .forEach { tickerItems.add(it.title) }
                        Log.d("Ticker247", "Firebase: ${firebaseItems.size} items")
                    } catch (e: Exception) { Log.e("Ticker247", "Firebase: ${e.message}") }

                    // 5. Телеграм-каналы — параллельно, по 5 каналов за раз
                    try {
                        val tgItems = com.mirlanmamytov.ticker247.network.TelegramParser.SOURCES
                            .chunked(5) // группами по 5
                            .flatMap { group ->
                                group.mapNotNull { src ->
                                    try {
                                        val items = withContext(Dispatchers.IO) {
                                            com.mirlanmamytov.ticker247.network.TelegramParser.fetchChannel(src)
                                        }
                                        items
                                    } catch (e: Exception) { null }
                                }.flatten()
                            }
                        allItems.addAll(tgItems)
                        // Срочные из Телеграма — в тикер шторки
                        tgItems.filter { it.priority >= 2 }.take(2)
                            .forEach { tickerItems.add(0, "⚡ ${it.title.take(60)}") }
                        Log.d("Ticker247", "Telegram: ${tgItems.size} items")
                    } catch (e: Exception) { Log.e("Ticker247", "Telegram: ${e.message}") }

                    // Обновляем буфер (без повторов)
                    com.mirlanmamytov.ticker247.data.repository.NewsBuffer.addItems(allItems)

                    Log.d("Ticker247", "allItems=${allItems.size} tickerItems=${tickerItems.size} buffer=${com.mirlanmamytov.ticker247.data.repository.NewsBuffer.size()}")
                    if (allItems.isEmpty()) {
                        Log.w("Ticker247", "allItems empty, skipping update")
                        delay(30_000L) // подождём 30 сек и попробуем снова
                        continue
                    }

                    withContext(Dispatchers.Main) {
                        // Сортируем: непрочитанные первыми (как Instagram)
                        val sortedForDisplay = com.mirlanmamytov.ticker247.data.repository.NewsBuffer.getSorted()
                        Log.d("Ticker247", "Updating DataBridge: ${sortedForDisplay.size} items (${com.mirlanmamytov.ticker247.data.repository.NewsBuffer.unseenCount()} unseen)")
                        DataBridge.setTickerAndNews(tickerItems, sortedForDisplay)
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
                    val lines = buildRotationLines()
                    if (lines.isNotEmpty()) {
                        val line = lines[index % lines.size]
                        index++
                        val isUrgent = line.startsWith("⚡")
                        val isImportant = line.startsWith("🏆") || line.startsWith("📰")

                        // ВИБРАЦИЯ: только первый раз на каждый уникальный URGENT-пост
                        val lineKey = line.take(40)
                        val shouldVibrate = isUrgent && lineKey !in vibratedUrgentIds
                        if (shouldVibrate) vibratedUrgentIds.add(lineKey)

                        // Повторный показ URGENT — без вибрации (используем info-канал)
                        val channelId = when {
                            isUrgent && shouldVibrate -> "ticker_urgent"   // вибрирует один раз
                            isUrgent -> "ticker_important"                  // повтор — тихо
                            isImportant -> "ticker_important"
                            else -> "ticker_info"
                        }
                        val iconRes = when {
                            isUrgent -> R.drawable.ic_lightning_urgent
                            isImportant -> R.drawable.ic_lightning_blue
                            else -> R.drawable.ic_lightning_white
                        }

                        // Находим URL статьи для deep link из уведомления
                        val urgentItem = if (isUrgent) {
                            DataBridge.newsItems.firstOrNull {
                                (it.category == "URGENT" || it.priority >= 2) &&
                                line.contains(it.title.take(30))
                            }
                        } else null

                        val notification = buildNotificationWithUrl(
                            line, channelId, iconRes, urgentItem?.url ?: ""
                        )
                        getSystemService(NotificationManager::class.java)?.notify(1001, notification)
                    }
                } catch (e: Exception) {
                    Log.e("Ticker247", "Rotation error: ${e.message}", e)
                }
                delay(6000L)
            }
        }
    }

    private fun buildRotationLines(): List<String> {
        val lines = mutableListOf<String>()
        val items = DataBridge.newsItems

        // Срочное — всегда первым если есть
        items.firstOrNull { it.priority >= 2 }?.let {
            lines.add("⚡ ${it.title.take(55)}")
        }

        // Валюта — все курсы одной строкой
        items.firstOrNull { it.category == "CURRENCY" }?.let {
            val rates = it.title.split("|").take(4).joinToString(" | ") { r -> r.trim() }
            if (rates.isNotEmpty()) lines.add(rates)
        }

        // Крипта — все монеты одной строкой
        val cryptoLine = items.filter { it.category == "CRYPTO" }.take(4).mapNotNull { coin ->
            val sym = coin.cryptoSymbol ?: return@mapNotNull null
            val price = coin.cryptoPrice?.let { p -> "\$${"%,.0f".format(p)}" } ?: return@mapNotNull null
            val chg = coin.cryptoChange24h?.let { c ->
                " ${if (c >= 0) "▲" else "▼"}${"%.1f".format(Math.abs(c))}%"
            } ?: ""
            "$sym$price$chg"
        }.joinToString("  ")
        if (cryptoLine.isNotEmpty()) lines.add(cryptoLine)

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

        // Срочная новость если есть
        items.firstOrNull { it.category == "URGENT" }?.let {
            parts.add("⚡ ${it.title.take(50)}")
        }

        return parts.joinToString("  |  ")
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

        // Параллельно: валюта
        val currencyDeferred = async(Dispatchers.IO) {
            try {
                val r = ApiClient.exchangeRate.getRates("USD")
                if (r.result == "success" && r.rates != null) {
                    val kgs = r.rates["KGS"] ?: return@async null
                    fun toSom(code: String): Double? = r.rates[code]?.let { kgs / it }

                    val parts = mutableListOf<String>()
                    parts.add("USD ${"%.2f".format(kgs)}")
                    toSom("EUR")?.let { parts.add("EUR ${"%.2f".format(it)}") }
                    toSom("RUB")?.let { parts.add("RUB ${"%.4f".format(it)}") }
                    toSom("KZT")?.let { parts.add("KZT ${"%.4f".format(it)}") }
                    toSom("UZS")?.let { parts.add("UZS ${"%.6f".format(it)}") }
                    toSom("CNY")?.let { parts.add("CNY ${"%.2f".format(it)}") }
                    toSom("TRY")?.let { parts.add("TRY ${"%.3f".format(it)}") }
                    toSom("AED")?.let { parts.add("AED ${"%.2f".format(it)}") }
                    toSom("SAR")?.let { parts.add("SAR ${"%.2f".format(it)}") }
                    toSom("JPY")?.let { parts.add("JPY ${"%.4f".format(it)}") }
                    toSom("KRW")?.let { parts.add("KRW ${"%.5f".format(it)}") }
                    parts.joinToString(" | ")
                } else null
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
        }
        val contentIntent = PendingIntent.getActivity(
            this, articleUrl.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(channelId == "ticker_info")  // только info — постоянное
            .setAutoCancel(channelId != "ticker_info") // urgent/important — закрывается при тапе
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
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
                setSound(null, null); enableVibration(true)
                vibrationPattern = longArrayOf(0, 50)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                lightColor = android.graphics.Color.RED; enableLights(true)
            }.also { manager.createNotificationChannel(it) }
        }
    }

    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_REFRESH = "com.mirlanmamytov.ticker247.REFRESH"

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
