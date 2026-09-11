package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.core.model.MeasurementGrid
import com.example.core.model.ScanProject
import com.example.core.model.UserTarget
import com.example.data.db.GeoScanDatabase
import com.example.data.repository.ScanRepository
import com.example.ui.screens.LiveAcquisitionScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.ScanViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

sealed class Screen {
    object Projects : Screen()
    data class Viewer(val projectId: String) : Screen()
    object LiveAcquisition : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = GeoScanDatabase.getDatabase(this)
        val repository = ScanRepository(db.geoScanDao())

        setContent {
            MyApplicationTheme {
                val scope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Projects) }
                val projects by repository.allProjects.collectAsState(initial = emptyList())

                // Pre-seed the 5 demo scan datasets on initial launch
                LaunchedEffect(Unit) {
                    repository.seedDemoScansIfEmpty()
                }

                when (val screen = currentScreen) {
                    is Screen.Projects -> {
                        ProjectsScreen(
                            projects = projects,
                            onProjectSelected = { projId ->
                                currentScreen = Screen.Viewer(projId)
                            },
                            onNewLiveScan = {
                                currentScreen = Screen.LiveAcquisition
                            },
                            onCreateProject = { proj, grid ->
                                scope.launch {
                                    repository.saveProject(proj, grid)
                                    currentScreen = Screen.Viewer(proj.id)
                                }
                            },
                            onDeleteProject = { id ->
                                scope.launch {
                                    repository.deleteProject(id)
                                }
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
                            }
                        )
                    }

                    is Screen.Viewer -> {
                        var project by remember(screen.projectId) { mutableStateOf<ScanProject?>(null) }
                        var grid by remember(screen.projectId) { mutableStateOf<MeasurementGrid?>(null) }
                        val targets by repository.getTargets(screen.projectId).collectAsState(initial = emptyList())

                        LaunchedEffect(screen.projectId) {
                            project = repository.getProject(screen.projectId)
                            grid = repository.loadGridForProject(screen.projectId)
                        }

                        if (project != null && grid != null) {
                            ScanViewerScreen(
                                project = project!!,
                                initialGrid = grid!!,
                                targets = targets,
                                onBack = { currentScreen = Screen.Projects },
                                onSaveGrid = { updatedGrid ->
                                    scope.launch {
                                        repository.updateProcessedGrid(project!!.id, updatedGrid)
                                    }
                                },
                                onAddTarget = { newTarget ->
                                    scope.launch {
                                        repository.addTarget(newTarget)
                                    }
                                },
                                onDeleteTarget = { targetId ->
                                    scope.launch {
                                        repository.deleteTarget(targetId)
                                    }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = GeoCyan)
                            }
                        }
                    }

                    is Screen.LiveAcquisition -> {
                        LiveAcquisitionScreen(
                            onBack = { currentScreen = Screen.Projects },
                            onSaveScan = { proj, grid ->
                                scope.launch {
                                    repository.saveProject(proj, grid)
                                    currentScreen = Screen.Viewer(proj.id)
                                }
                            }
                        )
                    }

                    is Screen.Settings -> {
                        SettingsScreen(
                            onBack = { currentScreen = Screen.Projects }
                        )
                    }
                }
            }
        }
    }
}
