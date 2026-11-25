package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.db.ApkArtifactEntity
import com.example.data.local.db.BuildRecordEntity
import com.example.data.model.BuildStatus
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber
import com.example.ui.theme.codeTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApksAndHistoryScreen(
    initialTab: Int,
    apks: List<ApkArtifactEntity>,
    builds: List<BuildRecordEntity>,
    onImportApkClick: () -> Unit,
    onSaveApk: (ApkArtifactEntity) -> Unit,
    onShareApk: (ApkArtifactEntity) -> Unit,
    onInstallOpenApk: (ApkArtifactEntity) -> Unit,
    onDeleteApk: (ApkArtifactEntity) -> Unit,
    onDeleteBuild: (BuildRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("APK Manager (${apks.size})") },
                modifier = Modifier.testTag("tab_apk_manager")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Build History (${builds.size})") },
                modifier = Modifier.testTag("tab_build_history")
            )
        }

        if (selectedTab == 0) {
            ApkManagerTab(
                apks = apks,
                onImportApkClick = onImportApkClick,
                onSaveApk = onSaveApk,
                onShareApk = onShareApk,
                onInstallOpenApk = onInstallOpenApk,
                onDeleteApk = onDeleteApk
            )
        } else {
            BuildHistoryTab(
                builds = builds,
                onDeleteBuild = onDeleteBuild
            )
        }
    }
}

@Composable
private fun ApkManagerTab(
    apks: List<ApkArtifactEntity>,
    onImportApkClick: () -> Unit,
    onSaveApk: (ApkArtifactEntity) -> Unit,
    onShareApk: (ApkArtifactEntity) -> Unit,
    onInstallOpenApk: (ApkArtifactEntity) -> Unit,
    onDeleteApk: (ApkArtifactEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Compiled & Inspected APKs", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onImportApkClick,
                modifier = Modifier.testTag("inspect_apk_button")
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Inspect APK")
            }
        }

        if (apks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Android, contentDescription = null, tint = AndroidEmerald, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No APK Artifacts Yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Build a project via your connected runner or tap Inspect APK to analyze any .apk file on your device.",
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
                items(apks, key = { it.id }) { apk ->
                    StudioCard(borderColor = AndroidEmerald.copy(alpha = 0.35f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusBadge(text = apk.buildVariant, color = AndroidEmerald)
                                    StatusBadge(text = formatSize(apk.sizeBytes), color = CompilerBlue)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(apk.appLabel, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = apk.packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CompilerBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "File: ${apk.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "v${apk.versionName} (${apk.versionCode}) • minSdk ${apk.minSdk} • targetSdk ${apk.targetSdk}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "SHA-256: ${apk.sha256}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteApk(apk) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete APK", tint = ErrorCoral)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSaveApk(apk) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_apk_${apk.id}")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = { onShareApk(apk) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_apk_${apk.id}")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                            OutlinedButton(
                                onClick = { onInstallOpenApk(apk) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_apk_${apk.id}")
                            ) {
                                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildHistoryTab(
    builds: List<BuildRecordEntity>,
    onDeleteBuild: (BuildRecordEntity) -> Unit
) {
    if (builds.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = GradleAmber, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("No Build History", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Validation checks and APK builds will appear here with full console logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(builds, key = { it.id }) { record ->
            var expanded by remember { mutableStateOf(false) }
            val statusEnum = runCatching { BuildStatus.valueOf(record.status) }.getOrDefault(BuildStatus.BUILD_FAILED)
            val color = when (statusEnum) {
                BuildStatus.SUCCESS -> AndroidEmerald
                BuildStatus.RUNNING -> CompilerBlue
                BuildStatus.RUNNER_NOT_CONFIGURED -> GradleAmber
                else -> ErrorCoral
            }
            val dateStr = remember(record.startedAt) {
                SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(record.startedAt))
            }

            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(text = statusEnum.label, color = color)
                            StatusBadge(text = record.variant, color = CompilerBlue)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(record.projectName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$dateStr • ${record.durationMs / 1000.0}s • ${record.engineType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(record.summaryMessage, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle logs"
                            )
                        }
                        IconButton(onClick = { onDeleteBuild(record) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete build record", tint = ErrorCoral)
                        }
                    }
                }

                if (expanded && record.logText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF070A0F))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = record.logText,
                            style = codeTextStyle(11),
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
