package com.stacklens.application.service.ticketcreation

import com.stacklens.application.port.inbound.ticket.TicketAttachmentOutcome
import com.stacklens.application.port.inbound.ticket.TicketAttachmentTypes
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.file.PdfTextExtractionPort
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class TicketAttachmentReadServiceTest {
    private val home = File("/home/tester")
    private val fileAttributes: FileAttributes = mock()
    private val fileContentStore: FileContentStore = mock()
    private val pdfTextExtraction: PdfTextExtractionPort = mock()
    private lateinit var service: TicketAttachmentReadService

    @BeforeEach
    fun setUp() {
        service = TicketAttachmentReadService(fileAttributes, fileContentStore, pdfTextExtraction)
        whenever(fileAttributes.homeDirectory()).thenReturn(home)
        whenever(fileAttributes.canonicalFile(home)).thenReturn(home)
    }

    private fun stubFile(file: File): File {
        whenever(fileAttributes.canonicalFile(File(file.path))).thenReturn(file)
        whenever(fileAttributes.exists(file)).thenReturn(true)
        whenever(fileAttributes.isFile(file)).thenReturn(true)
        return file
    }

    @Test
    fun `readAttachment denies paths that fail canonicalisation`() {
        val invalid = File("\u0000invalid")
        whenever(fileAttributes.canonicalFile(invalid)).thenThrow(IllegalStateException("bad path"))

        assertEquals(TicketAttachmentOutcome.AccessDenied, service.readAttachment("\u0000invalid"))
    }

    @Test
    fun `readAttachment denies paths outside the home directory`() {
        val outside = File("/etc/passwd")
        whenever(fileAttributes.canonicalFile(File(outside.path))).thenReturn(outside)

        assertEquals(TicketAttachmentOutcome.AccessDenied, service.readAttachment(outside.path))
    }

    @Test
    fun `readAttachment accepts the home directory itself and treats it as not found`() {
        whenever(fileAttributes.canonicalFile(File(home.path))).thenReturn(home)
        whenever(fileAttributes.exists(home)).thenReturn(true)
        whenever(fileAttributes.isFile(home)).thenReturn(false)

        assertEquals(TicketAttachmentOutcome.NotFound, service.readAttachment(home.path))
    }

    @Test
    fun `readAttachment reports a missing file`() {
        val missing = File(home, "missing.png")
        whenever(fileAttributes.canonicalFile(File(missing.path))).thenReturn(missing)
        whenever(fileAttributes.exists(missing)).thenReturn(false)

        assertEquals(TicketAttachmentOutcome.NotFound, service.readAttachment(missing.path))
    }

    @Test
    fun `readAttachment reports a directory as not a file`() {
        val directory = File(home, "sub")
        whenever(fileAttributes.canonicalFile(File(directory.path))).thenReturn(directory)
        whenever(fileAttributes.exists(directory)).thenReturn(true)
        whenever(fileAttributes.isFile(directory)).thenReturn(false)

        assertEquals(TicketAttachmentOutcome.NotFound, service.readAttachment(directory.path))
    }

    @Test
    fun `readAttachment rejects an image above the limit`() {
        val image = stubFile(File(home, "big.png"))
        whenever(fileAttributes.size(image)).thenReturn(TicketAttachmentTypes.MAX_IMAGE_BYTES + 1)

        assertEquals(TicketAttachmentOutcome.ImageTooLarge, service.readAttachment(image.path))
    }

    @Test
    fun `readAttachment returns image bytes with the resolved mime type`() {
        val bytes = byteArrayOf(1, 2, 3)
        val image = stubFile(File(home, "pic.png"))
        whenever(fileAttributes.size(image)).thenReturn(bytes.size.toLong())
        whenever(fileContentStore.readBytes(image)).thenReturn(bytes)

        val outcome = service.readAttachment(image.path) as TicketAttachmentOutcome.Image

        assertArrayEquals(bytes, outcome.bytes)
        assertEquals("pic.png", outcome.name)
        assertEquals("image/png", outcome.mimeType)
    }

    @Test
    fun `readAttachment rejects a file type without a mapping`() {
        val document = stubFile(File(home, "notes.xyz"))

        val outcome = service.readAttachment(document.path) as TicketAttachmentOutcome.Unsupported

        assertEquals("xyz", outcome.extension)
    }

    @Test
    fun `readAttachment rejects files without an extension`() {
        val document = stubFile(File(home, "notes"))

        val outcome = service.readAttachment(document.path) as TicketAttachmentOutcome.Unsupported

        assertEquals("", outcome.extension)
    }

    @Test
    fun `readAttachment rejects a pdf above the limit`() {
        val pdf = stubFile(File(home, "big.pdf"))
        whenever(fileAttributes.size(pdf)).thenReturn(TicketAttachmentTypes.MAX_PDF_BYTES + 1)

        assertEquals(TicketAttachmentOutcome.PdfTooLarge, service.readAttachment(pdf.path))
    }

    @Test
    fun `readAttachment extracts and returns the pdf text`() {
        val bytes = "pdf".toByteArray()
        val pdf = stubFile(File(home, "doc.pdf"))
        whenever(fileAttributes.size(pdf)).thenReturn(bytes.size.toLong())
        whenever(fileContentStore.readBytes(pdf)).thenReturn(bytes)
        whenever(pdfTextExtraction.extractText(bytes)).thenReturn("hello from pdf")

        val outcome = service.readAttachment(pdf.path) as TicketAttachmentOutcome.Pdf

        assertEquals("hello from pdf", outcome.text)
        assertEquals("doc.pdf", outcome.name)
        assertFalse(outcome.extractionError)
    }

    @Test
    fun `readAttachment truncates extracted pdf text`() {
        val bytes = "pdf".toByteArray()
        val pdf = stubFile(File(home, "long.pdf"))
        whenever(fileAttributes.size(pdf)).thenReturn(bytes.size.toLong())
        whenever(fileContentStore.readBytes(pdf)).thenReturn(bytes)
        whenever(pdfTextExtraction.extractText(bytes)).thenReturn("a".repeat(20_000))

        val outcome = service.readAttachment(pdf.path) as TicketAttachmentOutcome.Pdf

        assertEquals(15_000, outcome.text.length)
    }

    @Test
    fun `readAttachment reports a failed pdf extraction`() {
        val bytes = "pdf".toByteArray()
        val pdf = stubFile(File(home, "bad.pdf"))
        whenever(fileAttributes.size(pdf)).thenReturn(bytes.size.toLong())
        whenever(fileContentStore.readBytes(pdf)).thenReturn(bytes)
        whenever(pdfTextExtraction.extractText(bytes)).thenThrow(IllegalStateException("broken pdf"))

        val outcome = service.readAttachment(pdf.path) as TicketAttachmentOutcome.Pdf

        assertEquals("", outcome.text)
        assertEquals("bad.pdf", outcome.name)
        assertTrue(outcome.extractionError)
    }
}
