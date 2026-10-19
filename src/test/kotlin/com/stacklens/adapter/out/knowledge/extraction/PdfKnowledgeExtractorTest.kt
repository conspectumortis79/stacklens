package com.stacklens.adapter.out.knowledge.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class PdfKnowledgeExtractorTest {
    private val extractor = PdfKnowledgeExtractor()

    @Test
    fun `supports only accepts pdf files with a pdf magic number`() {
        val pdf = plainPdf("Quarterly numbers look good.")

        assertTrue(extractor.supports("report.PDF", pdf))
        assertTrue(extractor.supports("report.pdf", pdf))
        assertFalse(extractor.supports("report.pdf", zipBytes()))
        assertFalse(extractor.supports("report.txt", pdf))
        assertFalse(extractor.supports("report.pptx", pdf))
    }

    @Test
    fun `extract collapses repeated spaces and returns the page text in order`() {
        val pdf = plainPdf("First   line", "Second     line")

        val text = extractor.extract("notes.pdf", pdf)

        assertEquals("First line\nSecond line", text)
    }

    @Test
    fun `extract rejects password protected documents`() {
        val encrypted = encryptedPdf()

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("secret.pdf", encrypted)
            }

        assertTrue(failure.message!!.contains("password-protected"), "unexpected: ${failure.message}")
    }

    @Test
    fun `extract wraps a broken pdf into an illegal argument error`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("broken.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46, 1, 2, 3))
            }

        assertTrue(failure.message!!.startsWith("Failed to extract text from PDF"), "unexpected: ${failure.message}")
    }

    private fun plainPdf(vararg lines: String): ByteArray {
        val document = PDDocument()
        try {
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                var y = 720f
                lines.forEach { line ->
                    content.newLineAtOffset(72f, y - 720f)
                    content.showText(line)
                    y -= 20f
                }
                content.endText()
            }
            return ByteArrayOutputStream().use { out ->
                document.save(out)
                out.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun encryptedPdf(): ByteArray {
        val document = PDDocument()
        try {
            document.addPage(PDPage())
            val permission = AccessPermission().apply { setCanPrint(true) }
            document.protect(StandardProtectionPolicy("owner-secret", "", permission))
            return ByteArrayOutputStream().use { out ->
                document.save(out)
                out.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun zipBytes(): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
}
