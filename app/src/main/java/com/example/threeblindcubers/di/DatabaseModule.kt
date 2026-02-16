package com.example.threeblindcubers.di

import android.content.Context
import androidx.room.Room
import com.example.threeblindcubers.data.database.SolveDao
import com.example.threeblindcubers.data.database.SolveDatabase
import com.example.threeblindcubers.data.repository.SolveRepository
import com.example.threeblindcubers.data.repository.SolveRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database and repository dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSolveDatabase(@ApplicationContext context: Context): SolveDatabase {
        return Room.databaseBuilder(
            context,
            SolveDatabase::class.java,
            SolveDatabase.DATABASE_NAME
        ).addMigrations(SolveDatabase.MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideSolveDao(database: SolveDatabase): SolveDao {
        return database.solveDao()
    }

    @Provides
    @Singleton
    fun provideSolveRepository(dao: SolveDao): SolveRepository {
        return SolveRepositoryImpl(dao)
    }
}
