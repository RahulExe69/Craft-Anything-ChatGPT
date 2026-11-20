package ai.storytold.craftanything

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            Column(Modifier.weight(1f)) {
                Text(
                    if (detailResult == null) "Your projects" else detailResult.optString("name", "Project"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (detailResult == null) "${projects.size} projects on this device" else "Project overview",
                    color = Color(0xFF9A97A8),
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2029))) {
                Text(error, modifier = Modifier.padding(14.dp), color = Color(0xFFFFC2CD))
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
                onAddBuildNote = onAddBuildNote
            )
        } else if (projects.isEmpty() && !loading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Projects created on this device will appear here. Open a file from Home to begin an investigation, or use the Tools tab to explore the project tools.",
                        color = Color(0xFFB9B5C6),
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D)),
                        shape = RoundedCornerShape(18.dp)
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
                                    project.statusLabel.ifBlank { project.status },
                                    color = Color(0xFF9B7BFF),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                project.request,
                                color = Color(0xFFB9B5C6),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (project.waiting == 1) "1 question waiting" else "${project.waiting} questions waiting",
                                    color = if (project.waiting > 0) Color(0xFFFFC46B) else Color(0xFF9A97A8),
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
    onAddBuildNote: (String, String) -> Unit
) {
    val conversation = project.optJSONObject("conversation")
    val questions = project.optJSONArray("questions")
    val folder = project.optString("folder")
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.optString("status_label", project.optString("status")), color = Color(0xFF9B7BFF), fontWeight = FontWeight.Bold)
                Text(project.optString("request"), style = MaterialTheme.typography.bodyMedium)
                Text(folder, color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
                val stack = project.opt("stack")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                if (stack.isNotBlank() && stack != "[]") {
                    Text("Stack: $stack", color = Color(0xFFB9B5C6), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Questions", (questions?.length() ?: 0).toString(), Modifier.weight(1f))
            MetricCard("Conversation", (conversation?.optInt("total") ?: 0).toString(), Modifier.weight(1f))
            MetricCard("Waiting", project.optInt("waiting").toString(), Modifier.weight(1f))
        }
        BuildActivityPanel(
            projectFolder = folder,
            fallbackStatus = project.optString("status_label", project.optString("status")),
            statusJson = buildStatusJson,
            activityJson = activityJson,
            error = activityError,
            message = activityMessage,
            onStopBuild = onStopBuild,
            onAddBuildNote = onAddBuildNote
        )
        if (questions != null && questions.length() > 0) {
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171720)),
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
                        color = if (running || queuePosition != null) Color(0xFF9B7BFF) else Color(0xFFB9B5C6),
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
                Text(note, color = Color(0xFFB9B5C6), style = MaterialTheme.typography.bodySmall)
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFFA9B8), style = MaterialTheme.typography.bodySmall)
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
                    color = Color(0xFF9A97A8),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (lines.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF20202A)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Your builder's steps will appear here as they happen — planning, file changes, commands and checks.",
                        color = Color(0xFFB9B5C6),
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
            color = if (warning) Color(0xFFFFA9B8) else Color(0xFFCFCCD9),
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
            containerColor = if (kind == "approval") Color(0xFF2C261C) else Color(0xFF20202A)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (kind == "approval") "Approval request" else "Question",
                color = if (kind == "approval") Color(0xFFFFD99A) else Color(0xFF9B7BFF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(request.optString("title", "Project request"), fontWeight = FontWeight.SemiBold)
            val detail = request.optString("detail")
            if (detail.isNotBlank()) {
                Text(detail, color = Color(0xFFB9B5C6), style = MaterialTheme.typography.bodySmall)
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
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
        }
    }
}
