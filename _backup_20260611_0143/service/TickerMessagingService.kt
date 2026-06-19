package com.mirlanmamytov.ticker247.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mirlanmamytov.ticker247.DataBridge
import com.mirlanmamytov.ticker247.MainActivity
import com.mirlanmamytov.ticker247.R
import com.mirlanmamytov.ticker247.data.model.NewsItem

class TickerMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: return
        val category = message.data["category"] ?: "NEWS"
        val url = message.data["url"] ?: ""
        val priority = message.data["priority"]?.toIntOrNull() ?: 1

        // Добавляем в ленту
        val newItem = NewsItem(
            url = url, title = title, summary = body,
            imageUrl = message.data["image"], source = "Ticker 24/7",
            category = category, publishedAt = System.currentTimeMillis(), priority = priority
        )
        DataBridge.setNewsItems(listOf(newItem) + DataBridge.newsItems)

        // Показываем уведомление
        val channelId = when (priority) {
            2 -> "ticker_urgent"
            1 -> "ticker_important"
            else -> "ticker_info"
        }
        val iconRes = when (priority) {
            2 -> R.drawable.ic_lightning_urgent
            1 -> R.drawable.ic_lightning_blue
            else -> R.drawable.ic_lightning_white
        }

        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Токен обновился — в будущем отправлять на сервер
        android.util.Log.d("FCM", "New token: $token")
    }
}
