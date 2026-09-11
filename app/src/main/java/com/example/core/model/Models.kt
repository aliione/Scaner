package com.example.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Scan Modes supported by the workstation.
 */
enum class ScanMode {
    RECTANGULAR_GRID,
    SQUARE_GRID,
    LINE_SCAN,
    FREE_SCAN
}

/**
 * Measurement units for coordinates and physical dimensions.
 */
enum class DistanceUnit(val symbol: String, val toMetersMultiplier: Float) {
    METERS("m", 1.0f),
    CENTIMETERS("cm", 0.01f),
    FEET("ft", 0.3048f),
    INCHES("in", 0.0254f)
}

/**
 * Sensor types supported by external acquisition hardware.
 */
enum class SensorType(val label: String) {
    MAGNETOMETER_FLUXGATE("Fluxgate Magnetometer"),
    GRADIOMETER("Differential Gradiometer"),
    ELECTROMAGNETIC_EM("VLF / EM Conductivity"),
    EARTH_RESISTIVITY("Resistivity Meter"),
    GENERIC_DIY("DIY Custom Sensor")
}

/**
 * Supported color palettes for 2D & 3D visualization.
 */
enum class ColorPaletteType(val displayName: String) {
    SPECTRUM("Spectrum (Rainbow)"),
    THERMAL("Thermal (Ironbow)"),
    GRAYSCALE("Grayscale (High Contrast)"),
    BLUE_RED("Blue-Red (Bipolar)"),
    DIVERGING("Diverging (Geophysical)")
}

/**
 * Supported 2D and 3D interpolation algorithms.
 */
enum class InterpolationMethod(val displayName: String) {
    NONE("Raw Data (No Interpolation)"),
    NEAREST_NEIGHBOR("Nearest Neighbor"),
    BILINEAR("Bilinear Interpolation"),
    BICUBIC("Bicubic Spline"),
    IDW("Inverse Distance Weighting (IDW)"),
    GAUSSIAN("Gaussian Smoothing")
}

/**
 * Soil profile representation for ground attenuation & calibration.
 */
data class SoilProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val conductivityMsM: Float, // mS/m
    val magneticSusceptibility: Float, // x 10^-5 SI
    val attenuationFactor: Float, // dB/m
    val relativePermittivity: Float, // dielectric constant
    val calibrationCoefficient: Float = 1.0f,
    val isCustom: Boolean = false
) {
    companion object {
        val DEFAULT_SOILS = listOf(
            SoilProfile("dry_soil", "Dry Sandy Soil", conductivityMsM = 1.5f, magneticSusceptibility = 10f, attenuationFactor = 2.0f, relativePermittivity = 4.0f),
            SoilProfile("wet_soil", "Wet Loam Soil", conductivityMsM = 12.0f, magneticSusceptibility = 25f, attenuationFactor = 8.5f, relativePermittivity = 15.0f),
            SoilProfile("clay", "Clay Heavy", conductivityMsM = 28.0f, magneticSusceptibility = 40f, attenuationFactor = 16.0f, relativePermittivity = 22.0f),
            SoilProfile("sand", "Dry Sand / Desert", conductivityMsM = 0.5f, magneticSusceptibility = 5f, attenuationFactor = 1.2f, relativePermittivity = 3.2f),
            SoilProfile("gravel", "Gravel / Aggregates", conductivityMsM = 2.0f, magneticSusceptibility = 8f, attenuationFactor = 2.5f, relativePermittivity = 5.0f),
            SoilProfile("rock", "Solid Bedrock / Granite", conductivityMsM = 0.1f, magneticSusceptibility = 15f, attenuationFactor = 0.8f, relativePermittivity = 6.0f),
            SoilProfile("mixed", "Mixed Agricultural Ground", conductivityMsM = 8.0f, magneticSusceptibility = 20f, attenuationFactor = 6.0f, relativePermittivity = 10.0f)
        )
    }
}

