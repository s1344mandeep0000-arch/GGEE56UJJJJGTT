package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "apk_forge_settings")

class StudioPreferences(private val context: Context) {

    private object Keys {
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val REMOTE_SERVER_URL = stringPreferencesKey("remote_server_url")
        val REMOTE_SERVER_TOKEN = stringPreferencesKey("remote_server_token")
        val DEFAULT_COMPILE_SDK = intPreferencesKey("default_compile_sdk")
        val DEFAULT_MIN_SDK = intPreferencesKey("default_min_sdk")
        val DEFAULT_TARGET_SDK = intPreferencesKey("default_target_sdk")
        val GRADLE_JVM_ARGS = stringPreferencesKey("gradle_jvm_args")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val AUTO_VALIDATE_ON_SAVE = booleanPreferencesKey("auto_validate_on_save")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
            githubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
            remoteServerUrl = prefs[Keys.REMOTE_SERVER_URL] ?: "",
            remoteServerToken = prefs[Keys.REMOTE_SERVER_TOKEN] ?: "",
            defaultCompileSdk = prefs[Keys.DEFAULT_COMPILE_SDK] ?: 35,
            defaultMinSdk = prefs[Keys.DEFAULT_MIN_SDK] ?: 24,
            defaultTargetSdk = prefs[Keys.DEFAULT_TARGET_SDK] ?: 35,
            gradleJvmArgs = prefs[Keys.GRADLE_JVM_ARGS] ?: "-Xmx2048m",
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 13,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            autoValidateOnSave = prefs[Keys.AUTO_VALIDATE_ON_SAVE] ?: true,
            darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true
        )
    }

    suspend fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        context.studioDataStore.edit { prefs ->
            val current = StudioSettings(
                githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
                githubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
                githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
                remoteServerUrl = prefs[Keys.REMOTE_SERVER_URL] ?: "",
                remoteServerToken = prefs[Keys.REMOTE_SERVER_TOKEN] ?: "",
                defaultCompileSdk = prefs[Keys.DEFAULT_COMPILE_SDK] ?: 35,
                defaultMinSdk = prefs[Keys.DEFAULT_MIN_SDK] ?: 24,
                defaultTargetSdk = prefs[Keys.DEFAULT_TARGET_SDK] ?: 35,
                gradleJvmArgs = prefs[Keys.GRADLE_JVM_ARGS] ?: "-Xmx2048m",
                editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 13,
                showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
                autoValidateOnSave = prefs[Keys.AUTO_VALIDATE_ON_SAVE] ?: true,
                darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true
            )
            val updated = transform(current)
            prefs[Keys.GITHUB_TOKEN] = updated.githubToken
            prefs[Keys.GITHUB_USERNAME] = updated.githubUsername
            prefs[Keys.GITHUB_REPO] = updated.githubRepo
            prefs[Keys.REMOTE_SERVER_URL] = updated.remoteServerUrl
            prefs[Keys.REMOTE_SERVER_TOKEN] = updated.remoteServerToken
            prefs[Keys.DEFAULT_COMPILE_SDK] = updated.defaultCompileSdk
            prefs[Keys.DEFAULT_MIN_SDK] = updated.defaultMinSdk
            prefs[Keys.DEFAULT_TARGET_SDK] = updated.defaultTargetSdk
            prefs[Keys.GRADLE_JVM_ARGS] = updated.gradleJvmArgs
            prefs[Keys.EDITOR_FONT_SIZE] = updated.editorFontSizeSp
            prefs[Keys.SHOW_LINE_NUMBERS] = updated.showLineNumbers
            prefs[Keys.AUTO_VALIDATE_ON_SAVE] = updated.autoValidateOnSave
            prefs[Keys.DARK_THEME_ENABLED] = updated.darkThemeEnabled
        }
    }
}
