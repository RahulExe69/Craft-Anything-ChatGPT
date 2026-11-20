package ai.storytold.craftanything

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF15151D)
private val Violet = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9A97A8)
private const val MAX_IMPORT_BYTES = 512L * 1024L * 1024L

private fun requireOkEnvelope(response: String, fallbackMessage: String): JSONObject {
    val payload = JSONObject(response)
    if (!payload.optBoolean("ok")) {
        throw IOException(payload.optString("error").takeIf { it.isNotBlank() } ?: fallbackMessage)
    }
    return payload
}

private val projectTextExtensions = setOf(
    "kt", "kts", "java", "xml", "json", "md", "txt", "html", "htm", "js", "jsx",
    "ts", "tsx", "css", "scss", "sass", "yml", "yaml", "toml", "gradle", "properties",
    "sh", "py", "rs", "swift", "dart", "go", "c", "h", "cpp", "sql", "svg", "vue",
    "svelte", "graphql", "gql", "proto", "ini", "cfg", "conf", "gitignore", "env"
)
private val skippedProjectDirectories = setOf(
    ".git", "node_modules", ".gradle", ".idea", "Pods", "target", ".next", ".turbo", ".cache", "build"
)

internal fun listProjectFilesJson(appFolder: String): String = runCatching {
    val root = File(appFolder).canonicalFile
    if (!root.isDirectory) throw IOException("The project output folder isn't available yet.")
    val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
    val pending = java.util.ArrayDeque<Pair<File, Int>>()
    pending.add(root to 0)
    val files = JSONArray()
    var examined = 0
    var truncated = false
    var total = 0

    while (pending.isNotEmpty() && total < 300 && examined < 5000) {
        val (directory, depth) = pending.removeFirst()
        if (depth > 8) {
            truncated = true
            continue
        }
        val children = directory.listFiles()?.sortedBy { it.name.lowercase() } ?: continue
        for (child in children) {
            examined += 1
            if (examined >= 5000) {
                truncated = true
                break
            }
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue
            val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
            if (!canonical.path.startsWith(rootPrefix)) continue
            if (canonical.isDirectory) {
                if (canonical.name !in skippedProjectDirectories && !canonical.name.startsWith(".git")) {
                    pending.add(canonical to depth + 1)
                }
                continue
            }
            if (!canonical.isFile) continue
            val extension = canonical.extension.lowercase()
            val kind = if (extension in projectTextExtensions || canonical.name.startsWith(".env")) "text" else "binary"
            val relative = canonical.relativeTo(root).path.replace(File.separatorChar, '/')
            files.put(
                JSONObject()
                    .put("path", relative)
                    .put("name", canonical.name)
                    .put("size", canonical.length())
                    .put("kind", kind)
            )
            total += 1
            if (total >= 300) {
                truncated = pending.isNotEmpty() || children.lastOrNull() != child
                break
            }
        }
    }
    JSONObject().put("ok", true).put(
        "output",
        JSONObject().put("result", JSONObject().put("files", files).put("truncated", truncated).put("scanned", examined))
    ).toString()
}.getOrElse { error ->
    JSONObject().put("ok", false).put("error", error.message ?: "Couldn't read this project's files.").toString()
}

internal fun readProjectFileJson(appFolder: String, relativePath: String): String = runCatching {
    if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.contains('\\')) {
        throw IOException("That file path isn't valid.")
    }
    val root = File(appFolder).canonicalFile
    if (!root.isDirectory) throw IOException("The project output folder isn't available.")
    val candidate = File(root, relativePath).canonicalFile
    val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
    if (!candidate.path.startsWith(rootPrefix) || !candidate.isFile || java.nio.file.Files.isSymbolicLink(File(root, relativePath).toPath())) {
        throw IOException("That file isn't inside the project's output folder.")
    }
    if (candidate.length() > 256L * 1024L) {
        throw IOException("This file is larger than the 256 KB source viewer limit.")
    }
    val bytes = candidate.readBytes()
    if (bytes.any { it == 0.toByte() }) throw IOException("This looks like a binary file and can't be shown as source text.")
    val text = String(bytes, Charsets.UTF_8)
    JSONObject().put("ok", true).put(
        "output",
        JSONObject().put(
            "result",
            JSONObject()
                .put("path", relativePath)
                .put("content", text)
                .put("size", bytes.size)
        )
    ).toString()
}.getOrElse { error ->
    JSONObject().put("ok", false).put("error", error.message ?: "Couldn't open this file.").toString()
}

