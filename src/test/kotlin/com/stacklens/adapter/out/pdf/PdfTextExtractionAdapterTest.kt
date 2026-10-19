package com.stacklens.adapter.out.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.IOException

class PdfTextExtractionAdapterTest {
    private val adapter = PdfTextExtractionAdapter()

    private fun pdfBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("hello from pdf")
                stream.endText()
            }
            document.save(out)
        }
        return out.toByteArray()
    }

    @Test
    fun `extractText reads the text of a valid pdf`() {
        assertTrue(adapter.extractText(pdfBytes()).contains("hello from pdf"))
    }

    @Test
    fun `extractText propagates loader failures`() {
        assertThrows<IOException> { adapter.extractText("not a pdf".toByteArray()) }
    }
}
