package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.db.BuildRecordEntity
import com.example.data.local.db.ProjectEntity
import com.example.data.model.BuildStatus
import com.example.data.model.StudioSettings
import com.example.ui.components.ActionTile
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber

@Composable
fun DashboardScreen(
    projects: List<ProjectEntity>,
    activeProject: ProjectEntity?,
    recentBuilds: List<BuildRecordEntity>,
    settings: StudioSettings,
    onCreateProjectClick: () -> Unit,
    onImportZipClick: () -> Unit,
    onImportGitHubClick: () -> Unit,
    onBuildApkClick: () -> Unit,
    onOpenMyProjects: () -> Unit,
    onOpenBuildHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProjectWorkspace: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRunnerConfigured = (settings.githubToken.isNotBlank() && settings.githubRepo.isNotBlank()) ||
        settings.remoteServerUrl.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Studio Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1795609643391),
                        contentDescription = stringResource(id = R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xF00B0E14),
                                        Color(0xCC0B0E14),
                                        Color(0x660B0E14)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusBadge(
                                text = if (hasRunnerConfigured) "CLOUD BUILD READY" else "VALIDATION MODE",
                                color = if (hasRunnerConfigured) AndroidEmerald else GradleAmber
                            )
                            StatusBadge(
                                text = "${projects.size} PROJECTS",
                                color = CompilerBlue
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = if (hasRunnerConfigured) {
                                "Edit Kotlin, Java, XML & Gradle projects and compile APKs via your configured build runner."
                            } else {
                                "Import ZIP or GitHub projects, edit & validate manifests, and connect a Cloud Runner in Settings to compile APKs."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(
                                onClick = onCreateProjectClick,
                                modifier = Modifier.testTag("hero_create_project_button")
                            ) {
                                Icon(Icons.Rounded.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Project")
                            }
                            if (!hasRunnerConfigured) {
                                OutlinedButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("hero_configure_runner_button")
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Configure Runner")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onBuildApkClick,
                                    modifier = Modifier.testTag("hero_build_apk_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Build APK")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Workspace Card
        if (activeProject != null) {
            item {
                StudioCard(
                    onClick = { onOpenProjectWorkspace(activeProject) },
                    borderColor = AndroidEmerald.copy(alpha = 0.45f),
                    modifier = Modifier.testTag("active_project_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE WORKSPACE",
                                style = MaterialTheme.typography.labelSmall,
                                color = AndroidEmerald
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = activeProject.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeProject.packageName} • ${activeProject.fileCount} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        StatusBadge(
                            text = if (activeProject.errorCount == 0) "HEALTHY" else "${activeProject.errorCount} ERRORS",
                            color = if (activeProject.errorCount == 0) AndroidEmerald else ErrorCoral
                        )
                    }
                }
            }
        }

        // Quick Actions Bento Grid
        item {
            Text(
                text = "Studio Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 700.dp) 3 else 2
                val items = listOf(
                    DashboardAction(
                        title = stringResource(id = R.string.action_create_project),
                        subtitle = "Kotlin, Java or Web",
                        icon = Icons.Rounded.AddBox,
                        accent = AndroidEmerald,
                        tag = "dash_create_project",
                        onClick = onCreateProjectClick
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_import_zip),
                        subtitle = "Extract Android ZIP",
                        icon = Icons.Rounded.FolderZip,
                        accent = CompilerBlue,
                        tag = "dash_import_zip",
                        onClick = onImportZipClick
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_import_github),
                        subtitle = "Clone GitHub repo",
                        icon = Icons.Rounded.CloudDownload,
                        accent = CompilerBlue,
                        tag = "dash_import_github",
                        onClick = onImportGitHubClick
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_build_apk),
                        subtitle = "Debug or Release",
                        icon = Icons.Rounded.Build,
                        accent = GradleAmber,
                        tag = "dash_build_apk",
                        onClick = onBuildApkClick
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_my_projects),
                        subtitle = "${projects.size} saved workspaces",
                        icon = Icons.Rounded.FolderOpen,
                        accent = AndroidEmerald,
                        tag = "dash_my_projects",
                        onClick = onOpenMyProjects
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_build_history),
                        subtitle = "${recentBuilds.size} recorded runs",
                        icon = Icons.Rounded.History,
                        accent = GradleAmber,
                        tag = "dash_build_history",
                        onClick = onOpenBuildHistory
                    ),
                    DashboardAction(
                        title = stringResource(id = R.string.action_settings),
                        subtitle = "Runners & SDK config",
                        icon = Icons.Rounded.Settings,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                        tag = "dash_settings",
                        onClick = onOpenSettings
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.chunked(columns).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { item ->
                                ActionTile(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    icon = item.icon,
                                    accentColor = item.accent,
                                    testTag = item.tag,
                                    onClick = item.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size < columns) {
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Builds Summary
        if (recentBuilds.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Builds",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(recentBuilds.take(3), key = { it.id }) { build ->
                val statusEnum = runCatching { BuildStatus.valueOf(build.status) }.getOrDefault(BuildStatus.BUILD_FAILED)
                val badgeColor = when (statusEnum) {
                    BuildStatus.SUCCESS -> AndroidEmerald
                    BuildStatus.RUNNING -> CompilerBlue
                    BuildStatus.RUNNER_NOT_CONFIGURED -> GradleAmber
                    else -> ErrorCoral
                }
                StudioCard(onClick = onOpenBuildHistory) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${build.projectName} (${build.variant})",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = build.summaryMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(text = statusEnum.label, color = badgeColor)
                    }
                }
            }
        }
    }
}

private data class DashboardAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val tag: String,
    val onClick: () -> Unit
)
