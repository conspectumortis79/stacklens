package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyDocKnowledgeExtractorFileCoverageTest {
    private val extractor = LegacyDocKnowledgeExtractor()

    private fun legacyWord(): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/knowledge/legacy-word.doc"))
        return stream.use { it.readBytes() }
    }

    @Test
    fun `the fixture is recognised as an ole2 word document`() {
        val content = legacyWord()

        assertTrue(extractor.supports("Handreichung.doc", content))
    }

    @Test
    fun `a word binary document is extracted to its plain text`() {
        val text = extractor.extract("Handreichung.doc", legacyWord())

        assertTrue(text.contains("Einfuehrung"), "unexpected extraction result: <$text>")
        assertTrue(text.contains("Zweiter Absatz"), "unexpected extraction result: <$text>")
        assertEquals(text, text.trim())
    }
}
