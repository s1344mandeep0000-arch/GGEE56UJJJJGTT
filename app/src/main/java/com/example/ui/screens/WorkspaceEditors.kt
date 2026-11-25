package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.data.model.FixActionType
import com.example.data.model.GradleConfigSummary
import com.example.data.model.IssueSeverity
import com.example.data.model.ManifestConfig
import com.example.data.model.MavenArtifactResult
import com.example.data.model.ProjectIssue
import com.example.engine.ManifestParser
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManifestEditorPane(
    manifestPath: String,
    manifestConfig: ManifestConfig?,
    onSaveManifest: (ManifestConfig) -> Unit,
    onOpenRawXml: () -> Unit
) {
    val cfg = manifestConfig ?: ManifestConfig(isValidXml = false, parseError = "Manifest not loaded")
    var appLabel by remember(cfg) { mutableStateOf(cfg.appLabel) }
    var cleartext by remember(cfg) { mutableStateOf(cfg.usesCleartextTraffic) }
    var selectedPermissions by remember(cfg) { mutableStateOf(cfg.permissions) }
    var customPermission by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            StudioCard(
                borderColor = if (cfg.isValidXml) AndroidEmerald.copy(alpha = 0.4f) else ErrorCoral
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AndroidManifest.xml Editor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = manifestPath.ifBlank { "Missing AndroidManifest.xml" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (manifestPath.isNotBlank()) {
                        OutlinedButton(
                            onClick = onOpenRawXml,
                            modifier = Modifier.testTag("open_raw_manifest_button")
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Raw XML")
                        }
                    }
                }
                if (!cfg.isValidXml && cfg.parseError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(cfg.parseError, style = MaterialTheme.typography.bodySmall, color = ErrorCoral)
                }
            }
        }

        item {
            StudioCard {
                Text("Application Attributes", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = appLabel,
                    onValueChange = { appLabel = it },
                    label = { Text("android:label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manifest_label_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("usesCleartextTraffic", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Allow HTTP traffic in addition to HTTPS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cleartext,
                        onCheckedChange = { cleartext = it },
                        modifier = Modifier.testTag("manifest_cleartext_switch")
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Launcher Activity: ${cfg.launcherActivity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CompilerBlue
                )
            }
        }

        item {
            StudioCard {
                Text("Declared Permissions", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ManifestParser.COMMON_PERMISSIONS.forEach { perm ->
                        val shortName = perm.substringAfterLast('.')
                        val isSelected = selectedPermissions.contains(perm)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPermissions = if (isSelected) {
                                    selectedPermissions - perm
                                } else {
                                    selectedPermissions + perm
                                }
                            },
                            label = { Text(shortName) },
                            modifier = Modifier.testTag("perm_chip_$shortName")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customPermission,
                        onValueChange = { customPermission = it },
                        label = { Text("Custom android.permission.*") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = customPermission.trim()
                            if (trimmed.isNotBlank() && !selectedPermissions.contains(trimmed)) {
                                selectedPermissions = selectedPermissions + trimmed
                                customPermission = ""
                            }
                        }
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        onSaveManifest(
                            cfg.copy(
                                appLabel = appLabel,
                                usesCleartextTraffic = cleartext,
                                permissions = selectedPermissions
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_manifest_button")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Manifest Changes")
                }
            }
        }
    }
}

@Composable
fun DependenciesPane(
    gradleSummary: GradleConfigSummary?,
    mavenQuery: String,
    mavenResults: List<MavenArtifactResult>,
    isSearching: Boolean,
    onSearchMaven: (String) -> Unit,
    onAddDependency: (String, String) -> Unit,
    onRemoveDependency: (String) -> Unit
) {
    var manualCoords by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            StudioCard {
                Text("Maven Central Library Search", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Search live Maven Central artifacts and inject into ${gradleSummary?.gradleFilePath ?: "build.gradle"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = mavenQuery,
                    onValueChange = onSearchMaven,
                    label = { Text("Search artifact (e.g. retrofit, okhttp, gson, coil)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maven_search_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualCoords,
                        onValueChange = { manualCoords = it },
                        label = { Text("Manual group:artifact:version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (manualCoords.contains(":")) {
                                onAddDependency("implementation", manualCoords.trim())
                                manualCoords = ""
                            }
                        },
                        enabled = manualCoords.count { it == ':' } >= 2
                    ) { Text("Add") }
                }
            }
        }

        if (mavenResults.isNotEmpty()) {
            item {
                Text("Maven Central Results", style = MaterialTheme.typography.titleSmall)
            }
            items(mavenResults.take(8), key = { it.coordinates }) { item ->
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.artifactId, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = item.coordinates,
                                style = MaterialTheme.typography.labelSmall,
                                color = CompilerBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onAddDependency("implementation", item.coordinates) }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Configured Dependencies (${gradleSummary?.dependencies?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        val deps = gradleSummary?.dependencies.orEmpty()
        if (deps.isEmpty()) {
            item {
                StudioCard {
                    Text(
                        "No dependencies parsed in Gradle file yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(deps, key = { "${it.configuration}:${it.notation}" }) { dep ->
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(text = dep.configuration, color = GradleAmber)
                                StatusBadge(text = dep.version, color = AndroidEmerald)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${dep.group}:${dep.artifact}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveDependency(dep.notation) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove dependency", tint = ErrorCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IdentityAndValidationPane(
    project: ProjectEntity,
    gradleSummary: GradleConfigSummary?,
    issues: List<ProjectIssue>,
    onUpdateIdentity: (String, String, String, String) -> Unit,
    onPickCustomIcon: () -> Unit,
    onApplyAutoFix: (FixActionType) -> Unit,
    onOpenIssueFile: (String) -> Unit
) {
    var appName by remember(project) { mutableStateOf(project.name) }
    var appId by remember(project, gradleSummary) {
        mutableStateOf(gradleSummary?.applicationId?.ifBlank { project.packageName } ?: project.packageName)
    }
    var versionName by remember(gradleSummary) { mutableStateOf(gradleSummary?.versionName ?: "1.0") }
    var versionCode by remember(gradleSummary) { mutableStateOf(gradleSummary?.versionCode ?: "1") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            StudioCard {
                Text("App Identity & Custom Launcher Icon", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name (strings.xml)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text("Application ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("Version Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text("Version Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onUpdateIdentity(appName, appId, versionName, versionCode) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_identity_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Identity")
                    }
                    OutlinedButton(
                        onClick = onPickCustomIcon,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_custom_icon_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("App Icon")
                    }
                }
            }
        }

        item {
            Text(
                text = "Project Validation (${issues.size} findings)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (issues.isEmpty()) {
            item {
                StudioCard(borderColor = AndroidEmerald.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AndroidEmerald)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("All Structural Checks Passed", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Gradle scripts, AndroidManifest.xml, resources, and source files look valid.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(issues, key = { it.id }) { issue ->
                val color = when (issue.severity) {
                    IssueSeverity.ERROR -> ErrorCoral
                    IssueSeverity.WARNING -> GradleAmber
                    IssueSeverity.INFO -> CompilerBlue
                }
                val icon = when (issue.severity) {
                    IssueSeverity.ERROR -> Icons.Rounded.ErrorOutline
                    IssueSeverity.WARNING -> Icons.Rounded.WarningAmber
                    IssueSeverity.INFO -> Icons.Rounded.CheckCircle
                }
                StudioCard(borderColor = color.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(issue.title, style = MaterialTheme.typography.titleSmall, color = color)
                            Text(issue.filePath, style = MaterialTheme.typography.labelSmall, color = CompilerBlue)
                            Spacer(Modifier.height(4.dp))
                            Text(issue.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (issue.fixAction != null) {
                                    Button(
                                        onClick = { onApplyAutoFix(issue.fixAction) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("autofix_${issue.id}")
                                    ) {
                                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(issue.fixAction.label)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onOpenIssueFile(issue.filePath) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Inspect File")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
