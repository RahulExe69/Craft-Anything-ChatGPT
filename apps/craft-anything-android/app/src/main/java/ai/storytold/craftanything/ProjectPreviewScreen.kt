package ai.storytold.craftanything

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun ProjectPreviewPanel(
    url: String,
    loading: Boolean,
    error: String,
    onStart: () -> Unit,
    onReload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (url.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF17171F)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Live preview", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Open a built website here without leaving Craft Anything. Preview runs locally on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onStart, enabled = !loading) {
                        Text(if (loading) "Preparing preview…" else "Start website preview")
                    }
                }
            }
        } else {
            val parsed = Uri.parse(url)
            val safeUrl = parsed.scheme == "http" && parsed.host == "127.0.0.1" && parsed.port in 1..65535
            if (!safeUrl) {
                Text("The preview returned an address that isn't a local Craft Anything preview.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onStart, enabled = !loading) { Text("Try again") }
            } else {
                val context = LocalContext.current
                val webView = remember(url) {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return true
                                return !(target.scheme == "http" && target.host == "127.0.0.1" && target.port == parsed.port)
                            }
                        }
                        loadUrl(url)
                    }
                }
                DisposableEffect(webView) {
                    onDispose {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxWidth().height(480.dp)
                )
                RowPreviewControls(url = url, onReload = {
                    webView.reload()
                    onReload()
                }, onStart = onStart, loading = loading)
            }
        }
    }
}

@Composable
private fun RowPreviewControls(url: String, onReload: () -> Unit, onStart: () -> Unit, loading: Boolean) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        OutlinedButton(onClick = onReload) { Text("Reload") }
        OutlinedButton(onClick = onStart, enabled = !loading) { Text("Restart") }
    }
}
