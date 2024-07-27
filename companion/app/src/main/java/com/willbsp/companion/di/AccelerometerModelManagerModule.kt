package com.willbsp.companion.di

import com.willbsp.companion.util.AccelerometerModelManager
import com.willbsp.companion.util.LocalAccelerometerModelManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class AccelerometerModelManagerModule {

    @Binds
    abstract fun bindAccelerometerModelManager(
        accelerometerModelManager: LocalAccelerometerModelManager
    ): AccelerometerModelManager

}