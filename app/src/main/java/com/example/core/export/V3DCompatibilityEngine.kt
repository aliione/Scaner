package com.example.core.export

import com.example.core.model.MeasurementGrid
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standardized Binary Ground Scanner Compatibility Layer.
 * Produces structured Little-Endian binary payloads compatible with PC 3D Visualizer workflows.
 * 
 * Frame Specification (V3D Standard Little-Endian Frame):
 * [0..3]   Magic Bytes ("V3D\0" or 0x56 0x33 0x44 0x00)
 * [4..5]   File Format Version (UInt16 = 0x0100 -> v1.0)
 * [6..7]   Grid Columns / Pulses per Line (UInt16)
 * [8..9]   Grid Rows / Number of Lines (UInt16)
 * [10..13] Physical Width in meters (Float32)
 * [14..17] Physical Length in meters (Float32)
 * [18..21] Base Frequency or Sampling Rate in Hz (Float32)
 * [22..25] Sensor Type ID (UInt32)
 * [26..29] Scan Mode (0 = Parallel, 1 = ZigZag) (UInt32)
 * [30..33] Soil Profile Permittivity factor (Float32)
 * [34..63] Reserved Header Padding (30 bytes zeroed)
 * [64..]   Measurement Data Points:
 *          Float32 Little-Endian array of dimension (Rows * Cols).
 *          Followed by 16-bit Integer raw ADC equivalents.
 */
object V3DCompatibilityEngine {

    const val MAGIC_HEADER: String = "V3D\u0000"
    const val FORMAT_VERSION: Short = 0x0100

    data class ExportLossReport(
        val originalPointsCount: Int,
        val exportedPointsCount: Int,
        val precisionMode: String = "32-bit Floating Point + 16-bit ADC",
        val isInterpolationIncluded: Boolean = false,
        val unsupportedMetadataWarnings: List<String> = emptyList()
    )

    fun exportToV3DBinary(grid: MeasurementGrid): Pair<ByteArray, ExportLossReport> {
        val rows = grid.rows
        val cols = grid.cols
        val totalPoints = rows * cols
        val matrix = grid.getProcessedMatrix()
        val rawMatrix = grid.getRawMatrix()

        val headerSize = 64
        val dataSizeFloat = totalPoints * 4
        val dataSizeInt16 = totalPoints * 2
        val totalBytes = headerSize + dataSizeFloat + dataSizeInt16

        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Magic Bytes (4 bytes)
        buffer.put(0x56.toByte()) // 'V'
        buffer.put(0x33.toByte()) // '3'
        buffer.put(0x44.toByte()) // 'D'
        buffer.put(0x00.toByte()) // '\0'

        // 2. Version (2 bytes)
        buffer.putShort(FORMAT_VERSION)

        // 3. Grid Dimensions (4 bytes)
        buffer.putShort(cols.toShort())
        buffer.putShort(rows.toShort())

        // 4. Physical Dimensions (8 bytes)
        buffer.putFloat(grid.widthMeters)
        buffer.putFloat(grid.lengthMeters)

        // 5. Sampling parameters (12 bytes)
        buffer.putFloat(20.0f) // 20 Hz default
        buffer.putInt(1) // Gradiometer type
        buffer.putInt(1) // Zig-zag mode

        // 6. Soil factor (4 bytes)
        buffer.putFloat(1.0f)

        // 7. Reserved padding (30 bytes)
        for (i in 0 until 30) {
            buffer.put(0.toByte())
        }

        // 8. Float32 Processed Data array (Row-Major)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = if (r < matrix.size && c < matrix[0].size) matrix[r][c] else 0f
                buffer.putFloat(v)
            }
        }

        // 9. Int16 Raw equivalents
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val rawVal = if (r < rawMatrix.size && c < rawMatrix[0].size) rawMatrix[r][c] else 0f
                val clamped = (rawVal * 10f).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(clamped)
            }
        }

        val report = ExportLossReport(
            originalPointsCount = totalPoints,
            exportedPointsCount = totalPoints,
            precisionMode = "IEEE 754 32-bit Float + Little-Endian Int16",
            isInterpolationIncluded = false,
            unsupportedMetadataWarnings = listOf(
                "NOTE: Proprietary Windows 3D Visualizer internal checksum algorithms require verification with physical desktop software."
            )
        )

        return Pair(buffer.array(), report)
    }
}
