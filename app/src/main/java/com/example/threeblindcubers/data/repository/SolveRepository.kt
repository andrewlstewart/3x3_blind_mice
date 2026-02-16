package com.example.threeblindcubers.data.repository

import com.example.threeblindcubers.domain.models.ScrambleMode
import com.example.threeblindcubers.domain.models.Solve
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for solve data operations
 */
interface SolveRepository {
    /**
     * Save a new solve
     */
    suspend fun saveSolve(solve: Solve): Long

    /**
     * Get all solves as a Flow
     */
    fun getAllSolves(): Flow<List<Solve>>

    /**
     * Get solves filtered by scramble mode
     */
    fun getSolvesByMode(mode: ScrambleMode): Flow<List<Solve>>

    /**
     * Get a single solve by ID
     */
    suspend fun getSolveById(id: Long): Solve?

    /**
     * Delete a solve
     */
    suspend fun deleteSolve(solve: Solve)

    /**
     * Delete all solves
     */
    suspend fun deleteAllSolves()

    /**
     * Get total solve count
     */
    suspend fun getSolveCount(): Int

    /**
     * Get best time for a specific mode
     */
    suspend fun getBestTime(mode: ScrambleMode): Long?
}
