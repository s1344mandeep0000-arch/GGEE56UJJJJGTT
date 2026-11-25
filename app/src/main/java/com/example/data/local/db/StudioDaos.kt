package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface BuildRecordDao {
    @Query("SELECT * FROM build_records ORDER BY startedAt DESC")
    fun observeAllBuilds(): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeBuildsForProject(projectId: String): Flow<List<BuildRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuild(record: BuildRecordEntity)

    @Query("DELETE FROM build_records WHERE id = :id")
    suspend fun deleteBuildById(id: String)
}

@Dao
interface ApkArtifactDao {
    @Query("SELECT * FROM apk_artifacts ORDER BY createdAt DESC")
    fun observeAllApks(): Flow<List<ApkArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApk(apk: ApkArtifactEntity)

    @Query("DELETE FROM apk_artifacts WHERE id = :id")
    suspend fun deleteApkById(id: String)
}
