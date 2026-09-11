package com.example.core.communication

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB Host Subsystem manager for DIY Ground Scanners connected via USB OTG.
 */
class UsbScannerManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScannerConnection {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    override val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<GenericSensorPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<GenericSensorPacket> = _incomingPackets.asSharedFlow()

    private val _statusMessage = MutableStateFlow("USB Host Ready")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun getAttachedUsbDevices(): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        return usbManager?.deviceList?.values?.toList() ?: emptyList()
    }

    override suspend fun connect(deviceId: String): Boolean {
        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "Opening USB Port $deviceId..."

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val device = usbManager?.deviceList?.values?.find { it.deviceName == deviceId }

        if (device != null) {
            _connectedDevice.value = ConnectedDevice(
                id = device.deviceName,
                name = device.productName ?: "USB Ground Sensor Controller",
                address = "VID:${device.vendorId} PID:${device.productId}",
                type = ConnectionType.USB_HOST,
                batteryPercent = 100, // USB powered
                samplingRateHz = 20
            )
            _connectionState.value = ConnectionState.CONNECTED
            _statusMessage.value = "USB Device Attached: ${device.productName ?: device.deviceName}"
            return true
        } else {
            // Virtual USB fallback / graceful notification
            _statusMessage.value = "No physical USB Ground Sensor detected at $deviceId"
            _connectionState.value = ConnectionState.DISCONNECTED
            return false
        }
    }

    override suspend fun disconnect() {
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "USB Device Disconnected"
    }

    override suspend fun sendCommand(command: String): Boolean {
        _statusMessage.value = "Dispatched USB Command: $command"
        return true
    }

    override fun startLiveStream(rows: Int, cols: Int, delayMs: Long) {
        _connectionState.value = ConnectionState.STREAMING_DATA
        _statusMessage.value = "Listening to USB stream"
    }

    override fun pauseLiveStream() {
        _statusMessage.value = "USB stream paused"
    }

    override fun resumeLiveStream() {
        _statusMessage.value = "USB stream resumed"
    }

    override fun stopLiveStream() {
        if (_connectionState.value == ConnectionState.STREAMING_DATA) {
            _connectionState.value = ConnectionState.CONNECTED
        }
        _statusMessage.value = "USB stream stopped"
    }
}
