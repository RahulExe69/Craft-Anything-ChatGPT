package ai.storytold.craftanything

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val craftBuildPhases = listOf("Study", "Build", "Check", "Ready")

internal fun projectPhaseIndex(status: String): Int? = when (status.lowercase()) {
    "new", "inspecting" -> 0
    "building" -> 1
    "checking" -> 2
    "ready" -> 3
    else -> null
}

internal fun projectPhaseMessage(status: String): String? = when (status.lowercase()) {
    "needs_you" -> "This project needs your answer or approval before it can continue."
    "stopped" -> "This build is paused. You can continue it from the project conversation."
    else -> null
}

@Composable
internal fun ProjectPhaseStepper(status: String, statusLabel: String) {
    val active = projectPhaseIndex(status)
    val message = projectPhaseMessage(status)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROJECT PROGRESS", color = Color(0xFFAD91FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(statusLabel, color = if (message != null) Color(0xFFFFCC80) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                craftBuildPhases.forEachIndexed { index, title ->
                    val complete = active != null && index < active
                    val current = active == index
                    val tint = when {
                        complete -> Color(0xFF70C99B)
                        current -> Color(0xFFAD91FF)
                        else -> Color(0xFF484552)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(25.dp).background(
                                if (current) Color(0xFF382D54) else if (complete) Color(0xFF1D382D) else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (complete) "✓" else (index + 1).toString(),
                                color = tint,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            title,
                            color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else if (complete) Color(0xFF9BD9B7) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
            if (message != null) {
                Text(message, color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall)
            } else if (active == null) {
                Text("The project is waiting for its next action.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
