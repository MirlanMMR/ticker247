package com.mirlanmamytov.ticker247.workmanager

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirlanmamytov.ticker247.MainActivity
import com.mirlanmamytov.ticker247.R
import com.mirlanmamytov.ticker247.data.model.DigestItem
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.data.repository.FirebaseNewsRepository
import com.mirlanmamytov.ticker247.util.DigestStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Будни (пн-пт) — утренний дайджест: топ-3 новости за последние ~16 часов.
 * Выходные (сб/вс) — недельный дайджест: топ-3 за каждый из последних 7 дней,
 * собранные в один список (см. DigestStore.getLast7DaysTop).
 */
@HiltWorker
class DigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val items = FirebaseNewsRepository.fetchNews()
            val cutoff = System.currentTimeMillis() - 16 * 3600_000L
            val candidates = items.filter {
                it.category !in setOf("CURRENCY", "CRYPTO", "VIRAL") && it.publishedAt >= cutoff
            }
            val top3 = candidates.sortedByDescending(::score).take(3)
            if (top3.isEmpty()) return Result.success()

            val digestItems = top3.map { DigestItem(it.title, it.url, it.source) }
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            DigestStore.saveDailyTop(applicationContext, dateKey, digestItems)

            val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

            val (type, displayItems, title) = if (isWeekend) {
                val weekly = DigestStore.getLast7DaysTop(applicationContext)
                    .distinctBy { it.url.ifEmpty { it.title } }
                Triple("weekly", weekly.ifEmpty { digestItems }, "📅 Итоги недели")
            } else {
                Triple("daily", digestItems, "☀️ Утренний дайджест")
            }

            DigestStore.saveLatestDigest(applicationContext, type, displayItems)
            postNotification(title, displayItems)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun score(item: NewsItem): Int {
        var s = item.priority * 5
        if (item.summary.length > 80) s += 3
        if (item.imageUrl != null) s += 2
        val ageH = (System.currentTimeMillis() - item.publishedAt) / 3_600_000
        s += when {
            ageH < 1 -> 6
            ageH < 3 -> 4
            ageH < 8 -> 2
            else -> 0
        }
        return s
    }

    private fun postNotification(title: String, items: List<DigestItem>) {
        val body = items.joinToString(" · ") { it.title.take(60) }
        val intent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, "ticker_important")
            .setSmallIcon(R.drawable.ic_lightning_blue)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)?.notify(2001, notification)
    }
}
