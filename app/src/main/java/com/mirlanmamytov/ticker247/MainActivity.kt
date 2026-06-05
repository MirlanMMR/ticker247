package com.mirlanmamytov.ticker247

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mirlanmamytov.ticker247.data.datastore.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.mirlanmamytov.ticker247.network.ApiClient.youtubeApiKey =
            "AIzaSyBFVBAf07C3id09M4R2qapTVMG8SKUt7Vs"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val svcIntent = Intent(this, com.mirlanmamytov.ticker247.service.TickerForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svcIntent)
        else startService(svcIntent)

        // Deep link из уведомления
        handleNotificationIntent(intent)

        setContent { MainHomeScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val url = intent?.getStringExtra("article_url") ?: return
        if (url.isNotEmpty()) {
            DataBridge.pendingArticleUrl = url
        }
    }
}
