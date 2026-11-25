package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.ApkArtifactEntity
import com.example.data.local.db.BuildRecordEntity
import com.example.data.local.db.ProjectEntity
import com.example.data.model.BuildEngineType
import com.example.data.model.BuildLogLine
import com.example.data.model.BuildStatus
import com.example.data.model.BuildVariant
import com.example.data.model.ErrorSolution
import com.example.data.model.FileNode
import com.example.data.model.FixActionType
import com.example.data.model.GradleConfigSummary
import com.example.data.model.LogLevel
import com.example.data.model.ManifestConfig
import com.example.data.model.MavenArtifactResult
import com.example.data.model.ProjectIssue
import com.example.data.model.ProjectType
import com.example.data.model.StudioSettings
import com.example.data.repository.StudioRepository
import com.example.engine.BuildErrorAdvisor
import com.example.engine.NetworkClients
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceState(
    val activeProject: ProjectEntity? = null,
    val currentRelativeDir: String = "",
    val directoryNodes: List<FileNode> = emptyList(),
    val selectedFilePath: String? = null,
    val editorContent: String = "",
    val isEditorDirty: Boolean = false,
    val issues: List<ProjectIssue> = emptyList(),
    val gradleSummary: GradleConfigSummary? = null,
    val manifestConfig: ManifestConfig? = null,
    val manifestRelativePath: String = "",
    val mavenSearchQuery: String = "",
    val mavenResults: List<MavenArtifactResult> = emptyList(),
    val isSearchingMaven: Boolean = false,
    val isSyncingGitHub: Boolean = false
)

