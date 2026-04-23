package com.nordling.juoksu.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LogEntry::class, Session::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juoksu.db"
                ).build().also { INSTANCE = it }
            }
    }
}
