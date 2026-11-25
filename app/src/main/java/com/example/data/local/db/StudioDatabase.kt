package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, BuildRecordEntity::class, ApkArtifactEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun apkArtifactDao(): ApkArtifactDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "apk_forge_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
