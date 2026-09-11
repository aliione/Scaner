package com.example.core.communication

import com.example.core.model.GenericSensorPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

enum class SimulationScenario(val label: String, val description: String) {
    SINGLE_TARGET("Single Localized Target", "Prominent positive anomaly in grid center (simulating compact high-magnetic target)"),
    DUAL_TARGET("Dual Targets", "Two distinct targets with different amplitudes and depths"),
    CAVITY_VOID("Subsurface Cavity / Void", "Pronounced negative anomaly with subtle edge rim compaction"),
    HIGH_NOISE("High Geological Noise", "Challenging mineralized ground with low signal-to-noise ratio"),
    LINEAR_FEATURE("Linear Trench / Pipe", "Elongated subsurface utility or structural foundation line")
}

/**
 * Realistic ground-scanner hardware simulator.
 * Generates synthetic sensor packets adhering to scientific principles.
 * Always marks data as simulated.
 */
class HardwareSimulator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : ScannerConnection {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    override val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<GenericSensorPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<GenericSensorPacket> = _incomingPackets.asSharedFlow()

    private val _statusMessage = MutableStateFlow("Simulator Ready")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    var activeScenario: SimulationScenario = SimulationScenario.SINGLE_TARGET
    var noiseStdDev: Float = 1.5f

    private var streamJob: Job? = null
    private var isPaused = false
    private val random = Random()

    override suspend fun connect(deviceId: String): Boolean {
        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "Initializing Sensor Emulation Kernel..."
        delay(600)
        _connectedDevice.value = ConnectedDevice(
            id = "SIMULATOR_V2",
            name = "DIY Scanner Simulator [SIMULATED]",
            address = "00:SIM:GEO:SCAN",
            type = ConnectionType.HARDWARE_SIMULATOR,
            batteryPercent = 94,
            firmwareVersion = "v2.1-sim",
            samplingRateHz = 15,
            isCalibrated = true
        )
        _connectionState.value = ConnectionState.CONNECTED
        _statusMessage.value = "Connected to Sensor Simulator"
        return true
    }

    override suspend fun disconnect() {
        stopLiveStream()
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "Simulator Disconnected"
    }

    override suspend fun sendCommand(command: String): Boolean {
        _statusMessage.value = "Simulator Command Acknowledged: $command"
        return true
    }

    override fun startLiveStream(rows: Int, cols: Int, delayMs: Long) {
        stopLiveStream()
        isPaused = false
        _connectionState.value = ConnectionState.STREAMING_DATA
        _statusMessage.value = "Acquisition active: ${rows}x$cols grid"

        streamJob = scope.launch {
            var seq = 0
            val totalPoints = rows * cols

            for (r in 0 until rows) {
                // Zig-zag / raster survey walking pattern
                val colRange = if (r % 2 == 0) 0 until cols else (cols - 1 downTo 0)
                for (c in colRange) {
                    while (isPaused && isActive) {
                        delay(100)
                    }
                    if (!isActive) break

                    val rawVal = computeSyntheticValue(r, c, rows, cols)
                    val noise = (random.nextGaussian() * noiseStdDev).toFloat()
                    val sensor1 = rawVal + noise

                    val packet = GenericSensorPacket(
                        header = DeviceProtocol.START_BYTE,
                        deviceId = "SIM_DEVICE",
                        timestamp = System.currentTimeMillis(),
                        sequenceNumber = seq++,
                        xIndex = c,
                        yIndex = r,
                        sensorChannel1 = sensor1,
                        sensorChannel2 = sensor1 * 0.95f,
                        sensorChannel3 = sensor1 * 0.05f,
                        batteryPercent = (95 - (seq * 10 / totalPoints)).coerceIn(10, 100),
                        orientationPitch = (sin(seq * 0.1) * 2.0).toFloat(),
                        orientationRoll = (sin(seq * 0.08) * 1.5).toFloat(),
                        orientationYaw = (r * 180f / rows),
                        checksum = 0,
                        isValid = true
                    )

                    _incomingPackets.emit(packet)
                    delay(delayMs)
                }
            }
            _connectionState.value = ConnectionState.CONNECTED
            _statusMessage.value = "Scan Grid Completed ($totalPoints samples)"
        }
    }

    private fun computeSyntheticValue(r: Int, c: Int, rows: Int, cols: Int): Float {
        val normY = (r.toFloat() / rows.toFloat() - 0.5f) * 10f
        val normX = (c.toFloat() / cols.toFloat() - 0.5f) * 10f

        return when (activeScenario) {
            SimulationScenario.SINGLE_TARGET -> {
                // Intense positive Gaussian target at (X=0.5, Y=0.2)
                val dx = normX - 0.5f
                val dy = normY - 0.2f
                val peak = 85f * exp(-(dx * dx + dy * dy) / 4.5f)
                10f + peak
            }

            SimulationScenario.DUAL_TARGET -> {
                val dx1 = normX + 2.5f
                val dy1 = normY + 1.0f
                val p1 = 70f * exp(-(dx1 * dx1 + dy1 * dy1) / 3.0f)

                val dx2 = normX - 2.2f
                val dy2 = normY - 1.8f
                val p2 = 48f * exp(-(dx2 * dx2 + dy2 * dy2) / 2.2f)
                12f + p1 + p2
            }

            SimulationScenario.CAVITY_VOID -> {
                // Negative anomaly in center with rim halo
                val distSq = normX * normX + normY * normY
                val voidDip = -65f * exp(-distSq / 5.0f)
                val rim = 18f * exp(-((distSq - 9f) * (distSq - 9f)) / 8.0f)
                20f + voidDip + rim
            }

            SimulationScenario.HIGH_NOISE -> {
                val dx = normX - 1.0f
                val dy = normY + 0.5f
                val subtleTarget = 28f * exp(-(dx * dx + dy * dy) / 3.5f)
                // Geological regional slope + subtle target
                8f + normX * 2.2f - normY * 1.5f + subtleTarget
            }

            SimulationScenario.LINEAR_FEATURE -> {
                // Linear trench / pipe along diagonal normY = 0.6 * normX
                val lineDist = kotlin.math.abs(normY - 0.6f * normX) / 1.166f
                val pipeSignal = 62f * exp(-(lineDist * lineDist) / 1.2f)
                14f + pipeSignal
            }
        }
    }

    override fun pauseLiveStream() {
        isPaused = true
        _statusMessage.value = "Acquisition Paused"
    }

    override fun resumeLiveStream() {
        isPaused = false
        _statusMessage.value = "Acquisition Resumed"
    }

    override fun stopLiveStream() {
        streamJob?.cancel()
        streamJob = null
        isPaused = false
        if (_connectionState.value == ConnectionState.STREAMING_DATA) {
            _connectionState.value = ConnectionState.CONNECTED
            _statusMessage.value = "Acquisition Stopped"
        }
    }
}
