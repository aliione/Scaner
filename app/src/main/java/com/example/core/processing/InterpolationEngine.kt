package com.example.core.processing

import com.example.core.model.InterpolationMethod
import com.example.core.model.MeasurementGrid
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-precision spatial interpolation and grid subdivision engine.
 * Generates smooth high-density matrices for 2D Heatmaps and 3D meshes
 * while keeping original physical measurement points explicitly distinct.
 */
class InterpolationEngine {

    /**
     * Resamples and interpolates an input matrix (rows x cols) to a higher target density
     * (targetRows x targetCols) using the specified scientific interpolation method.
     */
    fun interpolateMatrix(
        sourceMatrix: Array<FloatArray>,
        targetRows: Int,
        targetCols: Int,
        method: InterpolationMethod
    ): Array<FloatArray> {
        val srcRows = sourceMatrix.size
        if (srcRows == 0) return Array(targetRows) { FloatArray(targetCols) }
        val srcCols = sourceMatrix[0].size
        if (srcCols == 0) return Array(targetRows) { FloatArray(targetCols) }

        if (srcRows == targetRows && srcCols == targetCols && method == InterpolationMethod.NONE) {
            return sourceMatrix
        }

        val result = Array(targetRows) { FloatArray(targetCols) }

        val rowRatio = if (targetRows > 1) (srcRows - 1).toFloat() / (targetRows - 1).toFloat() else 0f
        val colRatio = if (targetCols > 1) (srcCols - 1).toFloat() / (targetCols - 1).toFloat() else 0f

        when (method) {
            InterpolationMethod.NONE, InterpolationMethod.NEAREST_NEIGHBOR -> {
                for (tr in 0 until targetRows) {
                    val sr = (tr * rowRatio + 0.5f).toInt().coerceIn(0, srcRows - 1)
                    for (tc in 0 until targetCols) {
                        val sc = (tc * colRatio + 0.5f).toInt().coerceIn(0, srcCols - 1)
                        result[tr][tc] = sourceMatrix[sr][sc]
                    }
                }
            }

            InterpolationMethod.BILINEAR -> {
                for (tr in 0 until targetRows) {
                    val rExact = tr * rowRatio
                    val r0 = rExact.toInt().coerceIn(0, srcRows - 1)
                    val r1 = (r0 + 1).coerceIn(0, srcRows - 1)
                    val dr = rExact - r0

                    for (tc in 0 until targetCols) {
                        val cExact = tc * colRatio
                        val c0 = cExact.toInt().coerceIn(0, srcCols - 1)
                        val c1 = (c0 + 1).coerceIn(0, srcCols - 1)
                        val dc = cExact - c0

                        val v00 = sourceMatrix[r0][c0]
                        val v01 = sourceMatrix[r0][c1]
                        val v10 = sourceMatrix[r1][c0]
                        val v11 = sourceMatrix[r1][c1]

                        val top = v00 * (1f - dc) + v01 * dc
                        val bottom = v10 * (1f - dc) + v11 * dc
                        result[tr][tc] = top * (1f - dr) + bottom * dr
                    }
                }
            }

            InterpolationMethod.BICUBIC -> {
                for (tr in 0 until targetRows) {
                    val rExact = tr * rowRatio
                    val rInt = rExact.toInt()
                    val dr = rExact - rInt

                    for (tc in 0 until targetCols) {
                        val cExact = tc * colRatio
                        val cInt = cExact.toInt()
                        val dc = cExact - cInt

                        // 4x4 neighborhood
                        val colVals = FloatArray(4)
                        for (i in -1..2) {
                            val rNeighbor = (rInt + i).coerceIn(0, srcRows - 1)
                            val p0 = sourceMatrix[rNeighbor][(cInt - 1).coerceIn(0, srcCols - 1)]
                            val p1 = sourceMatrix[rNeighbor][cInt.coerceIn(0, srcCols - 1)]
                            val p2 = sourceMatrix[rNeighbor][(cInt + 1).coerceIn(0, srcCols - 1)]
                            val p3 = sourceMatrix[rNeighbor][(cInt + 2).coerceIn(0, srcCols - 1)]
                            colVals[i + 1] = cubicHermite(p0, p1, p2, p3, dc)
                        }
                        result[tr][tc] = cubicHermite(colVals[0], colVals[1], colVals[2], colVals[3], dr)
                    }
                }
            }

            InterpolationMethod.IDW -> {
                // Inverse Distance Weighting with power p=2
                for (tr in 0 until targetRows) {
                    val rExact = tr * rowRatio
                    for (tc in 0 until targetCols) {
                        val cExact = tc * colRatio
                        var weightSum = 0f
                        var valSum = 0f
                        var exactHit = false

                        // Check nearby grid window to keep IDW fast (4x4 or full if small)
                        val rMin = (rExact - 2).toInt().coerceIn(0, srcRows - 1)
                        val rMax = (rExact + 3).toInt().coerceIn(0, srcRows - 1)
                        val cMin = (cExact - 2).toInt().coerceIn(0, srcCols - 1)
                        val cMax = (cExact + 3).toInt().coerceIn(0, srcCols - 1)

                        for (r in rMin..rMax) {
                            for (c in cMin..cMax) {
                                val distSq = (rExact - r) * (rExact - r) + (cExact - c) * (cExact - c)
                                if (distSq < 0.00001f) {
                                    result[tr][tc] = sourceMatrix[r][c]
                                    exactHit = true
                                    break
                                }
                                val w = 1f / distSq
                                weightSum += w
                                valSum += w * sourceMatrix[r][c]
                            }
                            if (exactHit) break
                        }
                        if (!exactHit) {
                            result[tr][tc] = if (weightSum > 0f) valSum / weightSum else 0f
                        }
                    }
                }
            }

            InterpolationMethod.GAUSSIAN -> {
                // First bilinear, then Gaussian smoothing kernel
                val bilinear = interpolateMatrix(sourceMatrix, targetRows, targetCols, InterpolationMethod.BILINEAR)
                val sigma = 1.2f
                val kernelRadius = 2
                val weights = Array(5) { FloatArray(5) }
                var totalW = 0f
                for (dr in -kernelRadius..kernelRadius) {
                    for (dc in -kernelRadius..kernelRadius) {
                        val w = exp(-(dr * dr + dc * dc) / (2f * sigma * sigma))
                        weights[dr + kernelRadius][dc + kernelRadius] = w
                        totalW += w
                    }
                }
                for (r in 0 until targetRows) {
                    for (c in 0 until targetCols) {
                        var accum = 0f
                        var wSum = 0f
                        for (dr in -kernelRadius..kernelRadius) {
                            for (dc in -kernelRadius..kernelRadius) {
                                val nr = (r + dr).coerceIn(0, targetRows - 1)
                                val nc = (c + dc).coerceIn(0, targetCols - 1)
                                val w = weights[dr + kernelRadius][dc + kernelRadius]
                                accum += bilinear[nr][nc] * w
                                wSum += w
                            }
                        }
                        result[r][c] = if (wSum > 0f) accum / wSum else bilinear[r][c]
                    }
                }
            }
        }

        return result
    }

