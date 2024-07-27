package com.willbsp.companion.ui.screens.configurator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.willbsp.companion.util.DeviceConfigurationState
import com.willbsp.companion.util.DeviceConnectionState
import com.willbsp.companion.util.DeviceManager
import com.willbsp.companion.util.DeviceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfiguratorViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Ui state private set so can only be modified via updateUiState function
    var uiState by mutableStateOf(
        ConfiguratorUiState(DeviceConnectionState.NO_CONNECTION, DeviceState.UNKNOWN)
    )
        private set

    // Init will run on instantiation, will restore any state from savedStateHandle
    // if available (in case of process death) and will start a flow collection from
    // deviceManager for the connection and device states
    init {
        restoreFromSavedState()
        viewModelScope.launch {
            // Updates connection and device state whenever their values change
            combine(
                deviceManager.connectionState,
                deviceManager.deviceState
            ) { connectionState, deviceState -> Pair(connectionState, deviceState) }
                .collectLatest { state ->
                    uiState = uiState.copy(
                        connectionState = state.first,
                        deviceState = state.second
                    )
                }
        }
    }

    // Can be called from outside of this class to update the state of the ui,
    // will also update any relevant values in the saved state handle and
    // check the validity of configuration values
    fun updateUiState(newState: ConfiguratorUiState) {
        savedStateHandle[SAMPLE_RATE_HANDLE] = newState.sampleRate.ordinal
        savedStateHandle[DATA_TYPE_HANDLE] = newState.dataType.ordinal
        savedStateHandle[DATA_FILE_NAME_HANDLE] = newState.dataFileName
        savedStateHandle[TRACKING_TIME_HANDLE] = newState.trackingTime
        uiState = newState
        updateConfigurationValidity()
    }

    // Following methods expose DeviceManager functionality

    fun connectToDevice() {
        deviceManager.connectToDevice()
    }

    fun disconnectFromDevice() {
        deviceManager.disconnectFromDevice()
    }

    fun toggleTracking() {
        if (uiState.deviceState == DeviceState.TRACKING) deviceManager.stopTracking()
        else deviceManager.startTracking()
    }

    // Read method is moved to the IO dispatcher to prevent
    // the UI from stalling whilst waiting for new configuration
    // to return
    fun readConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            deviceManager.readConfiguration()?.let { configuration ->
                // Ensure deviceState returns to idle, in case flow
                // does not emit before new state is updated from copy
                uiState = uiState.copy(deviceState = DeviceState.IDLE)
                uiState.updateFromDeviceConfiguration(configuration)
            }
        }
    }

    fun writeConfiguration() {
        // Configuration is written from values stored in the ui state
        if (uiState.configurationIsValid) {
            deviceManager.writeConfiguration(uiState.toDeviceConfiguration())
        }
    }

    // Checks if saved state handle contains any values, if so
    // will update ui state
    private fun restoreFromSavedState() {
        if (savedStateHandle.contains(SAMPLE_RATE_HANDLE)) {
            val sampleRateIndex: Int? = savedStateHandle[SAMPLE_RATE_HANDLE]
            sampleRateIndex?.let { uiState = uiState.copy(sampleRate = SampleRate.values()[it]) }
            updateConfigurationValidity()
        }
        if (savedStateHandle.contains(DATA_TYPE_HANDLE)) {
            val dataTypeIndex: Int? = savedStateHandle[DATA_TYPE_HANDLE]
            dataTypeIndex?.let { uiState = uiState.copy(dataType = DataType.values()[it]) }
            updateConfigurationValidity()
        }
        if (savedStateHandle.contains(DATA_FILE_NAME_HANDLE)) {
            val dataFileName: String? = savedStateHandle[DATA_FILE_NAME_HANDLE]
            dataFileName?.let { uiState = uiState.copy(dataFileName = it) }
            updateConfigurationValidity()
        }
        if (savedStateHandle.contains(TRACKING_TIME_HANDLE)) {
            val trackingTime: Float? = savedStateHandle[TRACKING_TIME_HANDLE]
            trackingTime?.let { uiState = uiState.copy(trackingTime = it) }
            updateConfigurationValidity()
        }
    }

    // Checks file name and tracking time fields for valid input
    // For file names to be compatible with the wearable device,
    // they must be in 8.3 format, so a regex is used to check this
    private fun updateConfigurationValidity() {
        val regex = Regex("^\\S{1,8}\\.\\S{1,3}\$") // 8.3 filename
        val fileNameValid = uiState.dataFileName?.matches(regex) ?: false
        val trackingTimeValid = uiState.trackingTime?.let { it > 1 } ?: false
        uiState = uiState.copy(
            fileNameInvalid = !fileNameValid,
            trackingTimeInvalid = !trackingTimeValid,
            configurationIsValid = fileNameValid && trackingTimeValid
        )
    }

    // Extension function to update the ui from a device configuration
    private fun ConfiguratorUiState.updateFromDeviceConfiguration(
        configuration: DeviceConfigurationState
    ) {
        updateUiState(
            this.copy(
                trackingTime = configuration.trackingTime?.div(1000f), // to seconds
                dataFileName = configuration.dataFileName,
                dataType = configuration.dataType.toDataType(),
                sampleRate = configuration.highSampleRate.toSampleRate()
            )
        )
    }

    // Extension functions to convert ui state to a device configuration
    private fun ConfiguratorUiState.toDeviceConfiguration(): DeviceConfigurationState {
        return DeviceConfigurationState(
            trackingTime = trackingTime?.times(1000f)?.toInt(), // to millis
            dataFileName = dataFileName,
            dataType = dataType.value,
            highSampleRate = sampleRate.value == 1
        )
    }

    // Private extension functions to quickly convert primitive types
    // to their enum representations

    private fun Int?.toDataType(): DataType {
        return when (this) {
            1 -> DataType.ACCEL_ONLY
            2 -> DataType.GYRO_ONLY
            else -> DataType.BOTH
        }
    }

    private fun Boolean?.toSampleRate(): SampleRate {
        return when (this) {
            true -> SampleRate.RATE_208
            else -> SampleRate.RATE_104
        }
    }


    // Constants are used as keys for saved state handle
    companion object {
        const val SAMPLE_RATE_HANDLE = "sample_rate"
        const val DATA_TYPE_HANDLE = "data_type"
        const val DATA_FILE_NAME_HANDLE = "data_file_name"
        const val TRACKING_TIME_HANDLE = "tracking_time"
    }

}