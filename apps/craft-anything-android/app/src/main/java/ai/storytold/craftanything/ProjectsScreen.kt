package ai.storytold.craftanything

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val projects = remember(projectsJson) { parseProjectList(projectsJson) }
    val detail = remember(detailsJson) { runCatching { JSONObject(detailsJson) }.getOrNull() }
    val detailResult = detail?.takeIf { it.optBoolean("ok") }
        ?.optJSONObject("output")?.optJSONObject("result")

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
            ProjectDetails(detailResult)
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
private fun ProjectDetails(project: JSONObject) {
    val conversation = project.optJSONObject("conversation")
    val questions = project.optJSONArray("questions")
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.optString("status_label", project.optString("status")), color = Color(0xFF9B7BFF), fontWeight = FontWeight.Bold)
                Text(project.optString("request"), style = MaterialTheme.typography.bodyMedium)
                Text(project.optString("folder"), color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
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
        if (questions != null && questions.length() > 0) {
            Text("Waiting for you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for (index in 0 until questions.length()) {
                val question = questions.optJSONObject(index) ?: continue
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2C261C))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(question.optString("question"), color = Color(0xFFFFD99A))
                        val choices = question.optJSONArray("choices")
                        if (choices != null) {
                            Text(
                                (0 until choices.length()).joinToString(" · ") { choices.optString(it) },
                                color = Color(0xFFB9B5C6),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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
