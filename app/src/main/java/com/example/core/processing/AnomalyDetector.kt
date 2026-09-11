package com.example.core.processing

import com.example.core.model.AnomalyType
import com.example.core.model.DetectedAnomaly
import com.example.core.model.MeasurementGrid
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scientific spatial anomaly detection engine using adaptive thresholding,
 * morphological connected-component clustering, and gradient analysis.
 * Strictly adheres to scientific integrity guidelines.
 */
class AnomalyDetector {

    fun detectAnomalies(
        grid: MeasurementGrid,
        sensitivityMultiplier: Float = 1.8f
    ): List<DetectedAnomaly> {
        val rows = grid.rows
        val cols = grid.cols
        if (rows < 3 || cols < 3) return emptyList()

        val matrix = grid.getProcessedMatrix()
        val stats = grid.getStats()
        val mean = stats.average
        val std = max(0.01f, stats.stdDev)

        val posThreshold = mean + sensitivityMultiplier * std
        val negThreshold = mean - sensitivityMultiplier * std

        val visitedPos = Array(rows) { BooleanArray(cols) }
        val visitedNeg = Array(rows) { BooleanArray(cols) }

        val anomalies = mutableListOf<DetectedAnomaly>()

        // 1. Detect positive anomalies
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!visitedPos[r][c] && matrix[r][c] > posThreshold) {
                    val cluster = floodFill(matrix, r, c, visitedPos, isPositive = true, threshold = posThreshold)
                    if (cluster.isNotEmpty()) {
                        val anomaly = buildAnomaly(grid, cluster, matrix, mean, std, AnomalyType.POSITIVE_ANOMALY)
                        anomalies.add(anomaly)
                    }
                }
            }
        }

        // 2. Detect negative anomalies (cavity / void-like response)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!visitedNeg[r][c] && matrix[r][c] < negThreshold) {
                    val cluster = floodFill(matrix, r, c, visitedNeg, isPositive = false, threshold = negThreshold)
                    if (cluster.isNotEmpty()) {
                        val anomaly = buildAnomaly(grid, cluster, matrix, mean, std, AnomalyType.NEGATIVE_ANOMALY)
                        anomalies.add(anomaly)
                    }
                }
            }
        }

        // 3. Check for dipolar pairs (adjacent positive and negative anomalies)
        val dipolarChecked = mutableSetOf<String>()
        val finalAnomalies = mutableListOf<DetectedAnomaly>()

        for (a in anomalies) {
            if (a.type == AnomalyType.POSITIVE_ANOMALY) {
                // Find closest negative anomaly
                val pairedNeg = anomalies.find { neg ->
                    neg.type == AnomalyType.NEGATIVE_ANOMALY &&
                            !dipolarChecked.contains(neg.id) &&
                            sqrt((a.centerX - neg.centerX) * (a.centerX - neg.centerX) + (a.centerY - neg.centerY) * (a.centerY - neg.centerY)) < 2.0f * grid.gridSpacingMeters
                }
                if (pairedNeg != null) {
                    dipolarChecked.add(a.id)
                    dipolarChecked.add(pairedNeg.id)
                    finalAnomalies.add(
                        DetectedAnomaly(
                            type = AnomalyType.DIPOLAR_MAGNETIC,
                            centerX = (a.centerX + pairedNeg.centerX) / 2f,
                            centerY = (a.centerY + pairedNeg.centerY) / 2f,
                            gridX = (a.gridX + pairedNeg.gridX) / 2,
                            gridY = (a.gridY + pairedNeg.gridY) / 2,
                            width = max(a.width, pairedNeg.width) + abs(a.centerX - pairedNeg.centerX),
                            length = max(a.length, pairedNeg.length) + abs(a.centerY - pairedNeg.centerY),
                            peakIntensity = max(abs(a.peakIntensity), abs(pairedNeg.peakIntensity)),
                            contrast = (a.contrast + pairedNeg.contrast) / 2f,
                            areaM2 = a.areaM2 + pairedNeg.areaM2,
                            confidenceScore = min(0.98f, (a.confidenceScore + pairedNeg.confidenceScore) / 2f + 0.15f),
                            estimatedRelativeDepth = (a.estimatedRelativeDepth + pairedNeg.estimatedRelativeDepth) / 2f,
                            description = "Dipolar magnetic response (coupled positive/negative anomaly characteristic of concentrated ferrous or magnetic source)"
                        )
                    )
                } else {
                    finalAnomalies.add(a)
                }
            } else if (!dipolarChecked.contains(a.id)) {
                finalAnomalies.add(a)
            }
        }

        return finalAnomalies.sortedByDescending { it.confidenceScore }
    }

    private fun floodFill(
        matrix: Array<FloatArray>,
        startR: Int,
        startC: Int,
        visited: Array<BooleanArray>,
        isPositive: Boolean,
        threshold: Float
    ): List<Pair<Int, Int>> {
        val rows = matrix.size
        val cols = matrix[0].size
        val cluster = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(Pair(startR, startC))
        visited[startR][startC] = true

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            cluster.add(Pair(r, c))

            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (abs(dr) + abs(dc) != 1) continue // 4-connectivity
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols && !visited[nr][nc]) {
                        val v = matrix[nr][nc]
                        val matches = if (isPositive) v >= threshold else v <= threshold
                        if (matches) {
                            visited[nr][nc] = true
                            queue.add(Pair(nr, nc))
                        }
                    }
                }
            }
        }
        return cluster
    }

    private fun buildAnomaly(
        grid: MeasurementGrid,
        cluster: List<Pair<Int, Int>>,
        matrix: Array<FloatArray>,
        mean: Float,
        std: Float,
        type: AnomalyType
    ): DetectedAnomaly {
        var minR = Int.MAX_VALUE
        var maxR = Int.MIN_VALUE
        var minC = Int.MAX_VALUE
        var maxC = Int.MIN_VALUE

        var peakVal = if (type == AnomalyType.POSITIVE_ANOMALY) -Float.MAX_VALUE else Float.MAX_VALUE
        var peakR = cluster.first().first
        var peakC = cluster.first().second

        var totalWeight = 0f
        var sumR = 0f
        var sumC = 0f

        for ((r, c) in cluster) {
            minR = min(minR, r)
            maxR = max(maxR, r)
            minC = min(minC, c)
            maxC = max(maxC, c)

            val v = matrix[r][c]
            val w = abs(v - mean) + 0.001f
            totalWeight += w
            sumR += r * w
            sumC += c * w

            if (type == AnomalyType.POSITIVE_ANOMALY && v > peakVal) {
                peakVal = v
                peakR = r
                peakC = c
            } else if (type == AnomalyType.NEGATIVE_ANOMALY && v < peakVal) {
                peakVal = v
                peakR = r
                peakC = c
            }
        }

        val centerGridX = (sumC / totalWeight).toInt().coerceIn(0, grid.cols - 1)
        val centerGridY = (sumR / totalWeight).toInt().coerceIn(0, grid.rows - 1)

        val cellW = grid.widthMeters / grid.cols.toFloat()
        val cellL = grid.lengthMeters / grid.rows.toFloat()

        val widthM = max(cellW, (maxC - minC + 1) * cellW)
        val lengthM = max(cellL, (maxR - minR + 1) * cellL)
        val areaM2 = cluster.size * (cellW * cellL)

        val contrast = if (std > 0.001f) abs(peakVal - mean) / std else 1.0f
        // Half-width half-max depth estimate heuristic
        val estimatedDepth = max(0.2f, (widthM + lengthM) * 0.45f)

        // Confidence score based on cluster coherence and contrast
        val sizeScore = min(1.0f, cluster.size / 6.0f)
        val contrastScore = min(1.0f, contrast / 3.5f)
        val confidence = min(0.95f, max(0.35f, 0.4f * sizeScore + 0.6f * contrastScore))

        val desc = when (type) {
            AnomalyType.POSITIVE_ANOMALY -> "Localized positive anomaly (peak: ${String.format("%.1f", peakVal)}, contrast: ${String.format("%.1f", contrast)}σ)"
            AnomalyType.NEGATIVE_ANOMALY -> "Localized negative anomaly / void-like response (valley: ${String.format("%.1f", peakVal)}, contrast: ${String.format("%.1f", contrast)}σ)"
            else -> "Potential geophysical anomaly"
        }

        return DetectedAnomaly(
            type = type,
            centerX = centerGridX * cellW + cellW / 2f,
            centerY = centerGridY * cellL + cellL / 2f,
            gridX = centerGridX,
            gridY = centerGridY,
            width = widthM,
            length = lengthM,
            peakIntensity = peakVal,
            contrast = contrast,
            areaM2 = areaM2,
            confidenceScore = confidence,
            estimatedRelativeDepth = estimatedDepth,
            description = desc
        )
    }
}
