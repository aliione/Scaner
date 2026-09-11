package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.MeasurementGrid
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

enum class CrossSectionAxis {
    X_AXIS_ROW_SLICE,
    Y_AXIS_COL_SLICE
}

@Composable
fun CrossSectionCanvas(
    grid: MeasurementGrid,
    modifier: Modifier = Modifier
) {
    var axis by remember { mutableStateOf(CrossSectionAxis.X_AXIS_ROW_SLICE) }
    var sliceIndex by remember { mutableIntStateOf(grid.rows / 2) }

    val matrix = remember(grid) { grid.getProcessedMatrix() }
    val stats = remember(grid) { grid.getStats() }

    val maxIndex = if (axis == CrossSectionAxis.X_AXIS_ROW_SLICE) grid.rows - 1 else grid.cols - 1
    val currentIndex = sliceIndex.coerceIn(0, maxIndex)

    val profileValues = remember(axis, currentIndex, matrix) {
        if (axis == CrossSectionAxis.X_AXIS_ROW_SLICE) {
            FloatArray(grid.cols) { c -> matrix[currentIndex][c] }
        } else {
            FloatArray(grid.rows) { r -> matrix[r][currentIndex] }
        }
    }

    val minProfile = profileValues.minOrNull() ?: 0f
    val maxProfile = profileValues.maxOrNull() ?: 0f
    val avgProfile = if (profileValues.isNotEmpty()) profileValues.average().toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(12.dp)
    ) {
        // Mode & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = axis == CrossSectionAxis.X_AXIS_ROW_SLICE,
                    onClick = {
                        axis = CrossSectionAxis.X_AXIS_ROW_SLICE
                        sliceIndex = (grid.rows / 2)
                    },
                    label = { Text("X-Section (Row)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                        selectedLabelColor = GeoCyan
                    )
                )
                FilterChip(
                    selected = axis == CrossSectionAxis.Y_AXIS_COL_SLICE,
                    onClick = {
                        axis = CrossSectionAxis.Y_AXIS_COL_SLICE
                        sliceIndex = (grid.cols / 2)
                    },
                    label = { Text("Y-Section (Col)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                        selectedLabelColor = GeoCyan
                    )
                )
            }

            Text(
                text = if (axis == CrossSectionAxis.X_AXIS_ROW_SLICE) "Line Y=$currentIndex (${String.format("%.2f", currentIndex * (grid.lengthMeters / grid.rows))}m)"
                else "Line X=$currentIndex (${String.format("%.2f", currentIndex * (grid.widthMeters / grid.cols))}m)",
                color = GeoCyan,
                fontSize = 12.sp
            )
        }

        // Slider to move cutting plane
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { sliceIndex = it.toInt().coerceIn(0, maxIndex) },
            valueRange = 0f..maxIndex.toFloat(),
            steps = if (maxIndex > 1) maxIndex - 1 else 0,
            colors = SliderDefaults.colors(thumbColor = GeoCyan, activeTrackColor = GeoCyan)
        )

        // Cross Section Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateCardSurface)
                .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padBottom = 24f
                val padLeft = 40f
                val plotW = w - padLeft - 10f
                val plotH = h - padBottom - 10f

                // Draw Grid Axes
                drawLine(
                    color = SlateCardBorder,
                    start = Offset(padLeft, 10f),
                    end = Offset(padLeft, 10f + plotH),
                    strokeWidth = 2f
                )
                drawLine(
                    color = SlateCardBorder,
                    start = Offset(padLeft, 10f + plotH),
                    end = Offset(padLeft + plotW, 10f + plotH),
                    strokeWidth = 2f
                )

                // Baseline line
                val globalRange = if (stats.range > 0.001f) stats.range else 1f
                val baseNorm = (stats.average - stats.min) / globalRange
                val baseY = 10f + plotH * (1f - baseNorm)
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(padLeft, baseY),
                    end = Offset(padLeft + plotW, baseY),
                    strokeWidth = 1.5f
                )

                if (profileValues.size > 1) {
                    val path = Path()
                    val n = profileValues.size
                    for (i in 0 until n) {
                        val normX = i.toFloat() / (n - 1).toFloat()
                        val normY = ((profileValues[i] - stats.min) / globalRange).coerceIn(0f, 1f)

                        val px = padLeft + normX * plotW
                        val py = 10f + plotH * (1f - normY)

                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }

                    // Draw curve
                    drawPath(path, color = GeoCyan, style = Stroke(width = 3.5f))

                    // Peak point indicator
                    val maxIdx = profileValues.indices.maxByOrNull { profileValues[it] } ?: 0
                    val peakNormX = maxIdx.toFloat() / (n - 1).toFloat()
                    val peakNormY = ((profileValues[maxIdx] - stats.min) / globalRange).coerceIn(0f, 1f)
                    val peakPx = padLeft + peakNormX * plotW
                    val peakPy = 10f + plotH * (1f - peakNormY)

                    drawCircle(color = GeoAmber, radius = 6f, center = Offset(peakPx, peakPy))
                }
            }
        }

        // Stats Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SlateElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Min: ${String.format("%.1f", minProfile)}", color = TextMuted, fontSize = 12.sp)
            Text("Avg: ${String.format("%.1f", avgProfile)}", color = TextMuted, fontSize = 12.sp)
            Text("Peak: ${String.format("%.1f", maxProfile)}", color = GeoAmber, fontSize = 12.sp)
            Text("Contrast: ${String.format("%.1f", maxProfile - minProfile)}", color = GeoCyan, fontSize = 12.sp)
        }
    }
}
