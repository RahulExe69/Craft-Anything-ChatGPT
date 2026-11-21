package ai.storytold.craftanything

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeProject(
    val name: String,
    val folder: String,
    val request: String,
    val status: String,
    val statusLabel: String,
    val waiting: Int,
    val stack: String
)

internal fun parseBuildActivity(response: String): List<String> {
    return runCatching {
        val envelope = JSONObject(response)
        if (!envelope.optBoolean("ok")) return emptyList()
        val result = envelope.optJSONObject("output")?.optJSONObject("result") ?: return emptyList()
        val lines = result.optJSONArray("lines") ?: JSONArray()
        buildList {
            for (index in 0 until lines.length()) {
                val line = lines.optString(index).trim()
                if (line.isNotEmpty()) add(line)
            }
        }
    }.getOrDefault(emptyList())
}

internal fun parseProjectList(response: String): List<NativeProject> {
    return runCatching {
        val envelope = JSONObject(response)
        if (!envelope.optBoolean("ok")) return emptyList()
        val result = envelope.optJSONObject("output")?.optJSONObject("result") ?: return emptyList()
        val projects = result.optJSONArray("projects") ?: JSONArray()
        buildList {
            for (index in 0 until projects.length()) {
                val item = projects.optJSONObject(index) ?: continue
                add(
                    NativeProject(
                        name = item.optString("name", "Untitled project"),
                        folder = item.optString("folder"),
                        request = item.optString("request"),
                        status = item.optString("status"),
                        statusLabel = item.optString("status_label", item.optString("status")),
                        waiting = item.optInt("waiting"),
                        stack = item.opt("stack")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}


internal data class ProjectConversationEntry(val role: String, val text: String, val user: Boolean = false, val streaming: Boolean = false, val error: Boolean = false)

internal fun parseProjectConversation(conversation: JSONObject?): List<ProjectConversationEntry> {
    val items = conversation?.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val keys = item.keys()
            if (!keys.hasNext()) continue
            val kind = keys.next()
            when (kind) {
                "User" -> {
                    val value = item.opt(kind)
                    val text = if (value is JSONObject) value.optString("text") else value?.takeIf { it != JSONObject.NULL }?.toString().orEmpty()
                    val pictures = (value as? JSONObject)?.optJSONArray("pictures")?.length() ?: 0
                    add(ProjectConversationEntry("You", text.ifBlank { if (pictures > 0) "Shared " + pictures + " reference image(s)" else "" }, user = true))
                }
                "Agent" -> {
                    val value = item.optJSONObject(kind) ?: continue
                    if (value.optString("text").isNotBlank()) add(ProjectConversationEntry("Craft Anything", value.optString("text"), streaming = value.optBoolean("streaming")))
                }
                "Step" -> {
                    val value = item.optJSONObject(kind) ?: continue
                    add(ProjectConversationEntry(value.optString("step").ifBlank { "Builder update" }, value.optString("text")))
                }
                "Plan" -> {
                    val steps = item.optJSONArray(kind) ?: continue
                    val labels = buildList {
                        for (j in 0 until steps.length()) {
                            val step = steps.optJSONObject(j) ?: continue
                            val mark = when (step.optString("status")) { "Done" -> "✓"; "Doing" -> "◉"; "Stopped" -> "Ⅱ"; else -> "○" }
                            if (step.optString("text").isNotBlank()) add(mark + " " + step.optString("text"))
                        }
                    }
                    if (labels.isNotEmpty()) add(ProjectConversationEntry("Builder plan", labels.joinToString("\n")))
                }
                "Work" -> {
                    val value = item.optJSONObject(kind) ?: continue
                    val count = value.optJSONArray("tools")?.length() ?: 0
                    add(ProjectConversationEntry("Work update", "Used " + count + " engine tool(s)."))
                }
                "Ask" -> {
                    val value = item.optJSONObject(kind) ?: continue
                    val request = value.optJSONObject("request")
                    val answer = value.optString("answer").takeUnless { it == "null" } ?: "Waiting for your answer"
                    add(ProjectConversationEntry(request?.optString("title").orEmpty().ifBlank { "Builder question" }, answer))
                }
                "Check" -> add(ProjectConversationEntry("Build check", "The builder saved a verification result."))
                "Error" -> {
                    val value = item.optJSONObject(kind) ?: continue
                    add(ProjectConversationEntry("Needs attention", value.optString("message"), error = true))
                }
                "Paused" -> add(ProjectConversationEntry("Build paused", "The current work has been kept."))
            }
        }
    }.filter { it.text.isNotBlank() }
}


internal fun isMissingAndroidNodeRuntime(entries: List<ProjectConversationEntry>): Boolean =
    entries.any { entry ->
        entry.error &&
            (entry.text.contains("npm", ignoreCase = true) || entry.text.contains("Node.js", ignoreCase = true)) &&
            (entry.text.contains("not installed", ignoreCase = true) ||
                entry.text.contains("isn't installed", ignoreCase = true) ||
                entry.text.contains("does not include", ignoreCase = true) ||
                entry.text.contains("not on your PATH", ignoreCase = true))
    }

@Composable
private fun ProjectConversationPanel(
    projectFolder: String,
    conversation: JSONObject?,
    statusJson: String,
    message: String,
    onAddBuildNote: (String, String) -> Unit,
    onFollowUp: (String, String) -> Unit
) {
    val entries = remember(conversation?.toString()) { parseProjectConversation(conversation) }
    val status = remember(statusJson) { runCatching { JSONObject(statusJson).optJSONObject("output")?.optJSONObject("result") }.getOrNull() }
    val running = status?.optBoolean("running") == true
    val queued = status?.opt("queued")?.takeUnless { it == JSONObject.NULL }?.toString()
    var draft by remember(projectFolder) { mutableStateOf("") }
    var confirming by remember(projectFolder) { mutableStateOf(false) }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Start follow-up build?") },
        text = { Text("Craft Anything will continue this project with your new instruction. The builder may edit files and run commands under your configured approval settings.") },
        confirmButton = { Button(onClick = { val request = draft.trim(); confirming = false; draft = ""; onFollowUp(projectFolder, request) }) { Text("Start build") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
    )
    if (isMissingAndroidNodeRuntime(entries)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Node.js isn't available on this device", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                Text(
                    "Your generated source is saved, but this APK cannot run npm or install JavaScript packages. For a website, Craft Anything can convert the project to browser-native HTML, CSS and JavaScript so it can run in local Preview.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { onFollowUp(projectFolder, androidStaticWebsitePrompt()) }) {
                    Text("Make this website run without npm")
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Builder conversation", fontWeight = FontWeight.Bold)
                    Text(entries.size.toString() + " messages · saved with this project", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                if (running) Text("LIVE", color = Color(0xFF71D5A2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (entries.isEmpty()) Text("Your conversation with the builder will appear here as it plans, makes changes and checks the project.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            entries.takeLast(80).forEach { entry ->
                val bg = when { entry.user -> MaterialTheme.colorScheme.primaryContainer; entry.error -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
                Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.role.uppercase(), color = if (entry.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        if (entry.streaming) Text("  ·  WORKING", color = Color(0xFF71D5A2), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (queued != null && !running) Text("This project is queued (position " + queued + "). You can send a new direction after its run starts.", color = Color(0xFFFFD38D), style = MaterialTheme.typography.bodySmall)
            else {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (running) "Message the builder" else "What should the builder do next?") },
                    placeholder = { Text("Describe the next change or improvement…") }, minLines = 2, maxLines = 5)
                Button(onClick = {
                    if (running) {
                        onAddBuildNote(projectFolder, draft.trim())
                        draft = ""
                    } else {
                        confirming = true
                    }
                }, enabled = status != null && draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) "Send direction" else "Start follow-up")
                }
                if (!running) Text("A follow-up starts a new builder run on this existing project.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun ProjectsScreen(
    projectsJson: String,
    loading: Boolean,
    error: String,
    detailsJson: String,
    onRefresh: () -> Unit,
    onOpenProject: (String) -> Unit,
    onCloseDetails: () -> Unit,
    onAnswer: (projectFolder: String, id: String, choice: String?, text: String?, allow: Boolean?) -> Unit = { _, _, _, _, _ -> },
    buildStatusJson: String = "",
    activityJson: String = "",
    activityError: String = "",
    activityMessage: String = "",
    onStopBuild: (String) -> Unit = {},
    onAddBuildNote: (String, String) -> Unit = { _, _ -> },
    onFollowUp: (String, String) -> Unit = { _, _ -> },
    filesJson: String = "",
    selectedFileJson: String = "",
    filesLoading: Boolean = false,
    selectedFileLoading: Boolean = false,
    filesError: String = "",
    previewUrl: String = "",
    previewLoading: Boolean = false,
    previewError: String = "",
    onRefreshFiles: (String, String) -> Unit = { _, _ -> },
    onOpenProjectFile: (String, String, String) -> Unit = { _, _, _ -> },
    onCloseProjectFile: () -> Unit = {},
    onSaveProjectFile: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    fileSaving: Boolean = false,
    fileSaveMessage: String = "",
    onExportProject: (String, String) -> Unit = { _, _ -> },
    exportBusy: Boolean = false,
    exportMessage: String = "",
    onMakeStaticWebsite: (String) -> Unit = {},
    terminalCommandFolder: String = "",
    terminalCommandResultJson: String = "",
    terminalCommandError: String = "",
    terminalCommandRunning: Boolean = false,
    onRunProjectCommand: (String, String, String) -> Unit = { _, _, _ -> },
    onStartProjectPreview: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val projects = remember(projectsJson) { parseProjectList(projectsJson) }
    val detail = remember(detailsJson) { runCatching { JSONObject(detailsJson) }.getOrNull() }
    val detailResult = detail?.takeIf { it.optBoolean("ok") }
        ?.optJSONObject("output")?.optJSONObject("result")

    BackHandler(enabled = detailResult != null, onBack = onCloseDetails)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (detailResult == null) {
                    Text(
                        "YOUR WORKSPACE",
                        color = Color(0xFFAD91FF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    if (detailResult == null) "Your projects" else detailResult.optString("name", "Project"),
                    style = if (detailResult == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    if (detailResult == null) "${projects.size} projects · this device" else "Project workspace",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (detailResult != null) {
                OutlinedButton(onClick = onCloseDetails) { Text("Back") }
            } else {
                OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
            }
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (error.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (detailResult != null) {
            ProjectDetails(
                project = detailResult,
                onAnswer = onAnswer,
                buildStatusJson = buildStatusJson,
                activityJson = activityJson,
                activityError = activityError,
                activityMessage = activityMessage,
                onStopBuild = onStopBuild,
                onAddBuildNote = onAddBuildNote,
                onFollowUp = onFollowUp,
                filesJson = filesJson,
                selectedFileJson = selectedFileJson,
                filesLoading = filesLoading,
                selectedFileLoading = selectedFileLoading,
                filesError = filesError,
                previewUrl = previewUrl,
                previewLoading = previewLoading,
                previewError = previewError,
                onRefreshFiles = onRefreshFiles,
                onOpenProjectFile = onOpenProjectFile,
                onCloseProjectFile = onCloseProjectFile,
                onSaveProjectFile = onSaveProjectFile,
                fileSaving = fileSaving,
                fileSaveMessage = fileSaveMessage,
                onExportProject = onExportProject,
                exportBusy = exportBusy,
                exportMessage = exportMessage,
                onMakeStaticWebsite = onMakeStaticWebsite,
                terminalCommandFolder = terminalCommandFolder,
                terminalCommandResultJson = terminalCommandResultJson,
                terminalCommandError = terminalCommandError,
                terminalCommandRunning = terminalCommandRunning,
                onRunProjectCommand = onRunProjectCommand,
                onStartProjectPreview = onStartProjectPreview
            )
        } else if (projects.isEmpty() && !loading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Projects created on this device will appear here. Open a file from Home to begin an investigation, or use the Tools tab to explore the project tools.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onRefresh, enabled = !loading) { Text("Check again") }
                }
            }
        } else if (detailResult == null) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(projects, key = { it.folder }) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenProject(project.folder) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    project.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    project.statusLabel.ifBlank { project.status }.uppercase(),
                                    color = if (project.waiting > 0) Color(0xFFFFD38D) else Color(0xFFBEACFF),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(
                                            if (project.waiting > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                            Text(
                                project.request,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (project.waiting == 1) "1 question waiting" else "${project.waiting} questions waiting",
                                    color = if (project.waiting > 0) Color(0xFFFFC46B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.weight(1f))
                                Text("Open →", color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetails(
    project: JSONObject,
    onAnswer: (projectFolder: String, id: String, choice: String?, text: String?, allow: Boolean?) -> Unit,
    buildStatusJson: String,
    activityJson: String,
    activityError: String,
    activityMessage: String,
    onStopBuild: (String) -> Unit,
    onAddBuildNote: (String, String) -> Unit,
    onFollowUp: (String, String) -> Unit,
    filesJson: String,
    selectedFileJson: String,
    filesLoading: Boolean,
    selectedFileLoading: Boolean,
    filesError: String,
    previewUrl: String,
    previewLoading: Boolean,
    previewError: String,
    onRefreshFiles: (String, String) -> Unit,
    onOpenProjectFile: (String, String, String) -> Unit,
    onCloseProjectFile: () -> Unit,
    onSaveProjectFile: (String, String, String, String) -> Unit,
    fileSaving: Boolean,
    fileSaveMessage: String,
    onExportProject: (String, String) -> Unit,
    exportBusy: Boolean,
    exportMessage: String,
    onMakeStaticWebsite: (String) -> Unit,
    terminalCommandFolder: String,
    terminalCommandResultJson: String,
    terminalCommandError: String,
    terminalCommandRunning: Boolean,
    onRunProjectCommand: (String, String, String) -> Unit,
    onStartProjectPreview: (String) -> Unit
) {
    val conversation = project.optJSONObject("conversation")
    val questions = project.optJSONArray("questions")
    val folder = project.optString("folder")
    val appFolder = project.optString("app_folder")
    var workspaceTab by remember(folder) { mutableStateOf("Chat") }

    LaunchedEffect(workspaceTab, folder, appFolder) {
        when (workspaceTab) {
            "Code", "Console" -> onRefreshFiles(folder, appFolder)
            "Preview" -> if (previewUrl.isBlank() && !previewLoading) onStartProjectPreview(folder)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("PROJECT BRIEF", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(project.optString("request"), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stack = project.opt("stack")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                    Text(
                        if (stack.isNotBlank() && stack != "[]") "Stack · $stack" else "Saved on this device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onExportProject(project.optString("name", "craft-project"), appFolder) }, enabled = !exportBusy) {
                        Text(if (exportBusy) "Saving…" else "Export ZIP")
                    }
                }
                if (exportMessage.isNotBlank()) {
                    Text(exportMessage, color = if (exportMessage.startsWith("Exported")) Color(0xFF26834D) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        ProjectPhaseStepper(
            status = project.optString("status"),
            statusLabel = project.optString("status_label", project.optString("status").replace('_', ' ').replaceFirstChar { it.uppercase() }),
            hasCheck = project.opt("last_check")?.let { it != JSONObject.NULL } == true
        )
        val workspaceTabs = listOf("Chat", "Preview", "Code", "Console", "Activity")
        ScrollableTabRow(
            selectedTabIndex = workspaceTabs.indexOf(workspaceTab).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            workspaceTabs.forEach { tab ->
                Tab(
                    selected = workspaceTab == tab,
                    onClick = { workspaceTab = tab },
                    text = { Text(tab, maxLines = 1) }
                )
            }
        }

        when (workspaceTab) {
            "Chat" -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((conversation?.optInt("total") ?: 0).toString() + " messages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("·", color = MaterialTheme.colorScheme.outline)
                    Text((questions?.length() ?: 0).toString() + " questions", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    if (project.optInt("waiting") > 0) {
                        Text("· " + project.optInt("waiting") + " waiting", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (questions != null && questions.length() > 0) {
                    Text("Waiting for you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    for (index in 0 until questions.length()) {
                        val request = questions.optJSONObject(index) ?: continue
                        key(folder, request.optString("id")) { ProjectInboxCard(request, folder, onAnswer) }
                    }
                }
                ProjectConversationPanel(folder, conversation, buildStatusJson, activityMessage, onAddBuildNote, onFollowUp)
            }
            "Preview" -> ProjectPreviewPanel(
                url = previewUrl,
                loading = previewLoading,
                error = previewError,
                onStart = { onStartProjectPreview(folder) },
                onReload = {}
            )
            "Code" -> ProjectFilesPanel(
                filesJson = filesJson,
                selectedFileJson = selectedFileJson,
                loading = filesLoading,
                selectedFileLoading = selectedFileLoading,
                saving = fileSaving,
                saveMessage = fileSaveMessage,
                error = filesError,
                onRefresh = { onRefreshFiles(folder, appFolder) },
                onOpenFile = { relativePath -> onOpenProjectFile(folder, appFolder, relativePath) },
                onSaveFile = { path, content -> onSaveProjectFile(folder, appFolder, path, content) },
                onCloseFile = onCloseProjectFile
            )
            "Console" -> ProjectConsolePanel(
                projectFolder = folder,
                appFolder = appFolder,
                filesJson = filesJson,
                activityJson = activityJson,
                commandResultJson = terminalCommandResultJson.takeIf { terminalCommandFolder == folder }.orEmpty(),
                commandError = terminalCommandError.takeIf { terminalCommandFolder == folder }.orEmpty(),
                commandRunning = terminalCommandRunning && terminalCommandFolder == folder,
                onRunCommand = { command -> onRunProjectCommand(folder, appFolder, command) },
                onMakeStaticWebsite = { onMakeStaticWebsite(folder) }
            )
            "Activity" -> BuildActivityPanel(
                projectFolder = folder,
                fallbackStatus = project.optString("status_label", project.optString("status")),
                statusJson = buildStatusJson,
                activityJson = activityJson,
                error = activityError,
                message = activityMessage,
                onStopBuild = onStopBuild,
                onAddBuildNote = onAddBuildNote
            )
        }
        if (workspaceTab != "Chat" && questions != null && questions.length() > 0) {
            Text("Waiting for you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for (index in 0 until questions.length()) {
                val request = questions.optJSONObject(index) ?: continue
                key(folder, request.optString("id")) {
                    ProjectInboxCard(request, folder, onAnswer)
                }
            }
        }

    }
}

@Composable
private fun BuildActivityPanel(
    projectFolder: String,
    fallbackStatus: String,
    statusJson: String,
    activityJson: String,
    error: String,
    message: String,
    onStopBuild: (String) -> Unit,
    onAddBuildNote: (String, String) -> Unit
) {
    var noteDraft by remember(projectFolder) { mutableStateOf("") }
    val status = remember(statusJson) {
        runCatching {
            JSONObject(statusJson).optJSONObject("output")?.optJSONObject("result")
        }.getOrNull()
    }
    val lines = remember(activityJson) { parseBuildActivity(activityJson) }
    val running = status?.optBoolean("running") == true
    val queuePosition = status?.opt("queued")?.takeUnless { it == JSONObject.NULL }?.toString()
    val currentStatus = when {
        running -> "Building now"
        queuePosition != null -> "Queued · position $queuePosition"
        else -> status?.optString("status_label")?.takeIf { !it.isNullOrBlank() } ?: fallbackStatus
    }
    val note = status?.optString("note").orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live build activity", fontWeight = FontWeight.Bold)
                    Text(
                        currentStatus,
                        color = if (running || queuePosition != null) Color(0xFF9B7BFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (running || queuePosition != null) {
                    OutlinedButton(onClick = { onStopBuild(projectFolder) }) {
                        Text("Stop build")
                    }
                }
            }
            if (note.isNotBlank()) {
                Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
            if (running) {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tell the builder what to change") },
                    placeholder = { Text("Add a page, fix the layout, explain a requirement…") },
                    minLines = 2,
                    maxLines = 4
                )
                Button(
                    onClick = {
                        onAddBuildNote(projectFolder, noteDraft.trim())
                        noteDraft = ""
                    },
                    enabled = noteDraft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send direction") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity timeline", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${lines.size} recent events",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (lines.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Your builder's steps will appear here as they happen — planning, file changes, commands and checks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    lines.takeLast(18).asReversed().forEach { line ->
                        BuildActivityRow(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildActivityRow(line: String) {
    val warning = line.contains("FAIL", ignoreCase = true) || line.contains("ERROR", ignoreCase = true)
    val completed = line.contains("done", ignoreCase = true) || line.contains("finished", ignoreCase = true)
    val marker = when {
        warning -> Color(0xFFFF8D9C)
        completed -> Color(0xFF71D5A2)
        else -> Color(0xFF9B7BFF)
    }
    val title = line.substringAfter(' ').trim().ifBlank { line }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.padding(top = 5.dp).size(8.dp).background(marker, RoundedCornerShape(50))
        )
        Text(
            title,
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 4,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProjectInboxCard(
    request: JSONObject,
    projectFolder: String,
    onAnswer: (projectFolder: String, id: String, choice: String?, text: String?, allow: Boolean?) -> Unit
) {
    val id = request.optString("id")
    val kind = request.optString("kind", "question")
    val choices = request.optJSONArray("choices") ?: JSONArray()
    var typedAnswer by remember(id) { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (kind == "approval") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (kind == "approval") "Approval request" else "Question",
                color = if (kind == "approval") MaterialTheme.colorScheme.onTertiaryContainer else Color(0xFF9B7BFF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(request.optString("title", "Project request"), fontWeight = FontWeight.SemiBold)
            val detail = request.optString("detail")
            if (detail.isNotBlank()) {
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            if (kind == "approval") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswer(projectFolder, id, null, null, false) }
                    ) { Text("Deny") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswer(projectFolder, id, null, null, true) }
                    ) { Text("Allow") }
                }
            } else {
                for (choiceIndex in 0 until choices.length()) {
                    val choice = choices.optString(choiceIndex)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAnswer(projectFolder, id, choice, null, null) }
                    ) { Text(choice) }
                }
                if (request.optBoolean("allow_text")) {
                    OutlinedTextField(
                        value = typedAnswer,
                        onValueChange = { typedAnswer = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your answer") },
                        minLines = 2,
                        maxLines = 4
                    )
                    Button(
                        onClick = { onAnswer(projectFolder, id, null, typedAnswer.trim(), null) },
                        enabled = typedAnswer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send answer") }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