/**
 * Individual point measured by the scanner.
 */
data class MeasurementPoint(
    val pointId: Int,
    val gridX: Int, // column index (0..cols-1)
    val gridY: Int, // row index (0..rows-1)
    val posX: Float, // physical X position
    val posY: Float, // physical Y position
    val rawValue: Float, // direct uncalibrated sensor reading
    val processedValue: Float = rawValue, // after active signal filters
    val timestamp: Long = System.currentTimeMillis(),
    val scanLine: Int = gridY,
    val sampleIndex: Int = gridX,
    val signalAmplitude: Float = rawValue,
    val magneticFieldTesla: Float = 0f,
    val conductivity: Float = 0f,
    val frequency: Float = 0f,
    val phase: Float = 0f,
    val orientationYaw: Float = 0f,
    val orientationPitch: Float = 0f,
    val orientationRoll: Float = 0f,
    val isMissing: Boolean = false,
    val isInterpolated: Boolean = false
)

/**
 * Complete measurement 2D grid matrix with metadata.
 */
data class MeasurementGrid(
    val rows: Int,
    val cols: Int,
    val widthMeters: Float,
    val lengthMeters: Float,
    val gridSpacingMeters: Float,
    val points: List<MeasurementPoint> = emptyList()
) {
    fun getPoint(x: Int, y: Int): MeasurementPoint? {
        if (x !in 0 until cols || y !in 0 until rows) return null
        val idx = y * cols + x
        return points.getOrNull(idx)
    }

    fun getRawMatrix(): Array<FloatArray> {
        val matrix = Array(rows) { FloatArray(cols) }
        for (pt in points) {
            if (pt.gridY in 0 until rows && pt.gridX in 0 until cols) {
                matrix[pt.gridY][pt.gridX] = pt.rawValue
            }
        }
        return matrix
    }

    fun getProcessedMatrix(): Array<FloatArray> {
        val matrix = Array(rows) { FloatArray(cols) }
        for (pt in points) {
            if (pt.gridY in 0 until rows && pt.gridX in 0 until cols) {
                matrix[pt.gridY][pt.gridX] = pt.processedValue
            }
        }
        return matrix
    }

    fun getStats(): GridStatistics {
        val validPoints = points.filter { !it.isMissing }
        if (validPoints.isEmpty()) {
            return GridStatistics(0f, 0f, 0f, 0f, 0f, 0)
        }
        val values = validPoints.map { it.processedValue }
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val sum = values.sum()
        val avg = sum / values.size
        val variance = values.map { (it - avg) * (it - avg) }.sum() / values.size
        val stdDev = kotlin.math.sqrt(variance.toDouble()).toFloat()
        return GridStatistics(
            min = min,
            max = max,
            average = avg,
            stdDev = stdDev,
            range = max - min,
            pointCount = validPoints.size
        )
    }
}

data class GridStatistics(
    val min: Float,
    val max: Float,
    val average: Float,
    val stdDev: Float,
    val range: Float,
    val pointCount: Int
)

/**
 * Anomaly detected through mathematical morphology and spatial analysis.
 */
enum class AnomalyType(val label: String) {
    POSITIVE_ANOMALY("High-Intensity Positive Anomaly"),
    NEGATIVE_ANOMALY("Low-Intensity Negative Response (Cavity/Void-like)"),
    DIPOLAR_MAGNETIC("Dipolar Magnetic Signature"),
    CONDUCTIVE_REGION("High Conductive Zone"),
    HIGH_GRADIENT("Sharp Gradient Boundary")
}

data class DetectedAnomaly(
    val id: String = UUID.randomUUID().toString(),
    val type: AnomalyType,
    val centerX: Float, // physical X in meters
    val centerY: Float, // physical Y in meters
    val gridX: Int,
    val gridY: Int,
    val width: Float,
    val length: Float,
    val peakIntensity: Float,
    val contrast: Float,
    val areaM2: Float,
    val confidenceScore: Float, // 0.0 to 1.0
    val estimatedRelativeDepth: Float, // estimated relative pseudo-depth in meters
    val description: String
)

