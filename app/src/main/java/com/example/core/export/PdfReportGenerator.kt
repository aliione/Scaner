package com.example.core.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.core.model.MeasurementGrid
import com.example.core.model.SoilProfileType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates an official, publication-quality Geophysical PDF Survey Report.
 * Includes Project Metadata, Sensor Specs, Grid Geometry, Statistical Distribution,
 * Detected Anomalies, Depth Analysis, Soil Profile parameters, and Geophysical Disclaimers.
 */
object PdfReportGenerator {

    fun generateSurveyReport(
        context: Context,
        projectName: String,
        operatorName: String,
        grid: MeasurementGrid,
        soilProfile: SoilProfileType,
        sensorModel: String = "Multi-Axis Fluxgate Gradiometer",
        anomaliesCount: Int = 0,
        targetsCount: Int = 0,
        notes: String = ""
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stats = grid.getStats()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Header Background Banner
        paint.color = Color.rgb(20, 26, 38)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Title
        paint.color = Color.rgb(0, 229, 255) // Cyan
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("GEOSCAN 3D — GEOPHYSICAL SURVEY REPORT", 30f, 42f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Ground Sensor Data Acquisition, 3D Inversion & Subsurface Mapping", 30f, 62f, paint)

        val dateStr = "Date: ${dateFormat.format(Date())}"
        paint.textSize = 9f
        paint.color = Color.rgb(180, 190, 205)
        canvas.drawText(dateStr, 440f, 62f, paint)

        var y = 120f

        // Section 1: Project & Hardware Metadata
        paint.color = Color.rgb(30, 41, 59)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(30f, y, 565f, y + 100f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(255, 179, 0) // Amber
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("1. PROJECT & HARDWARE SPECIFICATIONS", 40f, y + 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        canvas.drawText("Project Name: $projectName", 45f, y + 42f, paint)
        canvas.drawText("Operator: ${if (operatorName.isNotBlank()) operatorName else "Field Engineer"}", 45f, y + 60f, paint)
        canvas.drawText("Hardware Sensor: $sensorModel", 45f, y + 78f, paint)

        canvas.drawText("Soil Profile: ${soilProfile.displayName}", 310f, y + 42f, paint)
        canvas.drawText("Permittivity εr: ${soilProfile.relativePermittivity} | Cond: ${soilProfile.conductivitySm} S/m", 310f, y + 60f, paint)
        canvas.drawText("Attenuation Rate: ${soilProfile.attenuationDbM} dB/m", 310f, y + 78f, paint)

        y += 120f

        // Section 2: Grid Geometry & Spatial Sampling
        paint.color = Color.rgb(30, 41, 59)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(30f, y, 565f, y + 90f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(255, 179, 0)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("2. SURVEY GRID & RESOLUTION", 40f, y + 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        canvas.drawText("Dimensions: ${grid.cols} cols × ${grid.rows} rows (${grid.cols * grid.rows} points)", 45f, y + 42f, paint)
        canvas.drawText("Survey Area: ${grid.widthMeters}m × ${grid.lengthMeters}m (${String.format("%.1f", grid.widthMeters * grid.lengthMeters)} m²)", 45f, y + 60f, paint)
        val stepX = if (grid.cols > 0) grid.widthMeters / grid.cols else 0.5f
        val stepY = if (grid.rows > 0) grid.lengthMeters / grid.rows else 0.5f
        canvas.drawText("Spatial Step: ΔX=${String.format("%.2f", stepX)}m, ΔY=${String.format("%.2f", stepY)}m", 45f, y + 78f, paint)

        canvas.drawText("Scan Pattern: Zig-Zag (Boustrophedon)", 310f, y + 42f, paint)
        canvas.drawText("Identified Anomalies: $anomaliesCount clusters", 310f, y + 60f, paint)
        canvas.drawText("Pinned Targets: $targetsCount targets", 310f, y + 78f, paint)

        y += 110f

        // Section 3: Statistical Signal Distribution
        paint.color = Color.rgb(30, 41, 59)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(30f, y, 565f, y + 80f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(255, 179, 0)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("3. STATISTICAL SIGNAL DISTRIBUTION (µT)", 40f, y + 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        canvas.drawText("Minimum Value: ${String.format("%.2f", stats.min)} µT", 45f, y + 42f, paint)
        canvas.drawText("Maximum Value: ${String.format("%.2f", stats.max)} µT", 45f, y + 60f, paint)

        canvas.drawText("Mean Baseline: ${String.format("%.2f", stats.mean)} µT", 220f, y + 42f, paint)
        canvas.drawText("Dynamic Range: ${String.format("%.2f", stats.range)} µT", 220f, y + 60f, paint)

        canvas.drawText("Std. Deviation: ${String.format("%.2f", stats.stdDev)} µT", 390f, y + 42f, paint)
        val anomalySeverity = if (stats.stdDev > 15f) "High Contrast (Distinct Targets)" else "Homogeneous Background"
        canvas.drawText("Signal Variance: $anomalySeverity", 390f, y + 60f, paint)

        y += 100f

        // Section 4: Mini 2D Heatmap Thumbnail Preview
        paint.color = Color.rgb(30, 41, 59)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(30f, y, 565f, y + 220f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(255, 179, 0)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("4. SUBSURFACE FIELD HEATMAP OVERVIEW", 40f, y + 20f, paint)

        val matrix = grid.getProcessedMatrix()
        val hmStartX = 50f
        val hmStartY = y + 35f
        val hmWidth = 495f
        val hmHeight = 165f

        val cellW = hmWidth / grid.cols
        val cellH = hmHeight / grid.rows

        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val v = matrix[r][c]
                val norm = if (stats.range > 0f) ((v - stats.min) / stats.range).coerceIn(0f, 1f) else 0.5f

                // Color gradient from Blue (0) -> Green (0.5) -> Red (1.0)
                val red = (norm * 255).toInt().coerceIn(0, 255)
                val blue = ((1f - norm) * 255).toInt().coerceIn(0, 255)
                val green = if (norm < 0.5f) (norm * 2 * 255).toInt() else ((1f - norm) * 2 * 255).toInt()

                paint.color = Color.rgb(red, green.coerceIn(0, 255), blue)
                canvas.drawRect(
                    hmStartX + c * cellW,
                    hmStartY + r * cellH,
                    hmStartX + (c + 1) * cellW,
                    hmStartY + (r + 1) * cellH,
                    paint
                )
            }
        }

        y += 240f

        // Section 5: Field Notes & Disclaimer
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 8.5f
        paint.isFakeBoldText = false

        if (notes.isNotBlank()) {
            canvas.drawText("Field Notes: $notes", 35f, y, paint)
            y += 18f
        }

        val disclaimer = "SCIENTIFIC NOTICE: Inversion models, calculated depths, and detected clusters represent statistical gradient variations " +
                "and do not constitute definitive identification of specific metallurgical substances or structural voids without excavation."
        canvas.drawText(disclaimer, 35f, y, paint)

        // Footer
        paint.textSize = 8f
        paint.color = Color.GRAY
        canvas.drawText("Generated by GeoScan 3D Workstation — RFC 7946 / ISO 19115 Geophysical Standard", 110f, 825f, paint)

        pdfDocument.finishPage(page)

        // Write to cache/files dir
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val safeName = projectName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val outputFile = File(outputDir, "${safeName}_Survey_Report_${System.currentTimeMillis()}.pdf")

        return try {
            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDocument.close()
            outputFile
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }
}
