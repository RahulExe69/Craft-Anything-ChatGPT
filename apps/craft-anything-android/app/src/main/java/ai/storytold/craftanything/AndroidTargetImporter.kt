package ai.storytold.craftanything

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import org.json.JSONObject

/**
 * Owns the Android-side copy/open/cleanup contract for a SAF-selected target.
 *
 * The activity obtains the provider stream (Android's ContentResolver API is context-bound)
 * and delegates storage and engine hand-off work here.
 */
internal data class ImportedAndroidTarget(val message: String, val path: String)

internal object AndroidTargetImporter {
    fun import(
        input: InputStream,
        displayName: String,
        targetsDir: File,
        maxBytes: Long,
        openTarget: (parametersJson: String) -> String
    ): String = importWithPath(input, displayName, targetsDir, maxBytes, openTarget).message

    fun importWithPath(
        input: InputStream,
        displayName: String,
        targetsDir: File,
        maxBytes: Long,
        openTarget: (parametersJson: String) -> String
    ): ImportedAndroidTarget {
        var copy: File? = null
        try {
            if (!targetsDir.exists() && !targetsDir.mkdirs()) {
                throw IOException("Couldn't create the private import directory.")
            }

            val destination = File.createTempFile("target-", FileImport.safeSuffix(displayName), targetsDir)
            copy = destination
            val copiedBytes = FileImport.copyBounded(input, destination, maxBytes)
            val parameters = JSONObject().put("path", destination.absolutePath).toString()
            val payload = JSONObject(openTarget(parameters))
            if (!payload.optBoolean("ok")) {
                throw IOException(
                    payload.optString("error").takeIf { it.isNotBlank() }
                        ?: "The Rust engine couldn't open this file."
                )
            }

            val target = payload.optJSONObject("output")?.optJSONObject("result")
            val enginePath = target?.optString("path")?.takeIf { it.isNotBlank() }
            if (enginePath != null && File(enginePath).absolutePath != destination.absolutePath) {
                destination.delete()
            }

            val title = target?.optString("name")?.takeIf { it.isNotBlank() } ?: displayName
            val kind = target?.optString("kind")?.takeIf { it.isNotBlank() } ?: "target"
            return ImportedAndroidTarget(
                message = "Opened $title · $kind · ${formatByteCount(copiedBytes)}",
                path = enginePath ?: destination.absolutePath,
            )
        } catch (error: Exception) {
            runCatching { input.close() }
            copy?.delete()
            throw error
        }
    }

    private fun formatByteCount(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
