package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ProjectConsolePanel(
    filesJson: String,
    activityJson: String,
    onMakeStaticWebsite: () -> Unit
) {
    val setups = remember(filesJson) { detectDependencySetups(filesJson) }
    val lines = remember(activityJson) { parseBuildActivity(activityJson) }
    val hasNodeProject = setups.any { it.ecosystem == "JavaScript / Node.js" }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PROJECT ENVIRONMENT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("Dependencies & build activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Manifests show what a project needs. They do not mean those runtimes are installed on this device.",
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
                        Text("Expected on a Node-capable build machine", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row {
                Text("Build activity", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(lines.size.toString() + " events", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                if (lines.isEmpty()) {
                    Text(
                        "No build activity yet. Start a build to see the engine's recent progress events here. This view is not an interactive shell.",
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
