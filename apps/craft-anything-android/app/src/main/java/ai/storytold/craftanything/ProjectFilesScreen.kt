package ai.storytold.craftanything

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeProjectFile(
    val path: String,
    val name: String,
    val size: Long,
    val kind: String
)

internal data class NativeProjectFileContent(
    val path: String,
    val content: String,
    val size: Long
)

internal fun parseProjectFiles(response: String): List<NativeProjectFile> = runCatching {
    val envelope = JSONObject(response)
    if (!envelope.optBoolean("ok")) return emptyList()
    val result = envelope.optJSONObject("output")?.optJSONObject("result") ?: return emptyList()
    val files = result.optJSONArray("files") ?: JSONArray()
    buildList {
        for (index in 0 until files.length()) {
            val item = files.optJSONObject(index) ?: continue
            val path = item.optString("path").takeIf { it.isNotBlank() } ?: continue
            add(
                NativeProjectFile(
                    path = path,
                    name = item.optString("name", path.substringAfterLast('/')),
                    size = item.optLong("size"),
                    kind = item.optString("kind", "file")
                )
            )
        }
    }
}.getOrDefault(emptyList())

internal fun projectFilesListingTruncated(response: String): Boolean = runCatching {
    val result = JSONObject(response).optJSONObject("output")?.optJSONObject("result")
    result?.optBoolean("truncated") == true
}.getOrDefault(false)

internal fun parseProjectFileContent(response: String): NativeProjectFileContent? = runCatching {
    val envelope = JSONObject(response)
    if (!envelope.optBoolean("ok")) return null
    val result = envelope.optJSONObject("output")?.optJSONObject("result") ?: return null
    val path = result.optString("path").takeIf { it.isNotBlank() } ?: return null
    NativeProjectFileContent(
        path = path,
        content = result.optString("content"),
        size = result.optLong("size")
    )
}.getOrNull()

@Composable
internal fun ProjectFilesPanel(
    filesJson: String,
    selectedFileJson: String,
    loading: Boolean,
    selectedFileLoading: Boolean = false,
    saving: Boolean = false,
    saveMessage: String = "",
    error: String,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String, String) -> Unit,
    onCloseFile: () -> Unit
) {
    val files = remember(filesJson) { parseProjectFiles(filesJson) }
    val truncated = remember(filesJson) { projectFilesListingTruncated(filesJson) }
    val selected = remember(selectedFileJson) { parseProjectFileContent(selectedFileJson) }
    var query by remember { mutableStateOf("") }
    var editing by remember(selected?.path) { mutableStateOf(false) }
    var draft by remember(selected?.path) { mutableStateOf(selected?.content.orEmpty()) }
    LaunchedEffect(selected?.path, selected?.content) {
        draft = selected?.content.orEmpty()
        editing = false
    }
    val filtered = remember(files, query) {
        if (query.isBlank()) files else files.filter {
            it.path.contains(query, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected == null && selectedFileLoading) {
            Text("Opening source file…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCloseFile) { Text("All files") }
                Spacer(Modifier.weight(1f))
                Text(formatProjectFileSize(selected.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(selected.path, fontWeight = FontWeight.SemiBold)
            if (editing) {
                Text(
                    "Editing a local project file · changes are saved on this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 18,
                    maxLines = 34,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onSaveFile(selected.path, draft) },
                        enabled = !saving && draft != selected.content,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (saving) "Saving…" else "Save changes") }
                    OutlinedButton(
                        onClick = {
                            draft = selected.content
                            editing = false
                        },
                        enabled = !saving
                    ) { Text("Discard") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SOURCE",
                        color = Color(0xFFAD91FF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { editing = true }) { Text("Edit file") }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        selected.content,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
            if (saveMessage.isNotBlank()) {
                Text(
                    saveMessage,
                    color = if (saveMessage.startsWith("Saved")) Color(0xFF83D7AA) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "Source editor · files stay in this app's private project folder",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Project source", fontWeight = FontWeight.SemiBold)
                    Text(
                        files.size.toString() + " files indexed" + if (truncated) " · listing capped at 300" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "Scanning…" else "Refresh")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search files") },
                placeholder = { Text("src, MainActivity, package.json…") }
            )
            if (loading && files.isEmpty()) {
                Text("Reading the project folder…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
            if (!loading && files.isEmpty() && error.isBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No source files found", fontWeight = FontWeight.SemiBold)
                        Text(
                            "This project may not have generated its app files yet, or its output may be a native artifact.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onRefresh) { Text("Scan again") }
                    }
                }
            }
            filtered.take(250).forEach { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().then(
                        if (file.kind == "text") Modifier.clickable { onOpenFile(file.path) } else Modifier
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            file.name.substringAfterLast('.', "FILE").take(5).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(file.path, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                        Text(
                            if (file.kind == "text") formatProjectFileSize(file.size) else "Binary",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (filtered.isEmpty() && files.isNotEmpty()) {
                Text("No files match that search.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatProjectFileSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
