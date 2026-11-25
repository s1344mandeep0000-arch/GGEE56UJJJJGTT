package com.example.engine

import com.example.data.model.GradleConfigSummary
import com.example.data.model.GradleDependency
import java.io.File

object GradleParser {

    fun findAppGradleFile(rootDir: File): File? {
        val candidates = listOf(
            File(rootDir, "app/build.gradle.kts"),
            File(rootDir, "app/build.gradle"),
            File(rootDir, "build.gradle.kts"),
            File(rootDir, "build.gradle")
        )
        return candidates.firstOrNull { file ->
            file.exists() && file.readText().let {
                it.contains("com.android.application") || it.contains("applicationId")
            }
        } ?: File(rootDir, "app/build.gradle.kts").takeIf { it.exists() }
            ?: File(rootDir, "app/build.gradle").takeIf { it.exists() }
    }

    fun parseGradleFile(gradleFile: File, rootDir: File): GradleConfigSummary {
        val text = gradleFile.readText()
        val relPath = gradleFile.canonicalPath.removePrefix(rootDir.canonicalPath)
            .trimStart(File.separatorChar).replace(File.separatorChar, '/')

        val appId = Regex("""applicationId\s*=?\s*["']([^"']+)["']""")
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        val namespace = Regex("""namespace\s*=?\s*["']([^"']+)["']""")
            .find(text)?.groupValues?.getOrNull(1).orEmpty()

        val compileSdk = Regex("""compileSdk\s*=?\s*(\d+)""")
            .find(text)?.groupValues?.getOrNull(1)
            ?: Regex("""compileSdkVersion\s*\(?\s*(\d+)\s*\)?""").find(text)?.groupValues?.getOrNull(1).orEmpty()

        val minSdk = Regex("""minSdk\s*=?\s*(\d+)""")
            .find(text)?.groupValues?.getOrNull(1)
            ?: Regex("""minSdkVersion\s*\(?\s*(\d+)\s*\)?""").find(text)?.groupValues?.getOrNull(1).orEmpty()

        val targetSdk = Regex("""targetSdk\s*=?\s*(\d+)""")
            .find(text)?.groupValues?.getOrNull(1)
            ?: Regex("""targetSdkVersion\s*\(?\s*(\d+)\s*\)?""").find(text)?.groupValues?.getOrNull(1).orEmpty()

        val versionCode = Regex("""versionCode\s*=?\s*(\d+)""")
            .find(text)?.groupValues?.getOrNull(1) ?: "1"

        val versionName = Regex("""versionName\s*=?\s*["']([^"']+)["']""")
            .find(text)?.groupValues?.getOrNull(1) ?: "1.0"

        val depRegex = Regex("""(implementation|api|compileOnly|runtimeOnly|testImplementation|ksp|kapt)\s*\(?\s*["']([^"':]+):([^"':]+):?([^"']*)["']\s*\)?""")
        val deps = depRegex.findAll(text).map { match ->
            val config = match.groupValues[1]
            val group = match.groupValues[2]
            val artifact = match.groupValues[3]
            val version = match.groupValues[4].ifBlank { "BOM" }
            GradleDependency(
                configuration = config,
                notation = "$group:$artifact${if (version == "BOM") "" else ":$version"}",
                group = group,
                artifact = artifact,
                version = version
            )
        }.toList()

        return GradleConfigSummary(
            applicationId = appId.ifBlank { namespace },
            compileSdk = compileSdk,
            minSdk = minSdk,
            targetSdk = targetSdk,
            versionCode = versionCode,
            versionName = versionName,
            usesKotlin = text.contains("kotlin") || gradleFile.extension == "kts",
            usesCompose = text.contains("compose"),
            dependencies = deps,
            gradleFilePath = relPath
        )
    }

    fun addDependency(gradleFile: File, configuration: String, coordinates: String): Boolean {
        val text = gradleFile.readText()
        if (text.contains(coordinates)) return false

        val isKts = gradleFile.extension == "kts"
        val line = if (isKts) {
            "    $configuration(\"$coordinates\")"
        } else {
            "    $configuration '$coordinates'"
        }

        val depHeader = Regex("""dependencies\s*\{""")
        val match = depHeader.find(text)
        val updated = if (match != null) {
            val insertPos = match.range.last + 1
            text.substring(0, insertPos) + "\n" + line + text.substring(insertPos)
        } else {
            "$text\n\ndependencies {\n$line\n}\n"
        }
        gradleFile.writeText(updated)
        return true
    }

    fun removeDependency(gradleFile: File, notation: String): Boolean {
        val lines = gradleFile.readLines()
        val filtered = lines.filterNot { it.contains(notation) }
        if (filtered.size == lines.size) return false
        gradleFile.writeText(filtered.joinToString("\n") + "\n")
        return true
    }

    fun updateIdentityFields(
        gradleFile: File,
        applicationId: String,
        versionName: String,
        versionCode: String
    ) {
        var text = gradleFile.readText()
        val isKts = gradleFile.extension == "kts"

        if (applicationId.isNotBlank()) {
            val appIdRegex = Regex("""applicationId\s*=?\s*["'][^"']+["']""")
            val replacement = if (isKts) "applicationId = \"$applicationId\"" else "applicationId \"$applicationId\""
            text = if (appIdRegex.containsMatchIn(text)) {
                appIdRegex.replaceFirst(text, replacement)
            } else text
        }

        if (versionName.isNotBlank()) {
            val vnRegex = Regex("""versionName\s*=?\s*["'][^"']+["']""")
            val replacement = if (isKts) "versionName = \"$versionName\"" else "versionName \"$versionName\""
            text = if (vnRegex.containsMatchIn(text)) {
                vnRegex.replaceFirst(text, replacement)
            } else text
        }

        val vcInt = versionCode.toIntOrNull()
        if (vcInt != null) {
            val vcRegex = Regex("""versionCode\s*=?\s*\d+""")
            val replacement = if (isKts) "versionCode = $vcInt" else "versionCode $vcInt"
            text = if (vcRegex.containsMatchIn(text)) {
                vcRegex.replaceFirst(text, replacement)
            } else text
        }

        gradleFile.writeText(text)
    }
}
