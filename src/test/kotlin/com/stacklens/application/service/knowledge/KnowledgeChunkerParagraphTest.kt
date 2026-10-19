package com.stacklens.application.service.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeChunkerParagraphTest {
    private val chunker = KnowledgeChunker()

    @Test
    fun `blank paragraphs between two separators are skipped`() {
        val chunks = chunker.chunk("first\n\n\n\nsecond", chunkSize = 100, chunkOverlap = 0)

        assertEquals(1, chunks.size)
        assertEquals("first\n\nsecond", chunks.single().content)
        assertEquals(0, chunks.single().ordinal)
    }

    @Test
    fun `a leading paragraph separator produces no empty chunk`() {
        val chunks = chunker.chunk("\n\nonly content", chunkSize = 100, chunkOverlap = 0)

        assertEquals(listOf("only content"), chunks.map { it.content })
    }

    @Test
    fun `the section title travels with every chunk`() {
        val chunks = chunker.chunk("a\n\n\n\nb", "Intro", chunkSize = 100, chunkOverlap = 0)

        assertEquals(chunks.map { "Intro" }, chunks.map { it.sectionTitle })
        assertEquals(1, chunks.size)
    }

    @Test
    fun `a long paragraph is split per sentence and respects the chunk size`() {
        val text = "One sentence here. Another sentence here. Third one is also long enough to matter."

        val chunks = chunker.chunk(text, chunkSize = 24, chunkOverlap = 0)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.content.isNotBlank() })
    }

    @Test
    fun `overlap is prefixed to every chunk but the first and capped at twice the size`() {
        val text = "alpha beta gamma delta. ".repeat(6)

        val overlapped = chunker.chunk(text, chunkSize = 30, chunkOverlap = 10)

        assertTrue(overlapped.size >= 2)
        assertTrue(overlapped[1].content.length <= 60)
        assertEquals(text.trim(), chunker.chunk(text, chunkSize = 400, chunkOverlap = 10).single().content)
        assertEquals(emptyList<Any>(), chunker.chunk("   "))
        assertEquals(1, chunker.chunk("single paragraph", chunkSize = 100, chunkOverlap = 50).size)
    }

    @Test
    fun `invalid sizes are rejected before any work happens`() {
        assertTrue(
            runCatching { chunker.chunk("text", chunkSize = 0) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { chunker.chunk("text", chunkSize = 10, chunkOverlap = 10) }.exceptionOrNull() is IllegalArgumentException,
        )
    }
}
