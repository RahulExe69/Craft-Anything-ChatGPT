package ai.storytold.craftanything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF15151D)
private val Violet = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9A97A8)

class MainActivity : ComponentActivity() {
    private external fun nativeVersion(): String
    private external fun nativeToolCatalog(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nativeReady = runCatching { System.loadLibrary("craft_anything_android") }.isSuccess
        setContent {
            var selected by remember { mutableStateOf("Home") }
            var coreVersion by remember { mutableStateOf(if (nativeReady) "Connecting to Rust core…" else "Rust core not built yet") }
            var toolCount by remember { mutableStateOf(0) }

            LaunchedEffect(nativeReady) {
                if (nativeReady) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            nativeVersion() to JSONArray(nativeToolCatalog()).length()
                        }
                    }
                    result.onSuccess {
                        coreVersion = "Rust core v${it.first}"
                        toolCount = it.second
                    }.onFailure {
                        coreVersion = "Rust bridge error: ${it.message ?: "unknown error"}"
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Violet,
                background = Ink,
                surface = Panel,
                onBackground = Color(0xFFF5F2FF),
                onSurface = Color(0xFFF5F2FF)
            )) {
                Scaffold(
                    containerColor = Ink,
                    bottomBar = {
                        NavigationBar(containerColor = Panel) {
                            listOf(
                                Triple("Home", Icons.Rounded.Home, "Home"),
                                Triple("Projects", Icons.Rounded.FolderOpen, "Projects"),
                                Triple("Tools", Icons.Rounded.Terminal, "Tools"),
                                Triple("Settings", Icons.Rounded.Settings, "Settings")
                            ).forEach { (label, icon, route) ->
                                NavigationBarItem(
                                    selected = selected == route,
                                    onClick = { selected = route },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(42.dp).background(Violet.copy(alpha = .16f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Violet)
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("CRAFT ANYTHING", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
                                Text(selected, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        when (selected) {
                            "Home" -> HomeScreen(coreVersion, toolCount) { selected = "Tools" }
                            "Projects" -> EmptyScreen("Your projects", "Projects you create or open on this device will appear here.")
                            "Tools" -> ToolsScreen(nativeReady, toolCount, coreVersion)
                            else -> EmptyScreen("Settings", "Mobile permissions, AI providers and connected companion devices will live here.")
                        }
                    }
                }
            }
        }
    }

    companion object {
        init {
            // Loading is attempted in onCreate so the UI can still explain a missing native library.
        }
    }
}

@Composable
private fun HomeScreen(coreVersion: String, toolCount: Int, openTools: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Understand it. Rebuild it.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Inspect software, collect evidence and build something of your own — from your phone.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("ENGINE STATUS", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(coreVersion, fontWeight = FontWeight.SemiBold)
                if (toolCount > 0) Text("$toolCount registered tools", color = Violet)
                Spacer(Modifier.height(14.dp))
                Text("The Android shell is in place. Core workflows are being connected in stages.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = openTools),
            colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = .13f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Violet)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Explore the tool registry", fontWeight = FontWeight.Bold)
                    Text("See what the Rust engine exposes", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("→", color = Violet)
            }
        }
    }
}

@Composable
private fun ToolsScreen(nativeReady: Boolean, toolCount: Int, coreVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Engine connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(coreVersion, color = Muted)
        Text(
            if (nativeReady) "$toolCount tools discovered from the Rust registry." else "Build the Rust Android library to populate the real tool registry.",
            color = Muted
        )
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("First vertical slice", fontWeight = FontWeight.Bold)
                Text("Native file picker → Rust inspection tool → evidence result.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyScreen(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, color = Muted)
    }
}
