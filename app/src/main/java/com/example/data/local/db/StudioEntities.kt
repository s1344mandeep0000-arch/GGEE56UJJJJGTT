package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val projectType: String,
    val rootPath: String,
    val sourceOrigin: String, // CREATED, ZIP_IMPORT, GITHUB_IMPORT
    val githubRepo: String = "",
    val hasGradle: Boolean = false,
    val hasManifest: Boolean = false,
    val fileCount: Int = 0,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "build_records")
data class BuildRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val variant: String, // DEBUG, RELEASE
    val engineType: String, // GITHUB_ACTIONS, REMOTE_GRADLE_SERVER, LOCAL_PREFLIGHT_ONLY
    val status: String, // RUNNING, SUCCESS, VALIDATION_FAILED, BUILD_FAILED, RUNNER_NOT_CONFIGURED
    val summaryMessage: String,
    val logText: String,
    val apkPath: String? = null,
    val durationMs: Long = 0L,
    val startedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "apk_artifacts")
data class ApkArtifactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val fileName: String,
    val filePath: String,
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val sha256: String,
    val buildVariant: String,
    val createdAt: Long = System.currentTimeMillis()
)
