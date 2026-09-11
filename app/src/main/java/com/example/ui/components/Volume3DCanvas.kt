package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ColorPaletteType
import com.example.core.model.MeasurementGrid
import com.example.core.processing.DepthEstimator
import com.example.core.visualization.ColorMapEngine
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Volume3DCanvas(
    grid: MeasurementGrid,
    palette: ColorPaletteType,
    modifier: Modifier = Modifier
) {
    val totalDepthSlices = 10
    var activeDepthSlice by remember { mutableIntStateOf(5) }
    var opacityThreshold by remember { mutableFloatStateOf(0.2f) }
    var rotX by remember { mutableFloatStateOf(40f) }
    var rotY by remember { mutableFloatStateOf(35f) }

    val matrix = remember(grid) { grid.getProcessedMatrix() }
    val stats = remember(grid) { grid.getStats() }
    val volume = remember(matrix) {
        DepthEstimator.generatePseudoDepthVolume(matrix, depthSlices = totalDepthSlices)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    rotY = (rotY + dragAmount.x * 0.4f) % 360f
                    rotX = (rotX - dragAmount.y * 0.4f).coerceIn(-80f, 80f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW / 2f
            val centerY = canvasH / 2f - 20f

            val radX = Math.toRadians(rotX.toDouble()).toFloat()
            val radY = Math.toRadians(rotY.toDouble()).toFloat()
            val cosX = cos(radX)
            val sinX = sin(radX)
            val cosY = cos(radY)
            val sinY = sin(radY)

            val baseRadius = minOf(canvasW, canvasH) * 0.38f
            val rows = grid.rows
            val cols = grid.cols
            val sliceH = (baseRadius * 0.5f) / totalDepthSlices

            // Render slices from bottom (deepest) to activeDepthSlice
            for (z in (totalDepthSlices - 1) downTo (totalDepthSlices - activeDepthSlice)) {
                val depthZOffset = (z - totalDepthSlices / 2f) * sliceH
                val sliceMatrix = volume[z]

                // Draw voxels/cells on this slicing plane
                val step = if (rows > 25) 2 else 1
                for (r in 0 until rows - step step step) {
                    val normY0 = (r.toFloat() / (rows - 1).toFloat() - 0.5f) * 2f
                    val normY1 = ((r + step).toFloat() / (rows - 1).toFloat() - 0.5f) * 2f

                    for (c in 0 until cols - step step step) {
                        val normX0 = (c.toFloat() / (cols - 1).toFloat() - 0.5f) * 2f
                        val normX1 = ((c + step).toFloat() / (cols - 1).toFloat() - 0.5f) * 2f

                        val v = sliceMatrix[r][c]
                        val normV = if (stats.range > 0.001f) (v - stats.min) / stats.range else 0.5f
                        if (normV < opacityThreshold) continue

                        val color = ColorMapEngine.getColorForValue(
                            value = v,
                            min = stats.min,
                            max = stats.max,
                            palette = palette
                        ).copy(alpha = 0.65f)

                        // 4 corner points
                        val corners = listOf(
                            Pair(normX0, normY0),
                            Pair(normX1, normY0),
                            Pair(normX1, normY1),
                            Pair(normX0, normY1)
                        ).map { (nx, ny) ->
                            val px = nx * baseRadius
                            val py = ny * baseRadius
                            val pz = -depthZOffset

                            val x1 = px * cosY + py * sinY
                            val y1 = -px * sinY + py * cosY
                            val z1 = pz

                            val x2 = x1
                            val y2 = y1 * cosX - z1 * sinX
                            Offset(centerX + x2, centerY + y2)
                        }

                        val path = Path().apply {
                            moveTo(corners[0].x, corners[0].y)
                            lineTo(corners[1].x, corners[1].y)
                            lineTo(corners[2].x, corners[2].y)
                            lineTo(corners[3].x, corners[3].y)
                            close()
                        }
                        drawPath(path, color)
                    }
                }
            }
        }

        // Top Scientific Notice
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SlateElevated.copy(alpha = 0.9f))
                .border(1.dp, GeoAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pseudo-Depth Visualization (Relative Intensity Volume)",
                color = GeoAmber,
                fontSize = 11.sp
            )
            Text(
                text = "Relative mathematical depth projection - not direct physical photography.",
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        // Bottom Slicing & Threshold Sliders
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateElevated.copy(alpha = 0.92f))
                .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Depth Slice: ${activeDepthSlice}/$totalDepthSlices",
                    color = GeoCyan,
                    fontSize = 12.sp
                )
                Text(
                    text = "Relative Depth: ~${String.format("%.1f", activeDepthSlice * 0.25f)}m",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
            Slider(
                value = activeDepthSlice.toFloat(),
                onValueChange = { activeDepthSlice = it.toInt().coerceIn(1, totalDepthSlices) },
                valueRange = 1f..totalDepthSlices.toFloat(),
                steps = totalDepthSlices - 2,
                colors = SliderDefaults.colors(thumbColor = GeoCyan, activeTrackColor = GeoCyan)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Isolate Anomaly (Threshold): ${(opacityThreshold * 100).toInt()}%",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Slider(
                value = opacityThreshold,
                onValueChange = { opacityThreshold = it },
                valueRange = 0.05f..0.85f,
                colors = SliderDefaults.colors(thumbColor = GeoAmber, activeTrackColor = GeoAmber)
            )
        }
    }
}
