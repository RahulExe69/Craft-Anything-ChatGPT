package ai.storytold.craftanything

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.util.Locale

internal data class NativeTool(
    val id: String,
    val title: String,
    val summary: String,
    val toolset: String,
    val toolsetTitle: String,
    val effects: Int,
    val requiresConsent: Boolean,
    val inputSchema: String
)

internal fun parseToolCatalog(json: String): List<NativeTool> {
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val schema = item.optJSONObject("input_schema")
                add(
                    NativeTool(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        summary = item.optString("summary"),
                        toolset = item.optString("toolset"),
                        toolsetTitle = item.optString("toolset_title", item.optString("toolset")),
                        effects = item.optInt("effects"),
                        requiresConsent = item.optBoolean("requires_consent"),
                        inputSchema = schema?.toString(2) ?: "{}"
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun filterToolCatalog(tools: List<NativeTool>, query: String): List<NativeTool> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return tools
    return tools.filter { tool ->
        tool.id.lowercase(Locale.ROOT).contains(needle) ||
            tool.title.lowercase(Locale.ROOT).contains(needle) ||
            tool.summary.lowercase(Locale.ROOT).contains(needle) ||
            tool.toolset.lowercase(Locale.ROOT).contains(needle) ||
            tool.toolsetTitle.lowercase(Locale.ROOT).contains(needle)
    }
}

@Composable
internal fun ToolsScreen(
    catalogJson: String,
    coreVersion: String,
    result: String,
    onInvoke: (toolId: String, paramsJson: String, consentGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = remember(catalogJson) { parseToolCatalog(catalogJson) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var paramsJson by remember { mutableStateOf("{}") }
    var pendingApprovalParams by remember { mutableStateOf("{}") }
    var approvalTool by remember { mutableStateOf<NativeTool?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val filtered = remember(tools, query) { filterToolCatalog(tools, query) }
    val selectedTool = tools.firstOrNull { it.id == selectedId }

    LaunchedEffect(selectedId) {
        paramsJson = selectedTool?.inputSchema?.let(::defaultParameters) ?: "{}"
        validationError = null
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tool registry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(coreVersion, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search tools") },
            placeholder = { Text("Name, ID, description or category") }
        )
        Text("${filtered.size} of ${tools.size} tools", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filtered.groupBy { it.toolset }.forEach { (_, groupTools) ->
                val groupTitle = groupTools.firstOrNull()?.toolsetTitle.orEmpty()
                item(key = "toolset-${groupTools.firstOrNull()?.toolset.orEmpty()}") {
                    Text(
                        groupTitle,
                        color = Color(0xFF9B7BFF),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(groupTools, key = { it.id }) { tool ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedId = if (selectedId == tool.id) null else tool.id
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(tool.title.ifBlank { tool.id }, fontWeight = FontWeight.SemiBold)
                                Text(tool.id, color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(tool.toolsetTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(tool.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (tool.requiresConsent) {
                            Text("Approval required", color = Color(0xFFFFC46B), style = MaterialTheme.typography.labelSmall)
                        }
                        if (selectedId == tool.id) {
                            Spacer(Modifier.height(4.dp))
                            Text("INPUT SCHEMA", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            Text(tool.inputSchema, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = paramsJson,
                                onValueChange = {
                                    paramsJson = it
                                    validationError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 6,
                                label = { Text("Tool parameters (JSON)") }
                            )
                            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = {
                                    val candidate = runCatching {
                                        org.json.JSONObject(paramsJson.ifBlank { "{}" }).toString()
                                    }
                                    candidate.onSuccess { normalized ->
                                        validationError = null
                                        if (tool.requiresConsent) {
                                            pendingApprovalParams = normalized
                                            approvalTool = tool
                                        } else onInvoke(tool.id, normalized, false)
                                    }.onFailure {
                                        validationError = "Enter a valid JSON object before running this tool."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (tool.requiresConsent) "Review and run" else "Run tool")
                            }
                        }
                    }
                }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text("No tools match that search.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
                }
            }
        }

        if (result.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    Text("LAST RESULT", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(result, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    approvalTool?.let { tool ->
        AlertDialog(
            onDismissRequest = { approvalTool = null },
            title = { Text("Confirm this tool action") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${tool.title} is marked by the Rust engine as running foreign code or changing a connected device. " +
                            "Approve this single invocation only if you trust the action."
                    )
                    Text("PARAMETERS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(pendingApprovalParams, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val approved = tool
                    approvalTool = null
                    onInvoke(approved.id, pendingApprovalParams, true)
                }) {
                    Text("Approve once and run")
                }
            },
            dismissButton = {
                TextButton(onClick = { approvalTool = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun defaultParameters(schema: String): String {
    val parsed = runCatching { org.json.JSONObject(schema) }.getOrNull() ?: return "{}"
    val properties = parsed.optJSONObject("properties") ?: return "{}"
    val defaults = org.json.JSONObject()
    val keys = properties.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val property = properties.optJSONObject(key) ?: continue
        if (property.has("default")) {
            defaults.put(key, property.opt("default"))
        }
    }
    return defaults.toString(2)
}
