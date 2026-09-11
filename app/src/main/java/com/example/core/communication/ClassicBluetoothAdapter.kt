package com.example.core.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
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

/**
 * Dedicated Bluetooth Classic (SPP / RFCOMM) implementation of ScannerAdapter.
 * Used for standard hardware modules (HC-05, HC-06, Classic Bluetooth Gradiometer boards).
 */
class ClassicBluetoothAdapter(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScannerAdapter {

    override val transportType: ScannerTransportType = ScannerTransportType.BLUETOOTH_CLASSIC_SPP

    private val _connectionState = MutableStateFlow(ScannerConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ScannerConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<GenericSensorPacket>(extraBufferCapacity = 128)
    override val incomingPackets: SharedFlow<GenericSensorPacket> = _incomingPackets.asSharedFlow()

    private val _statusMessage = MutableStateFlow("Classic SPP Idle")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    override val trafficLogs: SharedFlow<TrafficLogEntry> = TrafficMonitorHub.trafficLogs

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private var activeProfile: DeviceProfile? = null
    private var batteryLevel: Int = 90

    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override suspend fun connect(targetAddress: String, profile: DeviceProfile): Boolean {
        activeProfile = profile
        _connectionState.value = ScannerConnectionState.CONNECTING
        _statusMessage.value = "Opening RFCOMM SPP Socket to $targetAddress..."

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            _connectionState.value = ScannerConnectionState.ERROR
            _statusMessage.value = "Bluetooth Adapter not found"
            return false
        }

        return try {
            val device: BluetoothDevice = adapter.getRemoteDevice(targetAddress)
            val btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            btSocket.connect()

            socket = btSocket
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream

            _connectionState.value = ScannerConnectionState.CONNECTED
            _statusMessage.value = "Connected to ${device.name ?: targetAddress} via SPP"
            startStreamReader()
            true
        } catch (e: Exception) {
            _connectionState.value = ScannerConnectionState.ERROR
            _statusMessage.value = "SPP Connect Failed: ${e.message}"
            disconnect()
            false
        }
    }

    override suspend fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        _connectionState.value = ScannerConnectionState.DISCONNECTED
        _statusMessage.value = "SPP Disconnected"
    }

    override suspend fun startAcquisition(): Boolean {
        val cmd = activeProfile?.commandStart ?: byteArrayOf(0xAA.toByte(), 0x01, 0x55.toByte())
        val sent = sendRawBytes(cmd)
        if (sent) {
            _connectionState.value = ScannerConnectionState.STREAMING
            _statusMessage.value = "Acquisition Running"
        }
        return sent
    }

    override suspend fun stopAcquisition(): Boolean {
        val cmd = activeProfile?.commandStop ?: byteArrayOf(0xAA.toByte(), 0x02, 0x55.toByte())
        val sent = sendRawBytes(cmd)
        if (sent) {
            _connectionState.value = ScannerConnectionState.CONNECTED
            _statusMessage.value = "Acquisition Paused"
        }
        return sent
    }

    override suspend fun pauseAcquisition(): Boolean = stopAcquisition()
    override suspend fun resumeAcquisition(): Boolean = startAcquisition()

    override suspend fun sendCalibrationCommand(offset: Float): Boolean {
        val cmd = activeProfile?.commandCalibrate ?: byteArrayOf(0xAA.toByte(), 0x03, 0x55.toByte())
        return sendRawBytes(cmd)
    }

    override suspend fun setSamplingRate(hz: Int): Boolean {
        val rateCmd = "RATE,$hz\n".toByteArray()
        return sendRawBytes(rateCmd)
    }

    override suspend fun sendRawBytes(bytes: ByteArray): Boolean {
        return try {
            outputStream?.write(bytes)
            outputStream?.flush()
            TrafficMonitorHub.logTraffic(
                direction = TrafficDirection.TX,
                rawBytes = bytes,
                isChecksumValid = true,
                parsedDescription = "SPP Sent (${bytes.size} bytes)"
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getBatteryLevel(): Int = batteryLevel
    override fun getSignalRssi(): Int = -55
    override fun getConnectedDeviceInfo(): String = activeProfile?.name ?: "SPP Sensor"

    private fun startStreamReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val buffer = ByteArray(1024)
            val lineBuffer = StringBuilder()
            val stream = inputStream ?: return@launch

            while (isActive && _connectionState.value != ScannerConnectionState.DISCONNECTED) {
                try {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        val slice = buffer.copyOf(bytesRead)
                        if (bytesRead >= 32 && slice[0] == DeviceProtocol.START_BYTE && slice[31] == DeviceProtocol.END_BYTE) {
                            val packet = DeviceProtocol.parseBinaryPacket(slice)
                            val isValid = packet?.isValid ?: false
                            TrafficMonitorHub.logTraffic(
                                direction = TrafficDirection.RX,
                                rawBytes = slice,
                                isChecksumValid = isValid,
                                parsedDescription = if (packet != null) "Binary: ${packet.sensorChannel1} µT" else "Corrupted Packet"
                            )
                            if (packet != null) {
                                batteryLevel = packet.batteryPercent
                                _incomingPackets.emit(packet)
                            }
                        } else {
                            val textChunk = String(slice)
                            lineBuffer.append(textChunk)
                            while (lineBuffer.contains("\n")) {
                                val lineEnd = lineBuffer.indexOf("\n")
                                val line = lineBuffer.substring(0, lineEnd).trim()
                                lineBuffer.delete(0, lineEnd + 1)
                                if (line.isNotEmpty()) {
                                    val packet = DeviceProtocol.parseTextPacket(line)
                                    TrafficMonitorHub.logTraffic(
                                        direction = TrafficDirection.RX,
                                        rawBytes = line.toByteArray(),
                                        isChecksumValid = packet?.isValid ?: false,
                                        parsedDescription = line
                                    )
                                    if (packet != null) {
                                        batteryLevel = packet.batteryPercent
                                        _incomingPackets.emit(packet)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _statusMessage.value = "SPP stream read interrupted: ${e.message}"
                    }
                    break
                }
            }
        }
    }
}
