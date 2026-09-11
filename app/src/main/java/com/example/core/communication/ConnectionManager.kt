package com.example.core.communication

import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING_DATA,
    ERROR
}

enum class ConnectionType {
    BLUETOOTH_LE,
    USB_HOST,
    HARDWARE_SIMULATOR
}

data class ConnectedDevice(
    val id: String,
    val name: String,
    val address: String,
    val type: ConnectionType,
    val batteryPercent: Int = 100,
    val firmwareVersion: String = "v1.4.2",
    val samplingRateHz: Int = 10,
    val isCalibrated: Boolean = true
)

interface ScannerConnection {
    val connectionState: StateFlow<ConnectionState>
    val connectedDevice: StateFlow<ConnectedDevice?>
    val incomingPackets: SharedFlow<GenericSensorPacket>
    val statusMessage: StateFlow<String>

    suspend fun connect(deviceId: String): Boolean
    suspend fun disconnect()
    suspend fun sendCommand(command: String): Boolean
    fun startLiveStream(rows: Int, cols: Int, delayMs: Long = 100L)
    fun pauseLiveStream()
    fun resumeLiveStream()
    fun stopLiveStream()
}
