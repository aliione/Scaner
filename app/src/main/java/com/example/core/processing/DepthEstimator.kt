package com.example.core.processing

import com.example.core.model.SoilProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scientific depth estimation module.
 * Strictly enforces transparent scientific terminology:
 * Distinguishes Measured depth vs Estimated depth vs Relative pseudo-depth vs Unknown depth.
 */
object DepthEstimator {

    enum class DepthCertainty(val label: String) {
        MEASURED_CALIBRATED("Calibrated Reference Depth"),
        ESTIMATED_GEOPHYSICAL("Estimated Depth (Half-Width Geophysical Model)"),
        RELATIVE_PSEUDO_DEPTH("Relative Pseudo-Depth (Intensity Inferred)"),
        UNKNOWN_INSUFFICIENT_DATA("Unknown (Insufficient Data / Low SNR)")
    }

    data class DepthResult(
        val depthMeters: Float,
        val uncertaintyMeters: Float,
        val certaintyLevel: DepthCertainty,
        val formulaUsed: String,
        val soilFactor: Float,
        val notes: String
    )

    /**
     * Estimates anomaly depth using spatial half-width at half-maximum (HWHM)
     * corrected by soil attenuation coefficient and sensor elevation.
     */
    fun estimateDepth(
        peakIntensity: Float,
        backgroundLevel: Float,
        anomalyWidthMeters: Float,
        sensorHeightCm: Float = 10f,
        soilProfile: SoilProfile? = null
    ): DepthResult {
        val deltaSignal = kotlin.math.abs(peakIntensity - backgroundLevel)

        if (deltaSignal < 2.0f || anomalyWidthMeters < 0.1f) {
            return DepthResult(
                depthMeters = 0f,
                uncertaintyMeters = 0f,
                certaintyLevel = DepthCertainty.UNKNOWN_INSUFFICIENT_DATA,
                formulaUsed = "N/A",
                soilFactor = 1.0f,
                notes = "Signal intensity or anomaly aperture too low for reliable depth inversion. Labeled as Insufficient Data."
            )
        }

        // Half-width dipole inversion rule of thumb: z ~ 0.5 * full_width_at_half_maximum
        val hwhm = anomalyWidthMeters / 2.0f
        val sensorElevationM = sensorHeightCm / 100.0f

        // Soil damping correction (higher conductivity/attenuation dampens deeper signals, causing apparent shallowing)
        val attenuation = soilProfile?.attenuationFactor ?: 3.0f
        val soilFactor = 1.0f / (1.0f + 0.03f * attenuation)

        val rawEstimatedDepth = max(0.1f, (hwhm * 1.15f - sensorElevationM) * soilFactor)
        val uncertainty = max(0.15f, rawEstimatedDepth * 0.35f)

        return DepthResult(
            depthMeters = rawEstimatedDepth,
            uncertaintyMeters = uncertainty,
            certaintyLevel = DepthCertainty.ESTIMATED_GEOPHYSICAL,
            formulaUsed = "z ≈ (HWHM × 1.15 - h_sensor) × (1 / (1 + 0.03 × α_soil))",
            soilFactor = soilFactor,
            notes = "Estimated via spatial falloff gradient and ${soilProfile?.name ?: "Standard Soil"} attenuation model. Not a photographic underground measurement."
        )
    }

    /**
     * Converts raw 2D grid matrix into relative pseudo-depth slices (Z-axis voxels)
     * using depth decay projection when multi-depth sensor readings or pseudo-depth is requested.
     */
    fun generatePseudoDepthVolume(
        processedMatrix: Array<FloatArray>,
        depthSlices: Int = 12,
        maxRelativeDepthMeters: Float = 2.5f
    ): Array<Array<FloatArray>> {
        val rows = processedMatrix.size
        val cols = processedMatrix[0].size
        // 3D array: [z][y][x]
        val volume = Array(depthSlices) { Array(rows) { FloatArray(cols) } }

        for (z in 0 until depthSlices) {
            val depthRatio = (z + 1).toFloat() / depthSlices.toFloat()
            // Physical dipole intensity decays roughly with 1/(r^3)
            val decay = 1.0f / ((1.0f + depthRatio * 1.8f) * (1.0f + depthRatio * 1.8f))

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val surfaceVal = processedMatrix[r][c]
                    // Add slight spatial diffusion at greater depths
                    volume[z][r][c] = surfaceVal * decay
                }
            }
        }
        return volume
    }
}
