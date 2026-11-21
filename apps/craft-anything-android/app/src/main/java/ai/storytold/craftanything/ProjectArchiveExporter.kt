package ai.storytold.craftanything

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ProjectArchiveSummary(
    val files: Int,
    val uncompressedBytes: Long,
    val excludedSensitiveFiles: Int
)

internal object ProjectArchiveExporter {
    private val excludedDirectories = setOf(
        ".git", "node_modules", ".gradle", ".idea", ".next", ".turbo", ".cache", "Pods", "target", "build"
    )

    private fun isSensitive(file: File): Boolean {
        val name = file.name.lowercase()
        return name == ".env" || name.startsWith(".env.") ||
            name.endsWith(".pem") || name.endsWith(".key") ||
            name.endsWith(".jks") || name.endsWith(".keystore")
    }

    fun writeZip(projectFolder: File, output: OutputStream): ProjectArchiveSummary {
        val root = projectFolder.canonicalFile
        if (!root.isDirectory) throw IOException("The project source folder isn't available.")
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        var files = 0
        var bytes = 0L
        var excludedSensitive = 0

        ZipOutputStream(output).use { zip ->
            root.walkTopDown().onEnter { directory ->
                directory == root || (directory.name !in excludedDirectories && !directory.name.startsWith(".git"))
            }.forEach { candidate ->
                if (candidate == root || candidate.isDirectory) return@forEach
                if (Files.isSymbolicLink(candidate.toPath())) return@forEach
                val file = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
                if (!file.path.startsWith(rootPrefix) || !file.isFile) return@forEach
                if (isSensitive(file)) {
                    excludedSensitive += 1
                    return@forEach
                }
                val length = file.length()
                if (length > 512L * 1024L * 1024L || bytes + length > 512L * 1024L * 1024L) {
                    throw IOException("The project is too large to export (maximum 512 MB of source files).")
                }
                if (files >= 10_000) throw IOException("The project has more than 10,000 files; export was stopped.")
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                if (relative.isBlank() || relative.startsWith('/') || relative.split('/').any { it == ".." }) return@forEach
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
                files += 1
                bytes += length
            }
            zip.finish()
        }
        return ProjectArchiveSummary(files, bytes, excludedSensitive)
    }
}
