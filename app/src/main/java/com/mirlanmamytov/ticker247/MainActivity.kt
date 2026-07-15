package com.mirlanmamytov.ticker247

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mirlanmamytov.ticker247.service.TickerForegroundService
import com.mirlanmamytov.ticker247.ui.screens.SignInScreen
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Переключаемся на основную тему сразу — сплэш-фон держится до первого кадра Compose
        setTheme(R.style.Theme_Ticker247)
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ticker247_prefs", MODE_PRIVATE)
        handleDeepLink(intent)
        checkForUpdate()

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
        }
        intent?.getStringExtra("open_tab")?.takeIf { it.isNotEmpty() }?.let {
            DataBridge.pendingTab = it
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
        }
        MainHomeScreen()
    }
}
