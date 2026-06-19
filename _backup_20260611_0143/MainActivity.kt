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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Переключаемся на основную тему сразу — сплэш-фон держится до первого кадра Compose
        setTheme(R.style.Theme_Ticker247)
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ticker247_prefs", MODE_PRIVATE)
        handleDeepLink(intent)

        setContent {
            AppRoot(
                isFirstLaunch = prefs.getBoolean("first_launch_done", false).not(),
                onFirstLaunchDone = {
                    prefs.edit().putBoolean("first_launch_done", true).apply()
                }
            )
        }
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
    var showSignIn by remember { mutableStateOf(isFirstLaunch) }

    // Запрашиваем разрешение на уведомления — внутри Compose,
    // после того как UI отрисован (так диалог точно появится)
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("Ticker247", "POST_NOTIFICATIONS granted: $granted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyGranted) {
                // Небольшая задержка — даём UI полностью отрисоваться
                kotlinx.coroutines.delay(800)
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showSignIn) {
        SignInScreen(
            onSignedIn = { account ->
                android.util.Log.d("Auth", "Sign-in result: ${account?.email ?: "skipped"}")
                onFirstLaunchDone()
                showSignIn = false
            }
        )
    } else {
        LaunchedEffect(Unit) {
            TickerForegroundService.startService(context)
        }
        MainHomeScreen()
    }
}
