package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.ProjectEntity
import com.example.data.model.BuildEngineType
import com.example.data.model.BuildStatus
import com.example.data.model.BuildVariant
import com.example.data.model.LogLevel
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber
import com.example.ui.theme.codeTextStyle
import com.example.ui.viewmodel.BuildConsoleState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildConsoleScreen(
    projects: List<ProjectEntity>,
    state: BuildConsoleState,
    onSelectProject: (String) -> Unit,
    onSelectVariant: (BuildVariant) -> Unit,
    onSelectEngine: (BuildEngineType) -> Unit,
    onStartBuild: () -> Unit,
    onCancelBuild: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedProject = projects.firstOrNull { it.id == state.selectedProjectId } ?: projects.firstOrNull()
    var showProjectDropdown by remember { mutableStateOf(false) }
    val consoleListState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            consoleListState.animateScrollToItem(state.logs.lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StudioCard {
                Text("APK Build Configuration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                Box {
                    OutlinedButton(
                        onClick = { showProjectDropdown = true },
                        enabled = projects.isNotEmpty() && !state.isBuilding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("build_project_selector")
                    ) {
                        Text(
                            text = selectedProject?.name ?: "No projects available — create or import one",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = showProjectDropdown,
                        onDismissRequest = { showProjectDropdown = false }
                    ) {
                        projects.forEach { proj ->
                            DropdownMenuItem(
                                text = { Text("${proj.name} (${proj.packageName})") },
                                onClick = {
                                    onSelectProject(proj.id)
                                    showProjectDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Build Variant", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildVariant.entries.forEach { variant ->
                        FilterChip(
                            selected = state.selectedVariant == variant,
                            onClick = { if (!state.isBuilding) onSelectVariant(variant) },
                            label = { Text(variant.label) },
                            modifier = Modifier.testTag("variant_chip_${variant.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Build Environment", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BuildEngineType.entries.forEach { engine ->
                        FilterChip(
                            selected = state.selectedEngine == engine,
                            onClick = { if (!state.isBuilding) onSelectEngine(engine) },
                            label = { Text(engine.label) },
                            modifier = Modifier.testTag("engine_chip_${engine.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isBuilding) {
                        Button(
                            onClick = onCancelBuild,
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorCoral),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_build_button")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel Build")
                        }
                    } else {
                        Button(
                            onClick = onStartBuild,
                            enabled = selectedProject != null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_build_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run ${state.selectedVariant.gradleTask}")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("build_open_settings_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                if (state.isBuilding) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Status Banner
        state.status?.let { status ->
            item {
                val color = when (status) {
                    BuildStatus.SUCCESS -> AndroidEmerald
                    BuildStatus.RUNNING -> CompilerBlue
                    BuildStatus.RUNNER_NOT_CONFIGURED -> GradleAmber
                    else -> ErrorCoral
                }
                StudioCard(borderColor = color.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (status == BuildStatus.SUCCESS) Icons.Rounded.CheckCircle else Icons.Rounded.Build,
                                contentDescription = null,
                                tint = color
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Status: ${status.label}", style = MaterialTheme.typography.titleSmall, color = color)
                                if (status == BuildStatus.SUCCESS) {
                                    Text(
                                        "APK compiled and verified in APK Manager.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        if (status == BuildStatus.SUCCESS) {
                            Button(onClick = onOpenApks, modifier = Modifier.testTag("view_built_apk_button")) {
                                Text("Open APK")
                            }
                        } else if (status == BuildStatus.RUNNER_NOT_CONFIGURED) {
                            OutlinedButton(onClick = onOpenSettings) {
                                Text("Setup")
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Console Log Surface
        item {
            Text("Real-Time Build Console", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF070A0F))
                    .padding(12.dp)
                    .testTag("build_console_output")
            ) {
                if (state.logs.isEmpty()) {
                    Text(
                        text = "Ready. Select a project and press Run to start pre-flight validation and APK compilation.",
                        style = codeTextStyle(12),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        state = consoleListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.logs) { line ->
                            val lineColor = when (line.level) {
                                LogLevel.ERROR -> ErrorCoral
                                LogLevel.WARN -> GradleAmber
                                LogLevel.SUCCESS -> AndroidEmerald
                                LogLevel.TASK -> CompilerBlue
                                LogLevel.INFO -> Color(0xFFCBD5E1)
                            }
                            Text(
                                text = "[${line.timestamp}] ${line.message}",
                                style = codeTextStyle(12),
                                color = lineColor
                            )
                        }
                    }
                }
            }
        }

        // Build Error Advisor Solutions
        if (state.solutions.isNotEmpty()) {
            item {
                Text("Recommended Error Solutions", style = MaterialTheme.typography.titleMedium)
            }
            items(state.solutions) { sol ->
                StudioCard(borderColor = GradleAmber.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = GradleAmber)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(sol.title, style = MaterialTheme.typography.titleSmall, color = GradleAmber)
                            Spacer(Modifier.height(4.dp))
                            Text(sol.explanation, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            StatusBadge(text = sol.suggestedFix, color = CompilerBlue)
                        }
                    }
                }
            }
        }
    }
}
