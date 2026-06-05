package com.mirlanmamytov.ticker247.data.db

import androidx.room.*
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY publishedAt DESC")
    fun getAll(): Flow<List<NewsItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NewsItem)

    @Delete
    suspend fun delete(item: NewsItem)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean
}
