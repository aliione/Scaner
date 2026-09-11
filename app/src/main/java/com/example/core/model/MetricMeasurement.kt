package com.example.core.model

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Metric measurement between two arbitrary points on a ground scan grid.
 */
data class MetricMeasurement(
    val startCol: Int,
    val startRow: Int,
    val startXMeter: Float,
    val startYMeter: Float,
    val startValue: Float,

    val endCol: Int,
    val endRow: Int,
    val endXMeter: Float,
    val endYMeter: Float,
    val endValue: Float
) {
    val distanceMeters: Float
        get() {
            val dx = endXMeter - startXMeter
            val dy = endYMeter - startYMeter
            return sqrt(dx * dx + dy * dy)
        }

    val deltaValue: Float
        get() = endValue - startValue

    val gradientPerMeter: Float
        get() {
            val d = distanceMeters
            return if (d > 0.001f) (endValue - startValue) / d else 0f
        }

    val angleDegrees: Float
        get() {
            val dx = endXMeter - startXMeter
            val dy = endYMeter - startYMeter
            val rad = atan2(dy.toDouble(), dx.toDouble())
            var deg = Math.toDegrees(rad).toFloat()
            if (deg < 0) deg += 360f
            return deg
        }
}
