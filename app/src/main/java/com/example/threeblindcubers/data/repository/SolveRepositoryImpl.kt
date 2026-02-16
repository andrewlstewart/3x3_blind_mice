package com.example.threeblindcubers.data.repository

import com.example.threeblindcubers.data.database.SolveDao
import com.example.threeblindcubers.data.database.SolveEntity
import com.example.threeblindcubers.domain.models.ScrambleMode
import com.example.threeblindcubers.domain.models.Solve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SolveRepository using Room database
 */
@Singleton
class SolveRepositoryImpl @Inject constructor(
    private val solveDao: SolveDao
) : SolveRepository {

    override suspend fun saveSolve(solve: Solve): Long {
        val entity = SolveEntity.fromDomain(solve)
        return solveDao.insert(entity)
    }

    override fun getAllSolves(): Flow<List<Solve>> {
        return solveDao.getAllSolves().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSolvesByMode(mode: ScrambleMode): Flow<List<Solve>> {
        return solveDao.getSolvesByMode(mode.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSolveById(id: Long): Solve? {
        return solveDao.getSolveById(id)?.toDomain()
    }

    override suspend fun deleteSolve(solve: Solve) {
        val entity = SolveEntity.fromDomain(solve)
        solveDao.delete(entity)
    }

    override suspend fun deleteAllSolves() {
        solveDao.deleteAll()
    }

    override suspend fun getSolveCount(): Int {
        return solveDao.getCount()
    }

    override suspend fun getBestTime(mode: ScrambleMode): Long? {
        return solveDao.getBestTime(mode.name)
    }
}