data class BuildConsoleState(
    val selectedProjectId: String? = null,
    val selectedVariant: BuildVariant = BuildVariant.DEBUG,
    val selectedEngine: BuildEngineType = BuildEngineType.GITHUB_ACTIONS,
    val isBuilding: Boolean = false,
    val status: BuildStatus? = null,
    val logs: List<BuildLogLine> = emptyList(),
    val solutions: List<ErrorSolution> = emptyList(),
    val lastBuiltApkPath: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudioRepository(application)

    val projects: StateFlow<List<ProjectEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buildHistory: StateFlow<List<BuildRecordEntity>> = repository.buildsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apkArtifacts: StateFlow<List<ApkArtifactEntity>> = repository.apksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<StudioSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    private val _workspace = MutableStateFlow(WorkspaceState())
    val workspace: StateFlow<WorkspaceState> = _workspace.asStateFlow()

    private val _buildConsole = MutableStateFlow(BuildConsoleState())
    val buildConsole: StateFlow<BuildConsoleState> = _buildConsole.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var buildJob: Job? = null

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    fun createProject(name: String, packageName: String, type: ProjectType, onCreated: (ProjectEntity) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createNewProject(name, packageName, type)
                .onSuccess { project ->
                    openProject(project)
                    _toastMessage.value = "Created ${project.name}"
                    onCreated(project)
                }
                .onFailure {
                    _toastMessage.value = "Failed to create project: ${it.message}"
                }
            _isBusy.value = false
        }
    }

    fun importZipProject(uri: Uri, displayName: String?, onImported: (ProjectEntity) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.importZipProject(uri, displayName)
                .onSuccess { project ->
                    openProject(project)
                    _toastMessage.value = "Imported ${project.name} (${project.fileCount} files)"
                    onImported(project)
                }
                .onFailure {
                    _toastMessage.value = "ZIP import failed: ${it.message}"
                }
            _isBusy.value = false
        }
    }

    fun importGitHubRepo(ownerRepo: String, branch: String, token: String, onImported: (ProjectEntity) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val effectiveToken = token.ifBlank { settings.value.githubToken }
            repository.importGitHubProject(ownerRepo, branch, effectiveToken)
                .onSuccess { project ->
                    openProject(project)
                    _toastMessage.value = "Imported $ownerRepo (${project.fileCount} files)"
                    onImported(project)
                }
                .onFailure {
                    _toastMessage.value = "GitHub import failed: ${it.message}"
                }
            _isBusy.value = false
        }
    }

    fun openProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.touchProjectOpened(project)
            _buildConsole.update { it.copy(selectedProjectId = project.id) }
            reloadWorkspace(project, currentDir = "", clearEditor = true)
        }
    }

    private suspend fun reloadWorkspace(project: ProjectEntity, currentDir: String, clearEditor: Boolean = false) {
        val nodes = repository.listFiles(project, currentDir)
        val issues = repository.validateProject(project)
        val gradle = repository.loadGradleSummary(project)
        val (manifestPath, manifestCfg) = repository.loadManifestConfig(project)
        _workspace.update { state ->
            state.copy(
                activeProject = project,
                currentRelativeDir = currentDir,
                directoryNodes = nodes,
                selectedFilePath = if (clearEditor) null else state.selectedFilePath,
                editorContent = if (clearEditor) "" else state.editorContent,
                isEditorDirty = if (clearEditor) false else state.isEditorDirty,
                issues = issues,
                gradleSummary = gradle,
                manifestConfig = manifestCfg,
                manifestRelativePath = manifestPath
            )
        }
    }

    fun navigateDirectory(relativeDir: String) {
        val project = _workspace.value.activeProject ?: return
        viewModelScope.launch {
            val nodes = repository.listFiles(project, relativeDir)
            _workspace.update { it.copy(currentRelativeDir = relativeDir, directoryNodes = nodes) }
        }
    }

    fun navigateUpDirectory(): Boolean {
        val current = _workspace.value.currentRelativeDir
        if (current.isBlank()) return false
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        navigateDirectory(parent)
        return true
    }

    fun openFileInEditor(relativePath: String) {
        val project = _workspace.value.activeProject ?: return
        viewModelScope.launch {
            repository.readFileText(project, relativePath)
                .onSuccess { text ->
                    _workspace.update {
                        it.copy(
                            selectedFilePath = relativePath,
                            editorContent = text,
                            isEditorDirty = false
                        )
                    }
                }
                .onFailure {
                    _toastMessage.value = it.message ?: "Cannot open file"
                }
        }
    }

    fun closeFileEditor() {
        _workspace.update { it.copy(selectedFilePath = null, editorContent = "", isEditorDirty = false) }
    }

    fun updateEditorText(newText: String) {
        _workspace.update { it.copy(editorContent = newText, isEditorDirty = true) }
    }

    fun saveEditorFile() {
        val state = _workspace.value
        val project = state.activeProject ?: return
        val path = state.selectedFilePath ?: return
        viewModelScope.launch {
            repository.writeFileText(project, path, state.editorContent)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir, clearEditor = false)
                    _workspace.update { it.copy(isEditorDirty = false) }
                    _toastMessage.value = "Saved $path"
                }
                .onFailure {
                    _toastMessage.value = "Save failed: ${it.message}"
                }
        }
    }

    fun createNewFile(fileName: String, isDirectory: Boolean) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.createNewFile(project, state.currentRelativeDir, fileName, isDirectory)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    if (!isDirectory) {
                        val rel = if (state.currentRelativeDir.isBlank()) fileName.trim() else "${state.currentRelativeDir}/${fileName.trim()}"
                        openFileInEditor(rel)
                    }
                }
                .onFailure { _toastMessage.value = it.message ?: "Failed to create file" }
        }
    }

    fun deleteFileNode(node: FileNode) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.deleteFileOrDir(project, node.relativePath)
                .onSuccess { updated ->
                    val shouldClear = state.selectedFilePath == node.relativePath
                    reloadWorkspace(updated, state.currentRelativeDir, clearEditor = shouldClear)
                }
                .onFailure { _toastMessage.value = "Delete failed: ${it.message}" }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_workspace.value.activeProject?.id == project.id) {
                _workspace.value = WorkspaceState()
            }
            _toastMessage.value = "Deleted ${project.name}"
        }
    }

    fun exportProjectZip(project: ProjectEntity, destinationUri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.exportProjectAsZip(project, destinationUri)
                .onSuccess { _toastMessage.value = "Project ZIP exported successfully" }
                .onFailure { _toastMessage.value = "ZIP export failed: ${it.message}" }
            _isBusy.value = false
        }
    }

    fun applyAutoFix(action: FixActionType) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.applyAutoFix(project, action)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "Applied: ${action.label}"
                }
                .onFailure { _toastMessage.value = "Auto-fix failed: ${it.message}" }
        }
    }

    fun saveManifestConfig(config: ManifestConfig) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.saveManifestConfig(project, config)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "AndroidManifest.xml updated"
                }
                .onFailure { _toastMessage.value = "Manifest update failed: ${it.message}" }
        }
    }

    fun searchMavenDependencies(query: String) {
        _workspace.update { it.copy(mavenSearchQuery = query) }
        if (query.trim().length < 2) {
            _workspace.update { it.copy(mavenResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _workspace.update { it.copy(isSearchingMaven = true) }
            repository.searchMaven(query)
                .onSuccess { list ->
                    _workspace.update { it.copy(mavenResults = list, isSearchingMaven = false) }
                }
                .onFailure {
                    _workspace.update { it.copy(isSearchingMaven = false) }
                    _toastMessage.value = "Maven search error: ${it.message}"
                }
        }
    }

    fun addDependency(configuration: String, coordinates: String) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.addGradleDependency(project, configuration, coordinates)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "Added $coordinates"
                }
                .onFailure { _toastMessage.value = it.message ?: "Failed to add dependency" }
        }
    }

    fun removeDependency(notation: String) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.removeGradleDependency(project, notation)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "Removed $notation"
                }
                .onFailure { _toastMessage.value = it.message ?: "Failed to remove dependency" }
        }
    }

    fun updateAppIdentity(appName: String, applicationId: String, versionName: String, versionCode: String) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.updateProjectIdentity(project, appName, applicationId, versionName, versionCode)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "App identity saved"
                }
                .onFailure { _toastMessage.value = "Update failed: ${it.message}" }
        }
    }

    fun applyCustomIcon(imageUri: Uri) {
        val state = _workspace.value
        val project = state.activeProject ?: return
        viewModelScope.launch {
            repository.applyCustomLauncherIcon(project, imageUri)
                .onSuccess { updated ->
                    reloadWorkspace(updated, state.currentRelativeDir)
                    _toastMessage.value = "Custom launcher icon generated for all mipmap densities"
                }
                .onFailure { _toastMessage.value = "Icon update failed: ${it.message}" }
        }
    }

    fun exportToGitHub(ownerRepo: String, branch: String, token: String) {
        val project = _workspace.value.activeProject ?: return
        val effectiveToken = token.ifBlank { settings.value.githubToken }
        viewModelScope.launch {
            _workspace.update { it.copy(isSyncingGitHub = true) }
            repository.exportProjectToGitHub(project, ownerRepo, branch, effectiveToken) { _, _ -> }
                .onSuccess { sha ->
                    _toastMessage.value = "Pushed to $ownerRepo (${sha.take(7)})"
                }
                .onFailure {
                    _toastMessage.value = "GitHub push failed: ${it.message}"
                }
            _workspace.update { it.copy(isSyncingGitHub = false) }
        }
    }

    fun selectBuildProject(projectId: String) {
        _buildConsole.update { it.copy(selectedProjectId = projectId) }
    }

    fun selectBuildVariant(variant: BuildVariant) {
        _buildConsole.update { it.copy(selectedVariant = variant) }
    }

    fun selectBuildEngine(engine: BuildEngineType) {
        _buildConsole.update { it.copy(selectedEngine = engine) }
    }

    fun startBuild(projectOverride: ProjectEntity? = null) {
        val targetProject = projectOverride
            ?: projects.value.firstOrNull { it.id == _buildConsole.value.selectedProjectId }
            ?: _workspace.value.activeProject
            ?: projects.value.firstOrNull()

        if (targetProject == null) {
            _toastMessage.value = "Create or import a project first."
            return
        }

        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            _buildConsole.update {
                it.copy(
                    selectedProjectId = targetProject.id,
                    isBuilding = true,
                    status = BuildStatus.RUNNING,
                    logs = listOf(
                        BuildLogLine(
                            timestamp = "--:--:--",
                            level = LogLevel.INFO,
                            message = "Initializing ${it.selectedVariant.label} via ${it.selectedEngine.label}..."
                        )
                    ),
                    solutions = emptyList(),
                    lastBuiltApkPath = null
                )
            }

            val record = repository.runBuildPipeline(
                project = targetProject,
                variant = _buildConsole.value.selectedVariant,
                preferredEngine = _buildConsole.value.selectedEngine,
                onLogLine = { line ->
                    _buildConsole.update { current ->
                        current.copy(logs = current.logs + line)
                    }
                }
            )

            val finalStatus = runCatching { BuildStatus.valueOf(record.status) }.getOrDefault(BuildStatus.BUILD_FAILED)
            val solutions = if (finalStatus != BuildStatus.SUCCESS) {
                BuildErrorAdvisor.diagnoseLogs(record.logText)
            } else emptyList()

            _buildConsole.update {
                it.copy(
                    isBuilding = false,
                    status = finalStatus,
                    solutions = solutions,
                    lastBuiltApkPath = record.apkPath
                )
            }
        }
    }

    fun cancelBuild() {
        buildJob?.cancel()
        _buildConsole.update {
            it.copy(
                isBuilding = false,
                status = BuildStatus.BUILD_FAILED,
                logs = it.logs + BuildLogLine("--:--:--", LogLevel.WARN, "Build cancelled by user.")
            )
        }
    }

    fun importApkFile(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            repository.importExistingApk(uri, displayName)
                .onSuccess { _toastMessage.value = "Inspected ${it.fileName} (${it.packageName})" }
                .onFailure { _toastMessage.value = "APK import failed: ${it.message}" }
        }
    }

    fun saveApkToDevice(apk: ApkArtifactEntity, destinationUri: Uri) {
        viewModelScope.launch {
            repository.saveApkToUri(apk, destinationUri)
                .onSuccess { _toastMessage.value = "Saved ${apk.fileName}" }
                .onFailure { _toastMessage.value = "Save failed: ${it.message}" }
        }
    }

    fun deleteApk(apk: ApkArtifactEntity) {
        viewModelScope.launch {
            repository.deleteApk(apk)
            _toastMessage.value = "Removed ${apk.fileName}"
        }
    }

    fun deleteBuildRecord(record: BuildRecordEntity) {
        viewModelScope.launch {
            repository.deleteBuildRecord(record)
        }
    }

    fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun verifyGitHubToken(token: String) {
        viewModelScope.launch {
            _isBusy.value = true
            NetworkClients.verifyGitHubToken(token)
                .onSuccess { login ->
                    repository.updateSettings { it.copy(githubToken = token.trim(), githubUsername = login) }
                    _toastMessage.value = "Authenticated as @$login"
                }
                .onFailure {
                    _toastMessage.value = "Token check failed: ${it.message}"
                }
            _isBusy.value = false
        }
    }

    fun testRemoteServer(url: String, token: String) {
        viewModelScope.launch {
            _isBusy.value = true
            NetworkClients.testRemoteGradleServer(url, token)
                .onSuccess { msg ->
                    repository.updateSettings { it.copy(remoteServerUrl = url.trim(), remoteServerToken = token.trim()) }
                    _toastMessage.value = "Connected: $msg"
                }
                .onFailure {
                    _toastMessage.value = "Connection failed: ${it.message}"
                }
            _isBusy.value = false
        }
    }
}
