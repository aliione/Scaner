package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val operatorName: String,
    val dateCreated: Long,
    val lastModified: Long,
    val deviceName: String,
    val sensorType: String,
    val scanMode: String,
    val rows: Int,
    val cols: Int,
    val widthMeters: Float,
    val lengthMeters: Float,
    val gridSpacingMeters: Float,
    val soilProfileName: String,
    val coordinateSystem: String,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAltitude: Double?,
    val gpsAccuracyMeters: Float?,
    val notes: String,
    val isSimulated: Boolean,
    val isFavorite: Boolean
)

@Entity(tableName = "scan_data")
data class ScanDataEntity(
    @PrimaryKey val projectId: String,
    val rows: Int,
    val cols: Int,
    val widthMeters: Float,
    val lengthMeters: Float,
    val gridSpacingMeters: Float,
    val rawValuesJson: String, // Comma or semicolon-separated float string for high speed
    val processedValuesJson: String,
    val lastProcessingOperations: String = ""
)

@Entity(tableName = "user_targets")
data class TargetEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val posX: Float,
    val posY: Float,
    val gridX: Int,
    val gridY: Int,
    val estimatedDepthMeters: Float,
    val estimatedDiameterMeters: Float,
    val signalStrength: Float,
    val category: String,
    val confidence: Float,
    val notes: String,
    val timestamp: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val gridX: Int,
    val gridY: Int,
    val posX: Float,
    val posY: Float,
    val value: Float,
    val timestamp: Long
)
