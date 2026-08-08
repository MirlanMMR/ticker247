package com.mirlanmamytov.ticker247

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mirlanmamytov.ticker247.service.TickerForegroundService
import com.mirlanmamytov.ticker247.ui.screens.SignInScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    // In-App Updates: приложение само предлагает обновление из Play —
    // пользователям не нужно искать ссылку на стор
    private val appUpdateManager by lazy {
        com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(this)
    }
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { /* принял или отклонил — не настаиваем */ }

    private fun checkForUpdate() {
        try {
            val updateType = com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE
            appUpdateManager.registerListener { state ->
                if (state.installStatus() ==
                    com.google.android.play.core.install.model.InstallStatus.DOWNLOADED
                ) {
                    // Обновление скачано в фоне — применяем при следующем уходе в фон
                    appUpdateManager.completeUpdate()
                }
            }
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() ==
                    com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(updateType)
                ) {
                    appUpdateManager.startUpdateFlowForResult(
                        info, updateLauncher,
                        com.google.android.play.core.appupdate.AppUpdateOptions.newBuilder(updateType).build()
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Ticker247", "In-app update: ${e.message}")
        }
    }

    // AdMob: согласие (GDPR/UMP для Европы) → инициализация SDK
    @Volatile private var adsInitialized = false
    private fun initAdsWithConsent() {
        try {
            val consentInfo = com.google.android.ump.UserMessagingPlatform.getConsentInformation(this)
            val params = com.google.android.ump.ConsentRequestParameters.Builder().build()
            consentInfo.requestConsentInfoUpdate(this, params, {
                // Экран мог закрыться пока шёл сетевой запрос — не показываем форму
                if (isDestroyed || isFinishing) return@requestConsentInfoUpdate
                com.google.android.ump.UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    if (consentInfo.canRequestAds()) initAdsSdk()
                }
            }, {
                // Не удалось получить статус (нет сети и т.п.) — пробуем без формы
                initAdsSdk()
            })
        } catch (e: Exception) {
            android.util.Log.w("Ticker247", "Ads consent: ${e.message}")
        }
    }

    private fun initAdsSdk() {
        if (adsInitialized) return
        adsInitialized = true
        Thread {
            try { com.google.android.gms.ads.MobileAds.initialize(this) }
            catch (e: Exception) { android.util.Log.w("Ticker247", "MobileAds init: ${e.message}") }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тему НЕ переключаем: брендовый сплэш (лого) виден до первого кадра
        // Compose — раньше здесь был сплошной тёмный фон = «чёрный экран»
        // на холодном старте
        // Edge-to-edge объявляем явно: с Android 15 он и так принудительный
        // для targetSdk 35+, но явный вызов даёт то же поведение и на старых
        // версиях — интерфейс выглядит одинаково везде.
        //
        // Статус-бар — тёмный под цвет бегущей строки (0xFF050508): она идёт
        // первой на экране, и светлая подложка Scaffold, просвечивая сквозь
        // прозрачную панель, разрывала бы её белой полосой. dark() заодно
        // делает иконки светлыми — читаются на чёрном.
        // Навигация внизу — наоборот, светлая: там фон ленты.
        // Обе панели — light-иконки над dark(): весь интерфейс теперь
        // нейтрально-серый/тёмный (переход со светлой темы), под каждой
        // системной панелью лежит тёмный контент
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF050508.toInt()),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Система по умолчанию рисует свою полупрозрачную подложку поверх
        // статус-бара/навигации "для контраста", независимо от цвета,
        // который мы уже задали выше через enableEdgeToEdge — та самая
        // белёсая полоска. Отключаем: контраст мы обеспечиваем сами.
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        prefs = getSharedPreferences("ticker247_prefs", MODE_PRIVATE)
        handleDeepLink(intent)
        // Тяжёлую инициализацию (обновления, реклама) откладываем — сначала
        // рисуем интерфейс, холодный старт становится заметно быстрее
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            checkForUpdate()
            initAdsWithConsent()
        }

        setContent {
            AppRoot(
                isFirstLaunch = prefs.getBoolean("first_launch_done", false).not(),
                onFirstLaunchDone = {
                    prefs.edit().putBoolean("first_launch_done", true).apply()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        DataBridge.isAppVisible = true
    }

    override fun onPause() {
        super.onPause()
        DataBridge.isAppVisible = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.getStringExtra("article_url")?.takeIf { it.isNotEmpty() }?.let {
            DataBridge.pendingArticleUrl = it
            DataBridge.pendingArticleTitle = intent.getStringExtra("article_title") ?: ""
        }
        intent?.getStringExtra("open_tab")?.takeIf { it.isNotEmpty() }?.let {
            DataBridge.pendingTab = it
        }
        // App Link: ссылка шаринга (docs/share/?url=...&title=...) — если
        // приложение установлено, Android открывает его напрямую вместо
        // веб-страницы; достаём оригинальный URL статьи из параметров
        intent?.data?.takeIf { it.host == "mirlanmmr.github.io" }?.let { uri ->
            uri.getQueryParameter("url")?.takeIf { it.isNotEmpty() }?.let { articleUrl ->
                DataBridge.pendingArticleUrl = articleUrl
                DataBridge.pendingArticleTitle = uri.getQueryParameter("title") ?: ""
            }
        }
    }
}

@Composable
fun AppRoot(
    isFirstLaunch: Boolean,
    onFirstLaunchDone: () -> Unit
) {
    val context = LocalContext.current
    // Сплэш показываем всегда — он быстрый (1.5с) и создаёт фирменный вход в приложение
    var showSplash by remember { mutableStateOf(true) }

    // Разрешение запрашиваем ПОСЛЕ сплэша — чтобы не перекрывать экран "Тихо о важном"
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("Ticker247", "POST_NOTIFICATIONS granted: $granted")
    }

    // Разрешение "работать в фоне" (игнорировать оптимизацию батареи для ЭТОГО
    // приложения) — не то же самое, что отключить общую экономию батареи.
    // Нужно в первую очередь на Samsung: агрессивный Deep Sleep убивает
    // foreground-сервис в обход обычного жизненного цикла (см. ForegroundServiceDidNotStopInTimeException)
    var showBatteryDialog by remember { mutableStateOf(false) }
    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* принял или отклонил — не настаиваем повторно */ }

    if (showBatteryDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { androidx.compose.material3.Text("Работа в фоне") },
            text = {
                androidx.compose.material3.Text(
                    "На вашем устройстве Samsung иногда принудительно выгружает " +
                    "фоновые приложения, из-за чего бегущая строка с новостями и " +
                    "курсами может пропадать. Разрешите Ticker 24/7 работать в " +
                    "фоне без ограничений — это не отключает общую экономию " +
                    "батареи телефона."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showBatteryDialog = false
                    val intent = Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    try { batteryOptLauncher.launch(intent) } catch (_: Exception) {}
                }) { androidx.compose.material3.Text("Разрешить") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBatteryDialog = false }) {
                    androidx.compose.material3.Text("Не сейчас")
                }
            }
        )
    }

    if (showSplash) {
        SignInScreen(
            isFirstLaunch = isFirstLaunch,
            onSignedIn = {
                onFirstLaunchDone()
                showSplash = false
            }
        )
    } else {
        LaunchedEffect(Unit) {
            TickerForegroundService.startService(context)
            // Запрашиваем разрешение уже после того как главный экран отрисован
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!alreadyGranted) {
                    kotlinx.coroutines.delay(1500)  // даём ленте появиться
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // Только Samsung — именно там наблюдается принудительная выгрузка
            // (see ForegroundServiceDidNotStopInTimeException). Спрашиваем один раз.
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                val prefs = context.getSharedPreferences("ticker247_prefs", android.content.Context.MODE_PRIVATE)
                val alreadyAsked = prefs.getBoolean("battery_opt_asked", false)
                val powerManager = context.getSystemService(android.os.PowerManager::class.java)
                val alreadyIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                if (!alreadyAsked && !alreadyIgnoring) {
                    kotlinx.coroutines.delay(3000)  // после запроса на уведомления
                    showBatteryDialog = true
                    prefs.edit().putBoolean("battery_opt_asked", true).apply()
                }
            }
        }
        MainHomeScreen()
    }
}
