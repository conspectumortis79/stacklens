package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlainTextKnowledgeExtractorBomCoverageTest {
    private val extractor = PlainTextKnowledgeExtractor()

    @Test
    fun `a utf eight byte order mark is stripped from the text`() {
        val content = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hallo".toByteArray(Charsets.UTF_8)

        assertEquals("hallo", extractor.extract("note.txt", content))
    }

    @Test
    fun `a utf sixteen big endian byte order mark selects the big endian decoder`() {
        val content = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "hallo".toByteArray(Charsets.UTF_16BE)

        assertEquals("hallo", extractor.extract("note.txt", content))
    }

    @Test
    fun `a file without any byte order mark is read as is`() {
        assertEquals("hallo", extractor.extract("note.txt", "hallo".toByteArray(Charsets.UTF_8)))
    }
}