/**
 * User-marked target for field validation.
 */
enum class TargetCategory(val label: String) {
    POTENTIAL_FERROUS("Potential Ferrous / High Magnetic"),
    POTENTIAL_NON_FERROUS("Potential Non-Ferrous Conductive"),
    VOID_LIKE_ANOMALY("Void-like / Subsurface Cavity Response"),
    GEOLOGICAL_BOUNDARY("Geological Contact / Strata Boundary"),
    UNIDENTIFIED_TARGET("Unidentified Anomaly")
}

data class UserTarget(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val name: String,
    val posX: Float,
    val posY: Float,
    val gridX: Int,
    val gridY: Int,
    val estimatedDepthMeters: Float,
    val estimatedDiameterMeters: Float,
    val signalStrength: Float,
    val category: TargetCategory,
    val confidence: Float,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Point bookmark with notes and inspection records.
 */
data class PointBookmark(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val description: String,
    val gridX: Int,
    val gridY: Int,
    val posX: Float,
    val posY: Float,
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Non-destructive signal processing history operation.
 */
enum class ProcessingOperationType(val displayName: String) {
    RAW_DATA("Raw Acquisition"),
    BASELINE_CORRECTION("Baseline Offset Correction"),
    ZERO_OFFSET("Sensor Zeroing"),
    NORMALIZATION("Dynamic Range Normalization"),
    GAIN_ADJUSTMENT("Gain Amplification"),
    MEDIAN_FILTER("Median Filter (Spike Removal)"),
    GAUSSIAN_FILTER("2D Gaussian Smoothing"),
    DRIFT_CORRECTION("Linear Scan Drift Subtraction"),
    BACKGROUND_SUBTRACTION("Planar Background Subtraction"),
    ROTATIONAL_CORRECTION("Physical Alignment Angle Correction"),
    INTERPOLATION("Spatial Grid Interpolation")
}

data class ProcessingOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: ProcessingOperationType,
    val parametersDescription: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sensor hardware communication packet.
 */
data class GenericSensorPacket(
    val header: Byte = 0xAA.toByte(),
    val deviceId: String,
    val timestamp: Long,
    val sequenceNumber: Int,
    val xIndex: Int,
    val yIndex: Int,
    val sensorChannel1: Float,
    val sensorChannel2: Float,
    val sensorChannel3: Float,
    val batteryPercent: Int,
    val orientationRoll: Float,
    val orientationPitch: Float,
    val orientationYaw: Float,
    val checksum: Int,
    val isValid: Boolean = true
)

/**
 * Calibration profile for ground sensors.
 */
data class SensorCalibrationProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sensorType: SensorType,
    val zeroOffset: Float = 0f,
    val gainMultiplier: Float = 1.0f,
    val baselineAmbient: Float = 0f,
    val sensorHeightCm: Float = 10f,
    val temperatureCompCoeff: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Metadata & Project structure.
 */
data class ScanProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val location: String = "Field Site",
    val operatorName: String = "Survey Specialist",
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val deviceName: String = "DIY Ground Scanner V1",
    val sensorType: SensorType = SensorType.GRADIOMETER,
    val scanMode: ScanMode = ScanMode.RECTANGULAR_GRID,
    val rows: Int = 20,
    val cols: Int = 20,
    val widthMeters: Float = 10.0f,
    val lengthMeters: Float = 10.0f,
    val gridSpacingMeters: Float = 0.5f,
    val soilProfileName: String = "Dry Sandy Soil",
    val coordinateSystem: String = "Local Metric (Grid W/L)",
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAltitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val notes: String = "",
    val isSimulated: Boolean = false,
    val isFavorite: Boolean = false
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(dateCreated))
}
