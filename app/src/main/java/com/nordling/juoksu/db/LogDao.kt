package com.nordling.juoksu.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<LogEntry>

    @Query("DELETE FROM log_entries WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
