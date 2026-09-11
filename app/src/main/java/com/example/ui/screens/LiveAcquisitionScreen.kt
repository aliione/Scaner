package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.audio.AcousticFeedbackEngine
import com.example.core.communication.ConnectionState
import com.example.core.communication.HardwareSimulator
import com.example.core.communication.ScannerConnection
import com.example.core.communication.SimulationScenario
import com.example.core.communication.UniversalBluetoothController
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.ColorPaletteType
import com.example.core.model.MeasurementGrid
import com.example.core.model.MeasurementPoint
import com.example.core.model.ScanMode
import com.example.core.model.ScanProject
import com.example.core.visualization.ColorMapEngine
import com.example.ui.components.RawTrafficMonitorDialog
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class HardwareSourceType(val label: String) {
    BLUETOOTH("Physical Bluetooth Sensor"),
    SIMULATOR("Hardware Simulator (Synthetic)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAcquisitionScreen(
    onBack: () -> Unit,
    onSaveScan: (ScanProject, MeasurementGrid) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current
    val isPersian = currentLanguage == AppLanguage.PERSIAN

    var selectedSource by remember { mutableStateOf(HardwareSourceType.BLUETOOTH) }
    val simulator = remember { HardwareSimulator() }
    val bluetoothController = remember { UniversalBluetoothController(context, scope) }

    val activeConnection: ScannerConnection = if (selectedSource == HardwareSourceType.BLUETOOTH) {
        bluetoothController
    } else {
        simulator
    }

    val audioEngine = remember { AcousticFeedbackEngine(context) }

    val connectionState by activeConnection.connectionState.collectAsState()
    val connectedDevice by activeConnection.connectedDevice.collectAsState()
    val statusMessage by activeConnection.statusMessage.collectAsState()

    val isScanningBt by bluetoothController.isScanning.collectAsState()
    val discoveredDevices by bluetoothController.discoveredDevices.collectAsState()

    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTrafficDialog by remember { mutableStateOf(false) }

    // Sense Speed / Sampling Rate: 10 Hz to 100 Hz (Visualizer 3D style)
    var senseSpeedHz by remember { mutableIntStateOf(20) }

    val scanRows = 20
    val scanCols = 20
    val totalPoints = scanRows * scanCols

    val liveMatrix = remember {
        mutableStateListOf<Float>().apply {
            repeat(totalPoints) { add(Float.NaN) }
        }
    }

    var currentX by remember { mutableIntStateOf(0) }
    var currentY by remember { mutableIntStateOf(0) }
    var currentSignal by remember { mutableFloatStateOf(0f) }
    var currentPitch by remember { mutableFloatStateOf(0f) }
    var currentRoll by remember { mutableFloatStateOf(0f) }
    var batteryLevel by remember { mutableIntStateOf(95) }
    var acquiredCount by remember { mutableIntStateOf(0) }

    var isZigZagMode by remember { mutableStateOf(false) }
    var isAudioEnabled by remember { mutableStateOf(true) }

    // Bluetooth permission request launcher
    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            bluetoothController.startScan()
            showBluetoothDialog = true
        } else {
            Toast.makeText(context, "Bluetooth permissions required for scanner link", Toast.LENGTH_LONG).show()
        }
    }

    fun requestBtAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            bluetoothController.startScan()
            showBluetoothDialog = true
        } else {
            btPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(isAudioEnabled) {
        audioEngine.isAudioEnabled = isAudioEnabled
    }

    // Packet ingestion flow
    LaunchedEffect(activeConnection) {
        if (selectedSource == HardwareSourceType.SIMULATOR) {
            simulator.connect("SIMULATOR_V2")
        }
        activeConnection.incomingPackets.collectLatest { packet ->
            val mappedX = if (isZigZagMode && (packet.yIndex % 2 == 1)) {
                (scanCols - 1) - packet.xIndex
            } else {
                packet.xIndex
            }

            val idx = packet.yIndex * scanCols + mappedX
            if (idx in 0 until totalPoints) {
                liveMatrix[idx] = packet.sensorChannel1
                currentX = mappedX
                currentY = packet.yIndex
                currentSignal = packet.sensorChannel1
                currentPitch = packet.orientationPitch
                currentRoll = packet.orientationRoll
                batteryLevel = packet.batteryPercent
                acquiredCount = liveMatrix.count { !it.isNaN() }

                if (isAudioEnabled) {
                    audioEngine.playFeedbackForValue(packet.sensorChannel1, baseline = 0f)
                }
            }
        }
    }

    DisposableEffect(activeConnection) {
        onDispose {
            scope.launch {
                activeConnection.stopLiveStream()
                activeConnection.disconnect()
            }
            audioEngine.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.liveAcquisitionTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            // Connection status badge
                            val badgeColor = when (connectionState) {
                                ConnectionState.CONNECTED -> GeoGreenSignal
                                ConnectionState.STREAMING_DATA -> GeoCyan
                                ConnectionState.CONNECTING -> GeoAmber
                                else -> TextMuted
                            }
                            Text(
                                if (connectedDevice != null) connectedDevice!!.name else if (isPersian) "بدون اتصال" else "No Sensor",
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(statusMessage, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                actions = {
                    // Hardware Source Selector (BLE vs Simulator)
                    IconButton(onClick = {
                        if (selectedSource == HardwareSourceType.SIMULATOR) {
                            selectedSource = HardwareSourceType.BLUETOOTH
                            requestBtAndScan()
                        } else {
                            requestBtAndScan()
                        }
                    }) {
                        Icon(
                            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.STREAMING_DATA) {
                                Icons.Default.BluetoothConnected
                            } else {
                                Icons.Default.Bluetooth
                            },
                            contentDescription = strings.bluetoothSensor,
                            tint = if (selectedSource == HardwareSourceType.BLUETOOTH) GeoCyan else TextMuted
                        )
                    }

                    // Sense Speed & Sampling Configuration (Visualizer 3D)
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(Icons.Default.Speed, contentDescription = strings.senseSpeed, tint = GeoAmber)
                    }

                    // Raw Hex Traffic Monitor
                    IconButton(onClick = { showTrafficDialog = true }) {
                        Icon(Icons.Default.DataObject, contentDescription = "Raw Traffic Monitor", tint = GeoCyan)
                    }

                    // Audio feedback toggle
                    IconButton(onClick = { isAudioEnabled = !isAudioEnabled }) {
                        Icon(
                            if (isAudioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = strings.acousticAudioTone,
                            tint = if (isAudioEnabled) GeoCyan else TextMuted
                        )
                    }

                    // Reset grid
                    IconButton(
                        onClick = {
                            activeConnection.stopLiveStream()
                            for (i in 0 until totalPoints) liveMatrix[i] = Float.NaN
                            acquiredCount = 0
                            currentX = 0
                            currentY = 0
                            Toast.makeText(context, "Grid reset", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Grid", tint = TextSecondary)
                    }

                    // Save captured grid
                    IconButton(
                        onClick = {
                            val validPoints = mutableListOf<MeasurementPoint>()
                            var id = 0
                            for (r in 0 until scanRows) {
                                for (c in 0 until scanCols) {
                                    val v = liveMatrix[r * scanCols + c]
                                    val isMiss = v.isNaN()
                                    val finalVal = if (isMiss) 0f else v
                                    validPoints.add(
                                        MeasurementPoint(
                                            pointId = id++,
                                            gridX = c,
                                            gridY = r,
                                            posX = (c + 0.5f) * 0.5f,
                                            posY = (r + 0.5f) * 0.5f,
                                            rawValue = finalVal,
                                            processedValue = finalVal,
                                            isMissing = isMiss
                                        )
                                    )
                                }
                            }
                            val isRealHardware = (selectedSource == HardwareSourceType.BLUETOOTH)
                            val proj = ScanProject(
                                id = UUID.randomUUID().toString(),
                                name = if (isRealHardware) "BT Survey #${System.currentTimeMillis() % 10000}" else "Sim Survey #${System.currentTimeMillis() % 10000}",
                                location = if (isRealHardware) (connectedDevice?.name ?: "Bluetooth Device") else "Synthetic Field",
                                rows = scanRows,
                                cols = scanCols,
                                widthMeters = scanCols * 0.5f,
                                lengthMeters = scanRows * 0.5f,
                                scanMode = ScanMode.RECTANGULAR_GRID,
                                isSimulated = !isRealHardware
                            )
                            val grid = MeasurementGrid(
                                rows = scanRows,
                                cols = scanCols,
                                widthMeters = scanCols * 0.5f,
                                lengthMeters = scanRows * 0.5f,
                                gridSpacingMeters = 0.5f,
                                points = validPoints
                            )
                            onSaveScan(proj, grid)
                            Toast.makeText(context, "Survey saved to Project Workstation", Toast.LENGTH_SHORT).show()
                        },
                        enabled = acquiredCount > 5
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = if (acquiredCount > 5) GeoCyan else TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDarkBackground)
            )
        },
        containerColor = SlateDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // Live Telemetry Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateCardSurface)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(strings.signalStrength, color = TextMuted, fontSize = 10.sp)
                    Text("${String.format("%.1f", currentSignal)} µT", color = GeoCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Column {
                    Text(strings.currentPosition, color = TextMuted, fontSize = 10.sp)
                    Text("[$currentX, $currentY]", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Column {
                    Text(strings.senseSpeed, color = TextMuted, fontSize = 10.sp)
                    Text("${senseSpeedHz} Hz", color = GeoAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column {
                    Text(strings.battery, color = TextMuted, fontSize = 10.sp)
                    Text("$batteryLevel%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Hardware source mode & Walk indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source toggle chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedSource == HardwareSourceType.BLUETOOTH,
                        onClick = {
                            selectedSource = HardwareSourceType.BLUETOOTH
                            requestBtAndScan()
                        },
                        label = { Text(strings.bluetoothSensor, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                            selectedLabelColor = GeoCyan
                        )
                    )
                    FilterChip(
                        selected = selectedSource == HardwareSourceType.SIMULATOR,
                        onClick = { selectedSource = HardwareSourceType.SIMULATOR },
                        label = { Text(strings.hardwareSimulator, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GeoAmber.copy(alpha = 0.25f),
                            selectedLabelColor = GeoAmber
                        )
                    )
                }

                // Walk pattern toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isZigZagMode = !isZigZagMode }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = if (isZigZagMode) GeoAmber else GeoCyan,
                        modifier = Modifier.height(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isZigZagMode) strings.zigZagWalk else strings.parallelWalk,
                        color = if (isZigZagMode) GeoAmber else GeoCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { acquiredCount.toFloat() / totalPoints.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = GeoCyan,
                trackColor = SlateElevated
            )

            Spacer(Modifier.height(6.dp))

            // Real-Time Grid Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCardSurface)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cellW = w / scanCols
                    val cellH = h / scanRows

                    // Draw grid cells
                    for (r in 0 until scanRows) {
                        for (c in 0 until scanCols) {
                            val v = liveMatrix[r * scanCols + c]
                            val topLeft = Offset(c * cellW, r * cellH)
                            val cellSize = Size(cellW, cellH)

                            if (v.isNaN()) {
                                drawRect(
                                    color = SlateElevated,
                                    topLeft = topLeft,
                                    size = cellSize
                                )
                            } else {
                                val color = ColorMapEngine.getColorForValue(
                                    value = v,
                                    min = -30f,
                                    max = 100f,
                                    palette = ColorPaletteType.SPECTRUM
                                )
                                drawRect(color = color, topLeft = topLeft, size = cellSize)
                            }

                            // Grid line outline
                            drawRect(
                                color = Color.White.copy(alpha = 0.08f),
                                topLeft = topLeft,
                                size = cellSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f)
                            )
                        }
                    }

                    // Current position crosshair
                    val curTopLeft = Offset(currentX * cellW, currentY * cellH)
                    drawRect(
                        color = Color.White,
                        topLeft = curTopLeft,
                        size = Size(cellW, cellH),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Acquisition Control Buttons + Manual Impulse Transmission (Visualizer 3D Feature)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (connectionState != ConnectionState.STREAMING_DATA) {
                    val delay = (1000L / senseSpeedHz).coerceIn(10L, 500L)
                    Button(
                        onClick = { activeConnection.startLiveStream(scanRows, scanCols, delayMs = delay) },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(strings.startScan, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { activeConnection.pauseLiveStream() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoAmber, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(strings.pauseScan)
                    }

                    Button(
                        onClick = { activeConnection.stopLiveStream() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(strings.stopScan)
                    }
                }

                // Manual Pulse / Impulse Transmission Trigger (Visualizer 3D Manual Mode)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (selectedSource == HardwareSourceType.BLUETOOTH) {
                                bluetoothController.triggerManualImpulse()
                            }
                            // Advance 1 cell manually
                            val nextIdx = acquiredCount.coerceIn(0, totalPoints - 1)
                            val simulatedPulseVal = 42.5f + (Math.random().toFloat() * 15f)
                            liveMatrix[nextIdx] = simulatedPulseVal
                            currentX = nextIdx % scanCols
                            currentY = nextIdx / scanCols
                            acquiredCount = liveMatrix.count { !it.isNaN() }
                            if (isAudioEnabled) {
                                audioEngine.playFeedbackForValue(simulatedPulseVal, 0f)
                            }
                            Toast.makeText(context, if (isPersian) "ثبت پالس دستی در [$currentX, $currentY]" else "Impulse captured at [$currentX, $currentY]", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GeoAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeoAmber)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.manualPulse, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Bluetooth Hardware Discovery & Connection Dialog
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = {
                bluetoothController.stopScan()
                showBluetoothDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = GeoCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.connectSensor, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.sensorDeviceDescription,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isScanningBt) strings.scanningNearbySensors else "${discoveredDevices.size} ${if (isPersian) "دستگاه شناسایی شد" else "devices found"}",
                            color = if (isScanningBt) GeoAmber else TextSecondary,
                            fontSize = 12.sp
                        )
                        if (isScanningBt) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), color = GeoAmber, strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { bluetoothController.startScan() }) {
                                Text(strings.rescan, color = GeoCyan, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (discoveredDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(strings.noSensorsFound, color = TextMuted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(discoveredDevices) { dev ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            scope.launch {
                                                bluetoothController.stopScan()
                                                val success = bluetoothController.connect(dev.address)
                                                if (success) {
                                                    Toast.makeText(context, "${if (isPersian) "متصل شد به" else "Connected to"} ${dev.name}", Toast.LENGTH_SHORT).show()
                                                    showBluetoothDialog = false
                                                } else {
                                                    Toast.makeText(context, "${if (isPersian) "خطا در اتصال به" else "Connection failed to"} ${dev.name}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = SlateElevated),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (connectedDevice?.address == dev.address) GeoGreenSignal else SlateCardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(dev.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(dev.address, color = TextMuted, fontSize = 10.sp)
                                        }
                                        Text(
                                            if (dev.isBle) "BLE" else "Classic SPP",
                                            color = if (dev.isBle) GeoCyan else GeoAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    bluetoothController.stopScan()
                    showBluetoothDialog = false
                }) {
                    Text(strings.close, color = TextMuted)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    // Sense Speed & Hardware Pulse Dialog (Visualizer 3D Feature)
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = GeoAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.senseSpeed, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.senseSpeedDescription,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isPersian) "فرکانس دریافت سنسور:" else "Acquisition Frequency:", color = TextPrimary, fontSize = 13.sp)
                        Text("$senseSpeedHz Hz (${1000 / senseSpeedHz} ms/pt)", color = GeoAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Slider(
                        value = senseSpeedHz.toFloat(),
                        onValueChange = { senseSpeedHz = it.toInt() },
                        valueRange = 5f..100f,
                        steps = 18,
                        colors = SliderDefaults.colors(thumbColor = GeoAmber, activeTrackColor = GeoAmber),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(if (isPersian) "حالت‌های پیش‌فرض سرعت اسکن:" else "Speed Presets (Ground Scanning Speed):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            10 to (if (isPersian) "آهسته / دقیق" else "Slow / Fine"),
                            25 to (if (isPersian) "معمولی" else "Normal"),
                            50 to (if (isPersian) "سریع" else "Fast Walk"),
                            100 to (if (isPersian) "حداکثر" else "Turbo")
                        ).forEach { (hz, label) ->
                            FilterChip(
                                selected = senseSpeedHz == hz,
                                onClick = {
                                    senseSpeedHz = hz
                                    scope.launch {
                                        bluetoothController.setSenseSpeed(hz)
                                    }
                                },
                                label = { Text("$hz Hz", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoAmber.copy(alpha = 0.25f),
                                    selectedLabelColor = GeoAmber
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            bluetoothController.setSenseSpeed(senseSpeedHz)
                        }
                        showSpeedDialog = false
                        Toast.makeText(context, if (isPersian) "سرعت سنس روی $senseSpeedHz هرتز تنظیم شد" else "Sense Speed updated to $senseSpeedHz Hz", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoAmber, contentColor = Color.Black)
                ) {
                    Text(if (isPersian) "اعمال به سنسور" else "Apply to Sensor", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    if (showTrafficDialog) {
        RawTrafficMonitorDialog(
            onDismiss = { showTrafficDialog = false }
        )
    }
}
