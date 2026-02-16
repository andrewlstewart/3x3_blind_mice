package com.example.threeblindcubers.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.ScrambleMode
import com.example.threeblindcubers.domain.models.Solve

/**
 * Room entity representing a solve in the database
 */
@Entity(tableName = "solves")
@TypeConverters(SolveConverters::class)
data class SolveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val mode: ScrambleMode,
    val scrambleSequence: List<Move>,
    val solveMoves: List<Move>,
    val timeMillis: Long,
    val memoTimeMillis: Long = 0,
    val isDNF: Boolean = false
) {
    /**
     * Converts this entity to a domain Solve object
     */
    fun toDomain(): Solve {
        return Solve(
            id = id,
            timestamp = timestamp,
            mode = mode,
            scrambleSequence = scrambleSequence,
            solveMoves = solveMoves,
            timeMillis = timeMillis,
            memoTimeMillis = memoTimeMillis,
            isDNF = isDNF
        )
    }

    companion object {
        /**
         * Creates an entity from a domain Solve object
         */
        fun fromDomain(solve: Solve): SolveEntity {
            return SolveEntity(
                id = solve.id,
                timestamp = solve.timestamp,
                mode = solve.mode,
                scrambleSequence = solve.scrambleSequence,
                solveMoves = solve.solveMoves,
                timeMillis = solve.timeMillis,
                memoTimeMillis = solve.memoTimeMillis,
                isDNF = solve.isDNF
            )
        }
    }
}

/**
 * Type converters for Room to handle complex types
 */
class SolveConverters {
    @TypeConverter
    fun fromMoveList(moves: List<Move>): String {
        return moves.joinToString(",") { it.toNotation() }
    }

    @TypeConverter
    fun toMoveList(data: String): List<Move> {
        if (data.isEmpty()) return emptyList()
        return data.split(",").mapNotNull { Move.parse(it) }
    }

    @TypeConverter
    fun fromScrambleMode(mode: ScrambleMode): String {
        return mode.name
    }

    @TypeConverter
    fun toScrambleMode(data: String): ScrambleMode {
        return ScrambleMode.valueOf(data)
    }
}
