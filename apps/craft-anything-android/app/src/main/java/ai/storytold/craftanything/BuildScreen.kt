package ai.storytold.craftanything

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class BuildStarter(
    val title: String,
    val description: String,
    val prompt: String,
)

private val buildStarters = listOf(
    BuildStarter(
        "Website",
        "A polished site or storefront",
        "Build a polished, responsive website with a clear visual identity, refined typography, and mobile-friendly layouts."
    ),
    BuildStarter(
        "Mobile app",
        "Screens, navigation and flows",
        "Build a complete mobile app with a cohesive visual system, intuitive navigation, and polished core user flows."
    ),
    BuildStarter(
        "Dashboard",
        "Data, charts and workflows",
        "Build a modern dashboard with clear information hierarchy, useful data visualisations, filters, and responsive layouts."
    ),
    BuildStarter(
        "Game",
        "A playable game experience",
        "Build a playable game with a clear core loop, polished controls, a cohesive visual style, and a working start-to-finish flow."
    )
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
    var backendMenu by remember { mutableStateOf(false) }
    var confirmParams by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "YOUR CREATIVE WORKSPACE",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFAD91FF),
                fontWeight = FontWeight.Bold
            )
            Text("What are we building?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Start with an idea. Shape it with AI, then follow every step as your project comes to life.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Describe your idea", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("PROMPT", color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    placeholder = {
                        Text("I want to build something that… Tell Craft Anything what it should do, who it's for, and how it should feel.")
                    },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showReferenceUrl = !showReferenceUrl },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (showReferenceUrl) "Hide website" else "Add website") }
                    OutlinedButton(
                        onClick = onChooseTarget,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (importedTargetPath.isNotBlank()) "Replace file" else "Attach files") }
                }
                if (showReferenceUrl || referenceUrl.isNotBlank()) {
                    OutlinedTextField(
                        value = referenceUrl,
                        onValueChange = { referenceUrl = it },
                        label = { Text("Website to use as a reference") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (importedTargetPath.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF22212B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("REFERENCE ATTACHED", color = Color(0xFFAD91FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(importedTargetPath.substringAfterLast('/'), fontWeight = FontWeight.Medium)
                            Text(
                                "Copied into private app storage for the builder to inspect.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    "You can start with a rough idea. Add details, screenshots or a reference website as you go.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Jump-start your idea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Choose a direction to add a starter prompt. You can edit it afterwards.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                BuildStarterTile(buildStarters[0], Modifier.weight(1f)) { starter ->
                    request = appendStarter(request, starter.prompt)
                }
                BuildStarterTile(buildStarters[1], Modifier.weight(1f)) { starter ->
                    request = appendStarter(request, starter.prompt)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                BuildStarterTile(buildStarters[2], Modifier.weight(1f)) { starter ->
                    request = appendStarter(request, starter.prompt)
                }
                BuildStarterTile(buildStarters[3], Modifier.weight(1f)) { starter ->
                    request = appendStarter(request, starter.prompt)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Build configuration", fontWeight = FontWeight.SemiBold)
                        Text("You can keep the defaults and change these later.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { backendMenu = true },
                        enabled = backends.any { it.enabled }
                    ) {
                        Text(backends.firstOrNull { it.name == selectedBackend }?.name ?: "Choose builder")
                    }
                    DropdownMenu(expanded = backendMenu, onDismissRequest = { backendMenu = false }) {
                        backends.filter { it.enabled }.forEach { backend ->
                            DropdownMenuItem(
                                text = { Text(backend.name) },
                                onClick = {
                                    onBackendSelected(backend.name)
                                    backendMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = buildWith,
                    onValueChange = { buildWith = it },
                    label = { Text("Framework or engine (optional)") },
                    placeholder = { Text("React, Kotlin / Compose, Godot 4…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = generateAssets, onCheckedChange = { generateAssets = it })
                    Column(Modifier.weight(1f)) {
                        Text("Generate images and 3D assets", fontWeight = FontWeight.Medium)
                        Text(
                            "May use your configured asset provider and project budget.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (backends.none { it.enabled }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2118)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Connect a builder to get started", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open Settings and add an AI provider, then assign it to the Builder role.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (feedback.isNotBlank()) {
            Text(feedback, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                val fullRequest = buildString {
                    append(request.trim())
                    if (referenceUrl.isNotBlank()) {
                        append("\n\nReference website to study: ")
                        append(referenceUrl.trim())
                    }
                    if (importedTargetPath.isNotBlank()) {
                        append("\n\nUse the imported reference file as source material: ")
                        append(importedTargetPath)
                    }
                }
                val params = JSONObject()
                    .put("request", fullRequest)
                    .put("resume", false)
                    .put("rounds", 2)
                    .put("generate_assets", generateAssets)
                if (buildWith.isNotBlank()) params.put("build_with", buildWith.trim())
                if (selectedBackend.isNotBlank()) params.put("backend", selectedBackend)
                confirmParams = params.toString()
            },
            enabled = request.isNotBlank() && !starting && backends.any { it.enabled },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (starting) "Starting your build…" else "Start building")
        }
        OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
            Text("Open my projects")
        }
        Spacer(Modifier.height(14.dp))
    }

    confirmParams?.let { params ->
        AlertDialog(
            onDismissRequest = { confirmParams = null },
            title = { Text("Start this build?") },
            text = {
                Text(
                    "Craft Anything will use the selected AI provider, create project files, access the reference you supplied, and may run build commands. Only continue if you trust the target and the project request."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmParams = null
                    onStartBuild(params)
                }) { Text("Allow and build") }
            },
            dismissButton = {
                TextButton(onClick = { confirmParams = null }) { Text("Cancel") }
            }
        )
    }
}

private fun appendStarter(current: String, addition: String): String =
    if (current.isBlank()) addition else "$current\n\n$addition"

@Composable
private fun BuildStarterTile(
    starter: BuildStarter,
    modifier: Modifier = Modifier,
    onSelect: (BuildStarter) -> Unit
) {
    Card(
        modifier = modifier.clickable { onSelect(starter) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(starter.title, fontWeight = FontWeight.SemiBold)
            Text(
                starter.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
