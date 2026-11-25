package com.example

import com.example.data.model.FixActionType
import com.example.data.model.IssueSeverity
import com.example.data.model.ProjectType
import com.example.data.model.StudioSettings
import com.example.engine.BuildErrorAdvisor
import com.example.engine.GradleParser
import com.example.engine.ManifestParser
import com.example.engine.ProjectTemplateGenerator
import com.example.engine.ProjectValidator
import com.example.engine.ZipProjectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkForgeEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generateKotlinProject_passesValidation_andParsesGradleAndManifest() {
        val dir = tempFolder.newFolder("kotlin_proj")
        ProjectTemplateGenerator.generateProject(
            rootDir = dir,
            appName = "SampleApp",
            packageName = "com.test.sample",
            projectType = ProjectType.KOTLIN_COMPOSE
        )

        val issues = ProjectValidator.validateProject(dir)
        val errors = issues.filter { it.severity == IssueSeverity.ERROR }
        assertTrue("Expected zero errors, got $errors", errors.isEmpty())

        val gradleFile = GradleParser.findAppGradleFile(dir)
        assertNotNull(gradleFile)
        val summary = GradleParser.parseGradleFile(gradleFile!!, dir)
        assertEquals("com.test.sample", summary.applicationId)
        assertEquals("35", summary.compileSdk)
        assertTrue(summary.dependencies.isNotEmpty())

        assertTrue(GradleParser.addDependency(gradleFile, "implementation", "com.squareup.okhttp3:okhttp:4.12.0"))
        val updatedSummary = GradleParser.parseGradleFile(gradleFile, dir)
        assertTrue(updatedSummary.dependencies.any { it.artifact == "okhttp" })
    }

    @Test
    fun zipRoundTrip_preservesProject_andBlocksZipSlip() {
        val srcDir = tempFolder.newFolder("web_src")
        ProjectTemplateGenerator.generateProject(
            rootDir = srcDir,
            appName = "WebApp",
            packageName = "com.test.web",
            projectType = ProjectType.WEB_HYBRID
        )

        val zipBytes = ByteArrayOutputStream()
        ZipProjectManager.zipDirectory(srcDir, zipBytes)

        val destDir = tempFolder.newFolder("web_extracted")
        val scan = ZipProjectManager.extractZipSafely(ByteArrayInputStream(zipBytes.toByteArray()), destDir)
        assertEquals("WebApp", scan.detectedName)
        assertEquals("com.test.web", scan.detectedPackage)
        assertEquals(ProjectType.WEB_HYBRID, scan.detectedType)
        assertTrue(File(destDir, "app/src/main/assets/www/index.html").exists())

        // Zip-slip protection test
        val evilZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../../evil.txt"))
                zos.write("bad".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val evilTarget = tempFolder.newFolder("evil_target")
        var blocked = false
        try {
            ZipProjectManager.extractZipSafely(ByteArrayInputStream(evilZip), evilTarget)
        } catch (e: SecurityException) {
            blocked = true
        }
        assertTrue("Zip-slip entry must throw SecurityException", blocked)
    }

    @Test
    fun validatorAutoFix_andManifestEditor_workEndToEnd() {
        val dir = tempFolder.newFolder("broken_proj")
        val initialIssues = ProjectValidator.validateProject(dir)
        assertTrue(initialIssues.any { it.fixAction == FixActionType.GENERATE_MANIFEST })

        val settings = StudioSettings()
        ProjectValidator.applyAutoFix(dir, "FixedApp", "com.fixed.app", FixActionType.GENERATE_SETTINGS_GRADLE, settings)
        ProjectValidator.applyAutoFix(dir, "FixedApp", "com.fixed.app", FixActionType.GENERATE_APP_GRADLE, settings)
        ProjectValidator.applyAutoFix(dir, "FixedApp", "com.fixed.app", FixActionType.GENERATE_MANIFEST, settings)
        ProjectValidator.applyAutoFix(dir, "FixedApp", "com.fixed.app", FixActionType.GENERATE_STRINGS_XML, settings)
        ProjectValidator.applyAutoFix(dir, "FixedApp", "com.fixed.app", FixActionType.GENERATE_MAIN_ACTIVITY, settings)

        val postFixErrors = ProjectValidator.validateProject(dir).filter { it.severity == IssueSeverity.ERROR }
        assertTrue("Expected no errors after auto-fix, got $postFixErrors", postFixErrors.isEmpty())

        val manifestFile = File(dir, "app/src/main/AndroidManifest.xml")
        val parsed = ManifestParser.parseManifest(manifestFile.readText())
        assertTrue(parsed.isValidXml)
        val updatedXml = ManifestParser.applyConfigToXml(
            manifestFile.readText(),
            parsed.copy(
                usesCleartextTraffic = true,
                permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA")
            )
        )
        assertFalse(updatedXml.isBlank())
        assertTrue(updatedXml.contains("android.permission.CAMERA"))
        assertTrue(updatedXml.contains("usesCleartextTraffic=\"true\""))

        val solutions = BuildErrorAdvisor.diagnoseLogs("FAILURE: Manifest merger failed with multiple errors")
        assertTrue(solutions.isNotEmpty())
    }
}
