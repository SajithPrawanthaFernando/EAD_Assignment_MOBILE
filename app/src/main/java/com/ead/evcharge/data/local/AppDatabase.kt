package com.ead.evcharge.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ead.evcharge.data.local.dao.BookingDao
import com.ead.evcharge.data.local.dao.UserDao
import com.ead.evcharge.data.local.dao.StationDao
import com.ead.evcharge.data.local.entity.UserEntity
import com.ead.evcharge.data.local.entity.StationEntity
import com.ead.evcharge.data.local.entity.BookingEntity

@Database(
    entities = [UserEntity::class, StationEntity::class, BookingEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun stationDao(): StationDao

    abstract fun bookingDao(): BookingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "evcharge_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}