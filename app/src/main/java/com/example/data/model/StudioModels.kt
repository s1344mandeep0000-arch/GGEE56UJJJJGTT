package com.example.data.model

enum class ProjectType(val label: String, val badge: String) {
    KOTLIN_COMPOSE("Kotlin + Compose", "KT"),
    JAVA_VIEWS("Java + Views", "JAVA"),
    WEB_HYBRID("Web (HTML/CSS/JS)", "WEB"),
    IMPORTED("Imported Project", "SRC")
}

enum class BuildVariant(val label: String, val gradleTask: String) {
    DEBUG("Debug APK", "assembleDebug"),
    RELEASE("Release APK", "assembleRelease")
}

enum class BuildEngineType(val label: String) {
    GITHUB_ACTIONS("GitHub Actions Cloud"),
    REMOTE_GRADLE_SERVER("Remote Gradle Server"),
    LOCAL_PREFLIGHT_ONLY("Local Validation")
}

enum class BuildStatus(val label: String) {
    RUNNING("Running"),
    SUCCESS("Succeeded"),
    VALIDATION_FAILED("Validation Failed"),
    BUILD_FAILED("Build Failed"),
    RUNNER_NOT_CONFIGURED("Runner Needed")
}

enum class IssueSeverity {
    ERROR, WARNING, INFO
}

enum class FixActionType(val label: String) {
    GENERATE_MANIFEST("Generate AndroidManifest.xml"),
    GENERATE_APP_GRADLE("Generate app/build.gradle.kts"),
    GENERATE_SETTINGS_GRADLE("Generate settings.gradle.kts"),
    GENERATE_STRINGS_XML("Generate strings.xml"),
    GENERATE_MAIN_ACTIVITY("Generate MainActivity"),
    FIX_SDK_VERSIONS("Configure SDK Versions")
}

data class ProjectIssue(
    val id: String,
    val severity: IssueSeverity,
    val filePath: String,
    val title: String,
    val description: String,
    val fixAction: FixActionType? = null
)

data class FileNode(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val extension: String,
    val lastModified: Long
)

data class GradleDependency(
    val configuration: String,
    val notation: String,
    val group: String,
    val artifact: String,
    val version: String
)

data class GradleConfigSummary(
    val applicationId: String = "",
    val compileSdk: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val versionCode: String = "1",
    val versionName: String = "1.0",
    val usesKotlin: Boolean = true,
    val usesCompose: Boolean = false,
    val dependencies: List<GradleDependency> = emptyList(),
    val gradleFilePath: String = ""
)

data class ManifestConfig(
    val packageName: String = "",
    val appLabel: String = "",
    val usesCleartextTraffic: Boolean = false,
    val launcherActivity: String = ".MainActivity",
    val permissions: List<String> = emptyList(),
    val isValidXml: Boolean = true,
    val parseError: String? = null
)

data class MavenArtifactResult(
    val groupId: String,
    val artifactId: String,
    val latestVersion: String,
    val packaging: String
) {
    val coordinates: String get() = "$groupId:$artifactId:$latestVersion"
}

enum class LogLevel {
    INFO, TASK, WARN, ERROR, SUCCESS
}

data class BuildLogLine(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class ErrorSolution(
    val title: String,
    val explanation: String,
    val suggestedFix: String
)

data class StudioSettings(
    val githubToken: String = "",
    val githubUsername: String = "",
    val githubRepo: String = "",
    val remoteServerUrl: String = "",
    val remoteServerToken: String = "",
    val defaultCompileSdk: Int = 35,
    val defaultMinSdk: Int = 24,
    val defaultTargetSdk: Int = 35,
    val gradleJvmArgs: String = "-Xmx2048m",
    val editorFontSizeSp: Int = 13,
    val showLineNumbers: Boolean = true,
    val autoValidateOnSave: Boolean = true,
    val darkThemeEnabled: Boolean = true
)
