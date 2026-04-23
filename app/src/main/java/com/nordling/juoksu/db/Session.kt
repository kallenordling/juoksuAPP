package com.nordling.juoksu.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0
)
