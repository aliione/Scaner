package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ColorPaletteType
import com.example.core.model.MeasurementGrid
import com.example.core.visualization.CameraPreset
import com.example.core.visualization.Projection3DEngine
import com.example.core.visualization.SurfaceRenderStyle
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun Surface3DCanvas(
    grid: MeasurementGrid,
    palette: ColorPaletteType,
    modifier: Modifier = Modifier
) {
    val engine = remember { Projection3DEngine() }
    var renderStyle by remember { mutableStateOf(SurfaceRenderStyle.SOLID_SHADED) }
    var zExaggeration by remember { mutableFloatStateOf(2.0f) }

    // Trigger state to force redraw on gesture updates
    var redrawTick by remember { mutableFloatStateOf(0f) }

    val matrix = remember(grid) { grid.getProcessedMatrix() }
    val stats = remember(grid) { grid.getStats() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            // 1-finger drag for 360-degree rotation (yaw & pitch)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    engine.rotationY = (engine.rotationY + dragAmount.x * 0.45f) % 360f
                    engine.rotationX = (engine.rotationX - dragAmount.y * 0.45f).coerceIn(-89f, 89f)
                    redrawTick += 1f
                }
            }
            // 2-finger zoom and pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    engine.zoomScale = (engine.zoomScale * zoom).coerceIn(0.4f, 4.0f)
                    engine.panOffsetX += pan.x
                    engine.panOffsetY += pan.y
                    redrawTick += 1f
                }
            }
    ) {
        // 3D Rendering Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read redrawTick to force recomposition
            val dummy = redrawTick
            engine.zExaggeration = zExaggeration

            val faces = engine.buildSurfaceMesh(
                matrix = matrix,
                canvasWidth = size.width,
                canvasHeight = size.height,
                palette = palette,
                minVal = stats.min,
                maxVal = stats.max
            )

            for (face in faces) {
                val p = Path().apply {
                    moveTo(face.v0.screenX, face.v0.screenY)
                    lineTo(face.v1.screenX, face.v1.screenY)
                    lineTo(face.v2.screenX, face.v2.screenY)
                    face.v3?.let { lineTo(it.screenX, it.screenY) }
                    close()
                }

                if (renderStyle == SurfaceRenderStyle.SOLID_SHADED) {
                    drawPath(path = p, color = face.baseColor)
                    // Subtle wireframe edge for depth definition
                    drawPath(path = p, color = Color.Black.copy(alpha = 0.22f), style = Stroke(width = 0.8f))
                } else {
                    // Wireframe Mode
                    drawPath(path = p, color = face.baseColor, style = Stroke(width = 1.2f))
                }
            }
        }

        // Top Floating Toolbar: Camera Presets & Style
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateElevated.copy(alpha = 0.85f))
                .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraPresetChip("Persp", selected = !engine.isOrthographic && engine.rotationX in 20f..70f) {
                engine.applyPreset(CameraPreset.PERSPECTIVE_DEFAULT)
                redrawTick += 1f
            }
            CameraPresetChip("Top", selected = engine.rotationX >= 80f) {
                engine.applyPreset(CameraPreset.TOP_VIEW)
                redrawTick += 1f
            }
            CameraPresetChip("Front", selected = engine.rotationX in -15f..15f && engine.rotationY in -15f..15f) {
                engine.applyPreset(CameraPreset.FRONT_VIEW)
                redrawTick += 1f
            }
            CameraPresetChip("Side", selected = engine.rotationY in 75f..105f) {
                engine.applyPreset(CameraPreset.SIDE_VIEW)
                redrawTick += 1f
            }
            CameraPresetChip("Ortho", selected = engine.isOrthographic) {
                engine.applyPreset(CameraPreset.ORTHOGRAPHIC)
                redrawTick += 1f
            }

            IconButton(onClick = {
                engine.resetCamera()
                redrawTick += 1f
            }) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Camera",
                    tint = GeoCyan
                )
            }
        }

        // Bottom Controls: Exaggeration & Mode
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateElevated.copy(alpha = 0.9f))
                .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Z-Scale:", fontSize = 12.sp, color = TextMuted)
            listOf(1.0f to "1x", 2.0f to "2x", 5.0f to "5x", 10.0f to "10x").forEach { (v, label) ->
                FilterChip(
                    selected = zExaggeration == v,
                    onClick = {
                        zExaggeration = v
                        redrawTick += 1f
                    },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                        selectedLabelColor = GeoCyan
                    )
                )
            }

            // Wireframe toggle
            FilterChip(
                selected = renderStyle == SurfaceRenderStyle.WIREFRAME,
                onClick = {
                    renderStyle = if (renderStyle == SurfaceRenderStyle.WIREFRAME) {
                        SurfaceRenderStyle.SOLID_SHADED
                    } else {
                        SurfaceRenderStyle.WIREFRAME
                    }
                    redrawTick += 1f
                },
                label = { Text(if (renderStyle == SurfaceRenderStyle.WIREFRAME) "Wireframe" else "Solid", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GeoAmber.copy(alpha = 0.25f),
                    selectedLabelColor = GeoAmber
                )
            )
        }
    }
}

@Composable
private fun CameraPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
            selectedLabelColor = GeoCyan,
            containerColor = Color.Transparent,
            labelColor = TextPrimary
        )
    )
}
