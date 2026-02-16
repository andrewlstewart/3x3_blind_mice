package com.example.threeblindcubers.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the 3x3 Blind Mice app
 */
@Database(
    entities = [SolveEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(SolveConverters::class)
abstract class SolveDatabase : RoomDatabase() {
    abstract fun solveDao(): SolveDao

    companion object {
        const val DATABASE_NAME = "three_blind_cubers_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solves ADD COLUMN memoTimeMillis INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
