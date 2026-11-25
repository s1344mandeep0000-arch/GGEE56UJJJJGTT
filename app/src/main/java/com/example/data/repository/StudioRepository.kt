package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.db.ApkArtifactEntity
import com.example.data.local.db.BuildRecordEntity
import com.example.data.local.db.ProjectEntity
import com.example.data.local.db.StudioDatabase
import com.example.data.local.preferences.StudioPreferences
import com.example.data.model.BuildEngineType
import com.example.data.model.BuildLogLine
import com.example.data.model.BuildStatus
import com.example.data.model.BuildVariant
import com.example.data.model.FileNode
import com.example.data.model.FixActionType
import com.example.data.model.GradleConfigSummary
import com.example.data.model.IssueSeverity
import com.example.data.model.LogLevel
import com.example.data.model.ManifestConfig
import com.example.data.model.MavenArtifactResult
import com.example.data.model.ProjectIssue
import com.example.data.model.ProjectType
import com.example.data.model.StudioSettings
import com.example.engine.ApkInspector
import com.example.engine.GradleParser
import com.example.engine.ManifestParser
import com.example.engine.NetworkClients
import com.example.engine.ProjectTemplateGenerator
import com.example.engine.ProjectValidator
import com.example.engine.ZipProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StudioRepository(private val context: Context) {

    private val db = StudioDatabase.getInstance(context)
    private val projectDao = db.projectDao()
    private val buildDao = db.buildRecordDao()
    private val apkDao = db.apkArtifactDao()
    private val prefs = StudioPreferences(context)

    val projectsFlow: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()
    val buildsFlow: Flow<List<BuildRecordEntity>> = buildDao.observeAllBuilds()
    val apksFlow: Flow<List<ApkArtifactEntity>> = apkDao.observeAllApks()
    val settingsFlow: Flow<StudioSettings> = prefs.settingsFlow

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    private val apksRoot: File
        get() = File(context.filesDir, "apks").apply { if (!exists()) mkdirs() }

    suspend fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        prefs.updateSettings(transform)
    }

    suspend fun createNewProject(
        name: String,
        packageName: String,
        projectType: ProjectType
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString()
            val dir = File(projectsRoot, id)
            val settings = prefs.settingsFlow.first()
            ProjectTemplateGenerator.generateProject(
                rootDir = dir,
                appName = name.trim(),
                packageName = packageName.trim(),
                projectType = projectType,
                compileSdk = settings.defaultCompileSdk,
                minSdk = settings.defaultMinSdk,
                targetSdk = settings.defaultTargetSdk,
                jvmArgs = settings.gradleJvmArgs
            )
            refreshAndSaveProject(
                id = id,
                rootDir = dir,
                sourceOrigin = "CREATED",
                overrideName = name.trim(),
                overrideType = projectType
            )
        }
    }

    suspend fun importZipProject(uri: Uri, displayName: String?): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString()
            val dir = File(projectsRoot, id)
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected ZIP file.")
            val scan = stream.use { ZipProjectManager.extractZipSafely(it, dir) }
            val fallbackName = displayName?.removeSuffix(".zip")?.takeIf { it.isNotBlank() } ?: scan.detectedName
            refreshAndSaveProject(
                id = id,
                rootDir = dir,
                sourceOrigin = "ZIP_IMPORT",
                overrideName = if (scan.detectedName == dir.name) fallbackName else scan.detectedName
            )
        }
    }

    suspend fun importGitHubProject(
        ownerRepo: String,
        branch: String,
        token: String
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString()
            val dir = File(projectsRoot, id)
            val scan = NetworkClients.downloadGitHubRepoZip(ownerRepo, branch, token, dir).getOrThrow()
            val repoShortName = ownerRepo.trim().substringAfterLast('/')
            refreshAndSaveProject(
                id = id,
                rootDir = dir,
                sourceOrigin = "GITHUB_IMPORT",
                githubRepo = ownerRepo.trim(),
                overrideName = if (scan.detectedName == dir.name) repoShortName else scan.detectedName
            )
        }
    }

    suspend fun refreshAndSaveProject(
        id: String,
        rootDir: File,
        sourceOrigin: String,
        githubRepo: String = "",
        overrideName: String? = null,
        overrideType: ProjectType? = null
    ): ProjectEntity = withContext(Dispatchers.IO) {
        val existing = projectDao.getProjectById(id)
        val scan = ZipProjectManager.scanProjectDirectory(rootDir, fallbackName = overrideName ?: existing?.name ?: "Project")
        val issues = ProjectValidator.validateProject(rootDir)
        val errors = issues.count { it.severity == IssueSeverity.ERROR }
        val warnings = issues.count { it.severity == IssueSeverity.WARNING }

        val entity = ProjectEntity(
            id = id,
            name = overrideName?.takeIf { it.isNotBlank() } ?: scan.detectedName,
            packageName = scan.detectedPackage,
            projectType = (overrideType ?: scan.detectedType).name,
            rootPath = rootDir.absolutePath,
            sourceOrigin = existing?.sourceOrigin ?: sourceOrigin,
            githubRepo = githubRepo.ifBlank { existing?.githubRepo.orEmpty() },
            hasGradle = scan.hasGradle,
            hasManifest = scan.hasManifest,
            fileCount = scan.fileCount,
            errorCount = errors,
            warningCount = warnings,
            lastOpenedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(entity)
        entity
    }

    suspend fun touchProjectOpened(project: ProjectEntity) = withContext(Dispatchers.IO) {
        projectDao.upsertProject(project.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        File(project.rootPath).deleteRecursively()
        projectDao.deleteProjectById(project.id)
    }

    suspend fun exportProjectAsZip(project: ProjectEntity, destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("Unable to open destination file for export.")
            out.use { ZipProjectManager.zipDirectory(File(project.rootPath), it) }
        }
    }

    suspend fun listFiles(project: ProjectEntity, relativeDir: String): List<FileNode> = withContext(Dispatchers.IO) {
        ZipProjectManager.listDirectoryNodes(File(project.rootPath), relativeDir)
    }

    suspend fun readFileText(project: ProjectEntity, relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveSafeFile(project, relativePath)
            require(file.exists() && file.isFile) { "File not found: $relativePath" }
            if (file.length() > 1_500_000L) {
                throw IllegalStateException("File too large to edit in mobile editor (${file.length() / 1024} KB).")
            }
            file.readText()
        }
    }

    suspend fun writeFileText(project: ProjectEntity, relativePath: String, content: String): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveSafeFile(project, relativePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                refreshAndSaveProject(project.id, File(project.rootPath), project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun createNewFile(project: ProjectEntity, relativeDir: String, fileName: String, isDirectory: Boolean): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanName = fileName.trim().replace("..", "")
                require(cleanName.isNotBlank() && !cleanName.contains('/')) { "Invalid file name" }
                val rel = if (relativeDir.isBlank()) cleanName else "$relativeDir/$cleanName"
                val target = resolveSafeFile(project, rel)
                if (isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    if (!target.exists()) target.writeText("")
                }
                refreshAndSaveProject(project.id, File(project.rootPath), project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun deleteFileOrDir(project: ProjectEntity, relativePath: String): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = resolveSafeFile(project, relativePath)
                target.deleteRecursively()
                refreshAndSaveProject(project.id, File(project.rootPath), project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun validateProject(project: ProjectEntity): List<ProjectIssue> = withContext(Dispatchers.IO) {
        ProjectValidator.validateProject(File(project.rootPath))
    }

    suspend fun applyAutoFix(project: ProjectEntity, action: FixActionType): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val settings = prefs.settingsFlow.first()
                ProjectValidator.applyAutoFix(
                    rootDir = File(project.rootPath),
                    projectName = project.name,
                    packageName = project.packageName,
                    action = action,
                    settings = settings
                )
                refreshAndSaveProject(project.id, File(project.rootPath), project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun loadManifestConfig(project: ProjectEntity): Pair<String, ManifestConfig> = withContext(Dispatchers.IO) {
        val root = File(project.rootPath)
        val manifest = root.walkTopDown().firstOrNull { it.name == "AndroidManifest.xml" }
        if (manifest == null) {
            return@withContext "" to ManifestConfig(isValidXml = false, parseError = "AndroidManifest.xml not found")
        }
        val rel = manifest.canonicalPath.removePrefix(root.canonicalPath).trimStart(File.separatorChar).replace(File.separatorChar, '/')
        val xml = manifest.readText()
        rel to ManifestParser.parseManifest(xml)
    }

    suspend fun saveManifestConfig(project: ProjectEntity, config: ManifestConfig): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(project.rootPath)
                val manifest = root.walkTopDown().firstOrNull { it.name == "AndroidManifest.xml" }
                    ?: File(root, "app/src/main/AndroidManifest.xml").apply {
                        parentFile?.mkdirs()
                        writeText(ProjectTemplateGenerator.defaultManifestXml(project.name))
                    }
                val updatedXml = ManifestParser.applyConfigToXml(manifest.readText(), config)
                manifest.writeText(updatedXml)
                refreshAndSaveProject(project.id, root, project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun loadGradleSummary(project: ProjectEntity): GradleConfigSummary? = withContext(Dispatchers.IO) {
        val root = File(project.rootPath)
        val gradleFile = GradleParser.findAppGradleFile(root) ?: return@withContext null
        GradleParser.parseGradleFile(gradleFile, root)
    }

    suspend fun addGradleDependency(project: ProjectEntity, configuration: String, coordinates: String): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(project.rootPath)
                val gradleFile = GradleParser.findAppGradleFile(root)
                    ?: throw IllegalStateException("No app/build.gradle(.kts) found in project.")
                GradleParser.addDependency(gradleFile, configuration, coordinates)
                refreshAndSaveProject(project.id, root, project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun removeGradleDependency(project: ProjectEntity, notation: String): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(project.rootPath)
                val gradleFile = GradleParser.findAppGradleFile(root)
                    ?: throw IllegalStateException("No app/build.gradle(.kts) found in project.")
                GradleParser.removeDependency(gradleFile, notation)
                refreshAndSaveProject(project.id, root, project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun updateProjectIdentity(
        project: ProjectEntity,
        appName: String,
        applicationId: String,
        versionName: String,
        versionCode: String
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(project.rootPath)
            val gradleFile = GradleParser.findAppGradleFile(root)
            if (gradleFile != null) {
                GradleParser.updateIdentityFields(gradleFile, applicationId.trim(), versionName.trim(), versionCode.trim())
            }
            val stringsFile = root.walkTopDown().firstOrNull { it.name == "strings.xml" }
                ?: File(root, "app/src/main/res/values/strings.xml").apply { parentFile?.mkdirs() }
            if (stringsFile.exists()) {
                val xml = stringsFile.readText()
                val regex = Regex("""<string\s+name=["']app_name["']\s*>[^<]*</string>""")
                val updated = if (regex.containsMatchIn(xml)) {
                    regex.replaceFirst(xml, "<string name=\"app_name\">${appName.trim()}</string>")
                } else {
                    xml.replace("</resources>", "    <string name=\"app_name\">${appName.trim()}</string>\n</resources>")
                }
                stringsFile.writeText(updated)
            } else {
                stringsFile.writeText("<resources>\n    <string name=\"app_name\">${appName.trim()}</string>\n</resources>")
            }
            refreshAndSaveProject(
                id = project.id,
                rootDir = root,
                sourceOrigin = project.sourceOrigin,
                githubRepo = project.githubRepo,
                overrideName = appName.trim()
            )
        }
    }

    suspend fun applyCustomLauncherIcon(project: ProjectEntity, imageUri: Uri): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(project.rootPath)
                val resDir = File(root, "app/src/main/res").apply { mkdirs() }
                val input = context.contentResolver.openInputStream(imageUri)
                    ?: throw IllegalStateException("Cannot open selected image.")
                val originalBitmap = input.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Unsupported image format.")

                val densities = mapOf(
                    "mipmap-mdpi" to 48,
                    "mipmap-hdpi" to 72,
                    "mipmap-xhdpi" to 96,
                    "mipmap-xxhdpi" to 144,
                    "mipmap-xxxhdpi" to 192
                )
                for ((folder, size) in densities) {
                    val outDir = File(resDir, folder).apply { mkdirs() }
                    val scaled = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
                    File(outDir, "ic_launcher.png").outputStream().use {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    File(outDir, "ic_launcher_round.png").outputStream().use {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (scaled != originalBitmap) scaled.recycle()
                }
                originalBitmap.recycle()

                // Ensure AndroidManifest references @mipmap/ic_launcher
                val manifest = root.walkTopDown().firstOrNull { it.name == "AndroidManifest.xml" }
                if (manifest != null) {
                    var xml = manifest.readText()
                    if (!xml.contains("android:icon=")) {
                        xml = xml.replaceFirst(
                            "<application",
                            "<application\n        android:icon=\"@mipmap/ic_launcher\"\n        android:roundIcon=\"@mipmap/ic_launcher_round\""
                        )
                        manifest.writeText(xml)
                    }
                }
                refreshAndSaveProject(project.id, root, project.sourceOrigin, project.githubRepo)
            }
        }

    suspend fun searchMaven(query: String): Result<List<MavenArtifactResult>> =
        NetworkClients.searchMavenCentral(query)

    suspend fun exportProjectToGitHub(
        project: ProjectEntity,
        ownerRepo: String,
        branch: String,
        token: String,
        onLog: (LogLevel, String) -> Unit
    ): Result<String> {
        val result = NetworkClients.pushProjectToGitHub(
            projectDir = File(project.rootPath),
            ownerRepo = ownerRepo,
            branch = branch,
            token = token,
            includeBuildWorkflow = true,
            onLog = onLog
        )
        if (result.isSuccess) {
            projectDao.upsertProject(project.copy(githubRepo = ownerRepo.trim(), updatedAt = System.currentTimeMillis()))
        }
        return result
    }

    suspend fun runBuildPipeline(
        project: ProjectEntity,
        variant: BuildVariant,
        preferredEngine: BuildEngineType,
        onLogLine: (BuildLogLine) -> Unit
    ): BuildRecordEntity {
        val buildId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val settings = prefs.settingsFlow.first()
        val logBuffer = StringBuilder()

        fun emit(level: LogLevel, msg: String) {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val line = BuildLogLine(ts, level, msg)
            logBuffer.append("[$ts] [${level.name}] $msg\n")
            onLogLine(line)
        }

        val initialRecord = BuildRecordEntity(
            id = buildId,
            projectId = project.id,
            projectName = project.name,
            variant = variant.name,
            engineType = preferredEngine.name,
            status = BuildStatus.RUNNING.name,
            summaryMessage = "Starting pre-flight validation...",
            logText = "",
            startedAt = startedAt
        )
        buildDao.upsertBuild(initialRecord)

        // Step 1: Real Pre-flight validation
        emit(LogLevel.TASK, "Running pre-flight project validation for ${project.name}...")
        val issues = withContext(Dispatchers.IO) {
            ProjectValidator.validateProject(File(project.rootPath))
        }
        issues.forEach { issue ->
            val lvl = when (issue.severity) {
                IssueSeverity.ERROR -> LogLevel.ERROR
                IssueSeverity.WARNING -> LogLevel.WARN
                IssueSeverity.INFO -> LogLevel.INFO
            }
            emit(lvl, "${issue.filePath}: ${issue.title} — ${issue.description}")
        }

        val blockingErrors = issues.filter { it.severity == IssueSeverity.ERROR }
        if (blockingErrors.isNotEmpty()) {
            emit(LogLevel.ERROR, "Pre-flight validation failed with ${blockingErrors.size} error(s). Fix project errors before compiling.")
            val failed = initialRecord.copy(
                status = BuildStatus.VALIDATION_FAILED.name,
                summaryMessage = "${blockingErrors.size} validation error(s) found",
                logText = logBuffer.toString(),
                durationMs = System.currentTimeMillis() - startedAt
            )
            buildDao.upsertBuild(failed)
            return failed
        }
        emit(LogLevel.SUCCESS, "Pre-flight validation passed (0 blocking errors).")

        if (preferredEngine == BuildEngineType.LOCAL_PREFLIGHT_ONLY) {
            val validated = initialRecord.copy(
                status = BuildStatus.RUNNER_NOT_CONFIGURED.name,
                summaryMessage = "Validation passed. Select GitHub Actions or Remote Gradle Server to compile APK.",
                logText = logBuffer.toString(),
                durationMs = System.currentTimeMillis() - startedAt
            )
            buildDao.upsertBuild(validated)
            return validated
        }

        // Step 2: Check real build runner availability
        val resultApk: Result<File> = when (preferredEngine) {
            BuildEngineType.GITHUB_ACTIONS -> {
                val repo = project.githubRepo.ifBlank { settings.githubRepo }
                if (settings.githubToken.isBlank() || repo.isBlank()) {
                    emit(LogLevel.ERROR, "No build runner configured: GitHub Token or Target Repository (owner/repo) is missing in Settings.")
                    val missing = initialRecord.copy(
                        status = BuildStatus.RUNNER_NOT_CONFIGURED.name,
                        summaryMessage = "Configure GitHub Token & Build Repo in Settings",
                        logText = logBuffer.toString(),
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                    buildDao.upsertBuild(missing)
                    return missing
                }
                NetworkClients.runGitHubActionsBuild(
                    context = context,
                    projectDir = File(project.rootPath),
                    projectName = project.name,
                    ownerRepo = repo,
                    token = settings.githubToken,
                    variant = variant,
                    outputApkDir = apksRoot,
                    onLog = { lvl, msg -> emit(lvl, msg) }
                )
            }
            BuildEngineType.REMOTE_GRADLE_SERVER -> {
                if (settings.remoteServerUrl.isBlank()) {
                    emit(LogLevel.ERROR, "No build runner configured: Remote Gradle Server URL is empty in Settings.")
                    val missing = initialRecord.copy(
                        status = BuildStatus.RUNNER_NOT_CONFIGURED.name,
                        summaryMessage = "Configure Remote Gradle Server URL in Settings",
                        logText = logBuffer.toString(),
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                    buildDao.upsertBuild(missing)
                    return missing
                }
                NetworkClients.runRemoteGradleServerBuild(
                    context = context,
                    projectDir = File(project.rootPath),
                    projectName = project.name,
                    serverUrl = settings.remoteServerUrl,
                    token = settings.remoteServerToken,
                    variant = variant,
                    outputApkDir = apksRoot,
                    onLog = { lvl, msg -> emit(lvl, msg) }
                )
            }
            BuildEngineType.LOCAL_PREFLIGHT_ONLY -> Result.failure(IllegalStateException("Local preflight only"))
        }

        return resultApk.fold(
            onSuccess = { apkFile ->
                val artifact = withContext(Dispatchers.IO) {
                    ApkInspector.inspectApkFile(
                        context = context,
                        apkFile = apkFile,
                        projectId = project.id,
                        projectName = project.name,
                        buildVariant = variant.name
                    )
                }
                apkDao.upsertApk(artifact)
                emit(LogLevel.SUCCESS, "BUILD SUCCESSFUL — Registered ${artifact.fileName} (${artifact.packageName} v${artifact.versionName})")
                val success = initialRecord.copy(
                    status = BuildStatus.SUCCESS.name,
                    summaryMessage = "Built ${artifact.fileName} (${artifact.sizeBytes / 1024} KB)",
                    logText = logBuffer.toString(),
                    apkPath = apkFile.absolutePath,
                    durationMs = System.currentTimeMillis() - startedAt
                )
                buildDao.upsertBuild(success)
                success
            },
            onFailure = { error ->
                emit(LogLevel.ERROR, "BUILD FAILED: ${error.message ?: "Unknown compilation error"}")
                val failed = initialRecord.copy(
                    status = BuildStatus.BUILD_FAILED.name,
                    summaryMessage = error.message ?: "Gradle build failed",
                    logText = logBuffer.toString(),
                    durationMs = System.currentTimeMillis() - startedAt
                )
                buildDao.upsertBuild(failed)
                failed
            }
        )
    }

    suspend fun importExistingApk(uri: Uri, displayName: String?): Result<ApkArtifactEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = (displayName ?: "imported_${System.currentTimeMillis()}.apk")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .let { if (it.endsWith(".apk", true)) it else "$it.apk" }
                val dest = File(apksRoot, safeName)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to read selected APK.")
                stream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }

                val artifact = ApkInspector.inspectApkFile(
                    context = context,
                    apkFile = dest,
                    projectId = "imported",
                    projectName = safeName.removeSuffix(".apk"),
                    buildVariant = "IMPORTED"
                )
                apkDao.upsertApk(artifact)
                artifact
            }
        }

    suspend fun saveApkToUri(apk: ApkArtifactEntity, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(apk.filePath)
                require(file.exists()) { "APK file no longer exists on disk." }
                val out = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Cannot open destination stream.")
                out.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
                Unit
            }
        }

    suspend fun deleteApk(apk: ApkArtifactEntity) = withContext(Dispatchers.IO) {
        File(apk.filePath).delete()
        apkDao.deleteApkById(apk.id)
    }

    suspend fun deleteBuildRecord(record: BuildRecordEntity) = withContext(Dispatchers.IO) {
        buildDao.deleteBuildById(record.id)
    }

    private fun resolveSafeFile(project: ProjectEntity, relativePath: String): File {
        val root = File(project.rootPath)
        val rootCanonical = root.canonicalPath
        val target = File(root, relativePath.trimStart('/'))
        val targetCanonical = target.canonicalPath
        if (!targetCanonical.startsWith(rootCanonical + File.separator) && targetCanonical != rootCanonical) {
            throw SecurityException("Invalid file path outside project root.")
        }
        return target
    }
}
