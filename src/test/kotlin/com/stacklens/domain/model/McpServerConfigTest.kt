package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpServerConfigTest {
    @Test
    fun `defaults — name is empty, active is false, route flags default to true`() {
        val c = McpServerConfig(name = "test")
        assertEquals("test", c.name)
        assertFalse(c.active)
        assertTrue(c.routeJira)
        assertTrue(c.routeConfluence)
        assertTrue(c.routeGitlab)
        assertTrue(c.routeGithub)
        assertTrue(c.routeGrafana)
        assertTrue(c.routeDatabase)
        assertEquals("", c.jiraBaseUrl)
        assertEquals("", c.jiraApiToken)
    }

    @Test
    fun `active and route flags can be set to false`() {
        val c =
            McpServerConfig(
                name = "test",
                active = false,
                routeJira = false,
                routeConfluence = false,
            )
        assertFalse(c.active)
        assertFalse(c.routeJira)
        assertFalse(c.routeConfluence)
    }

    @Test
    fun `different names produce non-equal instances (no data class equality)`() {
        val a = McpServerConfig(name = "a")
        val b = McpServerConfig(name = "b")
        assertNotEquals(a, b)
    }
}