internal fun writeProjectFileJson(appFolder: String, relativePath: String, content: String): String = runCatching {
    if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.contains('\\') ||
        relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
    ) {
        throw IOException("That file path isn't valid.")
    }
    val root = File(appFolder).canonicalFile
    if (!root.isDirectory) throw IOException("The project output folder isn't available.")
    val original = File(root, relativePath)
    if (java.nio.file.Files.isSymbolicLink(original.toPath())) {
        throw IOException("Symbolic links can't be edited from the mobile source editor.")
    }
    val candidate = original.canonicalFile
    val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
    if (!candidate.path.startsWith(rootPrefix) || !candidate.isFile) {
        throw IOException("Only existing files inside the project's output folder can be edited.")
    }
    if (candidate.length() > 256L * 1024L) {
        throw IOException("This file is larger than the 256 KB source editor limit.")
    }
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size > 256 * 1024) throw IOException("This file is larger than the 256 KB source editor limit.")
    if (bytes.any { it == 0.toByte() }) throw IOException("Binary content can't be saved from the source editor.")
    val parent = candidate.parentFile ?: throw IOException("The file has no parent folder.")
    val temp = File.createTempFile(".craft-save-", ".tmp", parent)
    try {
        temp.writeBytes(bytes)
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                candidate.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temp.toPath(),
                candidate.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    } finally {
        temp.delete()
    }
    JSONObject().put("ok", true).put(
        "output",
        JSONObject().put(
            "result",
            JSONObject().put("path", relativePath).put("content", content).put("size", bytes.size)
        )
    ).toString()
}.getOrElse { error ->
    JSONObject().put("ok", false).put("error", error.message ?: "Couldn't save this file.").toString()
}

