package com.willbsp.companion.util

import com.willbsp.companion.common.BLE_UUID_DATA_FILE_NAME
import com.willbsp.companion.common.BLE_UUID_DATA_TO_RECORD
import com.willbsp.companion.common.BLE_UUID_READ_MILLIS
import com.willbsp.companion.common.BLE_UUID_HIGH_SAMPLE_RATE
import com.willbsp.companion.common.toByteArray
import java.util.UUID

data class DeviceConfigurationState(
    val trackingTime: Int? = null,
    val dataFileName: String? = null,
    val dataType: Int? = null,
    val highSampleRate: Boolean? = null
) {

    // Returns map of ByteArray representations of configuration settings,
    // along with their BLE UUIDs
    // Used to write values to their correct BLE characteristics
    fun getSerialized(): Map<UUID, ByteArray> {
        val map = mutableMapOf<UUID, ByteArray>()
        getUuid(this::trackingTime.name)?.let { uuid ->
            trackingTime?.let { value -> map.put(uuid, value.toByteArray()) }
        }
        getUuid(this::dataFileName.name)?.let { uuid ->
            dataFileName?.let { map.put(uuid, it.encodeToByteArray()) }
        }
        getUuid(this::dataType.name)?.let { uuid ->
            dataType?.let { value -> map.put(uuid, value.toByteArray()) }
        }
        getUuid(this::highSampleRate.name)?.let { uuid ->
            highSampleRate?.let { value -> map.put(uuid, value.toByteArray()) }
        }
        return map
    }

    // Associate names of parameters to their BLE UUIDs
    private fun getUuid(parameterName: String): UUID? {
        return when (parameterName) {
            (this::trackingTime.name) -> BLE_UUID_READ_MILLIS
            (this::dataFileName.name) -> BLE_UUID_DATA_FILE_NAME
            (this::dataType.name) -> BLE_UUID_DATA_TO_RECORD
            (this::highSampleRate.name) -> BLE_UUID_HIGH_SAMPLE_RATE
            else -> null
        }
    }

    private fun Boolean.toByteArray(): ByteArray {
        return if (this) (1).toByteArray()
        else (0).toByteArray()
    }


}