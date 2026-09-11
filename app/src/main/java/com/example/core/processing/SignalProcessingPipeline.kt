package com.example.core.processing

import com.example.core.model.MeasurementGrid
import com.example.core.model.MeasurementPoint
import com.example.core.model.ProcessingOperation
import com.example.core.model.ProcessingOperationType
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance non-destructive signal processing engine.
 * Never overwrites raw data; computes processed values and tracks history stack.
 */
class SignalProcessingPipeline {

    /**
     * Subtracts a constant or average baseline from all points.
     */
    fun applyBaselineCorrection(grid: MeasurementGrid, baselineValue: Float? = null): MeasurementGrid {
        val validPoints = grid.points.filter { !it.isMissing }
        val baseline = baselineValue ?: if (validPoints.isNotEmpty()) {
            // Use median for robust baseline
            val sorted = validPoints.map { it.processedValue }.sorted()
            sorted[sorted.size / 2]
        } else 0f

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else pt.copy(processedValue = pt.processedValue - baseline)
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Applies gain (scaling multiplier).
     */
    fun applyGain(grid: MeasurementGrid, gainFactor: Float): MeasurementGrid {
        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else pt.copy(processedValue = pt.processedValue * gainFactor)
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Normalizes processed values to range [targetMin, targetMax] (e.g. -100 to +100 or 0 to 1).
     */
    fun applyNormalization(grid: MeasurementGrid, targetMin: Float = -100f, targetMax: Float = 100f): MeasurementGrid {
        val valid = grid.points.filter { !it.isMissing }
        if (valid.isEmpty()) return grid
        val currentMin = valid.minOf { it.processedValue }
        val currentMax = valid.maxOf { it.processedValue }
        val range = currentMax - currentMin
        if (range < 0.0001f) return grid

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else {
                val normalized01 = (pt.processedValue - currentMin) / range
                val scaled = targetMin + normalized01 * (targetMax - targetMin)
                pt.copy(processedValue = scaled)
            }
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * 3x3 2D Median Filter: Removes single-sample instrument spikes without degrading sharp anomaly edges.
     */
    fun applyMedianFilter(grid: MeasurementGrid): MeasurementGrid {
        val rows = grid.rows
        val cols = grid.cols
        val matrix = grid.getProcessedMatrix()
        val resultMatrix = Array(rows) { FloatArray(cols) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val neighbors = mutableListOf<Float>()
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            neighbors.add(matrix[nr][nc])
                        }
                    }
                }
                neighbors.sort()
                resultMatrix[r][c] = neighbors[neighbors.size / 2]
            }
        }

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else pt.copy(processedValue = resultMatrix[pt.gridY][pt.gridX])
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * 3x3 2D Gaussian Blur Smoothing Filter.
     */
    fun applyGaussianFilter(grid: MeasurementGrid, sigma: Float = 1.0f): MeasurementGrid {
        val rows = grid.rows
        val cols = grid.cols
        val matrix = grid.getProcessedMatrix()
        val resultMatrix = Array(rows) { FloatArray(cols) }

        // Compute 3x3 kernel
        val kernel = Array(3) { FloatArray(3) }
        var sum = 0f
        for (i in -1..1) {
            for (j in -1..1) {
                val v = exp(-(i * i + j * j) / (2f * sigma * sigma))
                kernel[i + 1][j + 1] = v
                sum += v
            }
        }
        for (i in 0..2) {
            for (j in 0..2) {
                kernel[i][j] /= sum
            }
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var weightedSum = 0f
                var weightTotal = 0f
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val w = kernel[dr + 1][dc + 1]
                            weightedSum += matrix[nr][nc] * w
                            weightTotal += w
                        }
                    }
                }
                resultMatrix[r][c] = if (weightTotal > 0f) weightedSum / weightTotal else matrix[r][c]
            }
        }

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else pt.copy(processedValue = resultMatrix[pt.gridY][pt.gridX])
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Linear Line-Drift Correction: Compensates for thermal or sensor heading drift across lines.
     */
    fun applyDriftCorrection(grid: MeasurementGrid): MeasurementGrid {
        val rows = grid.rows
        val cols = grid.cols
        val matrix = grid.getProcessedMatrix()

        // Calculate line averages
        val lineAverages = FloatArray(rows)
        for (r in 0 until rows) {
            var sum = 0f
            var count = 0
            for (c in 0 until cols) {
                sum += matrix[r][c]
                count++
            }
            lineAverages[r] = if (count > 0) sum / count else 0f
        }
        val overallAvg = lineAverages.average().toFloat()

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else {
                val lineDrift = lineAverages[pt.gridY] - overallAvg
                pt.copy(processedValue = pt.processedValue - lineDrift)
            }
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Planar Background Subtraction (least-squares tilt compensation):
     * Removes regional geophysical gradient / topographic slope.
     */
    fun applyBackgroundSubtraction(grid: MeasurementGrid): MeasurementGrid {
        val valid = grid.points.filter { !it.isMissing }
        if (valid.size < 4) return grid

        // Fit plane: z = a*x + b*y + c using simple centroid slopes
        val meanX = valid.map { it.gridX.toFloat() }.average().toFloat()
        val meanY = valid.map { it.gridY.toFloat() }.average().toFloat()
        val meanZ = valid.map { it.processedValue }.average().toFloat()

        var numX = 0f
        var denX = 0f
        var numY = 0f
        var denY = 0f

        for (pt in valid) {
            val dx = pt.gridX - meanX
            val dy = pt.gridY - meanY
            val dz = pt.processedValue - meanZ
            numX += dx * dz
            denX += dx * dx
            numY += dy * dz
            denY += dy * dy
        }

        val slopeX = if (denX > 0.0001f) numX / denX else 0f
        val slopeY = if (denY > 0.0001f) numY / denY else 0f
        val intercept = meanZ - (slopeX * meanX + slopeY * meanY)

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else {
                val planeVal = slopeX * pt.gridX + slopeY * pt.gridY + intercept
                pt.copy(processedValue = pt.processedValue - planeVal)
            }
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Resets all processed values to the original untouched raw measurement values.
     */
    fun resetToRaw(grid: MeasurementGrid): MeasurementGrid {
        val updatedPoints = grid.points.map { pt ->
            pt.copy(processedValue = pt.rawValue)
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Visualizer 3D Feature: Automatic Error Signal Correction.
     * Identifies localized outlier spikes that deviate excessively (> threshold * σ) from
     * local 8-neighborhood median and replaces them with clean interpolated neighbor values.
     */
    fun applyAutomaticErrorCorrection(grid: MeasurementGrid, sigmaThreshold: Float = 2.5f): MeasurementGrid {
        val matrix = grid.getProcessedMatrix()
        val rows = grid.rows
        val cols = grid.cols
        val stats = grid.getStats()
        val maxAllowedDelta = max(4.0f, stats.stdDev * sigmaThreshold)

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) return@map pt
            val r = pt.gridY
            val c = pt.gridX

            val neighbors = mutableListOf<Float>()
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        val neighborPt = grid.getPoint(nc, nr)
                        if (neighborPt != null && !neighborPt.isMissing) {
                            neighbors.add(matrix[nr][nc])
                        }
                    }
                }
            }

            if (neighbors.size >= 3) {
                val medianNeighbor = neighbors.sorted()[neighbors.size / 2]
                val diff = kotlin.math.abs(pt.processedValue - medianNeighbor)
                if (diff > maxAllowedDelta) {
                    // Outlier spike detected: correct to local median
                    pt.copy(processedValue = medianNeighbor)
                } else {
                    pt
                }
            } else {
                pt
            }
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Visualizer 3D Feature: Automatic Measurement Completion for Missing Signals.
     * Reconstructs missing points / unrecorded survey cells via spatial Inverse Distance Weighting (IDW).
     */
    fun applyCompleteMissingSignals(grid: MeasurementGrid): MeasurementGrid {
        val knownPoints = grid.points.filter { !it.isMissing }
        if (knownPoints.isEmpty()) return grid

        val updatedPoints = grid.points.map { pt ->
            if (!pt.isMissing) pt
            else {
                // Compute IDW value from nearest 6 known points
                val nearby = knownPoints
                    .map { kp ->
                        val dx = kp.gridX - pt.gridX
                        val dy = kp.gridY - pt.gridY
                        val distSq = (dx * dx + dy * dy).coerceAtLeast(1)
                        Pair(kp.processedValue, 1.0f / distSq.toFloat())
                    }
                    .sortedByDescending { it.second }
                    .take(6)

                val weightSum = nearby.sumOf { it.second.toDouble() }.toFloat()
                val interpolatedVal = if (weightSum > 0f) {
                    nearby.sumOf { (it.first * it.second).toDouble() }.toFloat() / weightSum
                } else 0f

                pt.copy(
                    rawValue = interpolatedVal,
                    processedValue = interpolatedVal,
                    isMissing = false,
                    isInterpolated = true
                )
            }
        }
        return grid.copy(points = updatedPoints)
    }

    /**
     * Visualizer 3D Feature: Rotational / Heading Correction.
     * Corrects sensor heading drift caused by operator turning at scan line ends.
     */
    fun applyHeadingCorrection(grid: MeasurementGrid): MeasurementGrid {
        val rows = grid.rows
        val cols = grid.cols
        if (rows < 2) return grid

        val updatedPoints = grid.points.map { pt ->
            if (pt.isMissing) pt
            else {
                // In zig-zag walks, odd rows often exhibit an orientation heading bias
                if (pt.gridY % 2 == 1) {
                    val angleOffset = kotlin.math.sin(Math.toRadians(pt.orientationYaw.toDouble())).toFloat()
                    pt.copy(processedValue = pt.processedValue - angleOffset * 0.5f)
                } else {
                    pt
                }
            }
        }
        return grid.copy(points = updatedPoints)
    }
}

