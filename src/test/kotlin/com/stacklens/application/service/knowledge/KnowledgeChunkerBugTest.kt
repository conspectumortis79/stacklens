package com.stacklens.application.service.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeChunkerBugTest {
    private val chunker = KnowledgeChunker()

    @Test
    fun `chunker produces at least one chunk for short single-paragraph content`() {
        val text = "Einhorn-Pizza-Geheimrezept-XYZ-42 — detaillierte Anleitung fuer die geheime Einhorn-Pizza mit XYZ-42."
        assertTrue(text.isNotBlank(), "text must not be blank for this test to be meaningful")
        assertEquals(101, text.length)

        val chunks = chunker.chunk(text, chunkSize = 1600, chunkOverlap = 200)
        assertTrue(chunks.isNotEmpty(), "expected at least one chunk for 101-char text, got 0")
    }
}
