package ai.storytold.craftanything

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveExporterTest {
    @Test
    fun exportsSourceFilesButSkipsCachesAndSecrets() {
        val root = Files.createTempDirectory("craft-export").toFile()
        try {
            root.resolve("src").mkdirs()
            root.resolve("src/App.kt").writeText("fun main() = Unit")
            root.resolve("package.json").writeText("{\\"scripts\\":{}}")
            root.resolve(".env").writeText("TOKEN=secret")
            root.resolve("node_modules/pkg/index.js").also {
                it.parentFile.mkdirs()
                it.writeText("dependency")
            }
            val output = ByteArrayOutputStream()
            val summary = ProjectArchiveExporter.writeZip(root, output)
            val entries = mutableListOf<String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries += entry.name
                    entry = zip.nextEntry
                }
            }
            assertEquals(2, summary.files)
            assertEquals(1, summary.excludedSensitiveFiles)
            assertTrue(entries.contains("src/App.kt"))
            assertTrue(entries.contains("package.json"))
            assertFalse(entries.any { it.contains("node_modules") || it.contains(".env") })
        } finally {
            root.deleteRecursively()
        }
    }
}
