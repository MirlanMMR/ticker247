package com.mirlanmamytov.ticker247.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// Поиск канала
data class YouTubeSearchResponse(val items: List<YouTubeSearchItem>?)
data class YouTubeSearchItem(
    val id: YouTubeSearchId?,
    val snippet: YouTubeSearchSnippet?
)
data class YouTubeSearchId(val channelId: String?)
data class YouTubeSearchSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: YouTubeThumbnails?
)
data class YouTubeThumbnails(val default: YouTubeThumbnail?, val medium: YouTubeThumbnail?)
data class YouTubeThumbnail(val url: String?)

// Последние видео канала
data class YouTubeVideoResponse(val items: List<YouTubeVideoItem>?)
data class YouTubeVideoItem(
    val id: YouTubeVideoId?,
    val snippet: YouTubeVideoSnippet?
)
data class YouTubeVideoId(val videoId: String?)
data class YouTubeVideoSnippet(
    val title: String?,
    val description: String?,
    val channelTitle: String?,
    val thumbnails: YouTubeThumbnails?,
    val publishedAt: String?
)

interface YouTubeChannelApi {

    @GET("search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "channel",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") key: String = BuildConfig_YOUTUBE_KEY
    ): YouTubeSearchResponse

    @GET("search")
    suspend fun getLatestVideos(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("order") order: String = "date",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 3,
        @Query("key") key: String = BuildConfig_YOUTUBE_KEY
    ): YouTubeVideoResponse
}

// Константа ключа — берём из BuildConfig
const val BuildConfig_YOUTUBE_KEY = "YOUR_KEY" // заменится через Hilt модуль
