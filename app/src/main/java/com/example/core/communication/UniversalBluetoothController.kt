package com.example.core.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isBle: Boolean = true
)

/**
 * Universal Bluetooth Controller for Ground Scanner Hardware.
 * Supports:
 *  1. Bluetooth Low Energy (BLE - Nordic UART, HM-10, ESP32, Custom BLE GATT)
 *  2. Classic Bluetooth SPP (Serial Port Profile, HC-05, HC-06, Arduino BT)
 *  3. Dynamic Baud / Sampling Rate (Sense Speed)
 *  4. Device Discovery and Live Telemetry Streaming
 */
class UniversalBluetoothController(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScannerConnection {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    override val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<GenericSensorPacket>(extraBufferCapacity = 128)
    override val incomingPackets: SharedFlow<GenericSensorPacket> = _incomingPackets.asSharedFlow()

    private val _statusMessage = MutableStateFlow("Bluetooth Idle")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    // Active connection references
    private var bluetoothGatt: BluetoothGatt? = null
    private var classicSocket: BluetoothSocket? = null
    private var classicInputStream: InputStream? = null
    private var classicOutputStream: OutputStream? = null
    private var classicWorkerJob: Job? = null

    // UUIDs
    companion object {
        // Standard SPP UUID for Classic Bluetooth (HC-05, HC-06, etc.)
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Standard Nordic UART Service & Characteristic UUIDs (ESP32, nRF52, DIY BLE)
        val NORDIC_UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_TX_CHAR: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_RX_CHAR: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CLIENT_CHAR_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Start scanning for both Classic and BLE Ground Sensors.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth is disabled or not available."
            return
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _statusMessage.value = "Scanning for Ground Scanners (BLE & Classic)..."

        // Add already paired classic devices first
        try {
            val paired = adapter.bondedDevices
            val pairedList = paired.map { device ->
                DiscoveredBluetoothDevice(
                    name = device.name ?: "Paired Sensor",
                    address = device.address,
                    rssi = -60,
                    isBle = false
                )
            }
            _discoveredDevices.value = pairedList
        } catch (e: Exception) {
            // Permission catch
        }

        // Start BLE Scanner
        try {
            val bleScanner = adapter.bluetoothLeScanner
            bleScanner?.startScan(bleScanCallback)
        } catch (e: Exception) {
            _statusMessage.value = "BLE Scan notice: ${e.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            // Ignore
        }
        _statusMessage.value = "Scan stopped."
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = dev.name ?: "BLE Sensor (${dev.address.takeLast(5)})"
            val item = DiscoveredBluetoothDevice(
                name = name,
                address = dev.address,
                rssi = result.rssi,
                isBle = true
            )
            val current = _discoveredDevices.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.address == item.address }
            if (existingIdx >= 0) {
                current[existingIdx] = item
            } else {
                current.add(item)
            }
            _discoveredDevices.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            _statusMessage.value = "BLE Scan failed code: $errorCode"
            _isScanning.value = false
        }
    }

    /**
     * Connect to target scanner (tries BLE first, falls back to Classic SPP).
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String): Boolean {
        stopScan()
        disconnect()

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.ERROR
            _statusMessage.value = "Bluetooth is disabled"
            return false
        }

        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "Connecting to $deviceId..."

        try {
            val device = adapter.getRemoteDevice(deviceId)
            val devType = device.type

            if (devType == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                return connectClassic(device)
            } else {
                // Try BLE first, if gatt fails or classic requested, connectGatt
                return connectBle(device)
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.ERROR
            _statusMessage.value = "Bluetooth permission not granted: ${e.message}"
            return false
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _statusMessage.value = "Connection error: ${e.message}"
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBle(device: BluetoothDevice): Boolean {
        _statusMessage.value = "Opening BLE GATT channel..."
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun connectClassic(device: BluetoothDevice): Boolean {
        _statusMessage.value = "Opening Classic SPP Socket (RFCOMM)..."
        return try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            classicSocket = socket
            classicInputStream = socket.inputStream
            classicOutputStream = socket.outputStream

            _connectedDevice.value = ConnectedDevice(
                id = device.address,
                name = device.name ?: "Classic Ground Sensor",
                address = device.address,
                type = ConnectionType.BLUETOOTH_LE,
                batteryPercent = 92,
                firmwareVersion = "SPP-v1.0"
            )
            _connectionState.value = ConnectionState.CONNECTED
            _statusMessage.value = "Connected to ${device.name ?: device.address} via SPP"

            startClassicReader()
            true
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _statusMessage.value = "SPP Connect failed: ${e.localizedMessage}"
            false
        }
    }

    private fun startClassicReader() {
        classicWorkerJob?.cancel()
        classicWorkerJob = scope.launch {
            val buffer = ByteArray(1024)
            val lineBuffer = StringBuilder()
            val stream = classicInputStream ?: return@launch

            while (isActive && _connectionState.value != ConnectionState.DISCONNECTED) {
                try {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        // Check if binary 32-byte packet or ASCII line
                        if (bytesRead >= 32 && buffer[0] == DeviceProtocol.START_BYTE && buffer[31] == DeviceProtocol.END_BYTE) {
                            val packet = DeviceProtocol.parseBinaryPacket(buffer.copyOf(32))
                            if (packet != null) {
                                _incomingPackets.emit(packet)
                            }
                        } else {
                            val textChunk = String(buffer, 0, bytesRead)
                            lineBuffer.append(textChunk)
                            while (lineBuffer.contains("\n")) {
                                val lineEnd = lineBuffer.indexOf("\n")
                                val line = lineBuffer.substring(0, lineEnd).trim()
                                lineBuffer.delete(0, lineEnd + 1)
                                if (line.isNotEmpty()) {
                                    val packet = DeviceProtocol.parseTextPacket(line)
                                    if (packet != null) {
                                        _incomingPackets.emit(packet)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _statusMessage.value = "Classic stream error: ${e.message}"
                    }
                    break
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _statusMessage.value = "GATT Connected. Discovering sensor services..."
                gatt.discoverServices()
                _connectedDevice.value = ConnectedDevice(
                    id = gatt.device.address,
                    name = gatt.device.name ?: "BLE Ground Scanner",
                    address = gatt.device.address,
                    type = ConnectionType.BLUETOOTH_LE,
                    batteryPercent = 95,
                    firmwareVersion = "BLE-v2.0"
                )
                _connectionState.value = ConnectionState.CONNECTED
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null
                _statusMessage.value = "BLE Scanner Disconnected"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _statusMessage.value = "Sensor Services Ready. Enabling Notifications..."
                // Find Nordic UART or compatible notify characteristic
                val service = gatt.getService(NORDIC_UART_SERVICE)
                val rxChar = service?.getCharacteristic(NORDIC_RX_CHAR)

                if (rxChar != null) {
                    gatt.setCharacteristicNotification(rxChar, true)
                    val descriptor = rxChar.getDescriptor(CLIENT_CHAR_CONFIG)
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
                    _statusMessage.value = "Live Telemetry Channel Active"
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleIncomingBytes(characteristic.value ?: return)
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val packet = DeviceProtocol.parseBinaryPacket(bytes)
            ?: DeviceProtocol.parseTextPacket(String(bytes))

        if (packet != null) {
            scope.launch {
                _incomingPackets.emit(packet)
            }
        }
    }

    /**
     * Send hardware configuration commands (e.g. Set Sense Speed, Pulse Rate, Mode)
     */
    @SuppressLint("MissingPermission")
    override suspend fun sendCommand(command: String): Boolean {
        val payload = "$command\n".toByteArray()

        // Classic SPP
        classicOutputStream?.let { out ->
            return try {
                out.write(payload)
                out.flush()
                _statusMessage.value = "Sent command: $command"
                true
            } catch (e: Exception) {
                _statusMessage.value = "Send command error: ${e.message}"
                false
            }
        }

        // BLE UART
        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(NORDIC_UART_SERVICE)
            val txChar = service?.getCharacteristic(NORDIC_TX_CHAR)
            if (txChar != null) {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(txChar, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        @Suppress("DEPRECATION")
                        txChar.value = payload
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(txChar)
                    }
                    _statusMessage.value = "Sent BLE Command: $command"
                    true
                } catch (e: Exception) {
                    _statusMessage.value = "BLE send error: ${e.message}"
                    false
                }
            }
        }

        _statusMessage.value = "Command not sent: No active Bluetooth connection"
        return false
    }

    /**
     * Adjust hardware sensing frequency / pulse rate (Sense Speed).
     */
    suspend fun setSenseSpeed(speedHz: Int) {
        sendCommand("SPEED=$speedHz")
    }

    /**
     * Trigger manual impulse measurement (like Visualizer 3D manual pulse).
     */
    suspend fun triggerManualImpulse() {
        sendCommand("PULSE_TRIG")
    }

    override fun startLiveStream(rows: Int, cols: Int, delayMs: Long) {
        _connectionState.value = ConnectionState.STREAMING_DATA
        _statusMessage.value = "Receiving Live Bluetooth Telemetry (${rows}x$cols)"
        scope.launch {
            sendCommand("START_STREAM rows=$rows cols=$cols rate=$delayMs")
        }
    }

    override fun pauseLiveStream() {
        _statusMessage.value = "Live Stream Paused"
        scope.launch { sendCommand("PAUSE_STREAM") }
    }

    override fun resumeLiveStream() {
        _statusMessage.value = "Live Stream Resumed"
        scope.launch { sendCommand("RESUME_STREAM") }
    }

    override fun stopLiveStream() {
        if (_connectionState.value == ConnectionState.STREAMING_DATA) {
            _connectionState.value = ConnectionState.CONNECTED
        }
        _statusMessage.value = "Live Stream Stopped"
        scope.launch { sendCommand("STOP_STREAM") }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        classicWorkerJob?.cancel()
        classicWorkerJob = null

        try {
            classicInputStream?.close()
            classicOutputStream?.close()
            classicSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        classicSocket = null
        classicInputStream = null
        classicOutputStream = null

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            // Ignore
        }
        bluetoothGatt = null

        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "Disconnected"
    }
}
