package com.stacklens.domain.model.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JiraTicketKeyBranchTest {
    @Test
    fun `init rejects blank and malformed values`() {
        assertThrows(IllegalArgumentException::class.java) { JiraTicketKey("  ") }
        assertThrows(IllegalArgumentException::class.java) { JiraTicketKey("nope") }
        assertThrows(IllegalArgumentException::class.java) { JiraTicketKey("PROJ") }
        assertThrows(IllegalArgumentException::class.java) { JiraTicketKey("-123") }
    }

    @Test
    fun `accessors split project key and number`() {
        val key = JiraTicketKey("PAY-38092")
        assertEquals("PAY", key.projectKey)
        assertEquals("38092", key.number)
        assertEquals("PAY-38092", key.toString())
    }

    @Test
    fun `extract parses bare, url and full path forms`() {
        assertEquals("PROJ-123", JiraTicketKey.extract("PROJ-123"))
        assertEquals("PROJ-123", JiraTicketKey.extract("https://company.atlassian.net/browse/PROJ-123"))
        assertEquals("PROJ-123", JiraTicketKey.extract(" https://company.atlassian.net/browse/PROJ-123 "))
        assertEquals("PROJ-123", JiraTicketKey.extract("https://company.atlassian.net/browse/PROJ-123/subtask"))
        assertNull(JiraTicketKey.extract(""))
        assertNull(JiraTicketKey.extract("   "))
        assertNull(JiraTicketKey.extract("not-a-key"))
    }

    @Test
    fun `of trims input`() {
        assertEquals(JiraTicketKey("PROJ-123"), JiraTicketKey.of(" PROJ-123 "))
    }
}
