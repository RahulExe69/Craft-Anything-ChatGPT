package ai.storytold.craftanything

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Copies a SAF-provided stream into app-private storage with a strict byte ceiling.
 *
 * The input is consumed and closed by this method. Any failed copy removes the destination so
 * callers can never accidentally pass a truncated file to the engine.
 */
internal object FileImport {
    fun copyBounded(input: InputStream, destination: File, maxBytes: Long): Long {
        require(maxBytes >= 0) { "Maximum import size cannot be negative." }

        var completed = false
        try {
            val parent = destination.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("Couldn't create the private import directory.")
            }

            val copied = input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L

                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break

                        if (count == 0) {
                            // Defensive fallback for unusual ContentProviders that return zero.
                            val nextByte = source.read()
                            if (nextByte < 0) break
                            if (total >= maxBytes) {
                                throw tooLarge(maxBytes)
                            }
                            output.write(nextByte)
                            total += 1
                        } else {
                            if (count.toLong() > maxBytes - total) {
                                throw tooLarge(maxBytes)
                            }
                            output.write(buffer, 0, count)
                            total += count
                        }
                    }

                    total
                }
            }

            completed = true
            return copied
        } finally {
            if (!completed) {
                destination.delete()
            }
        }
    }

    private fun tooLarge(maxBytes: Long): IOException =
        IOException("File exceeds the maximum import size of $maxBytes bytes.")
}
