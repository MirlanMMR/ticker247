package com.mirlanmamytov.ticker247.reader

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

fun extractYouTubeId(url: String): String? {
    // https://youtu.be/VIDEO_ID
    val shortMatch = Regex("youtu\\.be/([\\w-]+)").find(url)
    if (shortMatch != null) return shortMatch.groupValues[1]
    // https://www.youtube.com/watch?v=VIDEO_ID
    val longMatch = Regex("[?&]v=([\\w-]+)").find(url)
    if (longMatch != null) return longMatch.groupValues[1]
    // https://www.youtube.com/embed/VIDEO_ID
    val embedMatch = Regex("youtube\\.com/embed/([\\w-]+)").find(url)
    if (embedMatch != null) return embedMatch.groupValues[1]
    return null
}

fun isYouTubeUrl(url: String): Boolean =
    url.contains("youtube.com") || url.contains("youtu.be")

@Composable
fun YouTubePlayerScreen(url: String, onClose: () -> Unit) {
    val videoId = remember(url) { extractYouTubeId(url) }
    val embedUrl = remember(videoId) {
        if (videoId != null)
            "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1"
        else url
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Кнопка закрыть
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
        }
    }
}
