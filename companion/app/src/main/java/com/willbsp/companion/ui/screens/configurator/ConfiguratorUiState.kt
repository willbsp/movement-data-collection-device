package com.willbsp.companion.ui.screens.configurator

import androidx.annotation.StringRes
import com.willbsp.companion.R
import com.willbsp.companion.util.DeviceConnectionState
import com.willbsp.companion.util.DeviceState

// Data class used to represent the state of the configurator
// Includes both connection state and device state, as well as form inputs
// and whether given inputs are valid
data class ConfiguratorUiState(
    val connectionState: DeviceConnectionState,
    val deviceState: DeviceState,
    val configurationIsValid: Boolean = false,
    val sampleRate: SampleRate = SampleRate.RATE_104,
    val dataType: DataType = DataType.BOTH,
    val trackingTime: Float? = null,
    val dataFileName: String? = null,
    val fileNameInvalid: Boolean = false,
    val trackingTimeInvalid: Boolean = false
)

// Values for enums correspond with value for the given option on the wearable
// Enums include string resource IDs for displaying in the UI
enum class SampleRate(val value: Int, @StringRes val userReadableStringRes: Int) {
    RATE_104(0, R.string._104hz),
    RATE_208(1, R.string._208hz)
}
enum class DataType(val value: Int, @StringRes val userReadableStringRes: Int) {
    BOTH(0, R.string.accelerometer_and_gyroscope),
    ACCEL_ONLY(1, R.string.accelerometer_only),
    GYRO_ONLY(2, R.string.gyroscope_only)
}