package com.willbsp.companion.di

import com.willbsp.companion.util.DeviceManager
import com.willbsp.companion.util.BleDeviceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class DeviceManagerModule {

    @Binds
    abstract fun bindDeviceManager(
        deviceManager: BleDeviceManager
    ): DeviceManager

}