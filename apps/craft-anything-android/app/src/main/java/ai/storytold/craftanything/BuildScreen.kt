package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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

@Composable
internal fun BuildScreen(
    backends: List<NativeAiBackend>,
    selectedBackend: String,
    onBackendSelected: (String) -> Unit,
    starting: Boolean,
    feedback: String,
    importedTargetPath: String,
    importedTargetFolder: String,
    onChooseTarget: () -> Unit,
    onStartBuild: (String) -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var request by remember { mutableStateOf("") }
    var referenceUrl by remember { mutableStateOf("") }
    var buildWith by remember { mutableStateOf("") }
    var generateAssets by remember { mutableStateOf(false) }
    var backendMenu by remember { mutableStateOf(false) }
    var confirmParams by remember { mutableStateOf<String?>(null) }


    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Build something", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Describe what you want. Craft Anything will study your reference, create a project, and work through the build with you.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your idea", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    placeholder = { Text("Example: Build a polished offline-first notes app with folders, search and a dark theme.") },
                    label = { Text("What should it make?") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = referenceUrl,
                    onValueChange = { referenceUrl = it },
                    label = { Text("Reference website (optional)") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onChooseTarget, modifier = Modifier.fillMaxWidth()) {
                    Text(if (importedTargetPath.isNotBlank()) "Replace imported reference file" else "Import a local reference file")
                }
                if (importedTargetPath.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20202A)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Imported reference", fontWeight = FontWeight.SemiBold)
                            Text(importedTargetPath.substringAfterLast('/'), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("The builder will use this file's private source folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Leave the website empty to start from your description alone. Imported files are copied into an isolated app-private folder before the builder reads them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Build settings", fontWeight = FontWeight.SemiBold)
                Text("Builder model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = buildWith,
                    onValueChange = { buildWith = it },
                    label = { Text("Preferred framework or engine (optional)") },
                    placeholder = { Text("Godot 4, Unity, React, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { backendMenu = true },
                        enabled = backends.any { it.enabled },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(backends.firstOrNull { it.name == selectedBackend }?.name ?: "Use default builder")
                    }
                    DropdownMenu(expanded = backendMenu, onDismissRequest = { backendMenu = false }) {
                        backends.filter { it.enabled }.forEach { backend ->
                            DropdownMenuItem(
                                text = { Text(backend.name) },
                                onClick = {
                                    onBackendSelected(backend.name)
                                    backendMenu = false
                                },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = generateAssets, onCheckedChange = { generateAssets = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow generated pictures and 3D assets")
                        Text(
                            "Optional. This can use a configured asset-provider key and the project's asset budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (backends.none { it.enabled }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2118)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Connect a builder first", fontWeight = FontWeight.SemiBold)
                    Text("Open Settings and add an API provider, then assign it to the Builder role.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (feedback.isNotBlank()) {
            Text(feedback, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = {
                val fullRequest = buildString {
                    append(request.trim())
                    if (referenceUrl.isNotBlank()) {
                        append("\\n\\nReference website to study: ")
                        append(referenceUrl.trim())
                    }
                    if (importedTargetPath.isNotBlank()) {
                        append("\\n\\nUse the imported reference file as source material: ")
                        append(importedTargetPath)
                    }
                }
                val params = JSONObject()
                    .put("request", fullRequest)
                    .put("resume", false)
                    .put("rounds", 2)
                    .put("generate_assets", generateAssets)
                if (importedTargetFolder.isNotBlank()) params.put("project_folder", importedTargetFolder)
                if (buildWith.isNotBlank()) params.put("build_with", buildWith.trim())
                if (selectedBackend.isNotBlank()) params.put("backend", selectedBackend)
                confirmParams = params.toString()
            },
            enabled = request.isNotBlank() && !starting && backends.any { it.enabled },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (starting) "Starting build…" else "Review and start build")
        }
        OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
            Text("View my projects")
        }
        Spacer(Modifier.height(12.dp))
    }

    confirmParams?.let { params ->
        AlertDialog(
            onDismissRequest = { confirmParams = null },
            title = { Text("Start this build?") },
            text = {
                Text(
                    "Craft Anything will use the selected AI provider, create project files, access the reference you supplied, and may run build commands. Only continue if you trust the target and the project request.",
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
            },
        )
    }
}
