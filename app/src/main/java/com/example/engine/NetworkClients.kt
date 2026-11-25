package com.example.engine

import android.content.Context
import android.util.Base64
import com.example.data.model.BuildVariant
import com.example.data.model.LogLevel
import com.example.data.model.MavenArtifactResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@JsonClass(generateAdapter = true)
data class MavenSearchResponse(
    val response: MavenDocsWrapper?
)

@JsonClass(generateAdapter = true)
data class MavenDocsWrapper(
    val docs: List<MavenDoc> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MavenDoc(
    @Json(name = "g") val groupId: String = "",
    @Json(name = "a") val artifactId: String = "",
    @Json(name = "latestVersion") val latestVersion: String = "",
    @Json(name = "p") val packaging: String = "aar"
)

interface MavenCentralApi {
    @GET("solrsearch/select")
    suspend fun searchArtifacts(
        @Query("q") query: String,
        @Query("rows") rows: Int = 20,
        @Query("wt") wt: String = "json"
    ): MavenSearchResponse
}

object NetworkClients {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    val mavenApi: MavenCentralApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://search.maven.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MavenCentralApi::class.java)
    }

    suspend fun searchMavenCentral(query: String): Result<List<MavenArtifactResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = mavenApi.searchArtifacts(query.trim())
            resp.response?.docs.orEmpty()
                .filter { it.groupId.isNotBlank() && it.artifactId.isNotBlank() && it.latestVersion.isNotBlank() }
                .map {
                    MavenArtifactResult(
                        groupId = it.groupId,
                        artifactId = it.artifactId,
                        latestVersion = it.latestVersion,
                        packaging = it.packaging
                    )
                }
        }
    }

    suspend fun verifyGitHubToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("GitHub API ${resp.code}: ${extractGitHubError(body)}")
                }
                val json = JSONObject(body)
                json.optString("login").ifBlank { throw IllegalStateException("No username returned") }
            }
        }
    }

    suspend fun downloadGitHubRepoZip(
        ownerRepo: String,
        branch: String,
        token: String,
        targetDir: File
    ): Result<ImportedProjectScan> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = ownerRepo.trim().removePrefix("https://github.com/").trim('/')
            val parts = cleanRepo.split("/")
            require(parts.size == 2) { "Repository must be formatted as owner/repo" }
            val ref = branch.trim().ifBlank { "HEAD" }
            val url = "https://api.github.com/repos/${parts[0]}/${parts[1]}/zipball/$ref"

            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer ${token.trim()}")
            }

            okHttpClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("GitHub download failed (${resp.code}): ${extractGitHubError(err)}")
                }
                val stream: InputStream = resp.body?.byteStream()
                    ?: throw IllegalStateException("Empty ZIP response from GitHub")
                ZipProjectManager.extractZipSafely(stream, targetDir)
            }
        }
    }

    suspend fun pushProjectToGitHub(
        projectDir: File,
        ownerRepo: String,
        branch: String,
        token: String,
        includeBuildWorkflow: Boolean,
        onLog: (LogLevel, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = ownerRepo.trim().removePrefix("https://github.com/").trim('/')
            val parts = cleanRepo.split("/")
            require(parts.size == 2) { "Repository must be formatted as owner/repo" }
            require(token.isNotBlank()) { "GitHub Personal Access Token is required" }

            val owner = parts[0]
            val repo = parts[1]
            val targetBranch = branch.ifBlank { "main" }

            onLog(LogLevel.INFO, "Connecting to GitHub repository $owner/$repo ($targetBranch)...")

            // 1. Get current branch ref SHA
            val refReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/ref/heads/$targetBranch", token).get().build()
            val headCommitSha = okHttpClient.newCall(refReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Cannot access branch '$targetBranch' (${resp.code}): ${extractGitHubError(body)}")
                }
                JSONObject(body).getJSONObject("object").getString("sha")
            }

            // 2. Get base tree SHA
            val commitReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/commits/$headCommitSha", token).get().build()
            val baseTreeSha = okHttpClient.newCall(commitReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Failed to fetch base commit (${resp.code})")
                JSONObject(body).getJSONObject("tree").getString("sha")
            }

            // 3. Upload blobs for project files
            val treeArray = JSONArray()
            val filesToPush = projectDir.walkTopDown()
                .filter {
                    it.isFile &&
                        !it.path.contains("${File.separator}.git${File.separator}") &&
                        !it.path.contains("${File.separator}build${File.separator}") &&
                        it.length() <= 4_000_000L
                }
                .toList()

            onLog(LogLevel.TASK, "Uploading ${filesToPush.size} project files to GitHub Git Tree...")

            val baseCanonical = projectDir.canonicalPath
            for (file in filesToPush) {
                val relPath = file.canonicalPath.removePrefix(baseCanonical)
                    .trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')

                val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                val blobPayload = JSONObject().apply {
                    put("content", encoded)
                    put("encoding", "base64")
                }
                val blobReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/blobs", token)
                    .post(blobPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val blobSha = okHttpClient.newCall(blobReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Blob upload failed for $relPath (${resp.code}): ${extractGitHubError(body)}")
                    }
                    JSONObject(body).getString("sha")
                }

                treeArray.put(JSONObject().apply {
                    put("path", relPath)
                    put("mode", if (relPath.endsWith("gradlew")) "100755" else "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                })
            }

            if (includeBuildWorkflow) {
                val workflowYaml = buildGitHubActionsWorkflow()
                val wfBlobPayload = JSONObject().apply {
                    put("content", Base64.encodeToString(workflowYaml.toByteArray(), Base64.NO_WRAP))
                    put("encoding", "base64")
                }
                val wfBlobReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/blobs", token)
                    .post(wfBlobPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val wfSha = okHttpClient.newCall(wfBlobReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException("Workflow upload failed (${resp.code})")
                    JSONObject(body).getString("sha")
                }
                treeArray.put(JSONObject().apply {
                    put("path", ".github/workflows/apk-forge-build.yml")
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", wfSha)
                })
            }

            // 4. Create tree
            val treePayload = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeArray)
            }
            val treeReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/trees", token)
                .post(treePayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val newTreeSha = okHttpClient.newCall(treeReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Create tree failed (${resp.code})")
                JSONObject(body).getString("sha")
            }

            // 5. Create commit
            val commitPayload = JSONObject().apply {
                put("message", "APK Forge Studio sync")
                put("tree", newTreeSha)
                put("parents", JSONArray().put(headCommitSha))
            }
            val newCommitReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/commits", token)
                .post(commitPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val newCommitSha = okHttpClient.newCall(newCommitReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Create commit failed (${resp.code})")
                JSONObject(body).getString("sha")
            }

            // 6. Update branch ref
            val updateRefPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }
            val patchRefReq = githubRequest("https://api.github.com/repos/$owner/$repo/git/refs/heads/$targetBranch", token)
                .patch(updateRefPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(patchRefReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    throw IllegalStateException("Update ref failed (${resp.code}): ${extractGitHubError(body)}")
                }
            }

            onLog(LogLevel.SUCCESS, "Committed snapshot $newCommitSha to $owner/$repo:$targetBranch")
            newCommitSha
        }
    }

    suspend fun runGitHubActionsBuild(
        context: Context,
        projectDir: File,
        projectName: String,
        ownerRepo: String,
        token: String,
        variant: BuildVariant,
        outputApkDir: File,
        onLog: (LogLevel, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = ownerRepo.trim().removePrefix("https://github.com/").trim('/')
            val parts = cleanRepo.split("/")
            require(parts.size == 2) { "GitHub repo must be owner/repo" }
            val owner = parts[0]
            val repo = parts[1]

            // Detect default branch
            val repoReq = githubRequest("https://api.github.com/repos/$owner/$repo", token).get().build()
            val defaultBranch = okHttpClient.newCall(repoReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Cannot access repository $owner/$repo (${resp.code}): ${extractGitHubError(body)}")
                }
                JSONObject(body).optString("default_branch", "main")
            }

            val commitSha = pushProjectToGitHub(
                projectDir = projectDir,
                ownerRepo = cleanRepo,
                branch = defaultBranch,
                token = token,
                includeBuildWorkflow = true,
                onLog = onLog
            ).getOrThrow()

            onLog(LogLevel.TASK, "Dispatching Gradle ${variant.gradleTask} workflow on GitHub Actions...")
            val dispatchPayload = JSONObject().apply {
                put("ref", defaultBranch)
                put("inputs", JSONObject().apply {
                    put("gradle_task", variant.gradleTask)
                })
            }
            val dispatchReq = githubRequest(
                "https://api.github.com/repos/$owner/$repo/actions/workflows/apk-forge-build.yml/dispatches",
                token
            ).post(dispatchPayload.toString().toRequestBody("application/json".toMediaType())).build()

            okHttpClient.newCall(dispatchReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    throw IllegalStateException("Workflow dispatch failed (${resp.code}): ${extractGitHubError(body)}")
                }
            }

            // Poll for workflow run
            var runId: Long? = null
            for (attempt in 1..15) {
                delay(2500)
                val runsReq = githubRequest(
                    "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=5",
                    token
                ).get().build()
                okHttpClient.newCall(runsReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val runs = JSONObject(resp.body?.string().orEmpty()).optJSONArray("workflow_runs")
                        if (runs != null) {
                            for (i in 0 until runs.length()) {
                                val run = runs.getJSONObject(i)
                                if (run.optString("head_sha") == commitSha || run.optString("path").contains("apk-forge-build.yml")) {
                                    runId = run.getLong("id")
                                    break
                                }
                            }
                        }
                    }
                }
                if (runId != null) break
                onLog(LogLevel.INFO, "Waiting for GitHub Actions runner assignment (attempt $attempt)...")
            }

            val activeRunId = runId ?: throw IllegalStateException("Timed out waiting for GitHub Actions run to start.")
            onLog(LogLevel.INFO, "Runner started (Run #$activeRunId). Streaming build steps...")

            val reportedSteps = mutableSetOf<String>()
            var conclusion = ""
            for (poll in 1..120) {
                delay(4000)
                val jobsReq = githubRequest(
                    "https://api.github.com/repos/$owner/$repo/actions/runs/$activeRunId/jobs",
                    token
                ).get().build()

                okHttpClient.newCall(jobsReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val jobs = JSONObject(resp.body?.string().orEmpty()).optJSONArray("jobs")
                        if (jobs != null && jobs.length() > 0) {
                            val job = jobs.getJSONObject(0)
                            val steps = job.optJSONArray("steps")
                            if (steps != null) {
                                for (s in 0 until steps.length()) {
                                    val step = steps.getJSONObject(s)
                                    val key = "${step.optString("name")}:${step.optString("status")}:${step.optString("conclusion")}"
                                    if (reportedSteps.add(key)) {
                                        val stepStatus = step.optString("status")
                                        val stepConclusion = step.optString("conclusion")
                                        val level = when (stepConclusion) {
                                            "failure" -> LogLevel.ERROR
                                            "success" -> LogLevel.SUCCESS
                                            else -> LogLevel.TASK
                                        }
                                        onLog(level, "[Step] ${step.optString("name")} -> $stepStatus ${if (stepConclusion.isNotBlank()) "($stepConclusion)" else ""}")
                                    }
                                }
                            }
                            if (job.optString("status") == "completed") {
                                conclusion = job.optString("conclusion")
                            }
                        }
                    }
                }
                if (conclusion.isNotBlank()) break
            }

            if (conclusion != "success") {
                throw IllegalStateException("GitHub Actions Gradle build finished with conclusion: ${conclusion.ifBlank { "timeout" }}")
            }

            // Download artifact zip
            onLog(LogLevel.TASK, "Fetching compiled APK artifact from GitHub Actions...")
            val artifactsReq = githubRequest(
                "https://api.github.com/repos/$owner/$repo/actions/runs/$activeRunId/artifacts",
                token
            ).get().build()

            val downloadUrl = okHttpClient.newCall(artifactsReq).execute().use { resp ->
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("artifacts")
                if (arr == null || arr.length() == 0) {
                    throw IllegalStateException("Build succeeded, but no APK artifact was uploaded.")
                }
                arr.getJSONObject(0).getString("archive_download_url")
            }

            if (!outputApkDir.exists()) outputApkDir.mkdirs()
            val safeName = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "app" }
            val outApk = File(outputApkDir, "${safeName}-${variant.name.lowercase()}-${System.currentTimeMillis()}.apk")

            val dlReq = githubRequest(downloadUrl, token).get().build()
            okHttpClient.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Artifact download failed (${resp.code})")
                val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty artifact stream")
                var foundApk = false
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            outApk.outputStream().use { fos -> zis.copyTo(fos) }
                            foundApk = true
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                if (!foundApk) throw IllegalStateException("Artifact archive contained no .apk file")
            }

            onLog(LogLevel.SUCCESS, "APK downloaded: ${outApk.name} (${outApk.length() / 1024} KB)")
            outApk
        }
    }

    suspend fun testRemoteGradleServer(serverUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = serverUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://")) {
                "Server URL must start with http:// or https://"
            }
            val builder = Request.Builder().url("$base/health")
            if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
                resp.body?.string()?.ifBlank { "Server reachable (HTTP 200)" } ?: "Server reachable (HTTP 200)"
            }
        }
    }

    suspend fun runRemoteGradleServerBuild(
        context: Context,
        projectDir: File,
        projectName: String,
        serverUrl: String,
        token: String,
        variant: BuildVariant,
        outputApkDir: File,
        onLog: (LogLevel, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val base = serverUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://")) {
                "Remote Gradle Server URL must start with http:// or https://"
            }

            val tempZip = File(context.cacheDir, "build_upload_${System.currentTimeMillis()}.zip")
            try {
                onLog(LogLevel.TASK, "Packaging project archive for remote Gradle build...")
                tempZip.outputStream().use { ZipProjectManager.zipDirectory(projectDir, it) }

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("variant", variant.name.lowercase())
                    .addFormDataPart("gradleTask", variant.gradleTask)
                    .addFormDataPart(
                        "project",
                        tempZip.name,
                        tempZip.asRequestBody("application/zip".toMediaType())
                    )
                    .build()

                val postReq = Request.Builder()
                    .url("$base/api/build")
                    .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
                    .post(body)
                    .build()

                onLog(LogLevel.INFO, "Uploading ${tempZip.length() / 1024} KB source archive to $base/api/build...")
                val jobId = okHttpClient.newCall(postReq).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException("Server rejected build (${resp.code}): $text")
                    JSONObject(text).getString("jobId")
                }

                onLog(LogLevel.INFO, "Remote build job queued: $jobId")
                var logOffset = 0
                var finalStatus = ""

                for (i in 1..150) {
                    delay(2000)
                    val statusReq = Request.Builder()
                        .url("$base/api/build/$jobId/status?offset=$logOffset")
                        .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
                        .get()
                        .build()

                    okHttpClient.newCall(statusReq).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Status poll error (${resp.code})")
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val logs = json.optJSONArray("logs")
                        if (logs != null) {
                            for (idx in 0 until logs.length()) {
                                val line = logs.getString(idx)
                                val lvl = when {
                                    line.contains("error", true) || line.contains("FAILED") -> LogLevel.ERROR
                                    line.contains("warning", true) -> LogLevel.WARN
                                    line.startsWith("> Task") -> LogLevel.TASK
                                    else -> LogLevel.INFO
                                }
                                onLog(lvl, line)
                            }
                            logOffset += logs.length()
                        }
                        val status = json.optString("status")
                        if (status == "success" || status == "failed") {
                            finalStatus = status
                        }
                    }
                    if (finalStatus.isNotBlank()) break
                }

                if (finalStatus != "success") {
                    throw IllegalStateException("Remote Gradle build failed or timed out.")
                }

                if (!outputApkDir.exists()) outputApkDir.mkdirs()
                val safeName = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "app" }
                val outApk = File(outputApkDir, "${safeName}-${variant.name.lowercase()}-${System.currentTimeMillis()}.apk")

                val dlReq = Request.Builder()
                    .url("$base/api/build/$jobId/apk")
                    .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
                    .get()
                    .build()

                okHttpClient.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("APK download failed (${resp.code})")
                    resp.body?.byteStream()?.use { input ->
                        outApk.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Empty APK response")
                }

                onLog(LogLevel.SUCCESS, "APK downloaded: ${outApk.name} (${outApk.length() / 1024} KB)")
                outApk
            } finally {
                tempZip.delete()
            }
        }
    }

    private fun githubRequest(url: String, token: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/vnd.github+json")
    }

    private fun extractGitHubError(body: String): String {
        return runCatching { JSONObject(body).optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body.take(160)
    }

    private fun buildGitHubActionsWorkflow(): String = """
name: APK Forge Build

on:
  workflow_dispatch:
    inputs:
      gradle_task:
        description: 'Gradle build task'
        required: true
        default: 'assembleDebug'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'
      - name: Make gradlew executable if present
        run: |
          if [ -f "./gradlew" ]; then chmod +x ./gradlew; fi
      - name: Build APK
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew ${'$'}{{ github.event.inputs.gradle_task }} --stacktrace
          else
            gradle ${'$'}{{ github.event.inputs.gradle_task }} --stacktrace
          fi
      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-apk
          path: '**/build/outputs/apk/**/*.apk'
          if-no-files-found: error
""".trimIndent()
}
