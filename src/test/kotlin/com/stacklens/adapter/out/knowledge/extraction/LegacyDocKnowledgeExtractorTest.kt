package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LegacyDocKnowledgeExtractorTest {
    private val extractor = LegacyDocKnowledgeExtractor()

    @Test
    fun `a file that is not a word binary is rejected while extracting`() {
        assertThrows(Exception::class.java) {
            extractor.extract("notes.doc", byteArrayOf(0, 1, 2, 3))
        }
    }

    @Test
    fun `only ole2 documents with a doc suffix are supported`() {
        assertEquals(false, extractor.supports("notes.txt", byteArrayOf(0, 1, 2, 3)))
        assertEquals(false, extractor.supports("notes.doc", byteArrayOf(0, 1, 2, 3)))
        assertEquals(true, extractor.supports("NOTES.DOC", newOleHeader()))
    }

    private fun newOleHeader(): ByteArray =
        byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11.toByte(),
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A.toByte(),
            0xE1.toByte(),
        )
}
