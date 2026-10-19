package com.stacklens.adapter.out.knowledge.extraction

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class DocxKnowledgeExtractorTest {
    private val extractor = DocxKnowledgeExtractor()

    @Test
    fun `supports only docx files that carry a zip signature`() {
        val docx = docx(paragraphs = listOf("Introduction"), table = listOf(listOf("A", "B")))

        assertTrue(extractor.supports("notes.Docx", docx))
        assertTrue(extractor.supports("notes.docx", docx))
        assertFalse(extractor.supports("notes.docx", "plain text without magic".toByteArray()))
        assertFalse(extractor.supports("notes.txt", docx))
    }

    @Test
    fun `extract joins paragraphs and renders table rows with pipes`() {
        val docx =
            docx(
                paragraphs = listOf("First paragraph", "   ", "Second paragraph"),
                table = listOf(listOf("Sprint", "Status"), listOf("27", "done")),
            )

        val text = extractor.extract("review.docx", docx)

        assertEquals("First paragraph\nSecond paragraph\nSprint | Status\n27 | done", text)
    }

    @Test
    fun `extract collapses repeated spaces and tabs inside paragraphs`() {
        val text = extractor.extract("spaced.docx", docx(paragraphs = listOf("a    b\tc"), table = emptyList()))

        assertEquals("a b c", text)
    }

    @Test
    fun `extract rejects documents above the size cap`() {
        val tooLarge = ByteArray(50 * 1024 * 1024 + 1)

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("huge.docx", tooLarge)
            }

        assertTrue(
            failure.message!!.contains("caps document uploads at 50 MB"),
            "unexpected: ${failure.message}",
        )
        assertTrue(failure.message!!.contains("${tooLarge.size} bytes"), "unexpected: ${failure.message}")
    }

    @Test
    fun `extract wraps a file that is no office document into an illegal argument error`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("broken.docx", zipHeader() + "garbage".toByteArray())
            }

        assertTrue(failure.message!!.startsWith("Failed to extract text from DOCX"), "unexpected: ${failure.message}")
        assertTrue(failure.cause != null, "the parser failure must be kept as the cause")
    }

    private fun zipHeader(): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)

    private fun docx(
        paragraphs: List<String>,
        table: List<List<String>>,
    ): ByteArray {
        val document = XWPFDocument()
        try {
            paragraphs.forEach { text -> document.createParagraph().createRun().setText(text) }
            if (table.isNotEmpty()) {
                val xwpfTable = document.createTable(table.size, table.first().size)
                table.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { columnIndex, cell ->
                        xwpfTable.getRow(rowIndex).getCell(columnIndex).setText(cell)
                    }
                }
            }
            val out = ByteArrayOutputStream()
            document.write(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }
}
