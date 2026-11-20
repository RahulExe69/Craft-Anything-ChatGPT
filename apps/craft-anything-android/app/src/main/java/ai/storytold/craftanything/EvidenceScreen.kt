package ai.storytold.craftanything

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeEvidence(
    val id: String,
    val operation: String,
    val summary: String,
    val confidence: String,
    val coverage: String,
    val limitations: Int
)

internal data class NativeUnknown(
    val id: String,
    val question: String,
    val domain: String,
    val severity: String,
    val status: String
)

internal fun parseEvidence(response: String): List<NativeEvidence> {
    return runCatching {
        val envelope = JSONObject(response)
        if (!envelope.optBoolean("ok")) return emptyList()
        val records = envelope.optJSONObject("output")?.optJSONObject("result")
            ?.optJSONArray("records") ?: JSONArray()
        buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                val coverage = item.optJSONObject("coverage")
                val limitations = item.optJSONArray("limitations")
                add(
                    NativeEvidence(
                        id = item.optString("evidence_id", item.optString("id")),
                        operation = item.optString("operation"),
                        summary = item.optString("summary"),
                        confidence = item.optString("confidence"),
                        coverage = coverage?.optString("status").orEmpty(),
                        limitations = limitations?.length() ?: 0
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun parseUnknowns(response: String): List<NativeUnknown> {
    return runCatching {
        val envelope = JSONObject(response)
        if (!envelope.optBoolean("ok")) return emptyList()
        val items = envelope.optJSONObject("output")?.optJSONObject("result")
            ?.optJSONArray("items") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    NativeUnknown(
                        id = item.optString("id"),
                        question = item.optString("question"),
                        domain = item.optString("domain"),
                        severity = item.optString("severity"),
                        status = item.optString("status")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

@Composable
internal fun EvidenceScreen(
    evidenceJson: String,
    unknownsJson: String,
    loading: Boolean,
    error: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val evidence = remember(evidenceJson) { parseEvidence(evidenceJson) }
    val unknowns = remember(unknownsJson) { parseUnknowns(unknownsJson) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Evidence & questions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${evidence.size} evidence records · ${unknowns.size} recorded questions",
                    color = Color(0xFF9A97A8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        }

        if (loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        if (error.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2029))) {
                Text(error, modifier = Modifier.padding(14.dp), color = Color(0xFFFFC2CD))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Evidence so far", color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (evidence.isEmpty()) {
                item {
                    EmptyEvidenceCard(
                        "No evidence recorded yet",
                        "Open a target and run an analysis tool. Verified observations will appear here."
                    )
                }
            } else {
                items(evidence, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.operation, fontWeight = FontWeight.SemiBold)
                            Text(item.summary.ifBlank { "No summary was supplied for this record." }, color = Color(0xFFB9B5C6), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(item.confidence.replace('_', ' '), color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelSmall)
                                Text("Coverage: ${item.coverage.ifBlank { "unknown" }}", color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
                                if (item.limitations > 0) {
                                    Text("${item.limitations} limits", color = Color(0xFFFFC46B), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(item.id, color = Color(0xFF747182), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.padding(top = 4.dp))
                Text("Recorded questions", color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (unknowns.isEmpty()) {
                item {
                    EmptyEvidenceCard(
                        "No recorded questions",
                        "Unresolved items recorded by the analysis engine will be listed here."
                    )
                }
            } else {
                items(unknowns, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20202A)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.question, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(item.severity.uppercase(), color = Color(0xFFFFC46B), style = MaterialTheme.typography.labelSmall)
                                Text(item.status.replace('_', ' '), color = Color(0xFF9B7BFF), style = MaterialTheme.typography.labelSmall)
                                Text(item.domain, color = Color(0xFF9A97A8), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEvidenceCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color(0xFF9A97A8), style = MaterialTheme.typography.bodySmall)
        }
    }
}
