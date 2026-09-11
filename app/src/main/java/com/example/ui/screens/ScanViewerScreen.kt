package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.export.ExportManager
import com.example.core.model.ColorPaletteType
import com.example.core.model.DetectedAnomaly
import com.example.core.model.InterpolationMethod
import com.example.core.model.MeasurementGrid
import com.example.core.model.ScanProject
import com.example.core.model.SoilProfile
import com.example.core.model.TargetCategory
import com.example.core.model.UserTarget
import com.example.core.processing.AnomalyDetector
import com.example.core.processing.DepthEstimator
import com.example.core.processing.InterpolationEngine
import com.example.core.processing.SignalProcessingPipeline
import com.example.ui.components.ColorScaleBar
import com.example.ui.components.CrossSectionCanvas
import com.example.ui.components.HeatmapCanvas
import com.example.ui.components.Surface3DCanvas
import com.example.ui.components.Volume3DCanvas
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.GeoRedPositive
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.core.i18n.LanguageManager
import com.example.core.i18n.LocalAppStrings
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.UUID

enum class ViewerTab {
    HEATMAP_2D,
    SURFACE_3D,
    VOLUME_3D,
    CROSS_SECTION,
    TARGETS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanViewerScreen(
    project: ScanProject,
    initialGrid: MeasurementGrid,
    targets: List<UserTarget>,
    onBack: () -> Unit,
    onSaveGrid: (MeasurementGrid) -> Unit,
    onAddTarget: (UserTarget) -> Unit,
    onDeleteTarget: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val isPersian = LanguageManager.isPersian

    var activeTab by remember { mutableStateOf(ViewerTab.HEATMAP_2D) }
    var gridState by remember { mutableStateOf(initialGrid) }
    var activePalette by remember { mutableStateOf(ColorPaletteType.SPECTRUM) }
    var activeInterpolation by remember { mutableStateOf(InterpolationMethod.NONE) }

    val pipeline = remember { SignalProcessingPipeline() }
    val detector = remember { AnomalyDetector() }
    val interpolator = remember { InterpolationEngine() }

    var detectedAnomalies by remember(gridState) {
        mutableStateOf(detector.detectAnomalies(gridState))
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showAnomalySheet by remember { mutableStateOf(false) }
    var showDepthDialog by remember { mutableStateOf(false) }
    var selectedAnomalyForDepth by remember { mutableStateOf<DetectedAnomaly?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSoilDialog by remember { mutableStateOf(false) }
    var activeSoilProfile by remember {
        mutableStateOf(SoilProfile.DEFAULT_SOILS.firstOrNull { it.name == project.soilProfileName } ?: SoilProfile.DEFAULT_SOILS.first())
    }

    // Interactive Tap Selection on 2D Heatmap
    var selectedCellCoordinates by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedCellValue by remember { mutableStateOf<Float?>(null) }
    var showAddTargetDialog by remember { mutableStateOf(false) }

    val stats = remember(gridState) { gridState.getStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                project.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (project.isSimulated) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GeoAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                        .testTag("demo_tag")
                                ) {
                                    Text(if (isPersian) "شبیه‌ساز" else "DEMO", color = GeoAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            "${gridState.cols}×${gridState.rows} ${if (isPersian) "نقطه" else "pts"} (${gridState.widthMeters}m × ${gridState.lengthMeters}m) | Min: ${String.format("%.1f", stats.min)} Peak: ${String.format("%.1f", stats.max)}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showSoilDialog = true }) {
                        Icon(Icons.Default.Landscape, contentDescription = strings.soilType, tint = GeoGreenSignal)
                    }
                    IconButton(onClick = { showAnomalySheet = true }) {
                        Icon(Icons.Default.Warning, contentDescription = strings.anomaliesDetected, tint = GeoAmber)
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = strings.filterPipeline, tint = GeoCyan)
                    }
                    IconButton(onClick = { showPaletteDialog = true }) {
                        Icon(Icons.Default.ColorLens, contentDescription = strings.colorPalette, tint = TextSecondary)
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = strings.exportData, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDarkBackground)
            )
        },
        bottomBar = {
            // Footer with numerical color scale legend
            if (activeTab != ViewerTab.TARGETS) {
                ColorScaleBar(
                    minVal = stats.min,
                    maxVal = stats.max,
                    palette = activePalette,
                    unitSymbol = "µT",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        containerColor = SlateDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // View Mode Tabs
            PrimaryTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = SlateCardSurface,
                contentColor = GeoCyan
            ) {
                ViewerTab.values().forEach { tab ->
                    val tabTitle = when (tab) {
                        ViewerTab.HEATMAP_2D -> strings.map2D
                        ViewerTab.SURFACE_3D -> strings.surface3D
                        ViewerTab.VOLUME_3D -> strings.voxelVolume
                        ViewerTab.CROSS_SECTION -> strings.crossSection
                        ViewerTab.TARGETS -> strings.targetsRegistry
                    }
                    val tabText = if (tab == ViewerTab.TARGETS) "$tabTitle (${targets.size})" else tabTitle
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = {
                            Text(
                                tabText,
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Main Active Visualization Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    ViewerTab.HEATMAP_2D -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HeatmapCanvas(
                                grid = gridState,
                                palette = activePalette,
                                anomalies = detectedAnomalies,
                                targets = targets,
                                onCellSelected = { x, y, v ->
                                    selectedCellCoordinates = Pair(x, y)
                                    selectedCellValue = v
                                }
                            )

                            // Interactive Selected Cell Inspection Bottom HUD
                            selectedCellCoordinates?.let { (col, row) ->
                                val cellVal = selectedCellValue ?: 0f
                                val posX = (col + 0.5f) * gridState.gridSpacingMeters
                                val posY = (row + 0.5f) * gridState.gridSpacingMeters
                                val contrast = cellVal - stats.average
                                val quickDepth = (gridState.gridSpacingMeters * 1.5f).coerceAtLeast(0.3f)

                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateElevated.copy(alpha = 0.95f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GeoCyan)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${if (isPersian) "موقعیت:" else "Pos:"} (${String.format("%.2f", posX)}m, ${String.format("%.2f", posY)}m)",
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    if (isPersian) "[ستون $col، ردیف $row]" else "[Col $col, Row $row]",
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                "${if (isPersian) "سیگنال:" else "Signal:"} ${String.format("%.2f", cellVal)} µT (${if (isPersian) "کنتراست:" else "Contrast:"} ${String.format("%+.2f", contrast)} µT)",
                                                color = if (contrast >= 0) GeoCyan else GeoAmber,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "${if (isPersian) "تخمین عمق وارون‌سازی:" else "Est. Inversion Depth:"} ~${String.format("%.2f", quickDepth)} m",
                                                color = GeoGreenSignal,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { showAddTargetDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.height(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.pinTarget, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            IconButton(onClick = {
                                                selectedCellCoordinates = null
                                                selectedCellValue = null
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = strings.close, tint = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ViewerTab.SURFACE_3D -> {
                        Surface3DCanvas(
                            grid = gridState,
                            palette = activePalette
                        )
                    }

                    ViewerTab.VOLUME_3D -> {
                        Volume3DCanvas(
                            grid = gridState,
                            palette = activePalette
                        )
                    }

                    ViewerTab.CROSS_SECTION -> {
                        CrossSectionCanvas(grid = gridState)
                    }

                    ViewerTab.TARGETS -> {
                        // Target Registry Screen
                        TargetRegistryView(
                            targets = targets,
                            anomalies = detectedAnomalies,
                            onAddTarget = { showAddTargetDialog = true },
                            onDeleteTarget = { id -> onDeleteTarget?.invoke(id) },
                            onViewOnMap = { t ->
                                selectedCellCoordinates = Pair(t.gridX, t.gridY)
                                selectedCellValue = t.signalStrength
                                activeTab = ViewerTab.HEATMAP_2D
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter & Processing Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = SlateCardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.filterPipeline,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = {
                        gridState = pipeline.resetToRaw(gridState)
                        onSaveGrid(gridState)
                    }) {
                        Text(if (isPersian) "بازنشانی به خام" else "Reset to Raw", color = GeoAmber)
                    }
                }

                Text(
                    if (isPersian) "اصلاحات علمی و غیرمخرب ژئوفیزیک. اندازه‌گیری‌های خام اولیه همواره دست‌نخورده حفظ می‌شوند." else "Non-destructive scientific corrections. Raw measurements are always preserved intact.",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            gridState = pipeline.applyMedianFilter(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(if (isPersian) "فیلتر میانه (۳×۳)" else "Median (3×3)", fontSize = 12.sp, color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            gridState = pipeline.applyGaussianFilter(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(strings.gaussianBlur, fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            gridState = pipeline.applyBaselineCorrection(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(if (isPersian) "صفرسازی خط مبنا" else "Zero Baseline", fontSize = 12.sp, color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            gridState = pipeline.applyBackgroundSubtraction(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(if (isPersian) "حذف شیب سطحی" else "Planar Tilt Removal", fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Button(
                    onClick = {
                        gridState = pipeline.applyDriftCorrection(gridState)
                        onSaveGrid(gridState)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                ) {
                    Text(strings.headingCorrection, fontSize = 12.sp, color = TextPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            gridState = pipeline.applyAutomaticErrorCorrection(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(strings.autoErrorCorrection, fontSize = 11.sp, color = GeoAmber)
                    }

                    Button(
                        onClick = {
                            gridState = pipeline.applyCompleteMissingSignals(gridState)
                            onSaveGrid(gridState)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                    ) {
                        Text(strings.completeMissingSignals, fontSize = 11.sp, color = GeoCyan)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(strings.spatialInterpolation, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val interpolationOptions = listOf(
                        InterpolationMethod.NONE to (if (isPersian) "خام" else "Raw"),
                        InterpolationMethod.BILINEAR to (if (isPersian) "خطی (۲x)" else "Bilinear (2x)"),
                        InterpolationMethod.BICUBIC to (if (isPersian) "اسپلاین" else "Bicubic Spline"),
                        InterpolationMethod.IDW to "IDW"
                    )
                    interpolationOptions.forEach { (m, lbl) ->
                        FilterChip(
                            selected = activeInterpolation == m,
                            onClick = {
                                activeInterpolation = m
                                gridState = if (m == InterpolationMethod.NONE) {
                                    initialGrid
                                } else {
                                    interpolator.interpolateGrid(initialGrid, factor = 2, method = m)
                                }
                            },
                            label = { Text(lbl, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                                selectedLabelColor = GeoCyan
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Palette Selector Dialog
    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text(strings.scientificColorPalette, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPaletteType.values().forEach { palette ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activePalette = palette
                                    showPaletteDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (activePalette == palette) SlateElevated else SlateCardSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (activePalette == palette) GeoCyan else SlateCardBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    palette.displayName,
                                    color = if (activePalette == palette) GeoCyan else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaletteDialog = false }) {
                    Text(strings.close, color = GeoCyan)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    // Detected Anomalies Sheet
    if (showAnomalySheet) {
        ModalBottomSheet(
            onDismissRequest = { showAnomalySheet = false },
            containerColor = SlateCardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "${strings.anomaliesDetected} (${detectedAnomalies.size})",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    if (isPersian) "خوشه‌های گرادیان فضایی منطبق بر امضاهای مغناطیسی و الکترومغناطیسی فیزیکی." else "Spatial gradient clusters matching physical magnetic/conductivity signatures.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))

                if (detectedAnomalies.isEmpty()) {
                    Text(
                        if (isPersian) "هیچ آنومالی معناداری بالاتر از آستانه زمینه کشف نشد." else "No statistically significant anomalies detected above baseline threshold.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detectedAnomalies) { anomaly ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAnomalyForDepth = anomaly
                                        showDepthDialog = true
                                        showAnomalySheet = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = SlateElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            anomaly.type.label,
                                            color = if (anomaly.type == com.example.core.model.AnomalyType.POSITIVE_ANOMALY) GeoAmber else GeoCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "${if (isPersian) "اطمینان:" else "Conf:"} ${(anomaly.confidenceScore * 100).toInt()}%",
                                            color = GeoGreenSignal,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(anomaly.description, color = TextSecondary, fontSize = 11.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${if (isPersian) "مرکز:" else "Center:"} (${String.format("%.2f", anomaly.centerX)}m, ${String.format("%.2f", anomaly.centerY)}m)",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "${if (isPersian) "عمق تخمینی:" else "Est. Depth:"} ~${String.format("%.2f", anomaly.estimatedRelativeDepth)}m",
                                            color = GeoCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Depth Inversion Analysis Dialog
    if (showDepthDialog && selectedAnomalyForDepth != null) {
        val a = selectedAnomalyForDepth!!
        val depthResult = DepthEstimator.estimateDepth(
            peakIntensity = a.peakIntensity,
            backgroundLevel = stats.average,
            anomalyWidthMeters = a.width,
            soilProfile = activeSoilProfile
        )

        AlertDialog(
            onDismissRequest = { showDepthDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = GeoCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.scientificDepthAnalysis, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${if (isPersian) "آنومالی:" else "Anomaly:"} ${a.type.label}",
                        color = GeoAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (isPersian) "پروفایل خاک جاری:" else "Active Soil Profile:"} ${activeSoilProfile.name} (α: ${activeSoilProfile.attenuationFactor} dB/m)",
                        color = GeoCyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${if (isPersian) "عمق تخمینی:" else "Estimated Depth:"} ${String.format("%.2f", depthResult.depthMeters)} m (± ${String.format("%.2f", depthResult.uncertaintyMeters)} m)",
                        color = GeoCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${if (isPersian) "مدل فیزیکی:" else "Model:"} ${depthResult.certaintyLevel.label}",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${if (isPersian) "فرمول محاسباتی:" else "Formula:"} ${depthResult.formulaUsed}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = depthResult.notes,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = UserTarget(
                            projectId = project.id,
                            name = "Anomaly @ (${String.format("%.1f", a.centerX)}m, ${String.format("%.1f", a.centerY)}m)",
                            posX = a.centerX,
                            posY = a.centerY,
                            gridX = a.gridX,
                            gridY = a.gridY,
                            estimatedDepthMeters = depthResult.depthMeters,
                            estimatedDiameterMeters = a.width,
                            signalStrength = a.peakIntensity,
                            category = com.example.core.model.TargetCategory.POTENTIAL_FERROUS,
                            confidence = a.confidenceScore,
                            notes = "Depth inverted via Half-Width model with ${activeSoilProfile.name}"
                        )
                        onAddTarget(target)
                        showDepthDialog = false
                        Toast.makeText(context, if (isPersian) "هدف با موفقیت ذخیره شد" else "Target pinned successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black)
                ) {
                    Text(strings.savePin, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDepthDialog = false }) {
                    Text(strings.close, color = TextMuted)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    // Soil Type Selection Dialog (Visualizer 3D Feature)
    if (showSoilDialog) {
        AlertDialog(
            onDismissRequest = { showSoilDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = GeoGreenSignal)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.selectSoilType, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        strings.soilTypeDescription,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    SoilProfile.DEFAULT_SOILS.forEach { soil ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    activeSoilProfile = soil
                                    showSoilDialog = false
                                    Toast.makeText(context, "${if (isPersian) "خاک فعال:" else "Active Soil:"} ${soil.name}", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (activeSoilProfile.id == soil.id) GeoGreenSignal.copy(alpha = 0.18f) else SlateElevated
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (activeSoilProfile.id == soil.id) GeoGreenSignal else SlateCardBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(soil.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${if (isPersian) "تضعیف:" else "Attenuation:"} ${soil.attenuationFactor} dB/m | ${if (isPersian) "گذردهی:" else "Permittivity:"} ${soil.relativePermittivity} | Cond: ${soil.conductivityMsM} mS/m",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoilDialog = false }) {
                    Text(strings.done, color = GeoCyan)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    // Add Target Dialog from 2D Cell Tap
    if (showAddTargetDialog) {
        val col = selectedCellCoordinates?.first ?: 0
        val row = selectedCellCoordinates?.second ?: 0
        val cellVal = selectedCellValue ?: 0f
        val posX = (col + 0.5f) * gridState.gridSpacingMeters
        val posY = (row + 0.5f) * gridState.gridSpacingMeters

        var targetName by remember { mutableStateOf("Target [${col}, ${row}]") }
        var targetNotes by remember { mutableStateOf("") }
        var targetCategory by remember { mutableStateOf(TargetCategory.POTENTIAL_FERROUS) }
        var targetDepthStr by remember { mutableStateOf(String.format("%.2f", (gridState.gridSpacingMeters * 1.5f).coerceAtLeast(0.3f))) }

        AlertDialog(
            onDismissRequest = { showAddTargetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = GeoCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.addTarget, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${if (isPersian) "موقعیت:" else "Location:"} X=${String.format("%.2f", posX)}m, Y=${String.format("%.2f", posY)}m | ${if (isPersian) "سیگنال:" else "Signal:"} ${String.format("%.2f", cellVal)} µT",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = targetName,
                        onValueChange = { targetName = it },
                        label = { Text(strings.targetName) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(if (isPersian) "دسته‌بندی هدف:" else "Target Classification:", color = TextPrimary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TargetCategory.values().take(3).forEach { cat ->
                            FilterChip(
                                selected = targetCategory == cat,
                                onClick = { targetCategory = cat },
                                label = { Text(cat.label.take(10), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = GeoCyan
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetDepthStr,
                        onValueChange = { targetDepthStr = it },
                        label = { Text(strings.estimatedDepthMeters) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetNotes,
                        onValueChange = { targetNotes = it },
                        label = { Text(if (isPersian) "یادداشت‌ها و مشاهدات میدانی" else "Field Notes & Observations") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedDepth = targetDepthStr.toFloatOrNull() ?: 1.0f
                        val newTarget = UserTarget(
                            id = UUID.randomUUID().toString(),
                            projectId = project.id,
                            name = targetName,
                            posX = posX,
                            posY = posY,
                            gridX = col,
                            gridY = row,
                            estimatedDepthMeters = parsedDepth,
                            estimatedDiameterMeters = gridState.gridSpacingMeters,
                            signalStrength = cellVal,
                            category = targetCategory,
                            confidence = 0.85f,
                            notes = targetNotes
                        )
                        onAddTarget(newTarget)
                        showAddTargetDialog = false
                        Toast.makeText(context, if (isPersian) "هدف در مختصات (${String.format("%.1f", posX)}m, ${String.format("%.1f", posY)}m) ذخیره شد" else "Target pinned at (${String.format("%.1f", posX)}m, ${String.format("%.1f", posY)}m)", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black)
                ) {
                    Text(strings.savePin, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTargetDialog = false }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    // Export Dialog with Full Functional Share/Copy
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = GeoCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.exportData, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isPersian) "خروجی کامل برداشت ژئوفیزیک در فرمت‌های استاندارد مهندسی و سامانه‌های GIS:" else "Export complete survey in standard geophysical and GIS formats:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    // CSV Export
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (isPersian) "ماتریس داده‌های عددی CSV (اکسل و سرفر Surfer)" else "CSV Data Matrix (Spreadsheet / Surfer)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (isPersian) "شامل ردیف/ستون، مختصات فیزیکی به متر X/Y، مقادیر خام و پردازش‌شده." else "Row/Col, X/Y physical meters, Raw and Processed values.", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val csv = ExportManager.generateCsv(project, gridState)
                                        ExportManager.shareContent(context, "${project.name}_data.csv", csv, "text/csv")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.shareCsv, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val csv = ExportManager.generateCsv(project, gridState)
                                        ExportManager.copyToClipboard(context, "Survey CSV", csv)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.height(14.dp), tint = TextSecondary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.copyText, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // GeoJSON Export
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (isPersian) "داده‌های جغرافیایی GeoJSON RFC 7946 (سامانه‌های QGIS و ArcGIS)" else "GeoJSON RFC 7946 (QGIS / ArcGIS)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (isPersian) "شامل نقاط برداشت ژئورفرنس، زون‌های آنومالی و اهداف نشانه‌گذاری‌شده." else "Georeferenced points, anomaly zones, and target pins.", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val geoJson = ExportManager.generateGeoJson(project, gridState, detectedAnomalies, targets)
                                        ExportManager.shareContent(context, "${project.name}_gis.geojson", geoJson, "application/geo+json")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.shareGeoJson, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val geoJson = ExportManager.generateGeoJson(project, gridState, detectedAnomalies, targets)
                                        ExportManager.copyToClipboard(context, "Survey GeoJSON", geoJson)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.height(14.dp), tint = TextSecondary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.copyText, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // Technical Report Export
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (isPersian) "گزارش مهندسی و فنی ژئوفیزیک (TXT / Doc)" else "Geophysical Survey Report (PDF/Doc)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (isPersian) "شامل جدول تحلیل آماری، وارون‌سازی عمق، جدول آنومالی‌ها و متادیتا." else "Statistics, depth inversions, anomaly tables, and metadata.", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val rep = ExportManager.generateTechnicalReport(project, gridState, detectedAnomalies, targets)
                                        ExportManager.shareContent(context, "${project.name}_survey_report.txt", rep, "text/plain")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.shareReport, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val rep = ExportManager.generateTechnicalReport(project, gridState, detectedAnomalies, targets)
                                        ExportManager.copyToClipboard(context, "Technical Report", rep)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.height(14.dp), tint = TextSecondary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.copyText, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(strings.close, color = GeoCyan)
                }
            },
            containerColor = SlateCardSurface
        )
    }
}

@Composable
fun TargetRegistryView(
    targets: List<UserTarget>,
    anomalies: List<DetectedAnomaly>,
    onAddTarget: () -> Unit,
    onDeleteTarget: (String) -> Unit,
    onViewOnMap: (UserTarget) -> Unit
) {
    val strings = LocalAppStrings.current
    val isPersian = LanguageManager.isPersian

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(strings.targetRegistryBookmarks, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (isPersian) "${targets.size} هدف نشانه‌گذاری‌شده | ${anomalies.size} آنومالی شناسایی‌شده" else "${targets.size} Pinned Targets | ${anomalies.size} Detected Anomalies",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onAddTarget,
                colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.addPin, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (targets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCardSurface)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = TextMuted, modifier = Modifier.height(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(strings.noTargetsPinned, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isPersian) "روی هر نقطه از نقشه ۲ بعدی ضربه بزنید یا از بخش آنومالی‌های کشف‌شده انتخاب کنید تا اهداف و تحلیل عمق ذخیره شوند." else "Tap on any cell on the 2D Map or select from Detected Anomalies to bookmark and save depth inversion targets.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(targets, key = { it.id }) { target ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (target.category) {
                                                    TargetCategory.POTENTIAL_FERROUS -> GeoRedPositive.copy(alpha = 0.2f)
                                                    TargetCategory.POTENTIAL_NON_FERROUS -> GeoAmber.copy(alpha = 0.2f)
                                                    TargetCategory.VOID_LIKE_ANOMALY -> GeoCyan.copy(alpha = 0.2f)
                                                    else -> SlateElevated
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            target.category.label,
                                            color = when (target.category) {
                                                TargetCategory.POTENTIAL_FERROUS -> GeoRedPositive
                                                TargetCategory.POTENTIAL_NON_FERROUS -> GeoAmber
                                                TargetCategory.VOID_LIKE_ANOMALY -> GeoCyan
                                                else -> TextPrimary
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(target.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                IconButton(onClick = { onDeleteTarget(target.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = TextMuted)
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${if (isPersian) "موقعیت:" else "Location:"} (${String.format("%.2f", target.posX)}m, ${String.format("%.2f", target.posY)}m)",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "${if (isPersian) "شبکه:" else "Grid:"} [${target.gridX}, ${target.gridY}]",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${if (isPersian) "عمق تخمینی:" else "Est. Depth:"} ~${String.format("%.2f", target.estimatedDepthMeters)} m",
                                    color = GeoCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "${if (isPersian) "سیگنال:" else "Signal:"} ${String.format("%.1f", target.signalStrength)} µT",
                                    color = GeoGreenSignal,
                                    fontSize = 12.sp
                                )
                            }

                            if (target.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("${if (isPersian) "یادداشت:" else "Notes:"} ${target.notes}", color = TextSecondary, fontSize = 11.sp)
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { onViewOnMap(target) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateElevated)
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = GeoCyan, modifier = Modifier.height(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPersian) "نمایش روی نقشه ۲ بعدی" else "Highlight on 2D Map", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
