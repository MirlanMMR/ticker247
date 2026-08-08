package com.mirlanmamytov.ticker247.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.data.model.YouTubeChannel

@Database(
    entities = [NewsItem::class, YouTubeChannel::class],
    // Версия поднята из-за апгрейда Room 2.6→2.8: компилятор чуть иначе
    // генерирует identity hash даже без изменений в самих @Entity, из-за
    // этого Room не узнаёт старую базу и падает вместо того, чтобы применить
    // fallbackToDestructiveMigration (тот срабатывает только при смене
    // номера версии, не сам по себе при расхождении хэша)
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun youtubeChannelDao(): YouTubeChannelDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "ticker247_db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
