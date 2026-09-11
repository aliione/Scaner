package com.example.data.repository

import com.example.core.model.DistanceUnit
import com.example.core.model.MeasurementGrid
import com.example.core.model.MeasurementPoint
import com.example.core.model.PointBookmark
import com.example.core.model.ScanMode
import com.example.core.model.ScanProject
import com.example.core.model.SensorType
import com.example.core.model.TargetCategory
import com.example.core.model.UserTarget
import com.example.data.db.BookmarkEntity
import com.example.data.db.GeoScanDao
import com.example.data.db.ProjectEntity
import com.example.data.db.ScanDataEntity
import com.example.data.db.TargetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

class ScanRepository(private val dao: GeoScanDao) {

    val allProjects: Flow<List<ScanProject>> = dao.getAllProjects().map { list ->
        list.map { entityToProject(it) }
    }

    suspend fun getProject(id: String): ScanProject? = withContext(Dispatchers.IO) {
        dao.getProjectById(id)?.let { entityToProject(it) }
    }

    suspend fun saveProject(project: ScanProject, grid: MeasurementGrid) = withContext(Dispatchers.IO) {
        dao.insertProject(projectToEntity(project))

        val rawSb = StringBuilder()
        val procSb = StringBuilder()
        grid.points.forEachIndexed { i, pt ->
            if (i > 0) {
                rawSb.append(",")
                procSb.append(",")
            }
            rawSb.append(pt.rawValue)
            procSb.append(pt.processedValue)
        }

        dao.insertScanData(
            ScanDataEntity(
                projectId = project.id,
                rows = grid.rows,
                cols = grid.cols,
                widthMeters = grid.widthMeters,
                lengthMeters = grid.lengthMeters,
                gridSpacingMeters = grid.gridSpacingMeters,
                rawValuesJson = rawSb.toString(),
                processedValuesJson = procSb.toString()
            )
        )
    }

    suspend fun loadGridForProject(projectId: String): MeasurementGrid? = withContext(Dispatchers.IO) {
        val data = dao.getScanData(projectId) ?: return@withContext null
        val rawTokens = data.rawValuesJson.split(",")
        val procTokens = data.processedValuesJson.split(",")

        val points = mutableListOf<MeasurementPoint>()
        var idx = 0
        val cellW = data.widthMeters / data.cols.toFloat()
        val cellL = data.lengthMeters / data.rows.toFloat()

        for (r in 0 until data.rows) {
            for (c in 0 until data.cols) {
                val rawVal = rawTokens.getOrNull(idx)?.toFloatOrNull() ?: 0f
                val procVal = procTokens.getOrNull(idx)?.toFloatOrNull() ?: rawVal
                points.add(
                    MeasurementPoint(
                        pointId = idx,
                        gridX = c,
                        gridY = r,
                        posX = c * cellW + cellW / 2f,
                        posY = r * cellL + cellL / 2f,
                        rawValue = rawVal,
                        processedValue = procVal,
                        scanLine = r,
                        sampleIndex = c
                    )
                )
                idx++
            }
        }

        MeasurementGrid(
            rows = data.rows,
            cols = data.cols,
            widthMeters = data.widthMeters,
            lengthMeters = data.lengthMeters,
            gridSpacingMeters = data.gridSpacingMeters,
            points = points
        )
    }

    suspend fun updateProcessedGrid(projectId: String, grid: MeasurementGrid) = withContext(Dispatchers.IO) {
        val procSb = StringBuilder()
        grid.points.forEachIndexed { i, pt ->
            if (i > 0) procSb.append(",")
            procSb.append(pt.processedValue)
        }
        val existing = dao.getScanData(projectId)
        if (existing != null) {
            dao.insertScanData(existing.copy(processedValuesJson = procSb.toString()))
        }
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        dao.deleteProject(id)
        dao.deleteScanData(id)
    }

    // Targets & Bookmarks
    fun getTargets(projectId: String): Flow<List<UserTarget>> =
        dao.getTargetsForProject(projectId).map { list ->
            list.map {
                UserTarget(
                    id = it.id,
                    projectId = it.projectId,
                    name = it.name,
                    posX = it.posX,
                    posY = it.posY,
                    gridX = it.gridX,
                    gridY = it.gridY,
                    estimatedDepthMeters = it.estimatedDepthMeters,
                    estimatedDiameterMeters = it.estimatedDiameterMeters,
                    signalStrength = it.signalStrength,
                    category = TargetCategory.valueOf(it.category),
                    confidence = it.confidence,
                    notes = it.notes,
                    timestamp = it.timestamp
                )
            }
        }

    suspend fun addTarget(target: UserTarget) = withContext(Dispatchers.IO) {
        dao.insertTarget(
            TargetEntity(
                id = target.id,
                projectId = target.projectId,
                name = target.name,
                posX = target.posX,
                posY = target.posY,
                gridX = target.gridX,
                gridY = target.gridY,
                estimatedDepthMeters = target.estimatedDepthMeters,
                estimatedDiameterMeters = target.estimatedDiameterMeters,
                signalStrength = target.signalStrength,
                category = target.category.name,
                confidence = target.confidence,
                notes = target.notes,
                timestamp = target.timestamp
            )
        )
    }

