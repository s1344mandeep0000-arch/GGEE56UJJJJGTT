package com.example.engine

import com.example.data.model.FixActionType
import com.example.data.model.IssueSeverity
import com.example.data.model.ProjectIssue
import com.example.data.model.StudioSettings
import java.io.File

object ProjectValidator {

    fun validateProject(rootDir: File): List<ProjectIssue> {
        val issues = mutableListOf<ProjectIssue>()
        if (!rootDir.exists() || !rootDir.isDirectory) {
            issues.add(
                ProjectIssue(
                    id = "missing_root",
                    severity = IssueSeverity.ERROR,
                    filePath = "/",
                    title = "Workspace directory missing",
                    description = "The project folder does not exist on device storage."
                )
            )
            return issues
        }

        val allFiles = rootDir.walkTopDown()
            .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .toList()

        // 1. Check settings.gradle(.kts)
        val hasSettings = allFiles.any {
            it.name == "settings.gradle" || it.name == "settings.gradle.kts"
        }
        if (!hasSettings) {
            issues.add(
                ProjectIssue(
                    id = "missing_settings_gradle",
                    severity = IssueSeverity.ERROR,
                    filePath = "settings.gradle.kts",
                    title = "Missing settings.gradle(.kts)",
                    description = "Gradle requires settings.gradle or settings.gradle.kts to define rootProject.name and modules.",
                    fixAction = FixActionType.GENERATE_SETTINGS_GRADLE
                )
            )
        }

        // 2. Check app/build.gradle(.kts)
        val appGradle = GradleParser.findAppGradleFile(rootDir)
        if (appGradle == null) {
            issues.add(
                ProjectIssue(
                    id = "missing_app_gradle",
                    severity = IssueSeverity.ERROR,
                    filePath = "app/build.gradle.kts",
                    title = "Missing module build.gradle(.kts)",
                    description = "No Android application Gradle build script found in app/.",
                    fixAction = FixActionType.GENERATE_APP_GRADLE
                )
            )
        } else {
            val summary = GradleParser.parseGradleFile(appGradle, rootDir)
            if (summary.applicationId.isBlank()) {
                issues.add(
                    ProjectIssue(
                        id = "missing_app_id",
                        severity = IssueSeverity.ERROR,
                        filePath = summary.gradleFilePath,
                        title = "Missing applicationId or namespace",
                        description = "Android Gradle Plugin requires a valid package identifier in defaultConfig.applicationId.",
                        fixAction = FixActionType.FIX_SDK_VERSIONS
                    )
                )
            }
            if (summary.compileSdk.isBlank() || summary.minSdk.isBlank()) {
                issues.add(
                    ProjectIssue(
                        id = "missing_sdk_versions",
                        severity = IssueSeverity.ERROR,
                        filePath = summary.gradleFilePath,
                        title = "Incomplete Android SDK configuration",
                        description = "compileSdk and minSdk must be declared in android { ... }.",
                        fixAction = FixActionType.FIX_SDK_VERSIONS
                    )
                )
            }
            val gradleBraceBalance = checkBraceBalance(appGradle.readText())
            if (gradleBraceBalance != 0) {
                issues.add(
                    ProjectIssue(
                        id = "gradle_brace_mismatch",
                        severity = IssueSeverity.ERROR,
                        filePath = summary.gradleFilePath,
                        title = "Unbalanced braces in Gradle script",
                        description = "Detected unclosed '{' or extra '}' in ${summary.gradleFilePath}."
                    )
                )
            }
        }

        // 3. Check AndroidManifest.xml
        val manifestFile = allFiles.firstOrNull { it.name == "AndroidManifest.xml" }
        if (manifestFile == null) {
            issues.add(
                ProjectIssue(
                    id = "missing_manifest",
                    severity = IssueSeverity.ERROR,
                    filePath = "app/src/main/AndroidManifest.xml",
                    title = "Missing AndroidManifest.xml",
                    description = "Every Android app requires AndroidManifest.xml declaring components and permissions.",
                    fixAction = FixActionType.GENERATE_MANIFEST
                )
            )
        } else {
            val relManifest = manifestFile.canonicalPath.removePrefix(rootDir.canonicalPath)
                .trimStart(File.separatorChar).replace(File.separatorChar, '/')
            val xml = manifestFile.readText()
            val config = ManifestParser.parseManifest(xml)
            if (!config.isValidXml) {
                issues.add(
                    ProjectIssue(
                        id = "invalid_manifest_xml",
                        severity = IssueSeverity.ERROR,
                        filePath = relManifest,
                        title = "Invalid XML in AndroidManifest.xml",
                        description = config.parseError ?: "XML syntax error in manifest."
                    )
                )
            } else if (!xml.contains("android.intent.action.MAIN")) {
                issues.add(
                    ProjectIssue(
                        id = "missing_launcher_activity",
                        severity = IssueSeverity.WARNING,
                        filePath = relManifest,
                        title = "No LAUNCHER Activity found",
                        description = "App may install without a home screen icon unless an activity declares MAIN/LAUNCHER intent-filter."
                    )
                )
            }
        }

        // 4. Check strings.xml
        val stringsFile = allFiles.firstOrNull { it.name == "strings.xml" }
        if (stringsFile == null) {
            issues.add(
                ProjectIssue(
                    id = "missing_strings",
                    severity = IssueSeverity.WARNING,
                    filePath = "app/src/main/res/values/strings.xml",
                    title = "Missing res/values/strings.xml",
                    description = "AndroidManifest references @string/app_name which requires strings.xml.",
                    fixAction = FixActionType.GENERATE_STRINGS_XML
                )
            )
        }

        // 5. Check source code files (.kt, .java)
        val sourceFiles = allFiles.filter {
            it.extension.equals("kt", ignoreCase = true) ||
                it.extension.equals("java", ignoreCase = true)
        }
        if (sourceFiles.isEmpty()) {
            issues.add(
                ProjectIssue(
                    id = "missing_source_entry",
                    severity = IssueSeverity.ERROR,
                    filePath = "app/src/main/java",
                    title = "No Kotlin or Java entry point found",
                    description = "No .kt or .java source files exist in the project.",
                    fixAction = FixActionType.GENERATE_MAIN_ACTIVITY
                )
            )
        } else {
            sourceFiles.take(40).forEach { src ->
                val content = src.readText()
                val rel = src.canonicalPath.removePrefix(rootDir.canonicalPath)
                    .trimStart(File.separatorChar).replace(File.separatorChar, '/')
                val balance = checkBraceBalance(content)
                if (balance != 0) {
                    issues.add(
                        ProjectIssue(
                            id = "brace_$rel",
                            severity = IssueSeverity.ERROR,
                            filePath = rel,
                            title = "Unbalanced curly braces in ${src.name}",
                            description = if (balance > 0) "Missing $balance closing '}' brace(s)." else "Found ${-balance} unexpected '}' brace(s)."
                        )
                    )
                }
                if (src.extension.equals("java", true) && !content.contains("package ")) {
                    issues.add(
                        ProjectIssue(
                            id = "pkg_$rel",
                            severity = IssueSeverity.WARNING,
                            filePath = rel,
                            title = "Missing package statement in ${src.name}",
                            description = "Java source files should declare a package header matching their directory."
                        )
                    )
                }
            }
        }

        // 6. Check gradle wrapper
        val hasWrapperProps = allFiles.any { it.name == "gradle-wrapper.properties" }
        if (!hasWrapperProps) {
            issues.add(
                ProjectIssue(
                    id = "missing_wrapper",
                    severity = IssueSeverity.INFO,
                    filePath = "gradle/wrapper/gradle-wrapper.properties",
                    title = "Gradle wrapper properties missing",
                    description = "Cloud CI runner will provision Gradle automatically, or you can inspect Gradle settings."
                )
            )
        }

        return issues
    }

