package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class AiChatMessage(val role: String, val text: String)
internal data class AiPendingConsent(val callId: String, val title: String, val explanation: String)

@Composable
internal fun AiChatScreen(
    backends: List<NativeAiBackend>,
    selectedBackend: String,
    messages: List<AiChatMessage>,
    connected: Boolean,
    busy: Boolean,
    status: String,
    pendingConsent: AiPendingConsent?,
    onBackendSelected: (String) -> Unit,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onConsent: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask Craft Anything", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (connected) {
                TextButton(onClick = onStop) { Text("Stop") }
            } else {
                OutlinedButton(onClick = { menuExpanded = true }, enabled = backends.isNotEmpty()) {
                    Text(backends.firstOrNull { it.name == selectedBackend }?.name ?: "Choose AI")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    backends.filter { it.enabled }.forEach { backend ->
                        DropdownMenuItem(
                            text = { Text(backend.name) },
                            onClick = {
                                onBackendSelected(backend.name)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (!connected && backends.none { it.enabled }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect an AI provider first", fontWeight = FontWeight.SemiBold)
                    Text("Open Settings to add OpenAI, Anthropic, Gemini, OpenRouter, or a compatible model server.")
                }
            }
        } else if (!connected) {
            Button(onClick = { onStart(selectedBackend) }, modifier = Modifier.fillMaxWidth()) {
                Text("Start conversation")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Your project companion", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Ask about an opened file, explore evidence, or get help understanding what Craft Anything has discovered. Tool actions that need permission will ask first.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, message ->
                val user = message.role == "user"
                Row(
                    horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (user) Color(0xFF30254A) else Color(0xFF17171F),
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(0.92f),
                    ) {
                        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (user) "You" else "Craft Anything", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(message.text.ifBlank { if (busy && !user) "…" else "" })
                        }
                    }
                }
            }
        }

        if (pendingConsent != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2118)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pendingConsent.title, fontWeight = FontWeight.SemiBold)
                    Text(pendingConsent.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConsent(pendingConsent.callId, true) }) { Text("Allow once") }
                        OutlinedButton(onClick = { onConsent(pendingConsent.callId, false) }) { Text("Deny") }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = connected && !busy,
                placeholder = { Text("Message Craft Anything…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Button(
                onClick = {
                    val message = draft.trim()
                    if (message.isNotEmpty()) {
                        onSend(message)
                        draft = ""
                    }
                },
                enabled = connected && !busy && draft.isNotBlank(),
            ) {
                Text("Send")
            }
        }
    }
}
