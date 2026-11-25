package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.db.ApkArtifactEntity
import com.example.data.local.db.ProjectEntity
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.GitHubRepoDialog
import com.example.ui.screens.ApksAndHistoryScreen
import com.example.ui.screens.BuildConsoleScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.ApkForgeTheme
import com.example.ui.viewmodel.StudioViewModel
import java.io.File

object Routes {
    const val ROUTE_STUDIO = "studio"
    const val ROUTE_PROJECTS = "projects"
    const val ROUTE_WORKSPACE = "workspace"
    const val ROUTE_BUILD = "build"
    const val ROUTE_APKS = "apks"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    NavDestinationItem(Routes.ROUTE_STUDIO, R.string.nav_studio, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "nav_studio"),
    NavDestinationItem(Routes.ROUTE_PROJECTS, R.string.nav_projects, Icons.Rounded.FolderOpen, Icons.Outlined.FolderOpen, "nav_projects"),
    NavDestinationItem(Routes.ROUTE_BUILD, R.string.nav_build, Icons.Rounded.Build, Icons.Outlined.Build, "nav_build"),
    NavDestinationItem(Routes.ROUTE_APKS, R.string.nav_apks, Icons.Rounded.Android, Icons.Outlined.Android, "nav_apks"),
    NavDestinationItem(Routes.ROUTE_SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkForgeApp(
    viewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.ROUTE_STUDIO

    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val buildHistory by viewModel.buildHistory.collectAsStateWithLifecycle()
    val apkArtifacts by viewModel.apkArtifacts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspace.collectAsStateWithLifecycle()
    val buildConsoleState by viewModel.buildConsole.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showGitHubImportDialog by remember { mutableStateOf(false) }
    var showGitHubExportDialog by remember { mutableStateOf(false) }
    var pendingExportProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var pendingSaveApk by remember { mutableStateOf<ApkArtifactEntity?>(null) }
    var apksScreenInitialTab by remember { mutableIntStateOf(0) }

    // SAF & Photo Picker Launchers (zero broad storage permissions)
    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val displayName = resolveDisplayName(context, uri)
            viewModel.importZipProject(uri, displayName) {
                navController.navigate(Routes.ROUTE_WORKSPACE)
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val target = pendingExportProject
        if (uri != null && target != null) {
            viewModel.exportProjectZip(target, uri)
        }
        pendingExportProject = null
    }

    val importApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importApkFile(uri, resolveDisplayName(context, uri))
        }
    }

