package com.willbsp.companion.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.core.content.getSystemService
import com.willbsp.companion.common.BLE_UUID_CONFIGURATION_SERVICE
import com.willbsp.companion.common.BLE_UUID_DATA_FILE_NAME
import com.willbsp.companion.common.BLE_UUID_DATA_TO_RECORD
import com.willbsp.companion.common.BLE_UUID_HIGH_SAMPLE_RATE
import com.willbsp.companion.common.BLE_UUID_READ_MILLIS
import com.willbsp.companion.common.BLE_UUID_TRACKING
import com.willbsp.companion.common.toByteArray
import com.willbsp.companion.common.toInt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Suppressed as permissions should be checked before calling methods in this class
@SuppressLint("MissingPermission")
class BleDeviceManager @Inject constructor(
    @ApplicationContext val context: Context
) : DeviceManager {

    private val _connectionState = MutableStateFlow(DeviceConnectionState.NO_CONNECTION)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState
    private val _deviceState = MutableStateFlow(DeviceState.UNKNOWN)
    override val deviceState: StateFlow<DeviceState> = _deviceState

    private val adapter = context.getSystemService<BluetoothManager>()?.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private var scanTimeoutJob: Job? = null
    private var deviceGatt: BluetoothGatt? = null


    // Start scanning for device
    override fun connectToDevice() {
        val scanSettings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        context.getSystemService<BluetoothManager>()?.adapter?.bluetoothLeScanner?.let { scanner ->
            scanner.startScan(null, scanSettings, scanCallback)
            _connectionState.value = DeviceConnectionState.SCANNING
            scanTimeoutJob = CoroutineScope(Dispatchers.Default).launch() { delay(SCAN_PERIOD) }
            scanTimeoutJob?.invokeOnCompletion { stopScan() }
        }
    }


    // Disconnect from device / stop scan and update states
    override fun disconnectFromDevice() {
        stopScan()
        deviceGatt?.close()
        deviceGatt = null
        _connectionState.value = DeviceConnectionState.NO_CONNECTION
        _deviceState.value = DeviceState.UNKNOWN
    }

    // Writes a value of 1 to the devices tracking
    // characteristic, starting tracking
    override fun startTracking() {
        deviceGatt?.let { gatt ->
            gatt.getConfigurationService()?.let { service ->
                val startCharacteristic = service.getCharacteristic(BLE_UUID_TRACKING)
                gatt.writeCharacteristic(
                    startCharacteristic,
                    (1).toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            }
        }
    }

    // Returns the service matching the UUID of the configuration service
    private fun BluetoothGatt?.getConfigurationService(): BluetoothGattService? {
        return this?.services?.find { it.uuid == BLE_UUID_CONFIGURATION_SERVICE }
    }

    // Writes a value of 0 to the devices tracking
    // characteristic, stopping tracking
    override fun stopTracking() {
        deviceGatt?.let { gatt ->
            gatt.getConfigurationService()?.let { service ->
                val startCharacteristic = service.getCharacteristic(BLE_UUID_TRACKING)
                gatt.writeCharacteristic(
                    startCharacteristic,
                    (0).toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            }
        }
    }

    // Will suspend and return the configuration of the device when available
    override suspend fun readConfiguration(): DeviceConfigurationState? {
        _deviceState.value = DeviceState.SYNCING
        deviceGatt?.let { gatt ->
            gatt.getConfigurationService()?.let { service ->
                // Starts the read process in the gatt callback
                val configuration =
                    gattCallback.readDeviceConfiguration(gatt, service.characteristics)
                _deviceState.value = DeviceState.IDLE
                return configuration
            }
        }
        // No device gatt available, so error
        _connectionState.value = DeviceConnectionState.ERROR
        return null
    }

    override fun writeConfiguration(configuration: DeviceConfigurationState) {
        deviceGatt?.let { gatt ->
            _deviceState.value = DeviceState.SYNCING
            gatt.getConfigurationService()?.let { service ->

                // Get map of BLE UUIDs to ByteArray representations
                val serializedConfiguration = configuration.getSerialized()
                    .map { entry -> Pair(service.getCharacteristic(entry.key), entry.value) }

                // Start a new coroutine for writing, which when returns will
                // update the state of the device based on return status
                CoroutineScope(Dispatchers.IO).launch {
                    if (gattCallback.writeDeviceConfiguration(gatt, serializedConfiguration)) {
                        // If success, return to idle
                        _deviceState.value = DeviceState.IDLE
                    } else {
                        // If error
                        _deviceState.value = DeviceState.ERROR
                    }
                }

            }
        } ?: run {
            // No gatt, so error
            _connectionState.value = DeviceConnectionState.ERROR
        }
    }

    // Stop scanning, cancelling any timeout jobs
    private fun stopScan() {
        if (_connectionState.value == DeviceConnectionState.SCANNING) {
            _connectionState.value = DeviceConnectionState.NO_CONNECTION
        }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanner?.stopScan(scanCallback)
    }

    // Called when device is found, will connect to its GATT server
    private fun onTrackingDeviceFound(trackingDevice: BluetoothDevice) {
        scanner?.stopScan(scanCallback)
        deviceGatt = trackingDevice.connectGatt(context, false, gattCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // If the name matches tracker, then device is found
            if (result.device.name == "tracker") {
                onTrackingDeviceFound(result.device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        // Deferred values are variables which can be await, allowing them
        // to be "completed" later
        private var deferredConfiguration = CompletableDeferred<DeviceConfigurationState?>()
        private var deferredWriteResult = CompletableDeferred<Boolean>()

        // Read / write queues, used as BLE GATT must be written synchronously
        private val characteristicReadQueue = mutableListOf<BluetoothGattCharacteristic>()
        private val characteristicWriteQueue =
            mutableListOf<Pair<BluetoothGattCharacteristic, ByteArray>>()

        // Temporary configuration state for reading, which is
        // updated as new values arrive
        private var tempConfigurationState = DeviceConfigurationState()

        suspend fun readDeviceConfiguration(
            gatt: BluetoothGatt,
            characteristics: List<BluetoothGattCharacteristic>
        ): DeviceConfigurationState? {

            // Reset the deferred configuration, in case completed from previous read
            deferredConfiguration = CompletableDeferred()
            characteristicReadQueue.clear()

            // Add only configuration parameters
            characteristicReadQueue.addAll(characteristics.filter {
                it.uuid == BLE_UUID_HIGH_SAMPLE_RATE ||
                        it.uuid == BLE_UUID_DATA_FILE_NAME ||
                        it.uuid == BLE_UUID_DATA_TO_RECORD ||
                        it.uuid == BLE_UUID_READ_MILLIS
            })

            // Start reading
            readNextCharacteristic(gatt)

            // Timeout after 5s
            val timeoutJob = CoroutineScope(Dispatchers.Default).launch {
                delay(5000)
                deferredConfiguration.complete(null)
            }

            // Suspend until all configuration values have been read
            val result = deferredConfiguration.await()
            timeoutJob.cancel()

            return result
        }

        // Read the next value in the read queue,
        // if empty, then complete the configuration
        private fun readNextCharacteristic(gatt: BluetoothGatt) {
            if (characteristicReadQueue.isNotEmpty())
                gatt.readCharacteristic(characteristicReadQueue.removeFirst())
            else deferredConfiguration.complete(tempConfigurationState)
        }

        suspend fun writeDeviceConfiguration(
            gatt: BluetoothGatt,
            values: List<Pair<BluetoothGattCharacteristic, ByteArray>>,
        ): Boolean {

            // Reset
            deferredWriteResult = CompletableDeferred()
            characteristicWriteQueue.clear()
            characteristicWriteQueue.addAll(values)

            // Start writing
            writeNextCharacteristic(gatt)

            // Timeout after 5s
            val timeoutJob = CoroutineScope(Dispatchers.Default).launch {
                delay(5000) // timeout deferredWriteResult
                deferredWriteResult.complete(false)
            }

            // Suspend until complete
            val result = deferredWriteResult.await()
            timeoutJob.cancel()

            return result
        }

        // Write next value, until queue is empty,
        // then return success
        private fun writeNextCharacteristic(gatt: BluetoothGatt) {
            if (characteristicWriteQueue.isNotEmpty()) {
                val value = characteristicWriteQueue.removeFirst()
                gatt.writeCharacteristic(
                    value.first,
                    value.second,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                deferredWriteResult.complete(true)
            }
        }

        // Update connection state, based on Bluetooth connection state
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = DeviceConnectionState.CONNECTED
                    gatt?.discoverServices()
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = DeviceConnectionState.NO_CONNECTION
                }
            } else {
                _connectionState.value = DeviceConnectionState.ERROR
            }
        }

        // Called when BLE services are discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            for (service in gatt.services) {
                if (service.uuid == BLE_UUID_CONFIGURATION_SERVICE) {

                    // Enable notifications for tracking characteristics,
                    // so app is notified when tracking is finished
                    val characteristic = service.getCharacteristic(BLE_UUID_TRACKING)
                    for (descriptor in characteristic.descriptors) {
                        gatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                    }
                    gatt.setCharacteristicNotification(characteristic, true)

                }
            }
        }

        // Will be called when the notification descriptor is written to
        // the tracking characteristic
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Read the initial value of the tracking characteristic once notifications
            // are enabled
            if (descriptor.characteristic.uuid == BLE_UUID_TRACKING) {
                gatt.readCharacteristic(descriptor.characteristic)
            }
        }

        // Called when characteristic is written
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // If successful, then write the next in the queue
                writeNextCharacteristic(gatt)
            } else {
                // Failed, so success returns as false
                characteristicWriteQueue.clear()
                deferredWriteResult.complete(false)
            }
        }

        // Called when characteristic is read
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // If tracking characteristic is read, then update the device state
                if (characteristic.uuid == BLE_UUID_TRACKING) {
                    val tracking = value.toInt()
                    if (tracking == 1) _deviceState.value = DeviceState.TRACKING
                    else _deviceState.value = DeviceState.IDLE
                    return
                }
                updateTemporaryConfiguration(characteristic, value)
                readNextCharacteristic(gatt)
            } else {
                // Failed, so complete configuration as null
                deferredConfiguration.complete(null)
                characteristicReadQueue.clear()
            }
        }

        // Called on characteristic change notification
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            // Update tracking state
            if (characteristic.uuid == BLE_UUID_TRACKING) {
                val tracking = value.toInt()
                if (tracking == 1) {
                    _deviceState.value = DeviceState.TRACKING
                } else {
                    _deviceState.value = DeviceState.IDLE
                }
            }
        }

        // Updates the temporary configuration based on the read characteristic
        private fun updateTemporaryConfiguration(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {

            when (characteristic.uuid) {

                BLE_UUID_DATA_FILE_NAME -> {
                    tempConfigurationState = tempConfigurationState.copy(
                        dataFileName = value.decodeToString()
                    )
                }

                BLE_UUID_HIGH_SAMPLE_RATE -> {
                    tempConfigurationState = tempConfigurationState.copy(
                        highSampleRate = value.toInt() == 1
                    )
                }

                BLE_UUID_DATA_TO_RECORD -> {
                    tempConfigurationState = tempConfigurationState.copy(
                        dataType = value.toInt()
                    )
                }

                BLE_UUID_READ_MILLIS -> {
                    tempConfigurationState = tempConfigurationState.copy(
                        trackingTime = value.toInt()
                    )
                }
            }
        }
    }


    companion object {
        const val SCAN_PERIOD: Long = 15000
    }

}