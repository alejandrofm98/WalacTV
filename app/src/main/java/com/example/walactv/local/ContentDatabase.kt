package com.example.walactv.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, MovieEntity::class, SeriesEntity::class],
    version = 6,
    exportSchema = false
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        @Volatile
        private var INSTANCE: ContentDatabase? = null

        fun getDatabase(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContentDatabase::class.java,
                    "content_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_nombreNormalizado ON channels(nombreNormalizado)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_nombreNormalizado ON movies(nombreNormalizado)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_serieName ON series(serieName)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE movies ADD COLUMN nombre TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE channels RENAME COLUMN country TO countries")
                database.execSQL("ALTER TABLE movies RENAME COLUMN country TO countries")
                database.execSQL("ALTER TABLE series RENAME COLUMN country TO countries")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }

        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        )
    }
}
