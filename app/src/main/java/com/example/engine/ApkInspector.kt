package com.example.engine

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.local.db.ApkArtifactEntity
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ApkInspector {

    @Suppress("DEPRECATION")
    fun inspectApkFile(
        context: Context,
        apkFile: File,
        projectId: String,
        projectName: String,
        buildVariant: String
    ): ApkArtifactEntity {
        val pm = context.packageManager
        val pkgInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
            )
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
        }

        val appInfo = pkgInfo?.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val appLabel = appInfo?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: projectName

        val versionCode = if (pkgInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
        } else 1L

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && appInfo != null) {
            appInfo.minSdkVersion
        } else 24

        val targetSdk = appInfo?.targetSdkVersion ?: 35

        return ApkArtifactEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            projectName = projectName,
            fileName = apkFile.name,
            filePath = apkFile.absolutePath,
            appLabel = appLabel,
            packageName = pkgInfo?.packageName ?: "unknown.package",
            versionName = pkgInfo?.versionName ?: "1.0",
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            sizeBytes = apkFile.length(),
            sha256 = computeSha256(apkFile),
            buildVariant = buildVariant,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun computeSha256(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buf = ByteArray(8192)
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        }.getOrDefault("unavailable")
    }
}
