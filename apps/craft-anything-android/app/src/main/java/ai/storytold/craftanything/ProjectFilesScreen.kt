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
    error: String,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCloseFile: () -> Unit
) {
    val files = remember(filesJson) { parseProjectFiles(filesJson) }
    val selected = remember(selectedFileJson) { parseProjectFileContent(selectedFileJson) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(files, query) {
        if (query.isBlank()) files else files.filter {
            it.path.contains(query, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCloseFile) { Text("Back to files") }
                Spacer(Modifier.weight(1f))
                Text(formatProjectFileSize(selected.size), color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
            }
            Text(selected.path, fontWeight = FontWeight.SemiBold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111117)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    selected.content,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    color = Color(0xFFD8D4E2),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
            Text(
                "Read-only preview · Files are stored in this app's private project folder.",
                color = Color(0xFF9A97A8),
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Project files", fontWeight = FontWeight.SemiBold)
                    Text("${files.size} files indexed", color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
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
                Text("Reading the project folder…", color = Color(0xFFB9B5C6))
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFFA9B8), style = MaterialTheme.typography.bodySmall)
            }
            if (!loading && files.isEmpty() && error.isBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No source files found", fontWeight = FontWeight.SemiBold)
                        Text("This project may not have generated its app files yet, or its output may be a native artifact.", color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onRefresh) { Text("Scan again") }
                    }
                }
            }
            filtered.take(250).forEach { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenFile(file.path) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            file.name.substringAfterLast('.', "FILE").take(5).uppercase(),
                            color = Color(0xFFBFAAFF),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(file.path, color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                        Text(formatProjectFileSize(file.size), color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (filtered.isEmpty() && files.isNotEmpty()) {
                Text("No files match that search.", color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatProjectFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(java.util.Locale.ROOT, bytes / 1024.0)} KB"
    else -> "${"%.1f".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))} MB"
}
