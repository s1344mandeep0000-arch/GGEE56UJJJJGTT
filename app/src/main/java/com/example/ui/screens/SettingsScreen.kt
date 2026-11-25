package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.StudioSettings
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.GradleAmber

@Composable
fun SettingsScreen(
    settings: StudioSettings,
    onUpdateSettings: ((StudioSettings) -> StudioSettings) -> Unit,
    onVerifyGitHubToken: (String) -> Unit,
    onTestRemoteServer: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ghToken by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var ghRepo by remember(settings.githubRepo) { mutableStateOf(settings.githubRepo) }
    var serverUrl by remember(settings.remoteServerUrl) { mutableStateOf(settings.remoteServerUrl) }
    var serverToken by remember(settings.remoteServerToken) { mutableStateOf(settings.remoteServerToken) }
    var compileSdkText by remember(settings.defaultCompileSdk) { mutableStateOf(settings.defaultCompileSdk.toString()) }
    var minSdkText by remember(settings.defaultMinSdk) { mutableStateOf(settings.defaultMinSdk.toString()) }
    var targetSdkText by remember(settings.defaultTargetSdk) { mutableStateOf(settings.defaultTargetSdk.toString()) }
    var jvmArgs by remember(settings.gradleJvmArgs) { mutableStateOf(settings.gradleJvmArgs) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // GitHub Actions Cloud Builder
        item {
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GitHub Actions Cloud Runner", style = MaterialTheme.typography.titleMedium)
                    if (settings.githubUsername.isNotBlank()) {
                        StatusBadge(text = "@${settings.githubUsername}", color = AndroidEmerald)
                    } else {
                        StatusBadge(text = "NOT CONNECTED", color = GradleAmber)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connect a GitHub Personal Access Token (repo + workflow scopes) and a repository to compile real APKs on Ubuntu + JDK 17 + Android SDK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = ghToken,
                    onValueChange = { ghToken = it },
                    label = { Text("GitHub Personal Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_github_token_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ghRepo,
                    onValueChange = { ghRepo = it },
                    label = { Text("Default Build Repository (owner/repo)") },
                    placeholder = { Text("username/apk-forge-runner") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_github_repo_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onVerifyGitHubToken(ghToken) },
                        enabled = ghToken.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("verify_github_token_button")
                    ) {
                        Text("Verify Token")
                    }
                    OutlinedButton(
                        onClick = {
                            onUpdateSettings {
                                it.copy(githubToken = ghToken.trim(), githubRepo = ghRepo.trim())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_github_settings_button")
                    ) {
                        Text("Save Repo")
                    }
                }
            }
        }

        // Custom Remote Gradle Server
        item {
            StudioCard {
                Text("Remote Gradle Build Server", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Optionally connect your own Gradle HTTP build server (/api/build, /health) for private builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server Base URL") },
                    placeholder = { Text("https://gradle-runner.example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_server_url_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverToken,
                    onValueChange = { serverToken = it },
                    label = { Text("Bearer Auth Token (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onTestRemoteServer(serverUrl, serverToken) },
                        enabled = serverUrl.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_remote_server_button")
                    ) {
                        Text("Test Connection")
                    }
                    OutlinedButton(
                        onClick = {
                            onUpdateSettings {
                                it.copy(remoteServerUrl = serverUrl.trim(), remoteServerToken = serverToken.trim())
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save URL")
                    }
                }
            }
        }

        // SDK & Gradle Defaults
        item {
            StudioCard {
                Text("Android SDK & Gradle Defaults", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = compileSdkText,
                        onValueChange = { compileSdkText = it },
                        label = { Text("compileSdk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minSdkText,
                        onValueChange = { minSdkText = it },
                        label = { Text("minSdk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetSdkText,
                        onValueChange = { targetSdkText = it },
                        label = { Text("targetSdk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jvmArgs,
                    onValueChange = { jvmArgs = it },
                    label = { Text("Gradle JVM Args") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                defaultCompileSdk = compileSdkText.toIntOrNull() ?: 35,
                                defaultMinSdk = minSdkText.toIntOrNull() ?: 24,
                                defaultTargetSdk = targetSdkText.toIntOrNull() ?: 35,
                                gradleJvmArgs = jvmArgs.ifBlank { "-Xmx2048m" }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_sdk_defaults_button")
                ) {
                    Text("Save SDK Defaults")
                }
            }
        }

        // Editor & Appearance
        item {
            StudioCard {
                Text("Editor & Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Code Editor Font Size: ${settings.editorFontSizeSp}sp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompilerBlue
                )
                Slider(
                    value = settings.editorFontSizeSp.toFloat(),
                    onValueChange = { value ->
                        onUpdateSettings { it.copy(editorFontSizeSp = value.toInt()) }
                    },
                    valueRange = 10f..18f,
                    steps = 7
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Line Numbers", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.showLineNumbers,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showLineNumbers = checked) }
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Obsidian Dark Theme", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.darkThemeEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(darkThemeEnabled = checked) }
                        },
                        modifier = Modifier.testTag("dark_theme_switch")
                    )
                }
            }
        }
    }
}