    suspend fun deleteTarget(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTarget(id)
    }

    fun getBookmarks(projectId: String): Flow<List<PointBookmark>> =
        dao.getBookmarksForProject(projectId).map { list ->
            list.map {
                PointBookmark(
                    id = it.id,
                    projectId = it.projectId,
                    title = it.title,
                    description = it.description,
                    gridX = it.gridX,
                    gridY = it.gridY,
                    posX = it.posX,
                    posY = it.posY,
                    value = it.value,
                    timestamp = it.timestamp
                )
            }
        }

    suspend fun addBookmark(bookmark: PointBookmark) = withContext(Dispatchers.IO) {
        dao.insertBookmark(
            BookmarkEntity(
                id = bookmark.id,
                projectId = bookmark.projectId,
                title = bookmark.title,
                description = bookmark.description,
                gridX = bookmark.gridX,
                gridY = bookmark.gridY,
                posX = bookmark.posX,
                posY = bookmark.posY,
                value = bookmark.value,
                timestamp = bookmark.timestamp
            )
        )
    }

    suspend fun deleteBookmark(id: String) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(id)
    }

    /**
     * Seeds initial demonstration datasets if the database is currently empty.
     */
    suspend fun seedDemoScansIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllProjects().first()
        if (existing.isNotEmpty()) return@withContext

        // Demo 1: Single Prominent Anomaly
        val p1 = ScanProject(
            id = "demo_01_single",
            name = "Demo 01: Single Anomaly",
            location = "Archaeological Test Site Alpha",
            notes = "Standard single localized magnetic response. Ideal for initial baseline testing.",
            rows = 24,
            cols = 24,
            widthMeters = 12f,
            lengthMeters = 12f,
            gridSpacingMeters = 0.5f,
            isSimulated = true
        )
        val g1 = createSyntheticGrid(p1.rows, p1.cols, p1.widthMeters, p1.lengthMeters) { normX, normY ->
            val distSq = (normX - 0.2f) * (normX - 0.2f) + (normY + 0.1f) * (normY + 0.1f)
            85f * exp(-distSq / 0.8f) + 12f
        }
        saveProject(p1, g1)

        // Demo 2: Multiple Anomalies
        val p2 = ScanProject(
            id = "demo_02_multiple",
            name = "Demo 02: Multiple Anomalies",
            location = "Survey Sector B-14",
            notes = "Two distinct adjacent anomaly centers with contrasting amplitudes.",
            rows = 26,
            cols = 26,
            widthMeters = 13f,
            lengthMeters = 13f,
            gridSpacingMeters = 0.5f,
            isSimulated = true
        )
        val g2 = createSyntheticGrid(p2.rows, p2.cols, p2.widthMeters, p2.lengthMeters) { normX, normY ->
            val a1 = 70f * exp(-((normX + 1.2f) * (normX + 1.2f) + (normY - 0.8f) * (normY - 0.8f)) / 0.6f)
            val a2 = 55f * exp(-((normX - 1.1f) * (normX - 1.1f) + (normY + 1.0f) * (normY + 1.0f)) / 0.7f)
            a1 + a2 + 10f
        }
        saveProject(p2, g2)

        // Demo 3: High Noise Field
        val p3 = ScanProject(
            id = "demo_03_noise",
            name = "Demo 03: High Noise Field",
            location = "Mineralized Basalt Ground",
            notes = "Challenging mineralized ground. Test signal filters (Median, Gaussian, Background tilt).",
            rows = 20,
            cols = 20,
            widthMeters = 10f,
            lengthMeters = 10f,
            gridSpacingMeters = 0.5f,
            isSimulated = true
        )
        val g3 = createSyntheticGrid(p3.rows, p3.cols, p3.widthMeters, p3.lengthMeters, noiseAmp = 7.5f) { normX, normY ->
            val tilt = normX * 3.5f - normY * 2.0f
            val target = 35f * exp(-((normX - 0.4f) * (normX - 0.4f) + (normY - 0.2f) * (normY - 0.2f)) / 1.2f)
            target + tilt + 15f
        }
        saveProject(p3, g3)

        // Demo 4: Void / Negative Anomaly
        val p4 = ScanProject(
            id = "demo_04_cavity",
            name = "Demo 04: Cavity / Negative Anomaly",
            location = "Limestone Karst Terrain",
            notes = "Pronounced negative anomaly characteristic of void, chamber or unconsolidated cavity.",
            rows = 24,
            cols = 24,
            widthMeters = 12f,
            lengthMeters = 12f,
            gridSpacingMeters = 0.5f,
            isSimulated = true
        )
        val g4 = createSyntheticGrid(p4.rows, p4.cols, p4.widthMeters, p4.lengthMeters) { normX, normY ->
            val distSq = normX * normX + normY * normY
            val voidSignal = -72f * exp(-distSq / 1.1f)
            val rimHalo = 16f * exp(-((distSq - 2.5f) * (distSq - 2.5f)) / 2.0f)
            25f + voidSignal + rimHalo
        }
        saveProject(p4, g4)

        // Demo 5: Linear Trench / Foundation
        val p5 = ScanProject(
            id = "demo_05_linear",
            name = "Demo 05: Irregular Linear Feature",
            location = "Historical Settlement Trench",
            notes = "Elongated continuous anomaly simulating subterranean pipe, ditch or wall foundation.",
            rows = 24,
            cols = 24,
            widthMeters = 12f,
            lengthMeters = 12f,
            gridSpacingMeters = 0.5f,
            isSimulated = true
        )
        val g5 = createSyntheticGrid(p5.rows, p5.cols, p5.widthMeters, p5.lengthMeters) { normX, normY ->
            val lineDist = kotlin.math.abs(normY - 0.5f * normX) / 1.118f
            val lineSignal = 58f * exp(-(lineDist * lineDist) / 0.4f)
            lineSignal + 10f
        }
        saveProject(p5, g5)
    }

    private fun createSyntheticGrid(
        rows: Int,
        cols: Int,
        widthMeters: Float,
        lengthMeters: Float,
        noiseAmp: Float = 2.0f,
        generator: (Float, Float) -> Float
    ): MeasurementGrid {
        val points = mutableListOf<MeasurementPoint>()
        val rnd = Random(42)
        var id = 0
        val cellW = widthMeters / cols.toFloat()
        val cellL = lengthMeters / rows.toFloat()

        for (r in 0 until rows) {
            val normY = (r.toFloat() / (rows - 1).toFloat() - 0.5f) * 4f
            for (c in 0 until cols) {
                val normX = (c.toFloat() / (cols - 1).toFloat() - 0.5f) * 4f
                val base = generator(normX, normY)
                val noise = (rnd.nextGaussian() * noiseAmp).toFloat()
                val finalVal = base + noise
                points.add(
                    MeasurementPoint(
                        pointId = id,
                        gridX = c,
                        gridY = r,
                        posX = c * cellW + cellW / 2f,
                        posY = r * cellL + cellL / 2f,
                        rawValue = finalVal,
                        processedValue = finalVal,
                        scanLine = r,
                        sampleIndex = c
                    )
                )
                id++
            }
        }
        return MeasurementGrid(
            rows = rows,
            cols = cols,
            widthMeters = widthMeters,
            lengthMeters = lengthMeters,
            gridSpacingMeters = widthMeters / cols.toFloat(),
            points = points
        )
    }

    private fun projectToEntity(p: ScanProject): ProjectEntity = ProjectEntity(
        id = p.id,
        name = p.name,
        location = p.location,
        operatorName = p.operatorName,
        dateCreated = p.dateCreated,
        lastModified = p.lastModified,
        deviceName = p.deviceName,
        sensorType = p.sensorType.name,
        scanMode = p.scanMode.name,
        rows = p.rows,
        cols = p.cols,
        widthMeters = p.widthMeters,
        lengthMeters = p.lengthMeters,
        gridSpacingMeters = p.gridSpacingMeters,
        soilProfileName = p.soilProfileName,
        coordinateSystem = p.coordinateSystem,
        gpsLatitude = p.gpsLatitude,
        gpsLongitude = p.gpsLongitude,
        gpsAltitude = p.gpsAltitude,
        gpsAccuracyMeters = p.gpsAccuracyMeters,
        notes = p.notes,
        isSimulated = p.isSimulated,
        isFavorite = p.isFavorite
    )

    private fun entityToProject(e: ProjectEntity): ScanProject = ScanProject(
        id = e.id,
        name = e.name,
        location = e.location,
        operatorName = e.operatorName,
        dateCreated = e.dateCreated,
        lastModified = e.lastModified,
        deviceName = e.deviceName,
        sensorType = try { SensorType.valueOf(e.sensorType) } catch (_: Exception) { SensorType.GRADIOMETER },
        scanMode = try { ScanMode.valueOf(e.scanMode) } catch (_: Exception) { ScanMode.RECTANGULAR_GRID },
        rows = e.rows,
        cols = e.cols,
        widthMeters = e.widthMeters,
        lengthMeters = e.lengthMeters,
        gridSpacingMeters = e.gridSpacingMeters,
        soilProfileName = e.soilProfileName,
        coordinateSystem = e.coordinateSystem,
        gpsLatitude = e.gpsLatitude,
        gpsLongitude = e.gpsLongitude,
        gpsAltitude = e.gpsAltitude,
        gpsAccuracyMeters = e.gpsAccuracyMeters,
        notes = e.notes,
        isSimulated = e.isSimulated,
        isFavorite = e.isFavorite
    )
}
