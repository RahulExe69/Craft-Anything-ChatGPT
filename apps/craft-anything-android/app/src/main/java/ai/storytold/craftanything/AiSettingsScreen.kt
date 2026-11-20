package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeAiBackend(
    val name: String,
    val kind: String,
    val model: String,
    val baseUrl: String,
    val enabled: Boolean,
    val hasKey: Boolean,
)

internal data class NativeAiStatus(
    val backends: List<NativeAiBackend> = emptyList(),
    val lead: String? = null,
    val builder: String? = null,
    val error: String? = null,
)

internal fun parseNativeAiStatus(raw: String): NativeAiStatus {
    return runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("ok")) {
            return NativeAiStatus(error = root.optString("error", "Couldn't read AI settings."))
        }
        val output = root.optJSONObject("output") ?: JSONObject()
        val entries = output.optJSONArray("backends") ?: JSONArray()
        val backends = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                add(
                    NativeAiBackend(
                        name = item.optString("name"),
                        kind = item.optString("kind"),
                        model = item.optString("model"),
                        baseUrl = item.optString("base_url").takeUnless { it == "null" },
                        enabled = item.optBoolean("enabled"),
                        hasKey = item.optBoolean("has_key"),
                    )
                )
            }
        }
        val roles = output.optJSONObject("roles") ?: JSONObject()
        fun role(key: String): String? =
            roles.optString(key).takeUnless { it.isBlank() || it == "null" }
        NativeAiStatus(backends = backends, lead = role("lead"), builder = role("builder"))
    }.getOrElse { NativeAiStatus(error = it.message ?: "Couldn't read AI settings.") }
}

private data class ProviderType(val title: String, val id: String)

private val providerTypes = listOf(
    ProviderType("OpenAI", "openai"),
    ProviderType("Anthropic", "anthropic"),
    ProviderType("OpenRouter", "openrouter"),
    ProviderType("Google Gemini", "gemini"),
    ProviderType("OpenAI-compatible server", "openai_compatible"),
)

private fun providerTitle(kind: String): String =
    providerTypes.firstOrNull { it.id == kind }?.title ?: kind.replace('_', ' ')

@Composable
internal fun AiSettingsScreen(
    statusJson: String,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onAddBackend: (name: String, kind: String, model: String, baseUrl: String, key: String) -> Unit,
    onSetRole: (role: String, backend: String) -> Unit,
    onSetEnabled: (name: String, enabled: Boolean) -> Unit,
    onRemoveBackend: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = remember(statusJson) { parseNativeAiStatus(statusJson) }
    var showForm by remember { mutableStateOf(status.backends.isEmpty()) }
    var providerKind by remember { mutableStateOf("openai") }
    var providerMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("OpenAI") }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AI providers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Connect the models that power your conversations and builds.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onRefresh, enabled = !loading) {
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                else Text("Refresh")
            }
        }

        if (status.error != null) {
            Text(status.error, color = MaterialTheme.colorScheme.error)
        }
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17131F)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", fontWeight = FontWeight.SemiBold)
                Text(
                    "Provider keys stay in this app's private storage. The app never sends saved keys back to the screen or includes them in project files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text("Connected providers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (status.backends.isEmpty()) {
            Text("Nothing connected yet. Add a provider to start an AI conversation.")
        } else {
            status.backends.forEach { backend ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backend.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${providerTitle(backend.kind)} · ${backend.model}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = backend.enabled, onCheckedChange = { onSetEnabled(backend.name, it) })
                        }
                        Text(
                            if (backend.hasKey) "Credentials ready" else "No API key saved",
                            color = if (backend.hasKey) Color(0xFF9FDEA9) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { onSetRole("lead", backend.name) }, modifier = Modifier.weight(1f)) {
                                Text(if (status.lead == backend.name) "Lead ✓" else "Set as Lead")
                            }
                            OutlinedButton(onClick = { onSetRole("builder", backend.name) }, modifier = Modifier.weight(1f)) {
                                Text(if (status.builder == backend.name) "Builder ✓" else "Set as Builder")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Remove",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .then(Modifier),
                            )
                            OutlinedButton(
                                onClick = { onRemoveBackend(backend.name) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Remove provider")
                            }
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Add a provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showForm = !showForm }) { Text(if (showForm) "Hide" else "Add") }
        }
        if (showForm) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box {
                        OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(providerTitle(providerKind))
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            providerTypes.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.title) },
                                    onClick = {
                                        providerKind = item.id
                                        name = item.title
                                        providerMenu = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, placeholder = { Text("Enter the exact model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (providerKind == "openai_compatible") {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Server URL") }, placeholder = { Text("http://192.168.1.20:11434/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(
                            "On a real phone, use your computer's local network IP—not localhost. The computer and phone must be on the same Wi-Fi.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(if (providerKind == "openai_compatible") "API key (optional)" else "API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onAddBackend(name.trim(), providerKind, model.trim(), baseUrl.trim(), apiKey.trim())
                            apiKey = ""
                        },
                        enabled = name.isNotBlank() && model.isNotBlank() &&
                            (providerKind != "openai_compatible" || baseUrl.startsWith("http://") || baseUrl.startsWith("https://")),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save provider")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
