package com.example.engine

import com.example.data.model.FileNode
import com.example.data.model.ProjectType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedProjectScan(
    val detectedName: String,
    val detectedPackage: String,
    val detectedType: ProjectType,
    val hasGradle: Boolean,
    val hasManifest: Boolean,
    val fileCount: Int
)

object ZipProjectManager {

    fun extractZipSafely(inputStream: InputStream, targetDir: File): ImportedProjectScan {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.trimStart('/')
                if (entryName.isNotEmpty() && !entryName.startsWith("__MACOSX")) {
                    val outFile = File(targetDir, entryName)
                    val canonicalOut = outFile.canonicalPath
                    if (!canonicalOut.startsWith(canonicalTarget + File.separator) && canonicalOut != canonicalTarget) {
                        throw SecurityException("Blocked unsafe ZIP path entry: $entryName")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Flatten single wrapper directory (common in GitHub zipball or zipped root folder)
        val topChildren = targetDir.listFiles()?.filter { it.name != "__MACOSX" }.orEmpty()
        if (topChildren.size == 1 && topChildren.first().isDirectory) {
            val wrapperDir = topChildren.first()
            val nestedFiles = wrapperDir.listFiles().orEmpty()
            nestedFiles.forEach { child ->
                val dest = File(targetDir, child.name)
                if (!child.renameTo(dest)) {
                    child.copyRecursively(dest, overwrite = true)
                    child.deleteRecursively()
                }
            }
            wrapperDir.deleteRecursively()
        }

        return scanProjectDirectory(targetDir, fallbackName = targetDir.name)
    }

    fun scanProjectDirectory(rootDir: File, fallbackName: String = "AndroidProject"): ImportedProjectScan {
        val allFiles = rootDir.walkTopDown()
            .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .toList()

        val hasGradle = allFiles.any {
            it.name == "build.gradle" || it.name == "build.gradle.kts"
        }
        val manifestFile = allFiles.firstOrNull { it.name == "AndroidManifest.xml" }
        val hasManifest = manifestFile != null

        val hasKotlin = allFiles.any { it.extension.equals("kt", ignoreCase = true) }
        val hasJava = allFiles.any { it.extension.equals("java", ignoreCase = true) }
        val hasHtml = allFiles.any { it.extension.equals("html", ignoreCase = true) }

        val detectedType = when {
            hasHtml && !hasJava && !hasKotlin -> ProjectType.WEB_HYBRID
            hasHtml && allFiles.any { it.path.contains("assets") } -> ProjectType.WEB_HYBRID
            hasKotlin -> ProjectType.KOTLIN_COMPOSE
            hasJava -> ProjectType.JAVA_VIEWS
            else -> ProjectType.IMPORTED
        }

        val appGradle = GradleParser.findAppGradleFile(rootDir)
        val gradleSummary = appGradle?.let { GradleParser.parseGradleFile(it, rootDir) }
        val manifestConfig = manifestFile?.let { ManifestParser.parseManifest(it.readText()) }

        val stringsFile = allFiles.firstOrNull { it.name == "strings.xml" }
        val appNameFromStrings = stringsFile?.let { extractAppNameFromStrings(it.readText()) }
        val settingsName = findProjectNameInSettings(rootDir)

        val detectedName = listOfNotNull(
            appNameFromStrings?.takeIf { it.isNotBlank() && !it.startsWith("@") },
            settingsName?.takeIf { it.isNotBlank() },
            fallbackName.takeIf { it.isNotBlank() }
        ).firstOrNull() ?: "AndroidProject"

        val detectedPackage = listOfNotNull(
            gradleSummary?.applicationId?.takeIf { it.isNotBlank() },
            manifestConfig?.packageName?.takeIf { it.isNotBlank() },
            "com.example.app"
        ).first()

        return ImportedProjectScan(
            detectedName = detectedName,
            detectedPackage = detectedPackage,
            detectedType = detectedType,
            hasGradle = hasGradle,
            hasManifest = hasManifest,
            fileCount = allFiles.size
        )
    }

    fun zipDirectory(sourceDir: File, outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            val basePath = sourceDir.canonicalPath
            sourceDir.walkTopDown()
                .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
                .forEach { file ->
                    val relPath = file.canonicalPath.removePrefix(basePath).trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(relPath))
                    file.inputStream().use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
        }
    }

    fun listDirectoryNodes(rootDir: File, currentRelativePath: String): List<FileNode> {
        val target = if (currentRelativePath.isBlank()) rootDir else File(rootDir, currentRelativePath)
        if (!target.exists() || !target.isDirectory) return emptyList()
        val rootCanonical = rootDir.canonicalPath
        if (!target.canonicalPath.startsWith(rootCanonical)) return emptyList()

        return target.listFiles()
            ?.filter { it.name != ".git" && it.name != "build" && it.name != ".gradle" }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { file ->
                val rel = file.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                FileNode(
                    relativePath = rel,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) file.listFiles()?.size?.toLong() ?: 0L else file.length(),
                    extension = if (file.isDirectory) "" else file.extension.lowercase(),
                    lastModified = file.lastModified()
                )
            }
            .orEmpty()
    }

    private fun extractAppNameFromStrings(xml: String): String? {
        val regex = Regex("""<string\s+name=["']app_name["']\s*>([^<]+)</string>""")
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun findProjectNameInSettings(rootDir: File): String? {
        val settings = File(rootDir, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(rootDir, "settings.gradle").takeIf { it.exists() }
            ?: return null
        val regex = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""")
        return regex.find(settings.readText())?.groupValues?.getOrNull(1)?.trim()
    }
}
