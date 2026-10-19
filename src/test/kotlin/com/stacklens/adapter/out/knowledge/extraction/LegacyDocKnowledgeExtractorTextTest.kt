package com.stacklens.adapter.out.knowledge.extraction

import org.apache.poi.hwpf.HWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LegacyDocKnowledgeExtractorTextTest {
    private val extractor = LegacyDocKnowledgeExtractor()

    private fun document(text: StringBuilder?): HWPFDocument = mock<HWPFDocument>().also { whenever(it.text).thenReturn(text) }

    @Test
    fun `runs of spaces and superfluous blank lines are collapsed`() {
        val text = extractor.readDocument(document(StringBuilder("x   y\n\n\n\nz")), "bericht.doc")

        assertEquals("x y\n\nz", text)
    }

    @Test
    fun `a document without any text extracts an empty string`() {
        val broken = document(null)

        assertTrue(extractor.readDocument(broken, "leer.doc").isEmpty())
        verify(broken).close()
    }

    @Test
    fun `a document whose text cannot be read is reported with its file name`() {
        val unreadable = mock<HWPFDocument>()
        whenever(unreadable.text).thenThrow(RuntimeException("strom zerstört"))

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.readDocument(unreadable, "kaputt.doc")
            }

        assertTrue(failure.message!!.contains("kaputt.doc"))
        verify(unreadable).close()
    }

    @Test
    fun `the document is closed even when the text succeeds`() {
        val readable = document(StringBuilder("  plain  "))

        assertEquals("plain", extractor.readDocument(readable, "d.doc"))
        verify(readable).close()
    }
}