class MainActivity : ComponentActivity() {
    private external fun nativeInitialize(appDataDir: String): String
    private external fun nativeVersion(): String
    private external fun nativeToolCatalog(): String
    private external fun nativeCallTool(toolId: String, paramsJson: String, consentGranted: Boolean): String
    private external fun nativeAiStatus(): String
    private external fun nativeAiAddBackend(inputJson: String): String
    private external fun nativeAiSetBackendKey(name: String, key: String): String
    private external fun nativeAiSetRole(role: String, name: String): String
    private external fun nativeAiSetEnabled(name: String, enabled: Boolean): String
    private external fun nativeAiRemoveBackend(name: String): String
    private external fun nativeAiStartChat(backend: String): String
    private external fun nativeAiSendChatMessage(message: String): String
    private external fun nativeAiPollChatEvents(): String
    private external fun nativeAiAnswerConsent(callId: String, allow: Boolean): String
    private external fun nativeAiStopChat(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nativeReady = runCatching { System.loadLibrary("craft_anything_android") }.isSuccess
        val notificationProjectFolder = intent.getStringExtra(BuildExecutionService.EXTRA_PROJECT_FOLDER)
        setContent {
            var selected by remember { mutableStateOf(if (notificationProjectFolder.isNullOrBlank()) "Home" else "Projects") }
            var coreVersion by remember {
                mutableStateOf(if (nativeReady) "Connecting to Rust core…" else "Rust core not built yet")
            }
            var toolCount by remember { mutableStateOf(0) }
            var toolCatalogJson by remember { mutableStateOf("[]") }
            var toolResult by remember { mutableStateOf("") }
            var aiStatusJson by remember { mutableStateOf("""{"ok":true,"output":{"backends":[],"roles":{}}}""") }
            var aiSettingsLoading by remember { mutableStateOf(false) }
            var aiSettingsMessage by remember { mutableStateOf("") }
            var selectedAiBackend by remember { mutableStateOf("") }
            var selectedBuildBackend by remember { mutableStateOf("") }
            var buildStarting by remember { mutableStateOf(false) }
            var buildFeedback by remember { mutableStateOf("") }
            var aiChatStarted by remember { mutableStateOf(false) }
            var aiChatBusy by remember { mutableStateOf(false) }
            var aiChatStatus by remember { mutableStateOf("Connect a provider to start.") }
            var aiChatMessages by remember { mutableStateOf(emptyList<AiChatMessage>()) }
            var aiPendingConsent by remember { mutableStateOf<AiPendingConsent?>(null) }
            var projectsJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"projects\":[]}}}") }
            var projectsLoading by remember { mutableStateOf(false) }
            var projectsError by remember { mutableStateOf("") }
            val projectsListRequestGeneration = remember { AtomicInteger(0) }
            var evidenceJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"records\":[]}}}") }
            var unknownsJson by remember { mutableStateOf("{\"ok\":true,\"output\":{\"result\":{\"items\":[]}}}") }
            var evidenceLoading by remember { mutableStateOf(false) }
            var evidenceError by remember { mutableStateOf("") }
            var projectDetailsJson by remember { mutableStateOf("") }
            var projectRequestGeneration by remember { mutableStateOf(0) }
            var selectedProjectFolder by remember { mutableStateOf<String?>(null) }
            var projectPollGeneration by remember { mutableStateOf(0) }
            var pendingProjectFolder by remember { mutableStateOf<String?>(notificationProjectFolder) }
            var buildStatusJson by remember { mutableStateOf("") }
            var buildActivityJson by remember { mutableStateOf("") }
            var buildActivityError by remember { mutableStateOf("") }
            var buildActivityMessage by remember { mutableStateOf("") }
            var projectFilesJson by remember { mutableStateOf("""{"ok":true,"output":{"result":{"files":[],"truncated":false}}}""") }
            var projectFilesLoading by remember { mutableStateOf(false) }
            var projectFilesError by remember { mutableStateOf("") }
            var projectFilesFolder by remember { mutableStateOf("") }
            var selectedProjectFileJson by remember { mutableStateOf("") }
            var selectedProjectFileLoading by remember { mutableStateOf(false) }
            var projectFileSaving by remember { mutableStateOf(false) }
            var projectFileSaveMessage by remember { mutableStateOf("") }
            var previewUrl by remember { mutableStateOf("") }
            var previewLoading by remember { mutableStateOf(false) }
            var previewError by remember { mutableStateOf("") }
            var previewFolder by remember { mutableStateOf("") }
            var targetResult by remember { mutableStateOf("No file opened yet.") }
            var importedTargetPath by remember { mutableStateOf("") }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { }
            val refreshProjects: () -> Unit = {
                if (nativeReady && coreVersion.startsWith("Rust core")) {
                    val requestGeneration = projectsListRequestGeneration.incrementAndGet()
                    projectsLoading = true
                    projectsError = ""
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                requireOkEnvelope(nativeCallTool("project_list", "{}", false), "Couldn't load projects.").toString(2)
                            }
                        }
                        if (projectsListRequestGeneration.get() == requestGeneration) {
                            response.onSuccess { projectsJson = it }.onFailure {
                                projectsError = it.message ?: "Couldn't load projects."
                            }
                            projectsLoading = false
                        }
                    }
                }
            }

            val refreshAi: () -> Unit = {
                if (nativeReady && coreVersion.startsWith("Rust core v")) {
                    scope.launch {
                        aiSettingsLoading = true
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                requireOkEnvelope(nativeAiStatus(), "Couldn't load AI providers.").toString(2)
                            }
                        }
                        response.onSuccess { aiStatusJson = it }.onFailure {
                            aiSettingsMessage = it.message ?: "Couldn't load AI providers."
                        }
                        aiSettingsLoading = false
                    }
                }
            }
            val runAiSettingsAction: (String, () -> String) -> Unit = { successMessage, action ->
                scope.launch {
                    aiSettingsLoading = true
                    aiSettingsMessage = ""
                    val response = withContext(Dispatchers.IO) {
                        runCatching { requireOkEnvelope(action(), "Couldn't update AI settings.") }
                    }
                    response.onSuccess {
                        aiSettingsMessage = successMessage
                        refreshAi()
                    }.onFailure {
                        aiSettingsMessage = it.message ?: "Couldn't update AI settings."
                    }
                    aiSettingsLoading = false
                }
            }
            val addAiBackend: (String, String, String, String, String) -> Unit = { name, kind, model, baseUrl, key ->
                val input = JSONObject()
                    .put("name", name)
                    .put("kind", kind)
                    .put("model", model)
                    .put("base_url", baseUrl.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("key", key.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                runAiSettingsAction("Provider saved. You can now start an AI conversation.") {
                    nativeAiAddBackend(input.toString())
                }
            }
            val setAiRole: (String, String) -> Unit = { role, name ->
                runAiSettingsAction("$name is now assigned to the $role role.") {
                    nativeAiSetRole(role, name)
                }
            }
            val setAiEnabled: (String, Boolean) -> Unit = { name, enabled ->
                runAiSettingsAction(if (enabled) "$name enabled." else "$name disabled.") {
                    nativeAiSetEnabled(name, enabled)
                }
            }
            val removeAiBackend: (String) -> Unit = { name ->
                runAiSettingsAction("$name removed.") { nativeAiRemoveBackend(name) }
            }

            val startBuild: (String) -> Unit = { paramsJson ->
                if (!buildStarting) {
                    scope.launch {
                        buildStarting = true
                        buildFeedback = "Handing your build to the background runner…"
                        val launch = runCatching {
                            if (BuildExecutionService.hasActiveBuild(context)) {
                                throw IllegalStateException("A build is already running. Wait for it to finish before starting another.")
                            }
                            val params = JSONObject(paramsJson)
                            if (params.optString("project").isBlank() && params.optString("project_folder").isBlank()) {
                                val folder = File(File(context.filesDir, "projects"), "build-" + java.util.UUID.randomUUID().toString())
                                params.put("project_folder", folder.absolutePath)
                            }
                            val folder = params.optString("project").ifBlank { params.optString("project_folder") }
                            if (folder.isBlank()) throw IllegalArgumentException("The project folder couldn't be prepared.")
                            val serviceIntent = android.content.Intent(context, BuildExecutionService::class.java).apply {
                                action = BuildExecutionService.ACTION_START
                                putExtra(BuildExecutionService.EXTRA_PARAMS, params.toString())
                            }
                            context.startForegroundService(serviceIntent)
                            folder
                        }
                        launch.onSuccess { folder ->
                            buildFeedback = "Build running in the background. You can follow it in Projects."
                            selected = "Projects"
                            projectPollGeneration += 1
                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repeat(40) {
                                        if (File(folder, "craft.json").isFile) return@withContext
                                        delay(250)
                                    }
                                }
                                pendingProjectFolder = folder
                                refreshProjects()
                                buildStarting = false
                            }
                        }.onFailure { error ->
                            buildFeedback = error.message ?: "Craft Anything couldn't start this build."
                            buildStarting = false
                        }
                    }
                }
            }
            val startAiChat: (String) -> Unit = { backend ->
                scope.launch {
                    aiChatStatus = "Connecting…"
                    val response = withContext(Dispatchers.IO) {
                        runCatching { requireOkEnvelope(nativeAiStartChat(backend), "Couldn't start the AI conversation.") }
                    }
                    response.onSuccess {
                        aiChatMessages = emptyList()
                        aiPendingConsent = null
                        aiChatStarted = true
                        aiChatBusy = false
                        aiChatStatus = "Ready — ask away."
                    }.onFailure {
                        aiChatStatus = it.message ?: "Couldn't start the AI conversation."
                    }
                }
            }
            val sendAiMessage: (String) -> Unit = { message ->
                if (message.isNotBlank() && aiChatStarted && !aiChatBusy) {
                    aiChatMessages = aiChatMessages + AiChatMessage("user", message) + AiChatMessage("assistant", "")
                    aiChatBusy = true
                    aiChatStatus = "Thinking…"
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            runCatching { requireOkEnvelope(nativeAiSendChatMessage(message), "Couldn't send the message.") }
                        }
                        response.onFailure {
                            aiChatMessages = aiChatMessages.dropLast(1) + AiChatMessage("assistant", it.message ?: "Couldn't send the message.")
                            aiChatBusy = false
                            aiChatStatus = "Message not sent."
                        }
                    }
                }
            }
            val answerAiConsent: (String, Boolean) -> Unit = { callId, allow ->
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching { requireOkEnvelope(nativeAiAnswerConsent(callId, allow), "Couldn't send the permission choice.") }
                    }
                    response.onSuccess {
                        aiPendingConsent = null
                        aiChatStatus = if (allow) "Permission granted for this action." else "Action denied."
                    }.onFailure {
                        aiChatStatus = it.message ?: "Couldn't send the permission choice."
                    }
                }
            }
            val stopAiChat: () -> Unit = {
                scope.launch {
                    withContext(Dispatchers.IO) { nativeAiStopChat() }
                    aiChatStarted = false
                    aiChatBusy = false
                    aiPendingConsent = null
                    aiChatStatus = "Conversation stopped."
                }
            }
            val refreshEvidence: () -> Unit = {
                if (nativeReady && coreVersion.startsWith("Rust core")) {
                    scope.launch {
                        evidenceLoading = true
                        evidenceError = ""
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                val evidence = requireOkEnvelope(
                                    nativeCallTool("core_evidence_show", """{"detail":"summary"}""", false),
                                    "Couldn't load the session evidence."
                                ).toString(2)
                                val unknowns = requireOkEnvelope(
                                    nativeCallTool("core_unknowns_show", "{}", false),
                                    "Couldn't load the session unknowns."
                                ).toString(2)
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
                val requestGeneration = projectRequestGeneration + 1
                projectRequestGeneration = requestGeneration
                selectedProjectFolder = folder
                projectDetailsJson = ""
                projectFilesJson = """{"ok":true,"output":{"result":{"files":[],"truncated":false}}}"""
                projectFilesLoading = false
                projectFilesError = ""
                projectFilesFolder = ""
                selectedProjectFileJson = ""
                selectedProjectFileLoading = false
                previewUrl = ""
                previewLoading = false
                previewError = ""
                previewFolder = ""
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            val params = JSONObject().put("project", folder).toString()
                            requireOkEnvelope(
                                nativeCallTool("project_show", params, false),
                                "Couldn't open this project."
                            ).toString(2)
                        }
                    }
                    if (projectRequestGeneration == requestGeneration && selectedProjectFolder == folder) {
                        response.onSuccess { projectDetailsJson = it }.onFailure {
                            projectsError = it.message ?: "Couldn't open this project."
                        }
                    }
                }
            }
            LaunchedEffect(pendingProjectFolder, coreVersion) {
                if (!coreVersion.startsWith("Rust core v")) return@LaunchedEffect
                val folder = pendingProjectFolder ?: return@LaunchedEffect
                pendingProjectFolder = null
                openProject(folder)
            }

            val refreshProjectFiles: (String, String) -> Unit = { projectFolder, appFolder ->
                projectFilesFolder = projectFolder
                projectFilesLoading = true
                projectFilesError = ""
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching { listProjectFilesJson(appFolder) }
                    }
                    if (selectedProjectFolder == projectFolder && projectFilesFolder == projectFolder) {
                        response.onSuccess { raw ->
                            val payload = JSONObject(raw)
                            if (payload.optBoolean("ok")) {
                                projectFilesJson = payload.toString(2)
                            } else {
                                projectFilesError = payload.optString("error", "Couldn't read project files.")
                            }
                        }.onFailure {
                            projectFilesError = it.message ?: "Couldn't read project files."
                        }
                        projectFilesLoading = false
                    }
                }
            }

            val openProjectFile: (String, String, String) -> Unit = { projectFolder, appFolder, relativePath ->
                selectedProjectFileJson = ""
                selectedProjectFileLoading = true
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            requireOkEnvelope(
                                readProjectFileJson(appFolder, relativePath),
                                "Couldn't open this project file."
                            ).toString(2)
                        }
                    }
                    if (selectedProjectFolder == projectFolder && projectFilesFolder == projectFolder) {
                        response.onSuccess { selectedProjectFileJson = it }.onFailure {
                            projectFilesError = it.message ?: "Couldn't open this project file."
                        }
                        selectedProjectFileLoading = false
                    }
                }
            }


            val saveProjectFile: (String, String, String, String) -> Unit = { projectFolder, appFolder, relativePath, content ->
                projectFileSaving = true
                projectFileSaveMessage = ""
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            requireOkEnvelope(
                                writeProjectFileJson(appFolder, relativePath, content),
                                "Couldn't save this source file."
                            ).toString(2)
                        }
                    }
                    if (selectedProjectFolder == projectFolder) {
                        response.onSuccess {
                            selectedProjectFileJson = it
                            projectFileSaveMessage = "Saved to this device · " + relativePath.substringAfterLast('/')
                            refreshProjectFiles(projectFolder, appFolder)
                        }.onFailure {
                            projectFileSaveMessage = it.message ?: "Couldn't save this source file."
                        }
                    }
                    projectFileSaving = false
                }
            }

            val startProjectPreview: (String) -> Unit = { projectFolder ->
                if (previewFolder != projectFolder || (previewUrl.isBlank() && !previewLoading)) {
                    previewFolder = projectFolder
                    previewUrl = ""
                    previewLoading = true
                    previewError = ""
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                val params = JSONObject().put("project", projectFolder).put("wait", false).toString()
                                val payload = requireOkEnvelope(
                                    nativeCallTool("project_preview", params, false),
                                    "This project doesn't have a previewable website yet."
                                )
                                val result = payload.optJSONObject("output")?.optJSONObject("result")
                                    ?: throw IOException("The preview server returned no address.")
                                val url = result.optString("url")
                                val address = Uri.parse(url)
                                if (address.scheme != "http" || address.host != "127.0.0.1" || address.port !in 1..65535) {
                                    throw IOException("Craft Anything returned an invalid local preview address.")
                                }
                                url
                            }
                        }
                        if (selectedProjectFolder == projectFolder && previewFolder == projectFolder) {
                            response.onSuccess { previewUrl = it }.onFailure {
                                previewError = it.message ?: "Couldn't start the website preview."
                            }
                            previewLoading = false
                        }
                    }
                }
            }

            val stopBuild: (String) -> Unit = { projectFolder ->
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            val params = JSONObject().put("project", projectFolder).toString()
                            requireOkEnvelope(nativeCallTool("rebuild_stop", params, false), "Couldn't stop this build.")
                        }
                    }
                    response.onSuccess {
                        buildActivityMessage = "Stop requested. The builder will keep its work so far."
                    }.onFailure {
                        buildActivityMessage = it.message ?: "Couldn't stop this build."
                    }
                }
            }

            val addBuildNote: (String, String) -> Unit = { projectFolder, note ->
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        runCatching {
                            val params = JSONObject()
                                .put("project", projectFolder)
                                .put("text", note.trim())
                            requireOkEnvelope(
                                nativeCallTool("rebuild_note", params.toString(), false),
                                "Couldn't send this direction to the builder."
                            )
                        }
                    }
                    response.onSuccess {
                        buildActivityMessage = "Direction sent. The builder will read it at its next step."
                    }.onFailure {
                        buildActivityMessage = it.message ?: "Couldn't send this direction to the builder."
                    }
                }
            }

            LaunchedEffect(selectedProjectFolder, projectPollGeneration) {
                val projectFolder = selectedProjectFolder ?: return@LaunchedEffect
                while (true) {
                    val snapshot = withContext(Dispatchers.IO) {
                        runCatching {
                            val params = JSONObject().put("project", projectFolder).toString()
                            val status = requireOkEnvelope(
                                nativeCallTool("rebuild_status", params, false),
                                "Couldn't read build status."
                            )
                            val activityParams = JSONObject().put("project", projectFolder).put("limit", 80).toString()
                            val activity = requireOkEnvelope(
                                nativeCallTool("rebuild_activity_show", activityParams, false),
                                "Couldn't read build activity."
                            )
                            val details = requireOkEnvelope(
                                nativeCallTool("project_show", params, false),
                                "Couldn't refresh project conversation."
                            )
                            Triple(status, activity, details)
                        }
                    }
                    if (selectedProjectFolder == projectFolder) {
                        snapshot.onSuccess { (status, activity, details) ->
                            buildStatusJson = status.toString(2)
                            buildActivityJson = activity.toString(2)
                            projectDetailsJson = details.toString(2)
                            buildActivityError = ""
                        }.onFailure {
                            buildActivityError = it.message ?: "Couldn't refresh build activity."
                        }
                    }
                    val statusResult = snapshot.getOrNull()?.first
                        ?.optJSONObject("output")?.optJSONObject("result")
                    val queued = statusResult?.opt("queued")?.let { it != JSONObject.NULL } == true
                    if (snapshot.isSuccess && statusResult != null && !statusResult.optBoolean("running") && !queued) {
                        break
                    }
                    delay(3_000)
                }
            }

                        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null && nativeReady && coreVersion.startsWith("Rust core v")) {
                    scope.launch {
                        targetResult = "Copying file into app storage…"
                        val opened = withContext(Dispatchers.IO) {
                            runCatching {
                                val displayName = context.contentResolver.query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null,
                                    null,
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                } ?: uri.lastPathSegment ?: "Selected file"
                                val input = context.contentResolver.openInputStream(uri)
                                    ?: throw IOException("The selected document provider returned no readable stream.")
                                AndroidTargetImporter.importWithPath(
                                    input = input,
                                    displayName = displayName,
                                    targetsDir = File(context.filesDir, "targets"),
                                    maxBytes = MAX_IMPORT_BYTES,
                                    openTarget = { parameters ->
                                        nativeCallTool("core_target_open", parameters, false)
                                    }
                                )
                            }
                        }
                        opened.onSuccess { imported ->
                            targetResult = imported.message
                            importedTargetPath = imported.path
                        }.onFailure { error ->
                            targetResult = error.message ?: "Couldn't open the selected file."
                        }
                    }
                } else if (uri != null) {
                    targetResult = if (nativeReady) {
                        "The Rust core is still initializing, so this file wasn't imported. Try again in a moment."
                    } else {
                        "The Rust core is unavailable, so this file wasn't imported."
                    }
                }
            }

            LaunchedEffect(coreVersion) {
                if (coreVersion.startsWith("Rust core v")) {
                    refreshEvidence()
                    refreshAi()
                    if (BuildExecutionService.hasActiveBuild(context)) {
                        val recoveryIntent = android.content.Intent(context, BuildExecutionService::class.java).apply {
                            action = BuildExecutionService.ACTION_RECOVER
                        }
                        runCatching { context.startForegroundService(recoveryIntent) }
                    }
                }
            }

            LaunchedEffect(aiStatusJson) {
                val aiStatus = parseNativeAiStatus(aiStatusJson)
                val usableBackends = aiStatus.backends.filter { it.enabled }.map { it.name }
                if (selectedAiBackend !in usableBackends) {
                    selectedAiBackend = aiStatus.lead?.takeIf { it in usableBackends } ?: usableBackends.firstOrNull().orEmpty()
                }
                if (selectedBuildBackend !in usableBackends) {
                    selectedBuildBackend = aiStatus.builder?.takeIf { it in usableBackends } ?: usableBackends.firstOrNull().orEmpty()
                }
            }

            LaunchedEffect(aiChatStarted) {
                while (aiChatStarted) {
                    val response = withContext(Dispatchers.IO) {
                        runCatching { JSONObject(nativeAiPollChatEvents()) }
                    }
                    response.onSuccess { payload ->
                        if (payload.optBoolean("ok")) {
                            val output = payload.optJSONObject("output") ?: JSONObject()
                            val events = output.optJSONArray("events") ?: JSONArray()
                            for (index in 0 until events.length()) {
                                val event = events.optJSONObject(index) ?: continue
                                when (event.optString("type")) {
                                    "text_delta" -> {
                                        val delta = event.optString("text")
                                        if (delta.isNotEmpty()) {
                                            val last = aiChatMessages.lastOrNull()
                                            aiChatMessages = if (last?.role == "assistant") {
                                                aiChatMessages.dropLast(1) + last.copy(text = last.text + delta)
                                            } else {
                                                aiChatMessages + AiChatMessage("assistant", delta)
                                            }
                                        }
                                    }
                                    "status" -> aiChatStatus = event.optString("text", "Working…")
                                    "tool_started" -> aiChatStatus = "Using ${event.optString("tool")}…"
                                    "tool_finished" -> {
                                        aiChatStatus = if (event.optBoolean("ok")) {
                                            "${event.optString("tool")} completed."
                                        } else {
                                            "${event.optString("tool")} needs attention."
                                        }
                                        if (event.optBoolean("ok")) {
                                            refreshEvidence()
                                            refreshProjects()
                                        }
                                    }
                                    "consent_needed" -> {
                                        aiPendingConsent = AiPendingConsent(
                                            callId = event.optString("call_id"),
                                            title = event.optString("title", "Permission needed"),
                                            explanation = event.optString("explanation"),
                                        )
                                    }
                                    "turn_done" -> {
                                        aiChatBusy = false
                                        aiChatStatus = "Ready — ask away."
                                    }
                                    "error" -> {
                                        aiChatBusy = false
                                        val error = event.optString("message", "The AI turn failed.")
                                        aiChatStatus = event.optString("remediation").takeUnless { it.isBlank() || it == "null" }?.let { "$error $it" } ?: error
                                        val last = aiChatMessages.lastOrNull()
                                        if (last?.role == "assistant" && last.text.isBlank()) {
                                            aiChatMessages = aiChatMessages.dropLast(1) + last.copy(text = error)
                                        } else {
                                            aiChatMessages = aiChatMessages + AiChatMessage("assistant", error)
                                        }
                                    }
                                }
                            }
                            aiChatBusy = output.optBoolean("busy", aiChatBusy)
                        } else {
                            aiChatStatus = payload.optString("error", "Couldn't read AI events.")
                        }
                    }.onFailure {
                        aiChatStatus = it.message ?: "Couldn't read AI events."
                    }
                    delay(250)
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
                            val aiStatus = nativeAiStatus()
                            Triple(nativeVersion(), catalog, projectList to aiStatus)
                        }
                    }
                    result.onSuccess {
                        coreVersion = "Rust core v${it.first}"
                        toolCatalogJson = it.second
                        toolCount = JSONArray(it.second).length()
                        projectsJson = it.third.first
                        aiStatusJson = it.third.second
                    }.onFailure {
                        coreVersion = "Rust bridge error: ${it.message ?: "unknown error"}"
                    }
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Violet,
                    secondary = Color(0xFFC6B5FF),
                    background = Ink,
                    surface = Panel,
                    surfaceVariant = Color(0xFF22212B),
                    error = Color(0xFFFF8D9C),
                    onBackground = Color(0xFFF5F2FF),
                    onSurface = Color(0xFFF5F2FF),
                    onSurfaceVariant = Muted
                )
            ) {
                Scaffold(
                    containerColor = Ink,
                    bottomBar = {
                        NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                            listOf(
                                Triple("Home", Icons.Rounded.Home, "Home"),
                                Triple("Projects", Icons.Rounded.FolderOpen, "Projects"),
                                Triple("Build", Icons.Rounded.AutoAwesome, "Build"),
                                Triple("Evidence", Icons.Rounded.Assessment, "Evidence"),
                                Triple("Tools", Icons.Rounded.Terminal, "Tools")
                            ).forEach { (label, icon, route) ->
                                NavigationBarItem(
                                    selected = selected == route,
                                    onClick = { selected = route },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Violet,
                                        selectedTextColor = Color(0xFFEAE2FF),
                                        indicatorColor = Violet.copy(alpha = 0.16f),
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier.size(42.dp).background(Violet.copy(alpha = .16f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("CRAFT ANYTHING", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
                                Text(selected, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { selected = "AI" }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Ask AI", tint = Violet)
                            }
                            IconButton(onClick = { selected = "Settings" }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Muted)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        when (selected) {
                            "Home" -> HomeScreen(
                                coreVersion,
                                toolCount,
                                targetResult,
                                {
                                    if (coreVersion.startsWith("Rust core v")) {
                                        filePicker.launch(arrayOf("*/*"))
                                    } else {
                                        targetResult = "The Rust core is still initializing. Try again in a moment."
                                    }
                                },
                                { selected = "Tools" },
                                { selected = "AI" },
                                { selected = "Build" }
                            )
                            "Projects" -> ProjectsScreen(
                                projectsJson = projectsJson,
                                loading = projectsLoading,
                                error = projectsError,
                                detailsJson = projectDetailsJson,
                                buildStatusJson = buildStatusJson,
                                activityJson = buildActivityJson,
                                activityError = buildActivityError,
                                activityMessage = buildActivityMessage,
                                onStopBuild = stopBuild,
                                onAddBuildNote = addBuildNote,
                                onFollowUp = { projectFolder, request ->
                                    val params = JSONObject()
                                        .put("request", request)
                                        .put("project", projectFolder)
                                        .put("resume", false)
                                        .put("rounds", 2)
                                        .toString()
                                    projectPollGeneration += 1
                                    startBuild(params)
                                },
                                onSaveProjectFile = saveProjectFile,
                                fileSaving = projectFileSaving,
                                fileSaveMessage = projectFileSaveMessage,
                                onInstallDependencies = { projectFolder, prompt ->
                                    val params = JSONObject()
                                        .put("request", prompt)
                                        .put("project", projectFolder)
                                        .put("resume", false)
                                        .put("rounds", 2)
                                        .toString()
                                    projectPollGeneration += 1
                                    startBuild(params)
                                },
                                filesJson = projectFilesJson,
                                selectedFileJson = selectedProjectFileJson,
                                filesLoading = projectFilesLoading,
                                selectedFileLoading = selectedProjectFileLoading,
                                filesError = projectFilesError,
                                previewUrl = if (previewFolder == selectedProjectFolder) previewUrl else "",
                                previewLoading = previewLoading && previewFolder == selectedProjectFolder,
                                previewError = if (previewFolder == selectedProjectFolder) previewError else "",
                                onRefreshFiles = refreshProjectFiles,
                                onOpenProjectFile = openProjectFile,
                                onCloseProjectFile = { selectedProjectFileJson = "" },
                                onStartProjectPreview = startProjectPreview,
                                onRefresh = refreshProjects,
                                onOpenProject = openProject,
                                onCloseDetails = {
                                    projectRequestGeneration += 1
                                    selectedProjectFolder = null
                                    projectDetailsJson = ""
                                    buildStatusJson = ""
                                    buildActivityJson = ""
                                    buildActivityError = ""
                                    buildActivityMessage = ""
                                    projectFilesJson = """{"ok":true,"output":{"result":{"files":[],"truncated":false}}}"""
                                    projectFilesLoading = false
                                    projectFilesError = ""
                                    projectFilesFolder = ""
                                    selectedProjectFileJson = ""
                                    selectedProjectFileLoading = false
                                    projectFileSaving = false
                                    projectFileSaveMessage = ""
                                    previewUrl = ""
                                    previewLoading = false
                                    previewError = ""
                                    previewFolder = ""
                                },
                                onAnswer = { projectFolder, requestId, choice, text, allow ->
                                    val requestGeneration = projectRequestGeneration
                                    val listGenerationAtStart = projectsListRequestGeneration.get()
                                    val listRequestGeneration = AtomicInteger(-1)
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
                                                val detail = requireOkEnvelope(
                                                    nativeCallTool("project_show", showParams, false),
                                                    "The answer was saved, but the project couldn't be refreshed."
                                                )
                                                val listGeneration = projectsListRequestGeneration.incrementAndGet()
                                                listRequestGeneration.set(listGeneration)
                                                val list = requireOkEnvelope(
                                                    nativeCallTool("project_list", "{}", false),
                                                    "The answer was saved, but the project list couldn't be refreshed."
                                                )
                                                Triple(detail.toString(2), list.toString(2), listGeneration)
                                            }
                                        }
                                        refreshed.onSuccess { (detailJson, listJson, listGeneration) ->
                                            if (projectsListRequestGeneration.get() == listGeneration) {
                                                projectsJson = listJson
                                                projectsLoading = false
                                            }
                                            if (projectRequestGeneration == requestGeneration && selectedProjectFolder == projectFolder) {
                                                projectDetailsJson = detailJson
                                            }
                                        }.onFailure { error ->
                                            if (
                                                projectsListRequestGeneration.get() == listRequestGeneration.get() ||
                                                (listRequestGeneration.get() < 0 && projectsListRequestGeneration.get() == listGenerationAtStart)
                                            ) {
                                                projectsError = error.message ?: "Couldn't refresh the project list."
                                                projectsLoading = false
                                            }
                                            if (projectRequestGeneration == requestGeneration && selectedProjectFolder == projectFolder) {
                                                projectsError = error.message ?: "Couldn't send the project answer."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )


                            "Build" -> BuildScreen(
                                backends = parseNativeAiStatus(aiStatusJson).backends,
                                selectedBackend = selectedBuildBackend,
                                onBackendSelected = { selectedBuildBackend = it },
                                starting = buildStarting,
                                feedback = buildFeedback,
                                importedTargetPath = importedTargetPath,
                                onChooseTarget = { filePicker.launch(arrayOf("*/*")) },
                                onStartBuild = startBuild,
                                onOpenProjects = { selected = "Projects" },
                                modifier = Modifier.weight(1f)
                            )
                            "AI" -> AiChatScreen(
                                backends = parseNativeAiStatus(aiStatusJson).backends,
                                selectedBackend = selectedAiBackend,
                                messages = aiChatMessages,
                                connected = aiChatStarted,
                                busy = aiChatBusy,
                                status = aiChatStatus,
                                pendingConsent = aiPendingConsent,
                                onBackendSelected = { selectedAiBackend = it },
                                onStart = startAiChat,
                                onStop = stopAiChat,
                                onSend = sendAiMessage,
                                onConsent = answerAiConsent,
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
                            "Settings" -> AiSettingsScreen(
                                statusJson = aiStatusJson,
                                loading = aiSettingsLoading,
                                message = aiSettingsMessage,
                                onRefresh = refreshAi,
                                onAddBackend = addAiBackend,
                                onSetRole = setAiRole,
                                onSetEnabled = setAiEnabled,
                                onRemoveBackend = removeAiBackend,
                                modifier = Modifier.weight(1f)
                            )
                            else -> EmptyScreen("Not found", "This section is not available yet.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    coreVersion: String,
    toolCount: Int,
    targetResult: String,
    openFile: () -> Unit,
    openTools: () -> Unit,
    openAi: () -> Unit,
    openBuild: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "YOUR CREATIVE WORKSPACE",
                color = Violet,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "What do you want to create?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Turn an idea, a reference or an existing project into something real. Build with AI and stay close to every step.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = openBuild),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1827)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Violet)
                    Text(
                        "  START A NEW PROJECT",
                        color = Violet,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("↗", color = Violet, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Describe it. We'll build it together.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Create a website, mobile app, dashboard or game. Add a reference, choose your AI builder and watch the work happen.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = openBuild, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Text("  Open builder")
                }
            }
        }

        Text("Bring something to work with", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = openFile),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Violet)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Import a file or project reference", fontWeight = FontWeight.SemiBold)
                    Text(
                        targetResult.takeIf { it != "No file opened yet." } ?: "Open a local file for inspection or use it as source material.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                Text("→", color = Violet)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.weight(1f).clickable(onClick = openAi),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Violet)
                    Text("Ask AI", fontWeight = FontWeight.SemiBold)
                    Text("Think through ideas and project questions.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(
                modifier = Modifier.weight(1f).clickable(onClick = openTools),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Violet)
                    Text("Engine tools", fontWeight = FontWeight.SemiBold)
                    Text("$toolCount available actions", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(9.dp).background(
                        if (coreVersion.startsWith("Rust core v")) Color(0xFF71D5A2) else Color(0xFFFFC46B),
                        RoundedCornerShape(50)
                    )
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Engine connection", fontWeight = FontWeight.SemiBold)
                    Text(coreVersion, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("$toolCount tools", color = Violet, style = MaterialTheme.typography.labelMedium)
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
