package ai.storytold.craftanything

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileImportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copiesInputAndReturnsItsExactByteCount() {
        val contents = "Craft Anything – Android".toByteArray(StandardCharsets.UTF_8)
        val destination = temporaryFolder.newFile("target.bin")

        val copied = FileImport.copyBounded(
            ByteArrayInputStream(contents),
            destination,
            maxBytes = 1024
        )

        assertEquals(contents.size.toLong(), copied)
        assertArrayEquals(contents, destination.readBytes())
    }

    @Test
    fun acceptsAnInputExactlyAtTheConfiguredLimit() {
        val contents = byteArrayOf(1, 2, 3, 4)
        val destination = temporaryFolder.newFile("exact-limit.bin")

        val copied = FileImport.copyBounded(
            ByteArrayInputStream(contents),
            destination,
            maxBytes = contents.size.toLong()
        )

        assertEquals(contents.size.toLong(), copied)
        assertArrayEquals(contents, destination.readBytes())
    }

    @Test
    fun rejectsOversizedInputAndDeletesPartialOutput() {
        val destination = temporaryFolder.newFile("too-large.bin")
        val error = org.junit.Assert.assertThrows(IOException::class.java) {
            FileImport.copyBounded(
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                destination,
                maxBytes = 4
            )
        }

        assertTrue(error.message.orEmpty().contains("maximum"))
        assertFalse("partial imports must not be left behind", destination.exists())
    }

    @Test
    fun deletesPartialOutputWhenTheSourceStreamFails() {
        val destination = temporaryFolder.newFile("interrupted.bin")
        val brokenStream = object : InputStream() {
            private var delivered = false

            override fun read(): Int {
                if (delivered) throw IOException("simulated provider failure")
                delivered = true
                return 0x41
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (delivered) throw IOException("simulated provider failure")
                delivered = true
                buffer[offset] = 0x41
                return 1
            }
        }

        val error = org.junit.Assert.assertThrows(IOException::class.java) {
            FileImport.copyBounded(brokenStream, destination, maxBytes = 16)
        }

        assertEquals("simulated provider failure", error.message)
        assertFalse("partial imports must be removed after read failures", destination.exists())
    }
}
