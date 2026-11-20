package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ProjectConsolePanel(
    filesJson: String,
    activityJson: String,
    onInstallDependencies: (String) -> Unit
) {
    val setups = remember(filesJson) { detectDependencySetups(filesJson) }
    val lines = remember(activityJson) { parseBuildActivity(activityJson) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ENVIRONMENT", color = Color(0xFFAD91FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("Packages & build console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Detected from this project's manifests and lockfiles.", color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
        }

        if (setups.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("No supported dependency manifest found", fontWeight = FontWeight.SemiBold)
                    Text(
                        "The project may be plain HTML/CSS/JS or use a toolchain not detected yet. Open Code to inspect its files.",
                        color = Color(0xFFB9B5C6),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            setups.forEach { setup ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(setup.ecosystem, fontWeight = FontWeight.SemiBold)
                                Text(
                                    setup.manifest + " · " + setup.manager + (setup.lockfile?.let { " · " + it } ?: " · no lockfile detected"),
                                    color = Color(0xFF9A97A8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text("DETECTED", color = Color(0xFF8BD7AC), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            setup.command,
                            color = Color(0xFFE0DCEC),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        setup.caution?.let {
                            Text(it, color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onInstallDependencies(dependencyInstallPrompt(setup)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ask builder to install")
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2419)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Execution environment", fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD18A))
                Text(
                    "This APK does not bundle Node.js or desktop package managers. The builder must verify the runtime on its execution target first; missing tools or failed installs must stay visible here instead of being marked complete.",
                    color = Color(0xFFE4D8C4),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Build output", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(lines.size.toString() + " events", color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101015)), shape = RoundedCornerShape(16.dp)) {
                if (lines.isEmpty()) {
                    Text(
                        "No build output yet. Start a build or dependency task and its command activity will appear here.",
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFF9A97A8),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.takeLast(80).forEach { line ->
                            val problem = line.contains("FAIL", true) || line.contains("ERROR", true) || line.contains("failed", true)
                            Text(
                                line,
                                color = if (problem) Color(0xFFFF9EAB) else Color(0xFFD0CBD9),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }
    }
}
