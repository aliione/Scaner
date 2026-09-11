package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LanguageManager
import com.example.core.i18n.LocalAppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.MeasurementGrid
import com.example.core.model.MeasurementPoint
import com.example.core.model.ScanMode
import com.example.core.model.ScanProject
import com.example.core.model.SensorType
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projects: List<ScanProject>,
    onProjectSelected: (String) -> Unit,
    onNewLiveScan: () -> Unit,
    onCreateProject: (ScanProject, MeasurementGrid) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current
    val isPersian = currentLanguage == AppLanguage.PERSIAN

    var filterType by remember { mutableStateOf("ALL") } // ALL, SIMULATED, FIELD
    var searchQuery by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    val filteredProjects = remember(projects, filterType, searchQuery) {
        val byType = when (filterType) {
            "SIMULATED" -> projects.filter { it.isSimulated }
            "FIELD" -> projects.filter { !it.isSimulated }
            else -> projects
        }
        if (searchQuery.isBlank()) {
            byType
        } else {
            val q = searchQuery.trim().lowercase()
            byType.filter { it.name.lowercase().contains(q) || it.location.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = GeoCyan,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Column {
                            Text(
                                strings.appName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                strings.appSubtitle,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Quick Language Toggle
                    IconButton(onClick = { LanguageManager.toggleLanguage() }) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GeoCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isPersian) "EN" else "فا",
                                color = GeoCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDarkBackground)
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Acquisition FAB
                FloatingActionButton(
                    onClick = onNewLiveScan,
                    containerColor = GeoAmber,
                    contentColor = Color.Black
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(strings.liveSurvey, fontWeight = FontWeight.Bold)
                    }
                }

                // New Project FAB
                FloatingActionButton(
                    onClick = { showNewProjectDialog = true },
                    containerColor = GeoCyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.newProject)
                }
            }
        },
        containerColor = SlateDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchPlaceholder, fontSize = 13.sp, color = TextMuted) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeoCyan,
                    unfocusedBorderColor = SlateCardBorder,
                    focusedContainerColor = SlateCardSurface,
                    unfocusedContainerColor = SlateCardSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Filter Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "ALL",
                    onClick = { filterType = "ALL" },
                    label = { Text("${strings.all} (${projects.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoCyan.copy(alpha = 0.2f),
                        selectedLabelColor = GeoCyan
                    )
                )
                FilterChip(
                    selected = filterType == "FIELD",
                    onClick = { filterType = "FIELD" },
                    label = { Text(strings.realHardware, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoGreenSignal.copy(alpha = 0.2f),
                        selectedLabelColor = GeoGreenSignal
                    )
                )
                FilterChip(
                    selected = filterType == "SIMULATED",
                    onClick = { filterType = "SIMULATED" },
                    label = { Text(strings.simulated, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoAmber.copy(alpha = 0.2f),
                        selectedLabelColor = GeoAmber
                    )
                )
            }

            // Project List
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.height(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(strings.noProjectsFound, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            strings.noProjectsSubtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProjects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectSelected(project.id) },
                            onDelete = { projectToDelete = project.id }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { targetId ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(strings.confirmDeleteProject, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmDeleteMessage, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(targetId)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SlateCardSurface
        )
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { newProj, newGrid ->
                onCreateProject(newProj, newGrid)
                showNewProjectDialog = false
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: ScanProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(project.lastModified) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(project.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (project.isSimulated) GeoAmber else GeoCyan,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = project.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                if (project.isSimulated) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeoAmber.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("DEMO", color = GeoAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.height(14.dp)
                )
                Text(
                    text = project.location,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateElevated)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val strings = LocalAppStrings.current
                ProjectStat(strings.gridDimension, "${project.cols} × ${project.rows}")
                ProjectStat(strings.areaMeters, "${project.widthMeters}m × ${project.lengthMeters}m")
                ProjectStat("سنسور", project.sensorType.label.take(12))
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val strings = LocalAppStrings.current
                Text(dateStr, color = TextMuted, fontSize = 11.sp)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ProjectStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (ScanProject, MeasurementGrid) -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current
    val isPersian = currentLanguage == AppLanguage.PERSIAN

    var name by remember { mutableStateOf(if (isPersian) "اسکن میدانی جدید" else "New Field Survey") }
    var location by remember { mutableStateOf(if (isPersian) "منطقه اسکن ۱" else "Sector Grid A1") }
    var rows by remember { mutableStateOf("20") }
    var cols by remember { mutableStateOf("20") }
    var spacing by remember { mutableStateOf("0.5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.createProjectTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.projectNameLabel) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoCyan,
                        focusedLabelColor = GeoCyan
                    )
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(strings.locationLabel) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoCyan,
                        focusedLabelColor = GeoCyan
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cols,
                        onValueChange = { cols = it },
                        label = { Text(strings.gridColsLabel) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        )
                    )
                    OutlinedTextField(
                        value = rows,
                        onValueChange = { rows = it },
                        label = { Text(strings.gridRowsLabel) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        )
                    )
                }
                OutlinedTextField(
                    value = spacing,
                    onValueChange = { spacing = it },
                    label = { Text(strings.stepSpacingLabel) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoCyan,
                        focusedLabelColor = GeoCyan
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = rows.toIntOrNull() ?: 20
                    val c = cols.toIntOrNull() ?: 20
                    val sp = spacing.toFloatOrNull() ?: 0.5f
                    val w = c * sp
                    val l = r * sp

                    val id = UUID.randomUUID().toString()
                    val proj = ScanProject(
                        id = id,
                        name = name,
                        location = location,
                        rows = r,
                        cols = c,
                        widthMeters = w,
                        lengthMeters = l,
                        gridSpacingMeters = sp,
                        isSimulated = false
                    )

                    val pts = mutableListOf<MeasurementPoint>()
                    var idx = 0
                    for (row in 0 until r) {
                        for (col in 0 until c) {
                            pts.add(
                                MeasurementPoint(
                                    pointId = idx++,
                                    gridX = col,
                                    gridY = row,
                                    posX = (col + 0.5f) * sp,
                                    posY = (row + 0.5f) * sp,
                                    rawValue = 0f,
                                    processedValue = 0f,
                                    isMissing = true
                                )
                            )
                        }
                    }

                    val grid = MeasurementGrid(
                        rows = r,
                        cols = c,
                        widthMeters = w,
                        lengthMeters = l,
                        gridSpacingMeters = sp,
                        points = pts
                    )
                    onCreate(proj, grid)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeoCyan, contentColor = Color.Black)
            ) {
                Text(strings.createAndLaunch, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = TextMuted)
            }
        },
        containerColor = SlateCardSurface
    )
}
