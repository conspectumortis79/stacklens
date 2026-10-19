package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.ticket.TicketAttachmentOutcome
import com.stacklens.application.port.inbound.ticket.TicketAttachmentReadUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64

class TicketCreationReadBinaryFileTest {
    private val useCase: TicketCreationUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val attachmentRead: TicketAttachmentReadUseCase = mock()
    private val controller =
        TicketCreationController(
            useCase,
            i18n,
            StaleAnalysisStatusFixtures.withRealClock(),
            attachmentRead,
        )

    @Test
    fun `readBinaryFile maps access denied to a 400 response`() {
        whenever(attachmentRead.readAttachment("/secret")).thenReturn(TicketAttachmentOutcome.AccessDenied)
        whenever(i18n.t("ticket_creation.be_access_denied", "Access denied.")).thenReturn("Access denied.")

        val resp = controller.readBinaryFile("/secret")

        assertEquals(400, resp.statusCode.value())
        assertEquals("Access denied.", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps not found to a 400 response`() {
        whenever(attachmentRead.readAttachment("/missing.png")).thenReturn(TicketAttachmentOutcome.NotFound)
        whenever(i18n.t("ticket_creation.be_file_not_found", "File not found.")).thenReturn("File not found.")

        val resp = controller.readBinaryFile("/missing.png")

        assertEquals(400, resp.statusCode.value())
        assertEquals("File not found.", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps an oversized image to a 400 response`() {
        whenever(attachmentRead.readAttachment("/big.png")).thenReturn(TicketAttachmentOutcome.ImageTooLarge)
        whenever(i18n.t("ticket_creation.be_file_too_large", "File exceeds 10 MB limit.")).thenReturn("Too large.")

        val resp = controller.readBinaryFile("/big.png")

        assertEquals(400, resp.statusCode.value())
        assertEquals("Too large.", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps an oversized pdf to a 400 response`() {
        whenever(attachmentRead.readAttachment("/big.pdf")).thenReturn(TicketAttachmentOutcome.PdfTooLarge)
        whenever(i18n.t("ticket_creation.be_file_too_large_pdf", "File exceeds 24 MB limit.")).thenReturn("Pdf too large.")

        val resp = controller.readBinaryFile("/big.pdf")

        assertEquals(400, resp.statusCode.value())
        assertEquals("Pdf too large.", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps an unsupported extension to a 400 response`() {
        whenever(attachmentRead.readAttachment("/notes.xyz")).thenReturn(TicketAttachmentOutcome.Unsupported("xyz"))

        val resp = controller.readBinaryFile("/notes.xyz")

        assertEquals(400, resp.statusCode.value())
        assertEquals("Unsupported file type: .xyz", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps unsupported files without an extension`() {
        whenever(attachmentRead.readAttachment("/noext")).thenReturn(TicketAttachmentOutcome.Unsupported(""))

        val resp = controller.readBinaryFile("/noext")

        assertEquals(400, resp.statusCode.value())
        assertEquals("Unsupported file type: .", resp.body?.get("error"))
    }

    @Test
    fun `readBinaryFile maps an image outcome to a data url`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        whenever(attachmentRead.readAttachment("/pic.png")).thenReturn(TicketAttachmentOutcome.Image(bytes, "pic.png", "image/png"))

        val resp = controller.readBinaryFile("/pic.png")

        assertEquals(200, resp.statusCode.value())
        assertEquals("image", resp.body?.get("type"))
        assertEquals("data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}", resp.body?.get("dataUrl"))
        assertEquals("pic.png", resp.body?.get("name"))
    }

    @Test
    fun `readBinaryFile maps an extracted pdf to its text`() {
        whenever(attachmentRead.readAttachment("/doc.pdf")).thenReturn(TicketAttachmentOutcome.Pdf("Hello PDF", "doc.pdf", false))

        val resp = controller.readBinaryFile("/doc.pdf")

        assertEquals(200, resp.statusCode.value())
        assertEquals("pdf", resp.body?.get("type"))
        assertEquals("Hello PDF", resp.body?.get("text"))
        assertEquals("doc.pdf", resp.body?.get("name"))
        assertFalse(resp.body?.containsKey("extractionError") ?: true)
    }

    @Test
    fun `readBinaryFile maps a failed pdf extraction to an extraction error`() {
        whenever(attachmentRead.readAttachment("/bad.pdf")).thenReturn(TicketAttachmentOutcome.Pdf("", "bad.pdf", true))
        whenever(i18n.t("ticket_creation.be_pdf_extract_failed", "Could not extract text from this PDF.")).thenReturn("Extraction failed.")

        val resp = controller.readBinaryFile("/bad.pdf")

        assertEquals(200, resp.statusCode.value())
        assertEquals("pdf", resp.body?.get("type"))
        assertEquals("", resp.body?.get("text"))
        assertEquals("bad.pdf", resp.body?.get("name"))
        assertEquals("Extraction failed.", resp.body?.get("extractionError"))
    }
}
