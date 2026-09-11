package com.example.core.visualization

import androidx.compose.ui.graphics.Color
import com.example.core.model.ColorPaletteType

/**
 * Color mapping engine providing scientific palettes with thresholding,
 * midpoint centering, and dynamic range scaling.
 */
object ColorMapEngine {

    /**
     * Map a normalized value in [0.0, 1.0] to a Color according to the chosen palette.
     */
    fun getColorForNormalized(
        t: Float,
        palette: ColorPaletteType,
        inverted: Boolean = false
    ): Color {
        val clamped = t.coerceIn(0f, 1f)
        val normalized = if (inverted) 1f - clamped else clamped

        return when (palette) {
            ColorPaletteType.SPECTRUM -> interpolateMulti(
                normalized,
                listOf(
                    0.0f to Color(0xFF00008F), // Deep Blue
                    0.2f to Color(0xFF005AFF), // Blue
                    0.4f to Color(0xFF00FFFF), // Cyan
                    0.6f to Color(0xFF00FF00), // Green
                    0.8f to Color(0xFFFFFF00), // Yellow
                    1.0f to Color(0xFFFF0000)  // Red
                )
            )

            ColorPaletteType.THERMAL -> interpolateMulti(
                normalized,
                listOf(
                    0.0f to Color(0xFF0A0A18), // Deep Navy / Black
                    0.25f to Color(0xFF4A0072), // Deep Purple
                    0.5f to Color(0xFFB71C1C),  // Crimson Red
                    0.75f to Color(0xFFFF8F00), // Bright Orange
                    0.9f to Color(0xFFFFEB3B),  // Yellow
                    1.0f to Color(0xFFFFFFFF)   // White-hot
                )
            )

            ColorPaletteType.GRAYSCALE -> {
                val c = (normalized * 255f).toInt().coerceIn(0, 255)
                Color(c, c, c)
            }

            ColorPaletteType.BLUE_RED -> interpolateMulti(
                normalized,
                listOf(
                    0.0f to Color(0xFF0D47A1), // Intense Negative Blue
                    0.25f to Color(0xFF42A5F5), // Light Blue
                    0.5f to Color(0xFF263238),  // Neutral Dark Gray
                    0.75f to Color(0xFFFF7043), // Light Red/Orange
                    1.0f to Color(0xFFD50000)   // Intense Positive Red
                )
            )

            ColorPaletteType.DIVERGING -> interpolateMulti(
                normalized,
                listOf(
                    0.0f to Color(0xFF1B5E20), // Forest Green
                    0.3f to Color(0xFF81C784), // Pale Green
                    0.5f to Color(0xFFFFF9C4), // Pale Yellow
                    0.7f to Color(0xFFFFB74D), // Amber
                    1.0f to Color(0xFFBF360C)  // Deep Red Ochre
                )
            )
        }
    }

    /**
     * Map a physical or processed value to Color given min and max limits.
     */
    fun getColorForValue(
        value: Float,
        min: Float,
        max: Float,
        palette: ColorPaletteType,
        inverted: Boolean = false,
        thresholdMin: Float? = null,
        thresholdMax: Float? = null
    ): Color {
        if (thresholdMin != null && value < thresholdMin) return Color.Transparent
        if (thresholdMax != null && value > thresholdMax) return Color.Transparent

        val range = max - min
        val t = if (range > 0.00001f) (value - min) / range else 0.5f
        return getColorForNormalized(t, palette, inverted)
    }

    private fun interpolateMulti(t: Float, stops: List<Pair<Float, Color>>): Color {
        if (t <= stops.first().first) return stops.first().second
        if (t >= stops.last().first) return stops.last().second

        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (t in t0..t1) {
                val factor = (t - t0) / (t1 - t0)
                return Color(
                    red = c0.red + factor * (c1.red - c0.red),
                    green = c0.green + factor * (c1.green - c0.green),
                    blue = c0.blue + factor * (c1.blue - c0.blue),
                    alpha = 1.0f
                )
            }
        }
        return stops.last().second
    }
}
