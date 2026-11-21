package ai.storytold.craftanything

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal data class NativeTerminalResult(
    val command: String,
    val cwd: String,
    val exitCode: Int?,
    val succeeded: Boolean,
    val stdout: String,
    val stderr: String,
    val stdoutTruncated: Boolean,
    val stderrTruncated: Boolean,
    val durationMs: Long
)

internal fun parseNativeTerminalResponse(response: String): NativeTerminalResult? = runCatching {
    val envelope = JSONObject(response)
    if (!envelope.optBoolean("ok")) return null
    val result = envelope.optJSONObject("output")?.optJSONObject("result") ?: return null
    val exit = result.opt("exit_code")
    NativeTerminalResult(
        command = result.optString("command"),
        cwd = result.optString("cwd"),
        exitCode = if (exit == null || exit == JSONObject.NULL) null else (exit as? Number)?.toInt(),
        succeeded = result.optBoolean("succeeded"),
        stdout = result.optString("stdout"),
        stderr = result.optString("stderr"),
        stdoutTruncated = result.optBoolean("stdout_truncated"),
        stderrTruncated = result.optBoolean("stderr_truncated"),
        durationMs = result.optLong("duration_ms")
    )
}.getOrNull()

@Composable
internal fun ProjectConsolePanel(
    projectFolder: String,
    appFolder: String,
    filesJson: String,
    activityJson: String,
    commandResultJson: String,
    commandError: String,
    commandRunning: Boolean,
    onRunCommand: (String) -> Unit,
    onMakeStaticWebsite: () -> Unit
) {
    val setups = remember(filesJson) { detectDependencySetups(filesJson) }
    val lines = remember(activityJson) { parseBuildActivity(activityJson) }
    val terminalResult = remember(commandResultJson) { parseNativeTerminalResponse(commandResultJson) }
    var command by remember(projectFolder) { mutableStateOf("") }
    var confirmRun by remember(projectFolder) { mutableStateOf(false) }

    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("Run this command on this device?") },
            text = {
                Text(
                    "The command runs in this project's source folder as Craft Anything. It can read or modify files available to the app. It has a 120-second limit and a 1 MB output limit per stream. Only run commands you understand and trust. Node.js/npm is not bundled."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val selected = command.trim()
                    confirmRun = false
                    command = ""
                    onRunCommand(selected)
                }) { Text("Run command") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PROJECT ENVIRONMENT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("Dependencies & terminal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Commands run locally in this project's source folder. Only executables already present on Android can run.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (setups.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No package manifest detected", fontWeight = FontWeight.SemiBold)
                    Text(
                        "This may be a dependency-free website. Open Preview to inspect the local app, or Code to browse its files.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            setups.forEach { setup ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(setup.ecosystem, fontWeight = FontWeight.SemiBold)
                                Text(
                                    setup.manifest + " · " + setup.manager + (setup.lockfile?.let { " · " + it } ?: " · no lockfile"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text("REQUIRES RUNTIME", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text("Expected on a compatible build machine", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text(setup.command, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        setup.caution?.let { Text(it, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall) }
                        if (setup.ecosystem == "JavaScript / Node.js") {
                            Button(onClick = onMakeStaticWebsite, modifier = Modifier.fillMaxWidth()) {
                                Text("Make this website run without npm")
                            }
                        } else {
                            Text(
                                "This APK does not bundle the required " + setup.manager + " runtime. No install command will be attempted here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("On-device execution", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "This APK includes the Craft Anything Rust engine and a local static website preview. It does not include Node/npm, Python, Go, Cargo, Flutter or a desktop Gradle/Android SDK toolchain. A manifest scan is not an install, and a missing runtime is never marked as ready.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Terminal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("One command at a time · project source folder", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    if (commandRunning) CircularProgressIndicator()
                }
                Text(
                    appFolder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Shell command") },
                    placeholder = { Text("pwd, ls -la, command -v node npm…") },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { confirmRun = true },
                        enabled = command.isNotBlank() && !commandRunning && projectFolder.isNotBlank() && appFolder.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (commandRunning) "Running…" else "Run command") }
                    TextButton(onClick = { command = "command -v node npm python3 git; pwd; ls -la" }, enabled = !commandRunning) {
                        Text("Check tools")
                    }
                }
                Text(
                    "Commands require confirmation. Maximum run time: 120 seconds. Output is capped at 1 MB for stdout and stderr.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                if (commandError.isNotBlank()) {
                    Text(commandError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                terminalResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                (result.exitCode?.let { "Exit code $it" } ?: "Process ended without an exit code") +
                                    " · " + if (result.succeeded) "Succeeded" else "Failed" +
                                    " · " + result.durationMs + " ms",
                                color = if (result.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(result.command, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            Text(result.cwd, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            if (result.stdout.isNotBlank()) TerminalStream("stdout", result.stdout, result.stdoutTruncated)
                            if (result.stderr.isNotBlank()) TerminalStream("stderr", result.stderr, result.stderrTruncated)
                            if (result.stdout.isBlank() && result.stderr.isBlank()) {
                                Text("(no output)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row {
                Text("Build activity", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(lines.size.toString() + " events", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                if (lines.isEmpty()) {
                    Text(
                        "No build activity yet. Start a build to see the engine's recent progress events here.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        lines.takeLast(80).forEach { line ->
                            val problem = line.contains("FAIL", true) || line.contains("ERROR", true) || line.contains("failed", true)
                            Text(
                                line,
                                color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalStream(label: String, content: String, truncated: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label.uppercase() + if (truncated) " · TRUNCATED" else "",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            content,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
