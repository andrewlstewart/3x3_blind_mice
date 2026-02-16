package com.example.threeblindcubers.di

import android.content.Context
import com.example.threeblindcubers.data.bluetooth.MoyuCubeService
import com.example.threeblindcubers.data.bluetooth.MoyuCubeServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Bluetooth-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideMoyuCubeService(
        @ApplicationContext context: Context
    ): MoyuCubeService {
        return MoyuCubeServiceImpl(context)
    }
}
