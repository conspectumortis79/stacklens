package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class PlainTextKnowledgeExtractorCoverageTest {
    private val extractor = PlainTextKnowledgeExtractor()

    @Test
    fun `a utf8 byte order mark is detected and stripped`() {
        val content = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hallo".toByteArray(StandardCharsets.UTF_8)

        assertEquals("hallo", extractor.extract("notes.txt", content))
    }

    @Test
    fun `a utf16le byte order mark is detected`() {
        val content = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "ab".toByteArray(StandardCharsets.UTF_16LE)

        assertEquals("ab", extractor.extract("notes.txt", content))
    }

    @Test
    fun `a utf16be byte order mark is detected`() {
        val content = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "ab".toByteArray(StandardCharsets.UTF_16BE)

        assertEquals("ab", extractor.extract("notes.txt", content))
    }

    @Test
    fun `plain bytes are decoded as utf8`() {
        assertEquals("abc", extractor.extract("notes.txt", "abc".toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun `short probes never match a multi byte marker`() {
        assertTrue(extractor.extract("notes.txt", byteArrayOf(0x41)).isNotEmpty())
        assertTrue(extractor.extract("notes.txt", byteArrayOf(0xEF.toByte(), 0x00)).isNotEmpty())
        assertTrue(extractor.extract("notes.txt", byteArrayOf(0xFF.toByte(), 0x00)).isNotEmpty())
        assertTrue(extractor.extract("notes.txt", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0x00)).isNotEmpty())
    }

    @Test
    fun `only txt filenames are supported`() {
        assertTrue(extractor.supports("NOTES.TXT", ByteArray(0)))
        assertFalse(extractor.supports("notes.md", ByteArray(0)))
    }
}