    val saveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val apk = pendingSaveApk
        if (uri != null && apk != null) {
            viewModel.saveApkToDevice(apk, uri)
        }
        pendingSaveApk = null
    }

    val customIconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.applyCustomIcon(uri)
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    ApkForgeTheme(darkTheme = settings.darkThemeEnabled) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (currentRoute != Routes.ROUTE_WORKSPACE) {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (currentRoute) {
                                            Routes.ROUTE_PROJECTS -> "My Projects"
                                            Routes.ROUTE_BUILD -> "Build Console"
                                            Routes.ROUTE_APKS -> "APKs & History"
                                            Routes.ROUTE_SETTINGS -> "Studio Settings"
                                            else -> stringResource(id = R.string.app_name)
                                        },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (isBusy) {
                                        Spacer(Modifier.width(10.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && currentRoute != Routes.ROUTE_WORKSPACE) {
                        NavigationBar {
                            TopDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(id = dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = dest.labelRes), maxLines = 1) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && currentRoute != Routes.ROUTE_WORKSPACE) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            TopDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(id = dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = dest.labelRes)) },
                                    modifier = Modifier.testTag("${dest.testTag}_rail")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentModifier = if (currentRoute == Routes.ROUTE_WORKSPACE) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.widthIn(max = 960.dp).fillMaxSize()
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Routes.ROUTE_STUDIO,
                            modifier = contentModifier
                        ) {
                            composable(Routes.ROUTE_STUDIO) {
                                DashboardScreen(
                                    projects = projects,
                                    activeProject = workspaceState.activeProject,
                                    recentBuilds = buildHistory,
                                    settings = settings,
                                    onCreateProjectClick = { showCreateDialog = true },
                                    onImportZipClick = {
                                        importZipLauncher.launch(
                                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                                        )
                                    },
                                    onImportGitHubClick = { showGitHubImportDialog = true },
                                    onBuildApkClick = { navigateTopLevel(Routes.ROUTE_BUILD) },
                                    onOpenMyProjects = { navigateTopLevel(Routes.ROUTE_PROJECTS) },
                                    onOpenBuildHistory = {
                                        apksScreenInitialTab = 1
                                        navigateTopLevel(Routes.ROUTE_APKS)
                                    },
                                    onOpenSettings = { navigateTopLevel(Routes.ROUTE_SETTINGS) },
                                    onOpenProjectWorkspace = { proj ->
                                        viewModel.openProject(proj)
                                        navController.navigate(Routes.ROUTE_WORKSPACE)
                                    }
                                )
                            }

                            composable(Routes.ROUTE_PROJECTS) {
                                ProjectsScreen(
                                    projects = projects,
                                    onOpenProject = { proj ->
                                        viewModel.openProject(proj)
                                        navController.navigate(Routes.ROUTE_WORKSPACE)
                                    },
                                    onBuildProject = { proj ->
                                        viewModel.openProject(proj)
                                        viewModel.selectBuildProject(proj.id)
                                        navigateTopLevel(Routes.ROUTE_BUILD)
                                    },
                                    onExportZip = { proj ->
                                        pendingExportProject = proj
                                        exportZipLauncher.launch("${proj.name.replace(' ', '_')}.zip")
                                    },
                                    onDeleteProject = { viewModel.deleteProject(it) },
                                    onCreateProjectClick = { showCreateDialog = true },
                                    onImportZipClick = {
                                        importZipLauncher.launch(
                                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                                        )
                                    },
                                    onImportGitHubClick = { showGitHubImportDialog = true }
                                )
                            }

                            composable(Routes.ROUTE_WORKSPACE) {
                                WorkspaceScreen(
                                    state = workspaceState,
                                    settings = settings,
                                    onBackToProjects = { navController.popBackStack() },
                                    onNavigateDir = { viewModel.navigateDirectory(it) },
                                    onNavigateUpDir = { viewModel.navigateUpDirectory() },
                                    onOpenFile = { viewModel.openFileInEditor(it) },
                                    onCloseEditor = { viewModel.closeFileEditor() },
                                    onEditorTextChange = { viewModel.updateEditorText(it) },
                                    onSaveEditorFile = { viewModel.saveEditorFile() },
                                    onCreateNewFile = { name, isDir -> viewModel.createNewFile(name, isDir) },
                                    onDeleteFileNode = { viewModel.deleteFileNode(it) },
                                    onSaveManifest = { viewModel.saveManifestConfig(it) },
                                    onSearchMaven = { viewModel.searchMavenDependencies(it) },
                                    onAddDependency = { cfg, coords -> viewModel.addDependency(cfg, coords) },
                                    onRemoveDependency = { viewModel.removeDependency(it) },
                                    onUpdateIdentity = { name, appId, vName, vCode ->
                                        viewModel.updateAppIdentity(name, appId, vName, vCode)
                                    },
                                    onPickCustomIcon = {
                                        customIconPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onApplyAutoFix = { viewModel.applyAutoFix(it) },
                                    onBuildClick = { proj ->
                                        viewModel.selectBuildProject(proj.id)
                                        navController.navigate(Routes.ROUTE_BUILD)
                                    },
                                    onExportZipClick = { proj ->
                                        pendingExportProject = proj
                                        exportZipLauncher.launch("${proj.name.replace(' ', '_')}.zip")
                                    },
                                    onExportGitHubClick = { showGitHubExportDialog = true }
                                )
                            }

                            composable(Routes.ROUTE_BUILD) {
                                BuildConsoleScreen(
                                    projects = projects,
                                    state = buildConsoleState,
                                    onSelectProject = { viewModel.selectBuildProject(it) },
                                    onSelectVariant = { viewModel.selectBuildVariant(it) },
                                    onSelectEngine = { viewModel.selectBuildEngine(it) },
                                    onStartBuild = { viewModel.startBuild() },
                                    onCancelBuild = { viewModel.cancelBuild() },
                                    onOpenSettings = { navigateTopLevel(Routes.ROUTE_SETTINGS) },
                                    onOpenApks = {
                                        apksScreenInitialTab = 0
                                        navigateTopLevel(Routes.ROUTE_APKS)
                                    }
                                )
                            }

                            composable(Routes.ROUTE_APKS) {
                                ApksAndHistoryScreen(
                                    initialTab = apksScreenInitialTab,
                                    apks = apkArtifacts,
                                    builds = buildHistory,
                                    onImportApkClick = {
                                        importApkLauncher.launch(
                                            arrayOf("application/vnd.android.package-archive", "application/octet-stream")
                                        )
                                    },
                                    onSaveApk = { apk ->
                                        pendingSaveApk = apk
                                        saveApkLauncher.launch(apk.fileName)
                                    },
                                    onShareApk = { apk -> shareApkFile(context, apk, viewModel::showMessage) },
                                    onInstallOpenApk = { apk -> openApkForInstall(context, apk, viewModel::showMessage) },
                                    onDeleteApk = { viewModel.deleteApk(it) },
                                    onDeleteBuild = { viewModel.deleteBuildRecord(it) }
                                )
                            }

                            composable(Routes.ROUTE_SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    onUpdateSettings = { viewModel.updateSettings(it) },
                                    onVerifyGitHubToken = { viewModel.verifyGitHubToken(it) },
                                    onTestRemoteServer = { url, token -> viewModel.testRemoteServer(url, token) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateProjectDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, pkg, type ->
                    showCreateDialog = false
                    viewModel.createProject(name, pkg, type) {
                        navController.navigate(Routes.ROUTE_WORKSPACE)
                    }
                }
            )
        }

        if (showGitHubImportDialog) {
            GitHubRepoDialog(
                title = "Import from GitHub",
                confirmLabel = "Clone ZIP",
                initialRepo = settings.githubRepo,
                hasSavedToken = settings.githubToken.isNotBlank(),
                onDismiss = { showGitHubImportDialog = false },
                onConfirm = { repo, branch, token ->
                    showGitHubImportDialog = false
                    viewModel.importGitHubRepo(repo, branch, token) {
                        navController.navigate(Routes.ROUTE_WORKSPACE)
                    }
                }
            )
        }

        if (showGitHubExportDialog) {
            GitHubRepoDialog(
                title = "Export Project to GitHub",
                confirmLabel = "Push Commit",
                initialRepo = workspaceState.activeProject?.githubRepo?.ifBlank { settings.githubRepo } ?: settings.githubRepo,
                hasSavedToken = settings.githubToken.isNotBlank(),
                onDismiss = { showGitHubExportDialog = false },
                onConfirm = { repo, branch, token ->
                    showGitHubExportDialog = false
                    viewModel.exportToGitHub(repo, branch, token)
                }
            )
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}

private fun shareApkFile(context: Context, apk: ApkArtifactEntity, onError: (String) -> Unit) {
    runCatching {
        val file = File(apk.filePath)
        require(file.exists()) { "APK file missing on disk" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${apk.fileName}"))
    }.onFailure {
        onError("Share failed: ${it.message}")
    }
}

private fun openApkForInstall(context: Context, apk: ApkArtifactEntity, onMessage: (String) -> Unit) {
    runCatching {
        val file = File(apk.filePath)
        require(file.exists()) { "APK file missing on disk" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${apk.fileName}"))
    }.onFailure {
        onMessage("Save APK to Downloads and open it via Android's Files app to install.")
    }
}
