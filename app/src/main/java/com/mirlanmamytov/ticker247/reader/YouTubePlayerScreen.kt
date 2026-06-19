package com.mirlanmamytov.ticker247.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

fun extractYouTubeId(url: String): String? {
    val shortMatch = Regex("youtu\\.be/([\\w-]+)").find(url)
    if (shortMatch != null) return shortMatch.groupValues[1]
    val longMatch = Regex("[?&]v=([\\w-]+)").find(url)
    if (longMatch != null) return longMatch.groupValues[1]
    val embedMatch = Regex("youtube\\.com/embed/([\\w-]+)").find(url)
    if (embedMatch != null) return embedMatch.groupValues[1]
    return null
}

fun isYouTubeUrl(url: String): Boolean =
    url.contains("youtube.com") || url.contains("youtu.be")

@Composable
fun YouTubePlayerScreen(url: String, onClose: () -> Unit) {
    val videoId = remember(url) { extractYouTubeId(url) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (videoId != null) {
            AndroidView(
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        lifecycleOwner.lifecycle.addObserver(this)
                        addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                youTubePlayer.loadVideo(videoId, 0f)
                            }
                            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                // Видео не разрешает embed — открываем в YouTube app
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        setPackage("com.google.android.youtube")
                                    }
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                                onClose()
                            }
                        })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            )
        } else {
            Text(
                "Не удалось загрузить видео",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

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
