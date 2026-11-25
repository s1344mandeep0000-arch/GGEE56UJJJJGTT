package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProjectType
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.CompilerBlue
import com.example.ui.theme.GradleAmber

@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        onClick = onClick,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

object SyntaxHighlighter {
    private val codeKeywords = Regex(
        """\b(package|import|class|interface|object|fun|val|var|override|private|public|protected|internal|return|if|else|when|for|while|true|false|null|void|int|boolean|String|extends|implements|new|static|final|const|let|function|async|await|suspend|data|sealed|enum|plugins|android|dependencies|implementation|defaultConfig|buildTypes|compileOptions|kotlinOptions|buildFeatures)\b"""
    )
    private val stringRegex = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'""")
    private val commentRegex = Regex("""(//[^\n]*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->)""")
    private val xmlTagRegex = Regex("""</?[a-zA-Z0-9:_-]+|\s/?\s*>""")
    private val numberRegex = Regex("""\b\d+(\.\d+)?\b""")

    fun highlight(code: String, extension: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            when (extension.lowercase()) {
                "xml", "html" -> {
                    xmlTagRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = CompilerBlue, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
                    }
                    stringRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = AndroidEmerald), it.range.first, it.range.last + 1)
                    }
                    commentRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = Color(0xFF64748B)), it.range.first, it.range.last + 1)
                    }
                }
                else -> {
                    codeKeywords.findAll(code).forEach {
                        addStyle(SpanStyle(color = CompilerBlue, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
                    }
                    numberRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = GradleAmber), it.range.first, it.range.last + 1)
                    }
                    stringRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = AndroidEmerald), it.range.first, it.range.last + 1)
                    }
                    commentRegex.findAll(code).forEach {
                        addStyle(SpanStyle(color = Color(0xFF64748B)), it.range.first, it.range.last + 1)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, packageName: String, type: ProjectType) -> Unit
) {
    var appName by remember { mutableStateOf("MyForgeApp") }
    var packageName by remember { mutableStateOf("com.example.myforgeapp") }
    var selectedType by remember { mutableStateOf(ProjectType.KOTLIN_COMPOSE) }

    val isPackageValid = remember(packageName) {
        Regex("""^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$""").matches(packageName.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Android Project", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = {
                        appName = it
                        val sanitized = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (sanitized.isNotBlank()) {
                            packageName = "com.example.$sanitized"
                        }
                    },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_project_name_input")
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name (applicationId)") },
                    isError = !isPackageValid,
                    supportingText = {
                        if (!isPackageValid) Text("Use reverse-domain format, e.g. com.example.myapp")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_project_package_input")
                )
                Text("Project Template", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ProjectType.KOTLIN_COMPOSE,
                        ProjectType.JAVA_VIEWS,
                        ProjectType.WEB_HYBRID
                    ).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text("${type.badge}  ${type.label}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("template_chip_${type.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(appName, packageName, selectedType) },
                enabled = appName.isNotBlank() && isPackageValid,
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GitHubRepoDialog(
    title: String,
    confirmLabel: String,
    initialRepo: String = "",
    hasSavedToken: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ownerRepo: String, branch: String, token: String) -> Unit
) {
    var ownerRepo by remember { mutableStateOf(initialRepo) }
    var branch by remember { mutableStateOf("main") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ownerRepo,
                    onValueChange = { ownerRepo = it },
                    label = { Text("Repository (owner/repo)") },
                    placeholder = { Text("octocat/hello-world") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_branch_input")
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (hasSavedToken) "Token (leave blank to use saved)" else "GitHub Token (required for private/push)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(ownerRepo, branch, token) },
                enabled = ownerRepo.contains("/"),
                modifier = Modifier.testTag("confirm_github_dialog_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
