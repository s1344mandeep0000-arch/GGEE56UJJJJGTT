package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.ProjectEntity
import com.example.data.model.FileNode
import com.example.data.model.FixActionType
import com.example.data.model.ManifestConfig
import com.example.data.model.StudioSettings
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GradleAmber
import com.example.ui.theme.codeTextStyle
import com.example.ui.viewmodel.WorkspaceState

@Composable
fun WorkspaceScreen(
    state: WorkspaceState,
    settings: StudioSettings,
    onBackToProjects: () -> Unit,
    onNavigateDir: (String) -> Unit,
    onNavigateUpDir: () -> Boolean,
    onOpenFile: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onEditorTextChange: (String) -> Unit,
    onSaveEditorFile: () -> Unit,
    onCreateNewFile: (String, Boolean) -> Unit,
    onDeleteFileNode: (FileNode) -> Unit,
    onSaveManifest: (ManifestConfig) -> Unit,
    onSearchMaven: (String) -> Unit,
    onAddDependency: (String, String) -> Unit,
    onRemoveDependency: (String) -> Unit,
    onUpdateIdentity: (String, String, String, String) -> Unit,
    onPickCustomIcon: () -> Unit,
    onApplyAutoFix: (FixActionType) -> Unit,
    onBuildClick: (ProjectEntity) -> Unit,
    onExportZipClick: (ProjectEntity) -> Unit,
    onExportGitHubClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project = state.activeProject ?: return
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateFileDialog by remember { mutableStateOf<Boolean?>(null) } // true=dir, false=file

    BackHandler {
        when {
            state.selectedFilePath != null -> onCloseEditor()
            selectedTab == 0 && onNavigateUpDir() -> Unit
            else -> onBackToProjects()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Workspace Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (state.selectedFilePath != null) onCloseEditor()
                    else onBackToProjects()
                },
                modifier = Modifier.testTag("workspace_back_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.selectedFilePath ?: project.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { onExportZipClick(project) },
                modifier = Modifier.testTag("workspace_export_zip_button")
            ) {
                Icon(Icons.Rounded.Archive, contentDescription = "Export ZIP")
            }
            IconButton(
                onClick = onExportGitHubClick,
                modifier = Modifier.testTag("workspace_export_github_button")
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = "Push to GitHub")
            }
            Button(
                onClick = { onBuildClick(project) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("workspace_build_button")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Build")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp
        ) {
            val tabs = listOf(
                "Files & Code",
                "Manifest",
                "Dependencies",
                "Identity & Fixes (${state.issues.size})"
            )
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("workspace_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (state.selectedFilePath != null) {
                    CodeEditorPane(
                        filePath = state.selectedFilePath,
                        content = state.editorContent,
                        isDirty = state.isEditorDirty,
                        fontSizeSp = settings.editorFontSizeSp,
                        showLineNumbers = settings.showLineNumbers,
                        onTextChange = onEditorTextChange,
                        onSave = onSaveEditorFile,
                        onClose = onCloseEditor
                    )
                } else {
                    FileExplorerPane(
                        currentRelativeDir = state.currentRelativeDir,
                        nodes = state.directoryNodes,
                        onNavigateDir = onNavigateDir,
                        onNavigateUp = { onNavigateUpDir() },
                        onOpenFile = onOpenFile,
                        onNewFileClick = { showCreateFileDialog = false },
                        onNewFolderClick = { showCreateFileDialog = true },
                        onDeleteNode = onDeleteFileNode
                    )
                }
            }
            1 -> ManifestEditorPane(
                manifestPath = state.manifestRelativePath,
                manifestConfig = state.manifestConfig,
                onSaveManifest = onSaveManifest,
                onOpenRawXml = {
                    if (state.manifestRelativePath.isNotBlank()) {
                        selectedTab = 0
                        onOpenFile(state.manifestRelativePath)
                    }
                }
            )
            2 -> DependenciesPane(
                gradleSummary = state.gradleSummary,
                mavenQuery = state.mavenSearchQuery,
                mavenResults = state.mavenResults,
                isSearching = state.isSearchingMaven,
                onSearchMaven = onSearchMaven,
                onAddDependency = onAddDependency,
                onRemoveDependency = onRemoveDependency
            )
            3 -> IdentityAndValidationPane(
                project = project,
                gradleSummary = state.gradleSummary,
                issues = state.issues,
                onUpdateIdentity = onUpdateIdentity,
                onPickCustomIcon = onPickCustomIcon,
                onApplyAutoFix = onApplyAutoFix,
                onOpenIssueFile = { relPath ->
                    selectedTab = 0
                    onOpenFile(relPath)
                }
            )
        }
    }

    showCreateFileDialog?.let { isDir ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = null },
            title = { Text(if (isDir) "New Folder" else "New Source File") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isDir) "Folder name" else "File name (e.g. Helper.kt, index.html)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewFile(name, isDir)
                        showCreateFileDialog = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileExplorerPane(
    currentRelativeDir: String,
    nodes: List<FileNode>,
    onNavigateDir: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (String) -> Unit,
    onNewFileClick: () -> Unit,
    onNewFolderClick: () -> Unit,
    onDeleteNode: (FileNode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (currentRelativeDir.isNotBlank()) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Parent folder")
                    }
                }
                Text(
                    text = "/${currentRelativeDir}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CompilerBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                IconButton(onClick = onNewFileClick, modifier = Modifier.testTag("new_file_button")) {
                    Icon(Icons.Rounded.Add, contentDescription = "New File")
                }
                IconButton(onClick = onNewFolderClick, modifier = Modifier.testTag("new_folder_button")) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New Folder")
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(nodes, key = { it.relativePath }) { node ->
                StudioCard(
                    onClick = {
                        if (node.isDirectory) onNavigateDir(node.relativePath)
                        else onOpenFile(node.relativePath)
                    },
                    modifier = Modifier.testTag("file_node_${node.name}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = when {
                                node.isDirectory -> GradleAmber
                                node.extension == "kt" -> CompilerBlue
                                node.extension == "java" -> GradleAmber
                                node.extension == "xml" -> AndroidEmerald
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (node.isDirectory) "${node.sizeBytes} items" else "${node.sizeBytes} B",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (node.extension.isNotBlank()) {
                            StatusBadge(text = node.extension.uppercase(), color = CompilerBlue)
                        }
                        IconButton(onClick = { onDeleteNode(node) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = ErrorCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeEditorPane(
    filePath: String,
    content: String,
    isDirty: Boolean,
    fontSizeSp: Int,
    showLineNumbers: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val ext = remember(filePath) { filePath.substringAfterLast('.', "") }
    val codeStyle = remember(fontSizeSp) { codeTextStyle(fontSizeSp) }
    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val syntaxTransformation = remember(ext) {
        VisualTransformation { text ->
            val highlighted: AnnotatedString = SyntaxHighlighter.highlight(text.text, ext)
            TransformedText(highlighted, OffsetMapping.Identity)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                StatusBadge(
                    text = if (isDirty) "UNSAVED" else ext.uppercase().ifBlank { "TXT" },
                    color = if (isDirty) GradleAmber else AndroidEmerald
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = filePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                IconButton(onClick = onSave, modifier = Modifier.testTag("save_code_button")) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save file", tint = AndroidEmerald)
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_code_button")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor")
                }
            }
        }

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(vScroll)
                .padding(10.dp)
        ) {
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    val gutterText = remember(lineCount) {
                        (1..lineCount).joinToString("\n")
                    }
                    Text(
                        text = gutterText,
                        style = codeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = onTextChange,
                    textStyle = codeStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(AndroidEmerald),
                    visualTransformation = syntaxTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_editor_textfield")
                )
            }
        }
    }
}
