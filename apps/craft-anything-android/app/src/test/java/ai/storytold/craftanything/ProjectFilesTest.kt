package ai.storytold.craftanything

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilesTest {
    @Test
    fun listsProjectFilesWithoutWalkingDependencyOrGitDirectories() {
        val root = Files.createTempDirectory("craft-project-files").toFile()
        try {
            root.resolve("index.html").writeText("<h1>Hello</h1>")
            root.resolve("src").mkdirs()
            root.resolve("src/App.kt").writeText("fun main() = Unit")
            root.resolve("node_modules/pkg/index.js").also {
                it.parentFile.mkdirs()
                it.writeText("dependency")
            }
            root.resolve(".git/config").also {
                it.parentFile.mkdirs()
                it.writeText("private git metadata")
            }

            val payload = JSONObject(listProjectFilesJson(root.absolutePath))
            assertTrue(payload.optBoolean("ok"))
            val files = payload.getJSONObject("output").getJSONObject("result").getJSONArray("files")
            val paths = (0 until files.length()).map { files.getJSONObject(it).getString("path") }

            assertTrue(paths.contains("index.html"))
            assertTrue(paths.contains("src/App.kt"))
            assertFalse(paths.any { it.contains("node_modules") })
            assertFalse(paths.any { it.startsWith(".git/") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readsOnlyFilesContainedInTheProjectAndWithinTheSizeLimit() {
        val parent = Files.createTempDirectory("craft-project-read").toFile()
        val root = parent.resolve("app")
        root.mkdirs()
        try {
            root.resolve("src").mkdirs()
            root.resolve("src/App.kt").writeText("fun hello() = \"world\"")
            parent.resolve("outside.txt").writeText("not part of the app")

            val opened = JSONObject(readProjectFileJson(root.absolutePath, "src/App.kt"))
            assertTrue(opened.optBoolean("ok"))
            val content = opened.getJSONObject("output").getJSONObject("result")
            assertEquals("fun hello() = \"world\"", content.getString("content"))
            assertEquals("src/App.kt", content.getString("path"))

            val traversal = JSONObject(readProjectFileJson(root.absolutePath, "../outside.txt"))
            assertFalse(traversal.optBoolean("ok"))

            root.resolve("large.txt").writeBytes(ByteArray(256 * 1024 + 1) { 65 })
            val large = JSONObject(readProjectFileJson(root.absolutePath, "large.txt"))
            assertFalse(large.optBoolean("ok"))
            assertTrue(large.optString("error").contains("256 KB"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun savesExistingTextFileAtomicallyAndReturnsUpdatedContent() {
        val root = Files.createTempDirectory("craft-project-save").toFile()
        try {
            root.resolve("src").mkdirs()
            root.resolve("src/App.kt").writeText("fun main() = Unit")
            val saved = JSONObject(writeProjectFileJson(root.absolutePath, "src/App.kt", "fun main() = println(42)"))
            assertTrue(saved.optBoolean("ok"))
            assertEquals("fun main() = println(42)", root.resolve("src/App.kt").readText())
            assertEquals("fun main() = println(42)", saved.getJSONObject("output").getJSONObject("result").getString("content"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesTraversalMissingFilesAndOversizedEdits() {
        val parent = Files.createTempDirectory("craft-project-save-guards").toFile()
        val root = parent.resolve("app")
        root.mkdirs()
        try {
            root.resolve("safe.txt").writeText("safe")
            assertFalse(JSONObject(writeProjectFileJson(root.absolutePath, "../outside.txt", "bad")).optBoolean("ok"))
            assertFalse(JSONObject(writeProjectFileJson(root.absolutePath, "missing.txt", "bad")).optBoolean("ok"))
            assertFalse(JSONObject(writeProjectFileJson(root.absolutePath, "safe.txt", "x".repeat(256 * 1024 + 1))).optBoolean("ok"))
            assertEquals("safe", root.resolve("safe.txt").readText())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsBinaryFilesInTheSourceViewer() {
        val root = Files.createTempDirectory("craft-project-binary").toFile()
        try {
            root.resolve("image.bin").writeBytes(byteArrayOf(1, 2, 0, 4))
            val result = JSONObject(readProjectFileJson(root.absolutePath, "image.bin"))
            assertFalse(result.optBoolean("ok"))
            assertTrue(result.optString("error").contains("binary"))
        } finally {
            root.deleteRecursively()
        }
    }
}
