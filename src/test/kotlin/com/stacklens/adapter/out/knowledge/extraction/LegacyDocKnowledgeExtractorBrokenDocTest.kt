package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LegacyDocKnowledgeExtractorBrokenDocTest {
    private val extractor = LegacyDocKnowledgeExtractor()

    @Test
    fun `a payload that is not a word document fails while opening it`() {
        assertThrows(Exception::class.java) {
            extractor.extract("not-a-doc.doc", byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 1, 2, 3, 4))
        }
    }
}
