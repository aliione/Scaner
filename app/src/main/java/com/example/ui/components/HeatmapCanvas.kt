package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ColorPaletteType
import com.example.core.model.DetectedAnomaly
import com.example.core.model.MeasurementGrid
import com.example.core.model.MetricMeasurement
import com.example.core.model.UserTarget
import com.example.core.visualization.ColorMapEngine
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoRedPositive
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.floor

@Composable
fun HeatmapCanvas(
    grid: MeasurementGrid,
    palette: ColorPaletteType,
    inverted: Boolean = false,
    showContours: Boolean = true,
    showGridLines: Boolean = true,
    gridLineOpacity: Float = 0.35f,
    rulerModeEnabled: Boolean = false,
    anomalies: List<DetectedAnomaly> = emptyList(),
    targets: List<UserTarget> = emptyList(),
    onCellSelected: ((x: Int, y: Int, value: Float) -> Unit)? = null,
    onMeasurementChanged: ((MetricMeasurement?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rulerStartCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rulerEndCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val matrix = remember(grid) { grid.getProcessedMatrix() }
    val stats = remember(grid) { grid.getStats() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateCardSurface)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                    panOffset += pan
                }
            }
            .pointerInput(grid) {
                detectTapGestures { tapOffset ->
                    val w = size.width
                    val h = size.height
                    val padding = 24f

                    val drawW = (w - padding * 2) * scale
                    val drawH = (h - padding * 2) * scale
                    val startX = (w - drawW) / 2f + panOffset.x
                    val startY = (h - drawH) / 2f + panOffset.y

                    val localX = tapOffset.x - startX
                    val localY = tapOffset.y - startY

                    if (localX >= 0 && localX < drawW && localY >= 0 && localY < drawH) {
                        val cellW = drawW / grid.cols
                        val cellH = drawH / grid.rows
                        val col = (localX / cellW).toInt().coerceIn(0, grid.cols - 1)
                        val row = (localY / cellH).toInt().coerceIn(0, grid.rows - 1)
                        val v = if (row < matrix.size && col < matrix[0].size) matrix[row][col] else 0f

                        if (rulerModeEnabled) {
                            if (rulerStartCell == null || rulerEndCell != null) {
                                rulerStartCell = Pair(col, row)
                                rulerEndCell = null
                                onMeasurementChanged?.invoke(null)
                            } else {
                                rulerEndCell = Pair(col, row)
                                val (sCol, sRow) = rulerStartCell!!
                                val sVal = if (sRow < matrix.size && sCol < matrix[0].size) matrix[sRow][sCol] else 0f
                                val dxStep = grid.widthMeters / grid.cols
                                val dyStep = grid.lengthMeters / grid.rows

                                val measurement = MetricMeasurement(
                                    startCol = sCol,
                                    startRow = sRow,
                                    startXMeter = (sCol + 0.5f) * dxStep,
                                    startYMeter = (sRow + 0.5f) * dyStep,
                                    startValue = sVal,
                                    endCol = col,
                                    endRow = row,
                                    endXMeter = (col + 0.5f) * dxStep,
                                    endYMeter = (row + 0.5f) * dyStep,
                                    endValue = v
                                )
                                onMeasurementChanged?.invoke(measurement)
                            }
                        } else {
                            selectedCell = Pair(col, row)
                            onCellSelected?.invoke(col, row, v)
                        }
                    } else {
                        if (!rulerModeEnabled) {
                            selectedCell = null
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 24f

            val baseDrawW = canvasW - padding * 2
            val baseDrawH = canvasH - padding * 2

            val drawW = baseDrawW * scale
            val drawH = baseDrawH * scale

            val startX = (canvasW - drawW) / 2f + panOffset.x
            val startY = (canvasH - drawH) / 2f + panOffset.y

            val rows = grid.rows
            val cols = grid.cols
            if (rows <= 0 || cols <= 0) return@Canvas

            val cellW = drawW / cols
            val cellH = drawH / rows

            // 1. Draw Heatmap Cells
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val v = matrix[r][c]
                    val color = ColorMapEngine.getColorForValue(
                        value = v,
                        min = stats.min,
                        max = stats.max,
                        palette = palette,
                        inverted = inverted
                    )

                    drawRect(
                        color = color,
                        topLeft = Offset(startX + c * cellW, startY + r * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                }
            }

            // 2. Draw Grid & Scan Field Overlay
            if (showGridLines && gridLineOpacity > 0f) {
                val lineColor = Color.White.copy(alpha = gridLineOpacity)
                for (c in 0..cols) {
                    drawLine(
                        color = lineColor,
                        start = Offset(startX + c * cellW, startY),
                        end = Offset(startX + c * cellW, startY + drawH),
                        strokeWidth = 1f
                    )
                }
                for (r in 0..rows) {
                    drawLine(
                        color = lineColor,
                        start = Offset(startX, startY + r * cellH),
                        end = Offset(startX + drawW, startY + r * cellH),
                        strokeWidth = 1f
                    )
                }
            }

            // 3. Draw Contour Lines
            if (showContours) {
                val contourSteps = 6
                val contourColor = Color.White.copy(alpha = 0.25f)
                val stepVal = stats.range / contourSteps.toFloat()

                if (stepVal > 0.001f) {
                    for (step in 1 until contourSteps) {
                        val targetV = stats.min + step * stepVal
                        for (r in 0 until rows - 1) {
                            for (c in 0 until cols - 1) {
                                val v0 = matrix[r][c]
                                val v1 = matrix[r][c + 1]
                                val v2 = matrix[r + 1][c]
                                if ((v0 - targetV) * (v1 - targetV) < 0f) {
                                    val t = (targetV - v0) / (v1 - v0)
                                    val ptX = startX + (c + t) * cellW
                                    val ptY = startY + (r + 0.5f) * cellH
                                    drawCircle(contourColor, radius = 1.2f, center = Offset(ptX, ptY))
                                }
                            }
                        }
                    }
                }
            }

            // 4. Highlight Detected Anomalies
            for (anomaly in anomalies) {
                val boxX = startX + (anomaly.gridX - 1).coerceAtLeast(0) * cellW
                val boxY = startY + (anomaly.gridY - 1).coerceAtLeast(0) * cellH
                val boxW = 3f * cellW
                val boxH = 3f * cellH

                drawRect(
                    color = GeoAmber,
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // Anomaly center marker
                val centerX = startX + (anomaly.gridX + 0.5f) * cellW
                val centerY = startY + (anomaly.gridY + 0.5f) * cellH
                drawCircle(color = GeoAmber, radius = 4f, center = Offset(centerX, centerY))
            }

            // 5. Highlight User Targets
            for (target in targets) {
                val targetX = startX + (target.gridX + 0.5f) * cellW
                val targetY = startY + (target.gridY + 0.5f) * cellH

                drawCircle(color = GeoRedPositive, radius = 7f, center = Offset(targetX, targetY))
                drawCircle(
                    color = Color.White,
                    radius = 9f,
                    center = Offset(targetX, targetY),
                    style = Stroke(width = 2f)
                )
            }

            // 6. Selected Cell Highlight
            selectedCell?.let { (col, row) ->
                val selX = startX + col * cellW
                val selY = startY + row * cellH
                drawRect(
                    color = GeoCyan,
                    topLeft = Offset(selX, selY),
                    size = Size(cellW, cellH),
                    style = Stroke(width = 3f)
                )
            }

            // 7. Metric Ruler Line & Markers
            if (rulerModeEnabled) {
                rulerStartCell?.let { (sCol, sRow) ->
                    val p1 = Offset(startX + (sCol + 0.5f) * cellW, startY + (sRow + 0.5f) * cellH)
                    drawCircle(color = GeoCyan, radius = 6f, center = p1)
                    drawCircle(color = Color.White, radius = 8f, center = p1, style = Stroke(width = 2f))

                    rulerEndCell?.let { (eCol, eRow) ->
                        val p2 = Offset(startX + (eCol + 0.5f) * cellW, startY + (eRow + 0.5f) * cellH)
                        drawCircle(color = GeoAmber, radius = 6f, center = p2)
                        drawCircle(color = Color.White, radius = 8f, center = p2, style = Stroke(width = 2f))

                        // Draw connecting ruler line with tick dash
                        drawLine(
                            color = GeoCyan,
                            start = p1,
                            end = p2,
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                        )
                    }
                }
            }
        }

        // Top HUD with zoom level, ruler metrics or inspection indicator
        if (rulerModeEnabled && rulerStartCell != null && rulerEndCell != null) {
            val (sCol, sRow) = rulerStartCell!!
            val (eCol, eRow) = rulerEndCell!!
            val dxStep = grid.widthMeters / grid.cols
            val dyStep = grid.lengthMeters / grid.rows
            val sVal = if (sRow < matrix.size && sCol < matrix[0].size) matrix[sRow][sCol] else 0f
            val eVal = if (eRow < matrix.size && eCol < matrix[0].size) matrix[eRow][eCol] else 0f
            val meas = MetricMeasurement(
                startCol = sCol, startRow = sRow,
                startXMeter = (sCol + 0.5f) * dxStep,
                startYMeter = (sRow + 0.5f) * dyStep,
                startValue = sVal,
                endCol = eCol, endRow = eRow,
                endXMeter = (eCol + 0.5f) * dxStep,
                endYMeter = (eRow + 0.5f) * dyStep,
                endValue = eVal
            )

            Text(
                text = "📏 Distance: ${String.format("%.2f", meas.distanceMeters)} m | ΔSignal: ${String.format("%.2f", meas.deltaValue)} µT | Grad: ${String.format("%.2f", meas.gradientPerMeter)} µT/m | Angle: ${String.format("%.1f", meas.angleDegrees)}°",
                color = GeoAmber,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(SlateCardSurface.copy(alpha = 0.90f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else {
            selectedCell?.let { (col, row) ->
                val v = if (row < matrix.size && col < matrix[0].size) matrix[row][col] else 0f
                val posX = (col + 0.5f) * (grid.widthMeters / grid.cols)
                val posY = (row + 0.5f) * (grid.lengthMeters / grid.rows)
                Text(
                    text = "Point [$col, $row] | Pos: (${String.format("%.2f", posX)}m, ${String.format("%.2f", posY)}m) | Value: ${String.format("%.2f", v)}",
                    color = GeoCyan,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(SlateCardSurface.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
