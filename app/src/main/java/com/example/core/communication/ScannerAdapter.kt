package com.example.core.communication

import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Common abstraction for all ground-scanner transport layers.
 * Whether connected via BLE, Classic SPP, USB-Serial, or Hardware Simulator,
 * the application interacts through this standard interface.
 */
interface ScannerAdapter {
    val transportType: ScannerTransportType
    val connectionState: StateFlow<ScannerConnectionState>
    val incomingPackets: SharedFlow<GenericSensorPacket>
    val statusMessage: StateFlow<String>
    val trafficLogs: SharedFlow<TrafficLogEntry>

    suspend fun connect(targetAddress: String, profile: DeviceProfile): Boolean
    suspend fun disconnect()

    suspend fun startAcquisition(): Boolean
    suspend fun stopAcquisition(): Boolean
    suspend fun pauseAcquisition(): Boolean
    suspend fun resumeAcquisition(): Boolean

    suspend fun sendCalibrationCommand(offset: Float = 0f): Boolean
    suspend fun setSamplingRate(hz: Int): Boolean
    suspend fun sendRawBytes(bytes: ByteArray): Boolean

    fun getBatteryLevel(): Int
    fun getSignalRssi(): Int
    fun getConnectedDeviceInfo(): String
}

enum class ScannerTransportType {
    BLUETOOTH_LE,
    BLUETOOTH_CLASSIC_SPP,
    USB_SERIAL,
    SIMULATOR
}

enum class ScannerConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR
}

data class TrafficLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: TrafficDirection,
    val rawBytes: ByteArray,
    val hexString: String,
    val asciiString: String,
    val length: Int,
    val isChecksumValid: Boolean,
    val parsedDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TrafficLogEntry
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class TrafficDirection {
    RX, // Received from Scanner
    TX  // Transmitted to Scanner
}
