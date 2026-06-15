package com.mirlanmamytov.ticker247.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mirlanmamytov.ticker247.DataBridge
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseNewsRepository {

    private val database = FirebaseDatabase.getInstance("https://ticker247-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val newsRef = database.getReference("news")

    suspend fun fetchNews(): List<NewsItem> = suspendCancellableCoroutine { cont ->
        newsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val items = mutableListOf<NewsItem>()
                    val itemsSnapshot = snapshot.child("items")
                    for (child in itemsSnapshot.children) {
                        val item = NewsItem(
                            url = child.child("url").getValue(String::class.java) ?: "",
                            title = child.child("title").getValue(String::class.java) ?: "",
                            summary = child.child("summary").getValue(String::class.java) ?: "",
                            imageUrl = child.child("imageUrl").getValue(String::class.java),
                            source = child.child("source").getValue(String::class.java) ?: "",
                            category = child.child("category").getValue(String::class.java) ?: "NEWS",
                            publishedAt = child.child("publishedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                            priority = child.child("priority").getValue(Int::class.java) ?: 0,
                            language = child.child("language").getValue(String::class.java) ?: "ru"
                        )
                        if (item.title.isNotEmpty()) items.add(item)
                    }
                    Log.d("Firebase", "Loaded ${items.size} news items")
                    cont.resume(items)
                } catch (e: Exception) {
                    Log.e("Firebase", "Parse error: ${e.message}")
                    cont.resume(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
                cont.resumeWithException(error.toException())
            }
        })
    }

    suspend fun fetchViral(): List<NewsItem> = suspendCancellableCoroutine { cont ->
        database.getReference("viral").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val items = mutableListOf<NewsItem>()
                    // Определяем регионы по системному языку
                    val sysLang = java.util.Locale.getDefault().language
                    val sysCountry = java.util.Locale.getDefault().country
                    val regions = when {
                        sysLang == "ky" -> listOf("kg", "ru")
                        sysCountry == "KG" -> listOf("kg", "ru", "world")
                        sysCountry == "KZ" -> listOf("kz", "kg", "ru")
                        sysCountry == "RU" -> listOf("ru", "kg", "world")
                        sysLang == "ru"  -> listOf("ru", "kg", "world")
                        else -> listOf("world", "kg", "ru")
                    }
                    regions.forEach { region ->
                        val label = when(region) {
                            "kg" -> "🔥 ВИРАЛЬНО В КГ"
                            "ru" -> "🔥 ВИРАЛЬНО В РФ"
                            "kz" -> "🔥 ВИРАЛЬНО В КЗ"
                            else -> "🌍 ВИРАЛЬНО В МИРЕ"
                        }
                        for (child in snapshot.child(region).children) {
                            val url = child.child("url").getValue(String::class.java) ?: ""
                            val item = NewsItem(
                                url = url,
                                title = child.child("title").getValue(String::class.java) ?: "",
                                summary = child.child("summary").getValue(String::class.java) ?: "",
                                imageUrl = child.child("imageUrl").getValue(String::class.java),
                                source = label,
                                category = "VIRAL",
                                publishedAt = child.child("publishedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                                priority = 1,
                                isVideo = url.contains("youtube.com") || url.contains("youtu.be")
                            )
                            if (item.title.isNotEmpty()) items.add(item)
                        }
                    }
                    cont.resume(items)
                } catch (e: Exception) {
                    cont.resume(emptyList())
                }
            }
            override fun onCancelled(error: DatabaseError) { cont.resume(emptyList()) }
        })
    }

    suspend fun fetchIndices(): List<String> = suspendCancellableCoroutine { cont ->
        database.getReference("indices/items").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull {
                    it.child("display").getValue(String::class.java)
                }
                cont.resume(items)
            }
            override fun onCancelled(error: DatabaseError) { cont.resume(emptyList()) }
        })
    }

    fun updateDataBridge(items: List<NewsItem>) {
        val tickerItems = items
            .filter { it.category in setOf("CURRENCY", "CRYPTO", "URGENT", "TRENDS") }
            .take(10)
            .map { it.title }
        DataBridge.setTickerAndNews(tickerItems, items)
    }
}
