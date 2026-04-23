package com.nordling.juoksu.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAll(): LiveData<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("UPDATE sessions SET endTime = :endTime, distanceMeters = :distance WHERE id = :id")
    suspend fun finish(id: Long, endTime: Long, distance: Double)

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
