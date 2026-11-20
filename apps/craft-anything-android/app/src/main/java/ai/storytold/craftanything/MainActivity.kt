package ai.storytold.craftanything

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF15151D)
private val Violet = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9A97A8)
private const val MAX_IMPORT_BYTES = 512L * 1024L * 1024L

class MainActivity : ComponentActivity() {
    private external fun nativeInitialize(appDataDir: String): String
    private external fun nativeVersion(): String
    private external fun nativeToolCatalog(): String
    private external fun nativeCallTool(toolId: String, paramsJson: String, consentGranted: Boolean): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nativeReady = runCatching { System.loadLibrary("craft_anything_android") }.isSuccess
        setContent {
            var selected by remember { mutableStateOf("Home") }
            var coreVersion by remember {
                mutableStateOf(if (nativeReady) "Connecting to Rust core…" else "Rust core not built yet")
            }
            var toolCount by remember { mutableStateOf(0) }
            var toolCatalogJson by remember { mutableStateOf("[]") }
            var toolResult by remember { mutableStateOf("") }
            var projectsJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"projects\":[]}}}") }
            var projectsLoading by remember { mutableStateOf(false) }
            var projectsError by remember { mutableStateOf("") }
            var evidenceJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"records\":[]}}}") }
            var unknownsJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"items\":[]}}}") }
            var evidenceLoading by remember { mutableStateOf(false) }
            var evidenceError by remember { mutableStateOf("") }
            var projectDetailsJson by remember { mutableStateOf("") }
            var targetResult by remember { mutableStateOf("No file opened yet.") }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val refreshProjects: () -> Unit = {
                if (nativeReady && coreVersion.startsWith("Rust core")) {
                    scope.launch {
                        projectsLoading = true
                        projectsError = ""
                        val response = withContext(Dispatchers.IO) {
                            runCatching { JSONObject(nativeCallTool("project_list", "{}", false)).toString(2) }
                        }
                        response.onSuccess { projectsJson = it }.onFailure {
                            projectsError = it.message ?: "Couldn't load projects."
                        }
                        projectsLoading = false
                    }
                }
            }
            val refreshEvidence: () -> Unit = {
                if (nativeReady && coreVersion.startsWith("Rust core")) {
                    scope.launch {
                        evidenceLoading = true
                        evidenceError = ""
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                val evidence = JSONObject(nativeCallTool("core_evidence_show", """{"detail":"summary"}""", false)).toString(2)
                                val unknowns = JSONObject(nativeCallTool("core_unknowns_show", "{}", false)).toString(2)
                                evidence to unknowns
                            }
                        }
                        response.onSuccess {
                            evidenceJson = it.first
                            unknownsJson = it.second
                        }.onFailure {
                            evidenceError = it.message ?: "Couldn't load the session evidence."
                        }
                        evidenceLoading = false
                    }
                }
            }
            val openProject: (String) -> Unit = { folder ->
                scope.launch {
                    projectDetailsJson = ""
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            val params = JSONObject().put("project", folder).toString()
                            JSONObject(nativeCallTool("project_show", params, false)).toString(2)
                        }
                    }
                    response.onSuccess { projectDetailsJson = it }.onFailure {
                        projectsError = it.message ?: "Couldn't open this project."
                    }
                }
            }
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null && nativeReady) {
                    scope.launch {
                        targetResult = "Copying file into app storage…"
                        val opened = withContext(Dispatchers.IO) {
                            runCatching {
                                val folder = File(context.filesDir, "targets")
                                if (!folder.exists() && !folder.mkdirs()) {
                                    throw IOException("Couldn't create the private import directory.")
                                }
                                val displayName = context.contentResolver.query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null,
                                    null,
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                } ?: uri.lastPathSegment ?: "Selected file"
                                val copy = File.createTempFile(
                                    "target-",
                                    FileImport.safeSuffix(displayName),
                                    folder
                                )
                                try {
                                    val input = context.contentResolver.openInputStream(uri)
                                        ?: throw IOException("The selected document provider returned no readable stream.")
                                    val copiedBytes = FileImport.copyBounded(input, copy, MAX_IMPORT_BYTES)
                                    val parameters = JSONObject().put("path", copy.absolutePath).toString()
                                    val payload = JSONObject(nativeCallTool("core_target_open", parameters, false))
                                    if (!payload.optBoolean("ok")) {
                                        copy.delete()
                                        throw IOException(payload.optString("error", "The Rust engine couldn't open this file."))
                                    }

                                    val target = payload.optJSONObject("output")?.optJSONObject("result")
                                    val title = target?.optString("name")?.takeIf { it.isNotBlank() } ?: displayName
                                    val kind = target?.optString("kind")?.takeIf { it.isNotBlank() } ?: "target"
                                    "Opened $title · $kind · ${formatByteCount(copiedBytes)}"
                                } catch (error: Exception) {
                                    copy.delete()
                                    throw error
                                }
                            }
                        }
                        opened.onSuccess { message ->
                            targetResult = message
                        }.onFailure { error ->
                            targetResult = error.message ?: "Couldn't open the selected file."
                        }
                    }
                } else if (uri != null) {
                    targetResult = "The Rust core is unavailable, so this file wasn't imported."
                }
            }

            LaunchedEffect(coreVersion) {
                if (coreVersion.startsWith("Rust core")) {
                    refreshEvidence()
                }
            }

            LaunchedEffect(nativeReady) {
                if (nativeReady) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val initialized = nativeInitialize(context.filesDir.absolutePath)
                            if (initialized != "ok") {
                                throw IllegalStateException(initialized.removePrefix("error:"))
                            }
                            val catalog = nativeToolCatalog()
                            val projectList = nativeCallTool("project_list", "{}", false)
                            Triple(nativeVersion(), catalog, projectList)
                        }
                    }
                    result.onSuccess {
                        coreVersion = "Rust core v${it.first}"
                        toolCatalogJson = it.second
                        toolCount = JSONArray(it.second).length()
                        projectsJson = it.third
                    }.onFailure {
                        coreVersion = "Rust bridge error: ${it.message ?: "unknown error"}"
                    }
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Violet,
                    background = Ink,
                    surface = Panel,
                    onBackground = Color(0xFFF5F2FF),
                    onSurface = Color(0xFFF5F2FF)
                )
            ) {
                Scaffold(
                    containerColor = Ink,
                    bottomBar = {
                        NavigationBar(containerColor = Panel) {
                            listOf(
                                Triple("Home", Icons.Rounded.Home, "Home"),
                                Triple("Projects", Icons.Rounded.FolderOpen, "Projects"),
                                Triple("Evidence", Icons.Rounded.Assessment, "Evidence"),
                                Triple("Tools", Icons.Rounded.Terminal, "Tools"),
                                Triple("Settings", Icons.Rounded.Settings, "Settings")
                            ).forEach { (label, icon, route) ->
                                NavigationBarItem(
                                    selected = selected == route,
                                    onClick = { selected = route },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(42.dp).background(Violet.copy(alpha = .16f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Violet)
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("CRAFT ANYTHING", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
                                Text(selected, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        when (selected) {
                            "Home" -> HomeScreen(
                                coreVersion,
                                toolCount,
                                targetResult,
                                { filePicker.launch(arrayOf("*/*")) },
                                { selected = "Tools" }
                            )
                            "Projects" -> ProjectsScreen(
                                projectsJson = projectsJson,
                                loading = projectsLoading,
                                error = projectsError,
                                detailsJson = projectDetailsJson,
                                onRefresh = refreshProjects,
                                onOpenProject = openProject,
                                onCloseDetails = { projectDetailsJson = "" },
                                onAnswer = { projectFolder, requestId, choice, text, allow ->
                                    projectsLoading = true
                                    projectsError = ""
                                    scope.launch {
                                        val refreshed = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val answerParams = JSONObject()
                                                    .put("project", projectFolder)
                                                    .put("id", requestId)
                                                if (choice != null) answerParams.put("choice", choice)
                                                if (!text.isNullOrBlank()) answerParams.put("text", text)
                                                if (allow != null) answerParams.put("allow", allow)
                                                val answer = JSONObject(
                                                    nativeCallTool("project_inbox_answer", answerParams.toString(), false)
                                                )
                                                if (!answer.optBoolean("ok")) {
                                                    throw IOException(answer.optString("error", "Couldn't save the answer."))
                                                }

                                                val showParams = JSONObject().put("project", projectFolder).toString()
                                                val detail = JSONObject(nativeCallTool("project_show", showParams, false))
                                                if (!detail.optBoolean("ok")) {
                                                    throw IOException(detail.optString("error", "The answer was saved, but the project couldn't be refreshed."))
                                                }
                                                val list = nativeCallTool("project_list", "{}", false)
                                                Pair(detail.toString(2), list)
                                            }
                                        }
                                        refreshed.onSuccess {
                                            projectDetailsJson = it.first
                                            projectsJson = it.second
                                        }.onFailure { error ->
                                            projectsError = error.message ?: "Couldn't send the project answer."
                                        }
                                        projectsLoading = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            "Evidence" -> EvidenceScreen(
                                evidenceJson = evidenceJson,
                                unknownsJson = unknownsJson,
                                loading = evidenceLoading,
                                error = evidenceError,
                                onRefresh = refreshEvidence,
                                modifier = Modifier.weight(1f)
                            )
                            "Tools" -> ToolsScreen(
                                catalogJson = toolCatalogJson,
                                coreVersion = coreVersion,
                                result = toolResult,
                                onInvoke = { toolId, paramsJson, consentGranted ->
                                    toolResult = "Running $toolId…"
                                    scope.launch {
                                        val response = withContext(Dispatchers.IO) {
                                            runCatching { JSONObject(nativeCallTool(toolId, paramsJson, consentGranted)) }
                                        }
                                        response.onSuccess { payload ->
                                            toolResult = if (payload.optBoolean("ok")) {
                                                payload.optJSONObject("output")?.toString(2) ?: payload.toString(2)
                                            } else {
                                                payload.optString("error", "The engine couldn't complete this action.")
                                            }
                                            if (payload.optBoolean("ok")) refreshEvidence()
                                        }.onFailure { error ->
                                            toolResult = error.message ?: "The native bridge call failed."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            else -> EmptyScreen("Settings", "Mobile permissions, AI providers and connected companion devices will live here.")
                        }
                    }
                }
            }
        }
    }
}

private fun formatByteCount(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun HomeScreen(
    coreVersion: String,
    toolCount: Int,
    targetResult: String,
    openFile: () -> Unit,
    openTools: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Understand it. Rebuild it.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Inspect software, collect evidence and build something of your own — from your phone.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("ENGINE STATUS", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(coreVersion, fontWeight = FontWeight.SemiBold)
                if (toolCount > 0) Text("$toolCount registered tools", color = Violet)
                Spacer(Modifier.height(14.dp))
                Text("The Android shell is in place. Core workflows are being connected in stages.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Inspect a file", fontWeight = FontWeight.Bold)
                Text(targetResult, color = Muted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = openFile, enabled = coreVersion.startsWith("Rust core")) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Text("  Choose file")
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = openTools),
            colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = .13f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Violet)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Explore the tool registry", fontWeight = FontWeight.Bold)
                    Text("See what the Rust engine exposes", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("→", color = Violet)
            }
        }
    }
}

@Composable
private fun EmptyScreen(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, color = Muted)
    }
}
