package ai.storytold.craftanything

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTargetImporterTest {
    @Test
    fun successfulImportKeepsTheFileWhenTheEngineUsesTheSamePath() {
        val directory = createTempDir(prefix = "android-import-same-")
        var importedPath: String? = null
        try {
            val message = AndroidTargetImporter.import(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                displayName = "sample.apk",
                targetsDir = directory,
                maxBytes = 16
            ) { parameters ->
                val path = JSONObject(parameters).getString("path")
                importedPath = path
                JSONObject()
                    .put("ok", true)
                    .put(
                        "output",
                        JSONObject().put(
                            "result",
                            JSONObject().put("path", path).put("name", "sample").put("kind", "apk")
                        )
                    )
                    .toString()
            }

            assertTrue(File(importedPath!!).isFile)
            assertTrue(message.contains("sample"))
            assertTrue(message.contains("apk"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedEngineOpenRemovesTheTemporaryCopy() {
        val directory = createTempDir(prefix = "android-import-fail-")
        try {
            val error = org.junit.Assert.assertThrows(IOException::class.java) {
                AndroidTargetImporter.import(
                    input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                    displayName = "broken.bin",
                    targetsDir = directory,
                    maxBytes = 16
                ) {
                    JSONObject().put("ok", false).put("error", "unsupported file").toString()
                }
            }

            assertEquals("unsupported file", error.message)
            assertEquals(0, directory.listFiles()?.size ?: 0)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun relocatedTargetRemovesTheRedundantImportCopy() {
        val directory = createTempDir(prefix = "android-import-move-")
        try {
            val message = AndroidTargetImporter.import(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                displayName = "source.zip",
                targetsDir = directory,
                maxBytes = 16
            ) {
                val relocated = File(directory, "engine-owned-target.zip")
                relocated.writeBytes(byteArrayOf(1, 2, 3))
                JSONObject()
                    .put("ok", true)
                    .put(
                        "output",
                        JSONObject().put(
                            "result",
                            JSONObject().put("path", relocated.absolutePath).put("name", "engine target").put("kind", "zip")
                        )
                    )
                    .toString()
            }

            val files = directory.listFiles().orEmpty()
            assertEquals(1, files.size)
            assertEquals("engine-owned-target.zip", files.single().name)
            assertTrue(message.contains("engine target"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
