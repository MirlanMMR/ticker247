package com.mirlanmamytov.ticker247.data.db

import androidx.room.*
import com.mirlanmamytov.ticker247.data.model.YouTubeChannel
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeChannelDao {
    @Query("SELECT * FROM youtube_channels ORDER BY addedAt DESC")
    fun getAll(): Flow<List<YouTubeChannel>>

    @Query("SELECT * FROM youtube_channels")
    suspend fun getAllOnce(): List<YouTubeChannel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: YouTubeChannel)

    @Delete
    suspend fun delete(channel: YouTubeChannel)

    @Query("UPDATE youtube_channels SET lastVideoId = :videoId, lastCheckedAt = :time WHERE channelId = :channelId")
    suspend fun updateLastVideo(channelId: String, videoId: String, time: Long)
}
