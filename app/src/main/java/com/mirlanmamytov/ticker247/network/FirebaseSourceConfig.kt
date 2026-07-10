package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Источники приложения из Firebase /config/app_sources.
 * Бэкенд публикует их при каждом запуске — правка источников на бэкенде
 * меняет контент у всех установленных приложений без релиза.
 * При недоступности Firebase SourceSelector использует зашитые списки.
 */
object FirebaseSourceConfig {

    @Volatile
    var groups: Map<String, List<SourceSelector.ChannelSource>> = emptyMap()
        private set

    /** Обновляет кэш групп из Firebase. Ошибки не фатальны — остаётся старый кэш. */
    suspend fun refresh() {
        try {
            val parsed = suspendCancellableCoroutine<Map<String, List<SourceSelector.ChannelSource>>> { cont ->
                FirebaseDatabase.getInstance("https://ticker247-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("config/app_sources")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val result = mutableMapOf<String, List<SourceSelector.ChannelSource>>()
                            for (group in snapshot.children) {
                                val name = group.key ?: continue
                                val sources = group.children.mapNotNull { entry ->
                                    val handle = entry.child("h").getValue(String::class.java) ?: return@mapNotNull null
                                    val category = entry.child("c").getValue(String::class.java) ?: return@mapNotNull null
                                    val type = when (entry.child("t").getValue(String::class.java)) {
                                        "YOUTUBE_RSS" -> SourceSelector.SourceType.YOUTUBE_RSS
                                        "RSS" -> SourceSelector.SourceType.RSS
                                        else -> SourceSelector.SourceType.TELEGRAM
                                    }
                                    val priority = entry.child("p").getValue(Int::class.java) ?: 0
                                    SourceSelector.ChannelSource(handle, category, type, priority)
                                }
                                if (sources.isNotEmpty()) result[name] = sources
                            }
                            cont.resume(result)
                        }
                        override fun onCancelled(error: DatabaseError) { cont.resume(emptyMap()) }
                    })
            }
            if (parsed.isNotEmpty()) {
                groups = parsed
                Log.d("SourceConfig", "Loaded ${parsed.size} source groups from Firebase")
            }
        } catch (e: Exception) {
            Log.w("SourceConfig", "refresh failed: ${e.message}")
        }
    }
}
