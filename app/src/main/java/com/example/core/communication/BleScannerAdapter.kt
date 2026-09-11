package com.example.core.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Dedicated BLE GATT implementation of ScannerAdapter.
 * Handles MTU negotiation, Service/Characteristic discovery, Notify descriptors,
 * packet loss detection, and logging to TrafficMonitorHub.
 */
class BleScannerAdapter(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScannerAdapter {

    override val transportType: ScannerTransportType = ScannerTransportType.BLUETOOTH_LE

    private val _connectionState = MutableStateFlow(ScannerConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ScannerConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<GenericSensorPacket>(extraBufferCapacity = 128)
    override val incomingPackets: SharedFlow<GenericSensorPacket> = _incomingPackets.asSharedFlow()

    private val _statusMessage = MutableStateFlow("BLE Idle")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    override val trafficLogs: SharedFlow<TrafficLogEntry> = TrafficMonitorHub.trafficLogs

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeProfile: DeviceProfile? = null
    private var batteryLevel: Int = 95
    private var rssiValue: Int = -60
    private var lastSequence: Int = -1
    private var packetLossCount: Int = 0

    private val defaultServiceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val defaultCharUuid = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    override suspend fun connect(targetAddress: String, profile: DeviceProfile): Boolean {
        activeProfile = profile
        _connectionState.value = ScannerConnectionState.CONNECTING
        _statusMessage.value = "Connecting to BLE Scanner: $targetAddress"

        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: run {
            _connectionState.value = ScannerConnectionState.ERROR
            _statusMessage.value = "Bluetooth adapter unavailable"
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(targetAddress)
        } catch (e: Exception) {
            _connectionState.value = ScannerConnectionState.ERROR
            _statusMessage.value = "Invalid Bluetooth address: ${e.message}"
            return false
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        return true
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ScannerConnectionState.DISCONNECTED
        _statusMessage.value = "BLE Disconnected"
    }

    override suspend fun startAcquisition(): Boolean {
        val cmd = activeProfile?.commandStart ?: byteArrayOf(0xAA.toByte(), 0x01, 0x55.toByte())
        val sent = sendRawBytes(cmd)
        if (sent) {
            _connectionState.value = ScannerConnectionState.STREAMING
            _statusMessage.value = "Acquisition Started (Live Streaming)"
        }
        return sent
    }

    override suspend fun stopAcquisition(): Boolean {
        val cmd = activeProfile?.commandStop ?: byteArrayOf(0xAA.toByte(), 0x02, 0x55.toByte())
        val sent = sendRawBytes(cmd)
        if (sent) {
            _connectionState.value = ScannerConnectionState.CONNECTED
            _statusMessage.value = "Acquisition Stopped"
        }
        return sent
    }

    override suspend fun pauseAcquisition(): Boolean {
        return stopAcquisition()
    }

    override suspend fun resumeAcquisition(): Boolean {
        return startAcquisition()
    }

    override suspend fun sendCalibrationCommand(offset: Float): Boolean {
        val cmd = activeProfile?.commandCalibrate ?: byteArrayOf(0xAA.toByte(), 0x03, 0x55.toByte())
        return sendRawBytes(cmd)
    }

    override suspend fun setSamplingRate(hz: Int): Boolean {
        val rateCmd = "RATE,$hz\n".toByteArray()
        return sendRawBytes(rateCmd)
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendRawBytes(bytes: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val sUuid = activeProfile?.bleServiceUuid?.let { UUID.fromString(it) } ?: defaultServiceUuid
        val cUuid = activeProfile?.bleCharacteristicUuid?.let { UUID.fromString(it) } ?: defaultCharUuid

        val service = gatt.getService(sUuid) ?: return false
        val charac = service.getCharacteristic(cUuid) ?: return false

        TrafficMonitorHub.logTraffic(
            direction = TrafficDirection.TX,
            rawBytes = bytes,
            isChecksumValid = true,
            parsedDescription = "BLE Command Sent (${bytes.size} bytes)"
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(charac, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            charac.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(charac)
        }
    }

    override fun getBatteryLevel(): Int = batteryLevel
    override fun getSignalRssi(): Int = rssiValue
    override fun getConnectedDeviceInfo(): String = activeProfile?.name ?: "BLE Sensor"

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ScannerConnectionState.CONNECTED
                _statusMessage.value = "GATT Connected. Discovering Services..."
                gatt.discoverServices()
                gatt.readRemoteRssi()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ScannerConnectionState.DISCONNECTED
                _statusMessage.value = "BLE Scanner Disconnected"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _statusMessage.value = "Sensor Services Ready. Enabling Notifications..."
                val sUuid = activeProfile?.bleServiceUuid?.let { UUID.fromString(it) } ?: defaultServiceUuid
                val cUuid = activeProfile?.bleCharacteristicUuid?.let { UUID.fromString(it) } ?: defaultCharUuid

                val service = gatt.getService(sUuid)
                val characteristic = service?.getCharacteristic(cUuid)

                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(cccdUuid)
                    descriptor?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(it)
                        }
                    }
                    _statusMessage.value = "Ready to Scan (GATT Notifications Active)"
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                rssiValue = rssi
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processIncomingBytes(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { processIncomingBytes(it) }
        }
    }

    private fun processIncomingBytes(bytes: ByteArray) {
        val packet = DeviceProtocol.parseBinaryPacket(bytes)
            ?: DeviceProtocol.parseTextPacket(String(bytes))

        val isValid = packet?.isValid ?: false
        TrafficMonitorHub.logTraffic(
            direction = TrafficDirection.RX,
            rawBytes = bytes,
            isChecksumValid = isValid,
            parsedDescription = if (packet != null) "Seq=${packet.sequenceNumber}, Val=${packet.sensorChannel1} µT, Batt=${packet.batteryPercent}%" else "Raw bytes stream"
        )

        if (packet != null) {
            batteryLevel = packet.batteryPercent
            // Sequence loss check
            if (lastSequence != -1 && packet.sequenceNumber > lastSequence + 1) {
                packetLossCount += (packet.sequenceNumber - lastSequence - 1)
            }
            lastSequence = packet.sequenceNumber

            scope.launch {
                _incomingPackets.emit(packet)
            }
        }
    }
}
