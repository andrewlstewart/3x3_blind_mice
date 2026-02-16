package com.example.threeblindcubers.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for solve operations
 */
@Dao
interface SolveDao {
    /**
     * Insert a new solve into the database
     */
    @Insert
    suspend fun insert(solve: SolveEntity): Long

    /**
     * Get all solves ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM solves ORDER BY timestamp DESC")
    fun getAllSolves(): Flow<List<SolveEntity>>

    /**
     * Get solves by scramble mode
     */
    @Query("SELECT * FROM solves WHERE mode = :mode ORDER BY timestamp DESC")
    fun getSolvesByMode(mode: String): Flow<List<SolveEntity>>

    /**
     * Get a single solve by ID
     */
    @Query("SELECT * FROM solves WHERE id = :id")
    suspend fun getSolveById(id: Long): SolveEntity?

    /**
     * Delete a solve
     */
    @Delete
    suspend fun delete(solve: SolveEntity)

    /**
     * Delete all solves
     */
    @Query("DELETE FROM solves")
    suspend fun deleteAll()

    /**
     * Get count of total solves
     */
    @Query("SELECT COUNT(*) FROM solves")
    suspend fun getCount(): Int

    /**
     * Get best time for a specific mode (excluding DNF)
     */
    @Query("SELECT MIN(timeMillis) FROM solves WHERE mode = :mode AND isDNF = 0")
    suspend fun getBestTime(mode: String): Long?
}
