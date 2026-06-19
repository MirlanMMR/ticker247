package com.mirlanmamytov.ticker247

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.mirlanmamytov.ticker247.service.TickerForegroundService
import com.mirlanmamytov.ticker247.ui.screens.SignInScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
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
    var showSignIn by remember { mutableStateOf(isFirstLaunch) }

    if (showSignIn) {
        SignInScreen(
            onSignedIn = { account ->
                // account == null → пропустил, account != null → вошёл через Google
                android.util.Log.d("Auth", "Sign-in result: ${account?.email ?: "skipped"}")
                onFirstLaunchDone()
                showSignIn = false
                // Запускаем сервис после onboarding
                // (контекст получаем через side-effect в TickerForegroundService.startService)
            }
        )
    } else {
        // Сервис запускается здесь, после sign-in
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            TickerForegroundService.startService(context)
        }
        MainHomeScreen()
    }
}
