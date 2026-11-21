package ai.storytold.craftanything

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class BuildStarter(val title: String, val description: String, val prompt: String)
private val buildStarters = listOf(
    BuildStarter("Website", "A responsive website", "Build a polished, responsive website with a clear visual identity, refined typography and mobile-friendly layouts."),
    BuildStarter("Mobile app", "Screens and user flows", "Build a complete mobile app with a cohesive visual system, intuitive navigation and polished core user flows."),
    BuildStarter("Dashboard", "Data and workflows", "Build a modern dashboard with useful data visualisations, filters and responsive layouts."),
    BuildStarter("Game", "A playable experience", "Build a playable game with a clear core loop, polished controls and a complete start-to-finish flow.")
)

@Composable
internal fun BuildScreen(
    backends: List<NativeAiBackend>,
    selectedBackend: String,
    onBackendSelected: (String) -> Unit,
    starting: Boolean,
    feedback: String,
    importedTargetPath: String,
    onChooseTarget: () -> Unit,
    onStartBuild: (String) -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var request by remember { mutableStateOf("") }
    var referenceUrl by remember { mutableStateOf("") }
    var showReferenceUrl by remember { mutableStateOf(false) }
    var buildWith by remember { mutableStateOf("") }
    var generateAssets by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var backendMenu by remember { mutableStateOf(false) }
    var confirmParams by remember { mutableStateOf<String?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50.dp)) {
                Text("  ✦  AI APP BUILDER  ", modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("What do you want to build?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Describe your idea. Then work with your AI builder, code and preview in one workspace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Describe your idea", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("NEW PROJECT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(value = request, onValueChange = { request = it }, placeholder = { Text("A 3D animated portfolio with floating objects, smooth camera movement and a playful visual style…") }, minLines = 5, maxLines = 9, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showReferenceUrl = !showReferenceUrl }, modifier = Modifier.weight(1f)) { Text(if (showReferenceUrl) "Hide link" else "Add reference") }
                    OutlinedButton(onClick = onChooseTarget, modifier = Modifier.weight(1f)) { Text(if (importedTargetPath.isNotBlank()) "Replace files" else "Attach files") }
                }
                if (showReferenceUrl || referenceUrl.isNotBlank()) {
                    OutlinedTextField(value = referenceUrl, onValueChange = { referenceUrl = it }, label = { Text("Reference website") }, placeholder = { Text("https://example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (importedTargetPath.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Text("Attached · " + importedTargetPath.substringAfterLast('/'), modifier = Modifier.fillMaxWidth().padding(11.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Build with", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(backends.firstOrNull { it.name == selectedBackend }?.let { it.name + " · " + it.model } ?: "Choose an AI provider", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    OutlinedButton(onClick = { backendMenu = true }, enabled = backends.any { it.enabled }) { Text("Change") }
                    DropdownMenu(expanded = backendMenu, onDismissRequest = { backendMenu = false }) {
                        backends.filter { it.enabled }.forEach { backend ->
                            DropdownMenuItem(text = { Text(backend.name + " · " + backend.model) }, onClick = { onBackendSelected(backend.name); backendMenu = false })
                        }
                    }
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.align(Alignment.Start)) { Text(if (showAdvanced) "Hide advanced options" else "Advanced options") }
                if (showAdvanced) {
                    OutlinedTextField(value = buildWith, onValueChange = { buildWith = it }, label = { Text("Framework or engine") }, placeholder = { Text("React, Kotlin / Compose, Godot 4…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = generateAssets, onCheckedChange = { generateAssets = it })
                        Column(Modifier.weight(1f)) {
                            Text("Generate images and 3D assets", fontWeight = FontWeight.Medium)
                            Text("Uses your configured asset provider and project budget.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = {
                        val fullRequest = buildString {
                            append(request.trim())
                            if (referenceUrl.isNotBlank()) append("\n\nReference website to study: " + referenceUrl.trim())
                            if (importedTargetPath.isNotBlank()) append("\n\nUse this attached file as source material: " + importedTargetPath)
                        }
                        val params = JSONObject().put("request", fullRequest).put("resume", false).put("rounds", 2).put("generate_assets", generateAssets)
                        if (buildWith.isNotBlank()) params.put("build_with", buildWith.trim())
                        if (selectedBackend.isNotBlank()) params.put("backend", selectedBackend)
                        confirmParams = params.toString()
                    },
                    enabled = request.isNotBlank() && !starting && backends.any { it.enabled },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (starting) "Preparing your workspace…" else "Start building") }
                if (backends.none { it.enabled }) Text("Add an AI provider in Settings and assign it to the Builder role to start.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start from an idea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("TEMPLATES", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buildStarters.forEach { starter ->
                    AssistChip(onClick = { request = if (request.isBlank()) starter.prompt else request + "\n\n" + starter.prompt }, label = { Text(starter.title) })
                }
            }
        }
        TextButton(onClick = onOpenProjects, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Open my projects") }
        Spacer(Modifier.height(4.dp))
    }

    confirmParams?.let { params ->
        AlertDialog(
            onDismissRequest = { confirmParams = null },
            title = { Text("Start this build?") },
            text = { Text("Craft Anything will create project files, use your selected AI provider and may run build commands. Project changes and commands follow the configured approval flow.") },
            confirmButton = { Button(onClick = { confirmParams = null; onStartBuild(params) }) { Text("Allow and build") } },
            dismissButton = { TextButton(onClick = { confirmParams = null }) { Text("Cancel") } }
        )
    }
}
