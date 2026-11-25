package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.ProjectEntity
import com.example.data.model.ProjectType
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onOpenProject: (ProjectEntity) -> Unit,
    onBuildProject: (ProjectEntity) -> Unit,
    onExportZip: (ProjectEntity) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onCreateProjectClick: () -> Unit,
    onImportZipClick: () -> Unit,
    onImportGitHubClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    val filtered = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateProjectClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("projects_new_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", maxLines = 1)
            }
            OutlinedButton(
                onClick = onImportZipClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("projects_import_zip_button")
            ) {
                Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("ZIP", maxLines = 1)
            }
            OutlinedButton(
                onClick = onImportGitHubClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("projects_import_github_button")
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("GitHub", maxLines = 1)
            }
        }

        if (projects.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search projects or package IDs...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("projects_search_input")
            )
            Spacer(Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    tint = CompilerBlue,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (projects.isEmpty()) "No Android Projects Yet" else "No Matching Projects",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Create a Kotlin/Java/Web project or import an existing Android Studio ZIP or GitHub repository.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { project ->
                    val typeEnum = runCatching { ProjectType.valueOf(project.projectType) }
                        .getOrDefault(ProjectType.IMPORTED)

                    StudioCard(
                        onClick = { onOpenProject(project) },
                        modifier = Modifier.testTag("project_card_${project.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusBadge(text = typeEnum.badge, color = CompilerBlue)
                                    StatusBadge(
                                        text = when (project.sourceOrigin) {
                                            "ZIP_IMPORT" -> "ZIP"
                                            "GITHUB_IMPORT" -> "GITHUB"
                                            else -> "TEMPLATE"
                                        },
                                        color = GradleAmber
                                    )
                                    if (project.errorCount > 0) {
                                        StatusBadge(text = "${project.errorCount} ERR", color = ErrorCoral)
                                    } else {
                                        StatusBadge(text = "VALID", color = AndroidEmerald)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = project.packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${project.fileCount} files • Gradle: ${if (project.hasGradle) "Yes" else "Missing"} • Manifest: ${if (project.hasManifest) "Yes" else "Missing"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onOpenProject(project) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_project_${project.id}")
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open IDE")
                            }
                            OutlinedButton(
                                onClick = { onBuildProject(project) },
                                modifier = Modifier.testTag("build_project_${project.id}")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Build")
                            }
                            IconButton(
                                onClick = { onExportZip(project) },
                                modifier = Modifier.testTag("export_zip_${project.id}")
                            ) {
                                Icon(Icons.Rounded.Archive, contentDescription = "Export ZIP")
                            }
                            IconButton(
                                onClick = { projectToDelete = project },
                                modifier = Modifier.testTag("delete_project_${project.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete project", tint = ErrorCoral)
                            }
                        }
                    }
                }
            }
        }
    }

    projectToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project?") },
            text = { Text("This permanently deletes '${target.name}' and all its workspace files from device storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target)
                        projectToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
