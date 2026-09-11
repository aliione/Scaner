package com.example.core.visualization

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Point in camera space.
 */
data class Point3D(val x: Float, val y: Float, val z: Float)

/**
 * 2D projected screen coordinate with depth (z) for Painter's Algorithm sorting.
 */
data class ProjectedVertex(val screenX: Float, val screenY: Float, val depthZ: Float, val originalValue: Float)

/**
 * 3D polygon face for solid surface rendering.
 */
data class MeshFace3D(
    val v0: ProjectedVertex,
    val v1: ProjectedVertex,
    val v2: ProjectedVertex,
    val v3: ProjectedVertex?,
    val averageDepthZ: Float,
    val baseColor: Color,
    val normalZ: Float // for diffuse directional lighting
)

enum class CameraPreset {
    PERSPECTIVE_DEFAULT,
    TOP_VIEW,
    FRONT_VIEW,
    SIDE_VIEW,
    ORTHOGRAPHIC
}

enum class SurfaceRenderStyle {
    SOLID_SHADED,
    WIREFRAME,
    POINTS
}

/**
 * High-performance 3D software rendering engine.
 * Computes rotation, projection, depth-sorting, and lighting
 * directly on Compose Canvas.
 */
class Projection3DEngine {

    var rotationX: Float = 45f // pitch in degrees
    var rotationY: Float = 35f // yaw in degrees
    var panOffsetX: Float = 0f
    var panOffsetY: Float = 0f
    var zoomScale: Float = 1.0f
    var isOrthographic: Boolean = false
    var zExaggeration: Float = 2.0f // 1x, 2x, 5x, 10x

    fun resetCamera() {
        rotationX = 45f
        rotationY = 35f
        panOffsetX = 0f
        panOffsetY = 0f
        zoomScale = 1.0f
        isOrthographic = false
    }

    fun applyPreset(preset: CameraPreset) {
        when (preset) {
            CameraPreset.PERSPECTIVE_DEFAULT -> {
                rotationX = 45f
                rotationY = 35f
                isOrthographic = false
            }
            CameraPreset.TOP_VIEW -> {
                rotationX = 90f
                rotationY = 0f
                isOrthographic = true
            }
            CameraPreset.FRONT_VIEW -> {
                rotationX = 0f
                rotationY = 0f
                isOrthographic = false
            }
            CameraPreset.SIDE_VIEW -> {
                rotationX = 0f
                rotationY = 90f
                isOrthographic = false
            }
            CameraPreset.ORTHOGRAPHIC -> {
                rotationX = 40f
                rotationY = 30f
                isOrthographic = true
            }
        }
    }

    /**
     * Projects a 3D matrix onto 2D screen coordinates and constructs sorted polygon faces.
     */
    fun buildSurfaceMesh(
        matrix: Array<FloatArray>,
        canvasWidth: Float,
        canvasHeight: Float,
        palette: com.example.core.model.ColorPaletteType,
        minVal: Float,
        maxVal: Float,
        thresholdMin: Float? = null,
        thresholdMax: Float? = null
    ): List<MeshFace3D> {
        val rows = matrix.size
        if (rows < 2) return emptyList()
        val cols = matrix[0].size
        if (cols < 2) return emptyList()

        val radX = Math.toRadians(rotationX.toDouble()).toFloat()
        val radY = Math.toRadians(rotationY.toDouble()).toFloat()

        val cosX = cos(radX)
        val sinX = sin(radX)
        val cosY = cos(radY)
        val sinY = sin(radY)

        val valRange = if (maxVal - minVal > 0.0001f) maxVal - minVal else 1f
        val centerX = canvasWidth / 2f + panOffsetX
        val centerY = canvasHeight / 2f + panOffsetY
        val baseRadius = minOf(canvasWidth, canvasHeight) * 0.42f * zoomScale

        val fovDistance = 600f

        // 1. Transform each vertex
        val projectedGrid = Array(rows) { Array<ProjectedVertex?>(cols) { null } }

        for (r in 0 until rows) {
            val normY = (r.toFloat() / (rows - 1).toFloat() - 0.5f) * 2f // -1 to 1
            for (c in 0 until cols) {
                val normX = (c.toFloat() / (cols - 1).toFloat() - 0.5f) * 2f // -1 to 1
                val rawV = matrix[r][c]
                val normZ = ((rawV - minVal) / valRange - 0.5f) * zExaggeration // centered

                // World coordinate in 3D
                val px = normX * baseRadius
                val py = normY * baseRadius
                val pz = normZ * (baseRadius * 0.45f)

                // Rotate around Y axis (Yaw)
                val x1 = px * cosY + py * sinY
                val y1 = -px * sinY + py * cosY
                val z1 = pz

                // Rotate around X axis (Pitch)
                val x2 = x1
                val y2 = y1 * cosX - z1 * sinX
                val z2 = y1 * sinX + z1 * cosX

                // Projection to 2D
                val screenX: Float
                val screenY: Float
                if (isOrthographic) {
                    screenX = centerX + x2
                    screenY = centerY + y2
                } else {
                    val cameraZ = z2 + fovDistance
                    val perspectiveFactor = if (cameraZ > 50f) fovDistance / cameraZ else 1.0f
                    screenX = centerX + x2 * perspectiveFactor
                    screenY = centerY + y2 * perspectiveFactor
                }

                projectedGrid[r][c] = ProjectedVertex(screenX, screenY, z2, rawV)
            }
        }

        // 2. Build quad faces
        val faces = ArrayList<MeshFace3D>((rows - 1) * (cols - 1))
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                val v0 = projectedGrid[r][c] ?: continue
                val v1 = projectedGrid[r][c + 1] ?: continue
                val v2 = projectedGrid[r + 1][c + 1] ?: continue
                val v3 = projectedGrid[r + 1][c] ?: continue

                val avgVal = (v0.originalValue + v1.originalValue + v2.originalValue + v3.originalValue) / 4f
                if (thresholdMin != null && avgVal < thresholdMin) continue
                if (thresholdMax != null && avgVal > thresholdMax) continue

                val avgZ = (v0.depthZ + v1.depthZ + v2.depthZ + v3.depthZ) / 4f
                val color = ColorMapEngine.getColorForValue(avgVal, minVal, maxVal, palette)

                // Simple directional light shading
                val dzX = (v1.depthZ - v0.depthZ) + (v2.depthZ - v3.depthZ)
                val dzY = (v3.depthZ - v0.depthZ) + (v2.depthZ - v1.depthZ)
                val lightDot = (0.7f - 0.2f * dzX + 0.3f * dzY).coerceIn(0.45f, 1.15f)

                val shadedColor = Color(
                    red = (color.red * lightDot).coerceIn(0f, 1f),
                    green = (color.green * lightDot).coerceIn(0f, 1f),
                    blue = (color.blue * lightDot).coerceIn(0f, 1f),
                    alpha = 0.92f
                )

                faces.add(
                    MeshFace3D(
                        v0 = v0,
                        v1 = v1,
                        v2 = v2,
                        v3 = v3,
                        averageDepthZ = avgZ,
                        baseColor = shadedColor,
                        normalZ = lightDot
                    )
                )
            }
        }

        // 3. Sort faces back-to-front (Painter's Algorithm)
        faces.sortBy { it.averageDepthZ }
        return faces
    }
}
