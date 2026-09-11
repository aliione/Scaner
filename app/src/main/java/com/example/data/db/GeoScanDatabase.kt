package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ScanDataEntity::class,
        TargetEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GeoScanDatabase : RoomDatabase() {
    abstract fun geoScanDao(): GeoScanDao

    companion object {
        @Volatile
        private var INSTANCE: GeoScanDatabase? = null

        fun getDatabase(context: Context): GeoScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GeoScanDatabase::class.java,
                    "geoscan_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