    private fun cubicHermite(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val a = -0.5f * p0 + 1.5f * p1 - 1.5f * p2 + 0.5f * p3
        val b = p0 - 2.5f * p1 + 2f * p2 - 0.5f * p3
        val c = -0.5f * p0 + 0.5f * p2
        val d = p1
        return a * t * t * t + b * t * t + c * t + d
    }

    /**
     * Interpolates an entire MeasurementGrid to higher density by a scale factor.
     */
    fun interpolateGrid(grid: MeasurementGrid, factor: Int = 2, method: InterpolationMethod): MeasurementGrid {
        if (method == InterpolationMethod.NONE || factor <= 1) return grid

        val targetRows = grid.rows * factor
        val targetCols = grid.cols * factor
        val rawMatrix = grid.getRawMatrix()
        val procMatrix = grid.getProcessedMatrix()

        val interpolatedRaw = interpolateMatrix(rawMatrix, targetRows, targetCols, method)
        val interpolatedProc = interpolateMatrix(procMatrix, targetRows, targetCols, method)

        val newPoints = mutableListOf<com.example.core.model.MeasurementPoint>()
        var ptId = 0
        val cellW = grid.widthMeters / targetCols
        val cellL = grid.lengthMeters / targetRows

        for (r in 0 until targetRows) {
            for (c in 0 until targetCols) {
                newPoints.add(
                    com.example.core.model.MeasurementPoint(
                        pointId = ptId++,
                        gridX = c,
                        gridY = r,
                        posX = (c + 0.5f) * cellW,
                        posY = (r + 0.5f) * cellL,
                        rawValue = interpolatedRaw[r][c],
                        processedValue = interpolatedProc[r][c],
                        isInterpolated = true
                    )
                )
            }
        }

        return grid.copy(
            rows = targetRows,
            cols = targetCols,
            gridSpacingMeters = grid.gridSpacingMeters / factor,
            points = newPoints
        )
    }
}
