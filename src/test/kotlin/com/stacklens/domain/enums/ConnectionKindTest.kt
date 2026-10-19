package com.stacklens.domain.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionKindTest {
    @Test
    fun `id getter returns the expected stable id for every value`() {
        assertEquals("database", ConnectionKind.DATABASE.id)
        assertEquals("jira", ConnectionKind.JIRA.id)
        assertEquals("confluence", ConnectionKind.CONFLUENCE.id)
        assertEquals("gitlab", ConnectionKind.GITLAB.id)
        assertEquals("github", ConnectionKind.GITHUB.id)
        assertEquals("grafana", ConnectionKind.GRAFANA.id)
        assertEquals("mcp", ConnectionKind.MCP.id)
    }

    @Test
    fun `displayName getter returns the German display name for every value`() {
        assertEquals("Datenbank", ConnectionKind.DATABASE.displayName)
        assertEquals("Jira", ConnectionKind.JIRA.displayName)
        assertEquals("Confluence", ConnectionKind.CONFLUENCE.displayName)
        assertEquals("GitLab", ConnectionKind.GITLAB.displayName)
        assertEquals("GitHub", ConnectionKind.GITHUB.displayName)
        assertEquals("Grafana", ConnectionKind.GRAFANA.displayName)
        assertEquals("MCP-Server", ConnectionKind.MCP.displayName)
    }

    @Test
    fun `values array contains all seven entries`() {
        assertEquals(7, ConnectionKind.values().size)
    }

    @Test
    fun `valueOf returns the expected entry`() {
        assertEquals(ConnectionKind.DATABASE, ConnectionKind.valueOf("DATABASE"))
        assertEquals(ConnectionKind.MCP, ConnectionKind.valueOf("MCP"))
    }
}
