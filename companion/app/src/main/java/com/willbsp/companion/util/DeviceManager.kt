package com.willbsp.companion.util

import kotlinx.coroutines.flow.StateFlow

// Interface for managing the wearable device
// Provides StateFlows for observing the connection and device state,
// methods for managing the connection and device configuration

interface DeviceManager {

    val connectionState: StateFlow<DeviceConnectionState>
    val deviceState: StateFlow<DeviceState>
    fun connectToDevice()
    fun disconnectFromDevice()
    fun startTracking()
    fun stopTracking()
    fun writeConfiguration(configuration: DeviceConfigurationState)
    suspend fun readConfiguration(): DeviceConfigurationState?

}

// Enum classes represent all possible connection and device states

enum class DeviceConnectionState() {
    CONNECTED,
    NO_CONNECTION,
    SCANNING,
    ERROR
}

enum class DeviceState() {
    IDLE,
    SYNCING,
    TRACKING,
    UNKNOWN,
    ERROR
}