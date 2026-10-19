package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CreatedJiraTicketTest {
    @Test
    fun `constructor populates all fields`() {
        val ticket = CreatedJiraTicket(key = "PROJ-1", id = "10001", url = "https://jira/browse/PROJ-1")
        assertEquals("PROJ-1", ticket.key)
        assertEquals("10001", ticket.id)
        assertEquals("https://jira/browse/PROJ-1", ticket.url)
    }

    @Test
    fun `equality is value-based`() {
        val a = CreatedJiraTicket("K", "I", "U")
        val b = CreatedJiraTicket("K", "I", "U")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy overrides fields`() {
        val a = CreatedJiraTicket("K", "I", "U")
        val b = a.copy(url = "X")
        assertEquals("X", b.url)
        assertEquals("U", a.url)
    }
}
