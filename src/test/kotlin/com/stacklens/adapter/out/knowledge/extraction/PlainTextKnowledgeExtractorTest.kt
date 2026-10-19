package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlainTextKnowledgeExtractorTest {
    private val extractor = PlainTextKnowledgeExtractor()

    @Test
    fun `supports recognises txt files`() {
        assertTrue(extractor.supports("notes.txt", ByteArray(0)))
        assertTrue(extractor.supports("README.TXT", ByteArray(0)))
    }

    @Test
    fun `supports rejects non-txt files`() {
        assertTrue(!extractor.supports("notes.md", ByteArray(0)))
        assertTrue(!extractor.supports("notes", ByteArray(0)))
    }

    @Test
    fun `extract decodes plain text without BOM`() {
        val text = extractor.extract("a.txt", "hello world".toByteArray())
        assertEquals("hello world", text)
    }

    @Test
    fun `extract strips UTF-8 BOM`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = extractor.extract("a.txt", bom + "hello".toByteArray())
        assertEquals("hello", text)
    }

    @Test
    fun `extract handles UTF-16 LE BOM`() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val content = bom + byteArrayOf(0x68, 0x00, 0x69, 0x00)
        val text = extractor.extract("a.txt", content)
        assertEquals("hi", text)
    }

    @Test
    fun `extract handles UTF-16 BE BOM`() {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val content = bom + byteArrayOf(0x00, 0x68, 0x00, 0x69)
        val text = extractor.extract("a.txt", content)
        assertEquals("hi", text)
    }

    @Test
    fun `extract trims surrounding whitespace`() {
        val text = extractor.extract("a.txt", "   hello   ".toByteArray())
        assertEquals("hello", text)
    }

    @Test
    fun `extract handles empty content`() {
        val text = extractor.extract("a.txt", ByteArray(0))
        assertEquals("", text)
    }
}
