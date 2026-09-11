package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoScanDao {

    // Projects
    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    // Scan Data
    @Query("SELECT * FROM scan_data WHERE projectId = :projectId LIMIT 1")
    suspend fun getScanData(projectId: String): ScanDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanData(scanData: ScanDataEntity)

    @Query("DELETE FROM scan_data WHERE projectId = :projectId")
    suspend fun deleteScanData(projectId: String)

    // Targets
    @Query("SELECT * FROM user_targets WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getTargetsForProject(projectId: String): Flow<List<TargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: TargetEntity)

    @Query("DELETE FROM user_targets WHERE id = :id")
    suspend fun deleteTarget(id: String)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getBookmarksForProject(projectId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)
}
