package com.example.core.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.core.model.DetectedAnomaly
import com.example.core.model.MeasurementGrid
import com.example.core.model.ScanProject
import com.example.core.model.UserTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    /**
     * Generates standard comma-separated values (CSV) representing raw and processed sensor points.
     */
    fun generateCsv(project: ScanProject, grid: MeasurementGrid): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val exportDate = dateFormat.format(Date())

        // Header Metadata
        sb.appendLine("# ========================================================")
        sb.appendLine("# GeoScan 3D - Scientific Ground Survey Export")
        sb.appendLine("# Project Name: ${project.name}")
        sb.appendLine("# Location: ${project.location}")
        sb.appendLine("# Export Timestamp: $exportDate")
        sb.appendLine("# Grid Dimensions: ${grid.cols} cols x ${grid.rows} rows (${grid.cols * grid.rows} points)")
        sb.appendLine("# Physical Survey Area: ${grid.widthMeters} m x ${grid.lengthMeters} m")
        sb.appendLine("# Grid Step Spacing: ${grid.gridSpacingMeters} m")
        sb.appendLine("# Sensor Probe Type: ${project.sensorType.name}")
        sb.appendLine("# Survey Walk Pattern: ${project.scanMode.name}")
        sb.appendLine("# ========================================================")
        sb.appendLine("Point_ID,Grid_Col,Grid_Row,Pos_X_m,Pos_Y_m,Raw_Signal_uT,Processed_Signal_uT,Delta_uT,Status")

        val rawMatrix = grid.getRawMatrix()
        val processedMatrix = grid.getProcessedMatrix()

        var id = 0
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val rawVal = if (r < rawMatrix.size && c < rawMatrix[0].size) rawMatrix[r][c] else 0f
                val procVal = if (r < processedMatrix.size && c < processedMatrix[0].size) processedMatrix[r][c] else 0f
                val delta = procVal - rawVal
                val posX = (c + 0.5f) * grid.gridSpacingMeters
                val posY = (r + 0.5f) * grid.gridSpacingMeters

                sb.append(id++).append(",")
                sb.append(c).append(",")
                sb.append(r).append(",")
                sb.append(String.format(Locale.US, "%.3f", posX)).append(",")
                sb.append(String.format(Locale.US, "%.3f", posY)).append(",")
                sb.append(String.format(Locale.US, "%.2f", rawVal)).append(",")
                sb.append(String.format(Locale.US, "%.2f", procVal)).append(",")
                sb.append(String.format(Locale.US, "%.2f", delta)).append(",")
                sb.append("VALID").append("\n")
            }
        }
        return sb.toString()
    }

    /**
     * Generates RFC 7946 compliant GeoJSON FeatureCollection containing sensor survey points,
     * detected anomaly bounding zones, and bookmarked user target pins.
     */
    fun generateGeoJson(
        project: ScanProject,
        grid: MeasurementGrid,
        anomalies: List<DetectedAnomaly>,
        targets: List<UserTarget>
    ): String {
        val sb = StringBuilder()
        val baseLon = project.gpsLongitude ?: 0.0
        val baseLat = project.gpsLatitude ?: 0.0
        val metersToDeg = 1.0 / 111320.0

        sb.appendLine("{")
        sb.appendLine("  \"type\": \"FeatureCollection\",")
        sb.appendLine("  \"properties\": {")
        sb.appendLine("    \"projectName\": \"${project.name}\",")
        sb.appendLine("    \"location\": \"${project.location}\",")
        sb.appendLine("    \"gridCols\": ${grid.cols},")
        sb.appendLine("    \"gridRows\": ${grid.rows},")
        sb.appendLine("    \"widthMeters\": ${grid.widthMeters},")
        sb.appendLine("    \"lengthMeters\": ${grid.lengthMeters}")
        sb.appendLine("  },")
        sb.appendLine("  \"features\": [")

        val rawMatrix = grid.getRawMatrix()
        val processedMatrix = grid.getProcessedMatrix()
        var featureIndex = 0

        // Point features
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val rawVal = if (r < rawMatrix.size && c < rawMatrix[0].size) rawMatrix[r][c] else 0f
                val procVal = if (r < processedMatrix.size && c < processedMatrix[0].size) processedMatrix[r][c] else 0f
                val posX = (c + 0.5f) * grid.gridSpacingMeters
                val posY = (r + 0.5f) * grid.gridSpacingMeters

                val ptLon = baseLon + (posX * metersToDeg)
                val ptLat = baseLat + (posY * metersToDeg)

                if (featureIndex > 0) sb.appendLine(",")
                sb.appendLine("    {")
                sb.appendLine("      \"type\": \"Feature\",")
                sb.appendLine("      \"geometry\": {")
                sb.appendLine("        \"type\": \"Point\",")
                sb.appendLine("        \"coordinates\": [${String.format(Locale.US, "%.6f", ptLon)}, ${String.format(Locale.US, "%.6f", ptLat)}]")
                sb.appendLine("      },")
                sb.appendLine("      \"properties\": {")
                sb.appendLine("        \"col\": $c,")
                sb.appendLine("        \"row\": $r,")
                sb.appendLine("        \"rawSignal\": ${String.format(Locale.US, "%.2f", rawVal)},")
                sb.appendLine("        \"processedSignal\": ${String.format(Locale.US, "%.2f", procVal)}")
                sb.appendLine("      }")
                sb.append("    }")
                featureIndex++
            }
        }

        // Anomaly bounding box polygons
        for (a in anomalies) {
            val minX = (a.gridX - 1).coerceAtLeast(0) * grid.gridSpacingMeters
            val maxX = minX + a.width
            val minY = (a.gridY - 1).coerceAtLeast(0) * grid.gridSpacingMeters
            val maxY = minY + a.length

            val minLon = baseLon + (minX * metersToDeg)
            val maxLon = baseLon + (maxX * metersToDeg)
            val minLat = baseLat + (minY * metersToDeg)
            val maxLat = baseLat + (maxY * metersToDeg)

            sb.appendLine(",")
            sb.appendLine("    {")
            sb.appendLine("      \"type\": \"Feature\",")
            sb.appendLine("      \"geometry\": {")
            sb.appendLine("        \"type\": \"Polygon\",")
            sb.appendLine("        \"coordinates\": [[")
            sb.appendLine("          [${String.format(Locale.US, "%.6f", minLon)}, ${String.format(Locale.US, "%.6f", minLat)}],")
            sb.appendLine("          [${String.format(Locale.US, "%.6f", maxLon)}, ${String.format(Locale.US, "%.6f", minLat)}],")
            sb.appendLine("          [${String.format(Locale.US, "%.6f", maxLon)}, ${String.format(Locale.US, "%.6f", maxLat)}],")
            sb.appendLine("          [${String.format(Locale.US, "%.6f", minLon)}, ${String.format(Locale.US, "%.6f", maxLat)}],")
            sb.appendLine("          [${String.format(Locale.US, "%.6f", minLon)}, ${String.format(Locale.US, "%.6f", minLat)}]")
            sb.appendLine("        ]]")
            sb.appendLine("      },")
            sb.appendLine("      \"properties\": {")
            sb.appendLine("        \"type\": \"ANOMALY_ZONE\",")
            sb.appendLine("        \"category\": \"${a.type.name}\",")
            sb.appendLine("        \"label\": \"${a.type.label}\",")
            sb.appendLine("        \"peakIntensity\": ${String.format(Locale.US, "%.2f", a.peakIntensity)},")
            sb.appendLine("        \"estimatedDepthMeters\": ${String.format(Locale.US, "%.2f", a.estimatedRelativeDepth)},")
            sb.appendLine("        \"confidence\": ${String.format(Locale.US, "%.2f", a.confidenceScore)}")
            sb.appendLine("      }")
            sb.append("    }")
        }

        // Target pins
        for (t in targets) {
            val tLon = baseLon + (t.posX * metersToDeg)
            val tLat = baseLat + (t.posY * metersToDeg)

            sb.appendLine(",")
            sb.appendLine("    {")
            sb.appendLine("      \"type\": \"Feature\",")
            sb.appendLine("      \"geometry\": {")
            sb.appendLine("        \"type\": \"Point\",")
            sb.appendLine("        \"coordinates\": [${String.format(Locale.US, "%.6f", tLon)}, ${String.format(Locale.US, "%.6f", tLat)}]")
            sb.appendLine("      },")
            sb.appendLine("      \"properties\": {")
            sb.appendLine("        \"type\": \"TARGET_PIN\",")
            sb.appendLine("        \"name\": \"${t.name}\",")
            sb.appendLine("        \"category\": \"${t.category.name}\",")
            sb.appendLine("        \"estimatedDepthMeters\": ${String.format(Locale.US, "%.2f", t.estimatedDepthMeters)},")
            sb.appendLine("        \"signalStrength\": ${String.format(Locale.US, "%.2f", t.signalStrength)},")
            sb.appendLine("        \"notes\": \"${t.notes}\"")
            sb.appendLine("      }")
            sb.append("    }")
        }

        sb.appendLine()
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * Generates a comprehensive, publishable geophysical engineering survey report.
     */
    fun generateTechnicalReport(
        project: ScanProject,
        grid: MeasurementGrid,
        anomalies: List<DetectedAnomaly>,
        targets: List<UserTarget>
    ): String {
        val stats = grid.getStats()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()

        sb.appendLine("================================================================")
        sb.appendLine("         GEOSCAN 3D - GEOPHYSICAL SURVEY TECHNICAL REPORT       ")
        sb.appendLine("================================================================")
        sb.appendLine("Project Identifier:   ${project.name}")
        sb.appendLine("Survey Location:      ${project.location}")
        sb.appendLine("Report Generated:     ${dateFormat.format(Date())}")
        sb.appendLine("Survey Mode:          ${project.scanMode.name}")
        sb.appendLine("Sensor Configuration: ${project.sensorType.name}")
        sb.appendLine("Simulated Dataset:    ${if (project.isSimulated) "YES (Synthetic Calibration)" else "NO (Field Physical Acquisition)"}")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("1. FIELD GEOMETRY & GRID PARAMETERS")
        sb.appendLine("  - Total Grid Matrix:     ${grid.cols} columns x ${grid.rows} rows")
        sb.appendLine("  - Total Data Points:     ${grid.cols * grid.rows}")
        sb.appendLine("  - Physical Width (X):    ${String.format(Locale.US, "%.2f", grid.widthMeters)} meters")
        sb.appendLine("  - Physical Length (Y):   ${String.format(Locale.US, "%.2f", grid.lengthMeters)} meters")
        sb.appendLine("  - Physical Survey Area:  ${String.format(Locale.US, "%.2f", grid.widthMeters * grid.lengthMeters)} m²")
        sb.appendLine("  - Line Spacing / Step:   ${grid.gridSpacingMeters} meters")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("2. STATISTICAL SIGNAL ANALYSIS")
        sb.appendLine("  - Minimum Reading:       ${String.format(Locale.US, "%.2f", stats.min)} µT")
        sb.appendLine("  - Maximum Peak:          ${String.format(Locale.US, "%.2f", stats.max)} µT")
        sb.appendLine("  - Dynamic Range (Span):  ${String.format(Locale.US, "%.2f", stats.range)} µT")
        sb.appendLine("  - Arithmetic Mean:       ${String.format(Locale.US, "%.2f", stats.average)} µT")
        sb.appendLine("  - Standard Deviation (σ):${String.format(Locale.US, "%.2f", stats.stdDev)} µT")
        sb.appendLine("  - Noise Floor Estimate:  ±${String.format(Locale.US, "%.2f", stats.stdDev * 0.4f)} µT")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("3. DETECTED GEOPHYSICAL ANOMALIES (${anomalies.size} Detected)")
        if (anomalies.isEmpty()) {
            sb.appendLine("  - No statistically significant anomalies exceeding baseline threshold.")
        } else {
            anomalies.forEachIndexed { i, a ->
                sb.appendLine("  [Anomaly #${i + 1}]")
                sb.appendLine("    • Signature Type:    ${a.type.label}")
                sb.appendLine("    • Center Position:   X = ${String.format(Locale.US, "%.2f", a.centerX)}m, Y = ${String.format(Locale.US, "%.2f", a.centerY)}m [Col ${a.gridX}, Row ${a.gridY}]")
                sb.appendLine("    • Peak Amplitude:    ${String.format(Locale.US, "%.2f", a.peakIntensity)} µT (Contrast: ${String.format(Locale.US, "%.2f", a.peakIntensity - stats.average)} µT)")
                sb.appendLine("    • Spatial Footprint: ${String.format(Locale.US, "%.2f", a.width)}m wide x ${String.format(Locale.US, "%.2f", a.length)}m long")
                sb.appendLine("    • Inverted Depth:    ~${String.format(Locale.US, "%.2f", a.estimatedRelativeDepth)} meters (HWHM Model)")
                sb.appendLine("    • Algorithmic Conf:  ${(a.confidenceScore * 100).toInt()}%")
                sb.appendLine("    • Interpretation:    ${a.description}")
            }
        }
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("4. USER TARGET REGISTRY & BOOKMARKS (${targets.size} Pinned)")
        if (targets.isEmpty()) {
            sb.appendLine("  - No manual targets bookmarked.")
        } else {
            targets.forEachIndexed { i, t ->
                sb.appendLine("  [Target #${i + 1}: ${t.name}]")
                sb.appendLine("    • Category:          ${t.category.label}")
                sb.appendLine("    • Coordinates:       X = ${String.format(Locale.US, "%.2f", t.posX)}m, Y = ${String.format(Locale.US, "%.2f", t.posY)}m [Col ${t.gridX}, Row ${t.gridY}]")
                sb.appendLine("    • Estimated Depth:   ${String.format(Locale.US, "%.2f", t.estimatedDepthMeters)} meters")
                sb.appendLine("    • Signal Amplitude:  ${String.format(Locale.US, "%.2f", t.signalStrength)} µT")
                sb.appendLine("    • Operator Notes:    ${t.notes.ifBlank { "None" }}")
            }
        }
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("5. SCIENTIFIC DISCLAIMER & METHODOLOGY")
        sb.appendLine("  Measurements represent sensor potential differences processed non-destructively.")
        sb.appendLine("  Inversions are calculated using Half-Width at Half-Maximum dipole models")
        sb.appendLine("  assuming homogeneous electromagnetic ground parameters. Actual excavation")
        sb.appendLine("  depths may vary based on soil mineral content and moisture saturation.")
        sb.appendLine("================================================================")
        return sb.toString()
    }

    /**
     * Dispatches an Android Share Intent with text content.
     */
    fun shareContent(context: Context, subject: String, content: String, mimeType: String = "text/plain") {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
            type = mimeType
        }
        val shareIntent = Intent.createChooser(sendIntent, subject)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    /**
     * Copies text to the system clipboard and notifies user.
     */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
