package com.mirlanmamytov.ticker247.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "youtube_channels")
data class YouTubeChannel(
    @PrimaryKey val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val lastVideoId: String? = null,
    val lastCheckedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)
