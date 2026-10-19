package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.service.I18nService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.File

class PipelineAndMockupCoverageTest {
    private val i18n: I18nService =
        mock<I18nService>().also { m ->
            m.stub {
                on { m.t(any()) } doReturn "error"
                on { m.t(any(), any()) } doReturn "error"
                on { m.t(any(), any(), any()) } doReturn "error"
            }
        }

    @Test
    fun `readMockupFile returns bad request when file is a directory`(
        @TempDir tempDir: File,
    ) {
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile(tempDir.absolutePath + "/")
        assertEquals(400, r.statusCode.value())
        assertNotNull(r.body!!["error"])
    }

    @Test
    fun `readMockupFile returns 400 when extension is not in allowlist`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "script.exe")
        file.writeText("MZ")
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile(file.absolutePath)
        assertEquals(400, r.statusCode.value())
        assertTrue(r.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `readMockupFile returns 200 for valid jpeg with correct mime type`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "photo.jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile(file.absolutePath)
        assertEquals(200, r.statusCode.value())
        assertEquals("image/jpeg", r.body!!["mimeType"])
    }

    @Test
    fun `readMockupFile returns 200 for valid heic with correct mime type`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "photo.heic")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile(file.absolutePath)
        assertEquals(200, r.statusCode.value())
        assertEquals("image/heic", r.body!!["mimeType"])
    }

    @Test
    fun `readMockupFile returns 200 for valid tif with correct mime type`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "image.tif")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile(file.absolutePath)
        assertEquals(200, r.statusCode.value())
        assertEquals("image/tiff", r.body!!["mimeType"])
    }

    @Test
    fun `validateMockup returns null when both are blank`() {
        val reader = MockupReader(i18n)
        assertNull(reader.validateMockup(null, null))
        assertNull(reader.validateMockup("", ""))
        assertNull(reader.validateMockup("   ", "   "))
    }

    @Test
    fun `validateMockup returns mime-missing error when base64 set but mime blank`() {
        val reader = MockupReader(i18n)
        val result = reader.validateMockup("AAAA", "  ")
        assertNotNull(result)
    }

    @Test
    fun `validateMockup returns not-supported error for unknown mime`() {
        val reader = MockupReader(i18n)
        val result = reader.validateMockup("AAAA", "application/json")
        assertNotNull(result)
    }

    @Test
    fun `validateMockup returns too-large error for oversized base64`() {
        val reader = MockupReader(i18n)
        val tooBig = "A".repeat(11_000_000)
        val result = reader.validateMockup(tooBig, "image/png")
        assertNotNull(result)
    }

    @Test
    fun `validateMockup returns null for all allowed mime types`() {
        val reader = MockupReader(i18n)
        val allowed =
            listOf(
                "image/png",
                "image/jpeg",
                "image/svg+xml",
                "image/heic",
                "image/heif",
                "image/tiff",
            )
        for (mime in allowed) {
            assertNull(reader.validateMockup("AAAA", mime), "Expected null for MIME $mime")
        }
    }

    @Test
    fun `readMockupFile returns 400 when canonicalFile throws`() {
        val reader = MockupReader(i18n)
        val r = reader.readMockupFile("/some/path\u0000/file.png")
        assertEquals(400, r.statusCode.value())
        assertNotNull(r.body!!["error"])
    }

    @Test
    fun `toStatusMap returns jiraMode true when jiraTicketData is non-null`() {
        val run = AgentPipelineRun(1L, "t", null, "slug", jiraTicketData = "JRA-1", createdAt = System.currentTimeMillis())
        val map = run.toStatusMap()
        assertEquals(true, map["jiraMode"])
    }

    @Test
    fun `toStatusMap returns jiraMode false when jiraTicketData is null`() {
        val run = AgentPipelineRun(1L, "t", null, "slug", jiraTicketData = null, createdAt = System.currentTimeMillis())
        val map = run.toStatusMap()
        assertEquals(false, map["jiraMode"])
    }
}
