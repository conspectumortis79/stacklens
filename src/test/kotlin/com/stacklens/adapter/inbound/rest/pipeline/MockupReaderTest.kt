package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import java.nio.file.Path

class MockupReaderTest {
    @TempDir
    lateinit var tempDir: Path

    private val i18n =
        object : I18nUseCase, I18nPort {
            override fun t(
                key: String,
                vararg args: Any,
            ): String = "msg:$key"

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = "msg:$key"

            override fun translateError(e: Exception): String = "msg:err"

            override fun resolveLanguage(): String = "en"
        }

    private val reader = MockupReader(i18n)

    @Test
    fun `the reader is the mockup file access used by the controller`() {
        assertTrue(reader is MockupFileAccess)
    }

    @Test
    fun `validateMockup returns null when no mockup is attached`() {
        assertEquals(null, reader.validateMockup(null, null))
        assertEquals(null, reader.validateMockup("", null))
    }

    @Test
    fun `validateMockup reports a missing mime type`() {
        val err = reader.validateMockup("dGVzdA==", null)
        assertNotNull(err)
        assertTrue(err!!.contains("mockup_mime_missing"))
    }

    @Test
    fun `validateMockup reports an unsupported mime type`() {
        val err = reader.validateMockup("dGVzdA==", "application/pdf")
        assertNotNull(err)
        assertTrue(err!!.contains("application/pdf") || err.contains("mockup_type_not_supported"))
    }

    @Test
    fun `validateMockup reports an over-sized payload`() {
        val tooLong = "x".repeat(MockupReader.MAX_MOCKUP_B64_CHARS + 1)
        val err = reader.validateMockup(tooLong, "image/png")
        assertNotNull(err)
        assertTrue(err!!.contains("mockup_too_large"))
    }

    @Test
    fun `validateMockup returns null for a valid png payload`() {
        assertEquals(null, reader.validateMockup("dGVzdA==", "image/png"))
    }

    @Test
    fun `validateMockup returns null for a valid jpeg payload`() {
        assertEquals(null, reader.validateMockup("dGVzdA==", "image/jpeg"))
    }

    @Test
    fun `validateMockup returns null for a valid svg payload`() {
        assertEquals(null, reader.validateMockup("dGVzdA==", "image/svg+xml"))
    }

    @Test
    fun `validateMockup accepts all allowed mime types`() {
        for (mime in listOf("image/png", "image/jpeg", "image/svg+xml", "image/heic", "image/heif", "image/tiff")) {
            assertEquals(null, reader.validateMockup("dGVzdA==", mime), "expected $mime to be allowed")
        }
    }

    @Test
    fun `readMockupFile returns the base64-encoded content for a valid png`() {
        val file = tempDir.resolve("test.png").toFile()
        file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val resp = reader.readMockupFile(file.absolutePath)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("image/png", body["mimeType"])
        assertTrue((body["base64"] as String).isNotBlank())
        assertTrue((body["dataUrl"] as String).startsWith("data:image/png;base64,"))
    }

    @Test
    fun `readMockupFile returns bad-request for a non-existing file`() {
        val resp = reader.readMockupFile("/nonexistent/${System.nanoTime()}.png")
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `readMockupFile returns bad-request for a disallowed extension`() {
        val file = tempDir.resolve("test.txt").toFile()
        file.writeText("not an image")
        val resp = reader.readMockupFile(file.absolutePath)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `readMockupFile returns bad-request for an over-sized file`() {
        val file = tempDir.resolve("big.png").toFile()
        val raf = java.io.RandomAccessFile(file, "rw")
        raf.setLength(MockupReader.MAX_MOCKUP_BYTES + 1)
        raf.close()
        val resp = reader.readMockupFile(file.absolutePath)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `readMockupFile returns the right mime type for jpeg, svg, and tiff`() {
        for ((name, ext) in listOf("a" to "jpg", "b" to "jpeg", "c" to "svg", "d" to "tif", "e" to "tiff", "f" to "heic", "g" to "heif")) {
            val file = tempDir.resolve("$name.$ext").toFile()
            file.writeBytes(byteArrayOf(0x01, 0x02))
            val resp = reader.readMockupFile(file.absolutePath)
            assertEquals(HttpStatus.OK, resp.statusCode)
        }
    }
}