    fun applyAutoFix(
        rootDir: File,
        projectName: String,
        packageName: String,
        action: FixActionType,
        settings: StudioSettings
    ): Boolean {
        val safePkg = packageName.ifBlank { "com.example.app" }
        val safeName = projectName.ifBlank { "MyApp" }

        return when (action) {
            FixActionType.GENERATE_SETTINGS_GRADLE -> {
                File(rootDir, "settings.gradle.kts").writeText(
                    """
                    pluginManagement {
                        repositories {
                            google()
                            mavenCentral()
                            gradlePluginPortal()
                        }
                    }
                    dependencyResolutionManagement {
                        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                        repositories {
                            google()
                            mavenCentral()
                        }
                    }
                    rootProject.name = "$safeName"
                    include(":app")
                    """.trimIndent()
                )
                true
            }
            FixActionType.GENERATE_APP_GRADLE -> {
                val appDir = File(rootDir, "app").apply { mkdirs() }
                File(appDir, "build.gradle.kts").writeText(
                    """
                    plugins {
                        id("com.android.application")
                        id("org.jetbrains.kotlin.android")
                    }

                    android {
                        namespace = "$safePkg"
                        compileSdk = ${settings.defaultCompileSdk}

                        defaultConfig {
                            applicationId = "$safePkg"
                            minSdk = ${settings.defaultMinSdk}
                            targetSdk = ${settings.defaultTargetSdk}
                            versionCode = 1
                            versionName = "1.0"
                        }

                        compileOptions {
                            sourceCompatibility = JavaVersion.VERSION_17
                            targetCompatibility = JavaVersion.VERSION_17
                        }
                        kotlinOptions {
                            jvmTarget = "17"
                        }
                    }

                    dependencies {
                        implementation("androidx.core:core-ktx:1.15.0")
                        implementation("androidx.appcompat:appcompat:1.7.0")
                    }
                    """.trimIndent()
                )
                true
            }
            FixActionType.GENERATE_MANIFEST -> {
                val mainDir = File(rootDir, "app/src/main").apply { mkdirs() }
                File(mainDir, "AndroidManifest.xml").writeText(
                    ProjectTemplateGenerator.defaultManifestXml(safeName, includeInternet = true)
                )
                true
            }
            FixActionType.GENERATE_STRINGS_XML -> {
                val valuesDir = File(rootDir, "app/src/main/res/values").apply { mkdirs() }
                File(valuesDir, "strings.xml").writeText(
                    """
                    <resources>
                        <string name="app_name">$safeName</string>
                    </resources>
                    """.trimIndent()
                )
                true
            }
            FixActionType.GENERATE_MAIN_ACTIVITY -> {
                val pkgPath = safePkg.replace('.', '/')
                val javaDir = File(rootDir, "app/src/main/java/$pkgPath").apply { mkdirs() }
                File(javaDir, "MainActivity.kt").writeText(
                    """
                    package $safePkg

                    import android.os.Bundle
                    import android.widget.TextView
                    import androidx.activity.ComponentActivity

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            val tv = TextView(this).apply {
                                text = "Welcome to $safeName"
                                textSize = 20f
                                setPadding(48, 96, 48, 48)
                            }
                            setContentView(tv)
                        }
                    }
                    """.trimIndent()
                )
                true
            }
            FixActionType.FIX_SDK_VERSIONS -> {
                val gradleFile = GradleParser.findAppGradleFile(rootDir) ?: return false
                var content = gradleFile.readText()
                if (!content.contains("compileSdk")) {
                    content = content.replaceFirst(
                        "android {",
                        "android {\n    compileSdk = ${settings.defaultCompileSdk}"
                    )
                }
                if (!content.contains("minSdk")) {
                    content = content.replaceFirst(
                        "defaultConfig {",
                        "defaultConfig {\n        minSdk = ${settings.defaultMinSdk}\n        targetSdk = ${settings.defaultTargetSdk}"
                    )
                }
                if (!content.contains("applicationId")) {
                    content = content.replaceFirst(
                        "defaultConfig {",
                        "defaultConfig {\n        applicationId = \"$safePkg\""
                    )
                }
                gradleFile.writeText(content)
                true
            }
        }
    }

    private fun checkBraceBalance(source: String): Int {
        var depth = 0
        var inString = false
        var inChar = false
        var escaped = false
        for (ch in source) {
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '"' && !inChar) {
                inString = !inString
                continue
            }
            if (ch == '\'' && !inString) {
                inChar = !inChar
                continue
            }
            if (!inString && !inChar) {
                when (ch) {
                    '{' -> depth++
                    '}' -> depth--
                }
            }
        }
        return depth
    }
}
