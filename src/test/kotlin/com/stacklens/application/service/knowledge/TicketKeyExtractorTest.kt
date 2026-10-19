package com.stacklens.application.service.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TicketKeyExtractorTest {
    @Test
    fun `findAll extracts a single bare ticket key`() {
        assertEquals(listOf("BINIT-38092"), TicketKeyExtractor.findAll("binit-38092"))
    }

    @Test
    fun `findAll extracts a ticket key embedded in a sentence`() {
        assertEquals(
            listOf("BINIT-38092"),
            TicketKeyExtractor.findAll("Was steht eigentlich in binit-38092?"),
        )
    }

    @Test
    fun `findAll upper-cases lowercase keys`() {
        assertEquals(listOf("PROJ-1"), TicketKeyExtractor.findAll("proj-1"))
    }

    @Test
    fun `findAll supports underscores and digits in the project prefix`() {
        assertEquals(listOf("MY_PROJECT-9"), TicketKeyExtractor.findAll("MY_PROJECT-9"))
    }

    @Test
    fun `findAll returns every distinct key in the query`() {
        assertEquals(
            listOf("BINIT-38092", "OTHER-1"),
            TicketKeyExtractor.findAll("binit-38092 und OTHER-1"),
        )
    }

    @Test
    fun `findAll deduplicates repeated keys while preserving order`() {
        assertEquals(
            listOf("BINIT-38092"),
            TicketKeyExtractor.findAll("binit-38092 binit-38092 binit-38092"),
        )
    }

    @Test
    fun `findAll returns empty list for an empty or blank query`() {
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll(""))
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll("   "))
    }

    @Test
    fun `findAll returns empty list for prose without any key`() {
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll("Was ist der Stand des Sprints?"))
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll("lorem ipsum dolor"))
    }

    @Test
    fun `findAll does NOT match bare uppercase words without a dash-digit tail`() {
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll("binit 2026"))
    }
}
