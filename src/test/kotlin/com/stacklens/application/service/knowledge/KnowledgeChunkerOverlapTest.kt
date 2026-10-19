package com.stacklens.application.service.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeChunkerOverlapTest {
    private val chunker = KnowledgeChunker()

    @Test fun `blank text yields no chunks`() {
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   \n\n   ").isEmpty())
    }

    @Test fun `chunk size must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { chunker.chunk("text", chunkSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { chunker.chunk("text", chunkSize = -5) }
    }

    @Test fun `overlap must stay inside the chunk size`() {
        assertThrows(IllegalArgumentException::class.java) { chunker.chunk("text", chunkSize = 10, chunkOverlap = -1) }
        assertThrows(IllegalArgumentException::class.java) { chunker.chunk("text", chunkSize = 10, chunkOverlap = 10) }
        assertThrows(IllegalArgumentException::class.java) { chunker.chunk("text", chunkSize = 10, chunkOverlap = 40) }
    }

    @Test fun `blank paragraphs are dropped while packing the buffer`() {
        val chunks = chunker.chunk("alpha\n\n \n\nbeta", chunkSize = 100, chunkOverlap = 0)

        assertEquals(1, chunks.size)
        assertEquals("alpha\n\nbeta", chunks.single().content)
        assertEquals(0, chunks.single().ordinal)
    }

    @Test fun `the section title is carried by every chunk`() {
        val chunks = chunker.chunk("one\n\ntwo", sectionTitle = "Kapitel 1", chunkSize = 5, chunkOverlap = 0)

        assertEquals(2, chunks.size)
        assertEquals(listOf(0, 1), chunks.map { it.ordinal })
        assertTrue(chunks.all { it.sectionTitle == "Kapitel 1" })
    }

    @Test fun `oversized paragraphs are split on sentence boundaries`() {
        val text = "Ein langer Satz. Noch einer. Und ein dritter. Vierter auch."
        val chunks = chunker.chunk(text, chunkSize = 20, chunkOverlap = 0)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.content.length <= 20 })
        assertEquals(text.replace(Regex("\\s+"), " "), chunks.joinToString(" ") { it.content })
    }

    @Test fun `a single long sentence becomes its own chunk`() {
        val chunks = chunker.chunk("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", chunkSize = 10, chunkOverlap = 0)

        assertEquals(1, chunks.size)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", chunks.single().content)
    }

    @Test fun `overlap is prefixed to every following chunk`() {
        val chunks = chunker.chunk("alpha text here\n\nbeta text here\n\ngamma text", chunkSize = 18, chunkOverlap = 6)

        assertEquals(3, chunks.size)
        assertEquals("alpha text here", chunks[0].content)
        assertEquals("t here\n\nbeta text here", chunks[1].content)
        assertEquals("t here\n\ngamma text", chunks[2].content)
    }

    @Test fun `overlapping prefixes that would double the chunk are dropped`() {
        val chunks = chunker.chunk("aaaaaaaaaaaa\n\nbbbbbbbbbbbb\n\ncccccccccccc", chunkSize = 12, chunkOverlap = 11)

        assertEquals(3, chunks.size)
        assertEquals("aaaaaaaaaaaa", chunks[0].content)
        assertEquals("bbbbbbbbbbbb", chunks[1].content)
        assertEquals("cccccccccccc", chunks[2].content)
    }

    @Test fun `a single chunk skips the overlap pass`() {
        val chunks = chunker.chunk("only one paragraph", chunkSize = 100, chunkOverlap = 20)

        assertEquals(listOf("only one paragraph"), chunks.map { it.content })
    }
}
