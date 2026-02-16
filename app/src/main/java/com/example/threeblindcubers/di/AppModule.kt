package com.example.threeblindcubers.di

import com.example.threeblindcubers.domain.cube.ScrambleGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for general app-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideScrambleGenerator(): ScrambleGenerator {
        return ScrambleGenerator()
    }
}
