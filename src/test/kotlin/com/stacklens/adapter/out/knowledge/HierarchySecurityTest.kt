package com.stacklens.adapter.out.knowledge

import com.stacklens.adapter.out.jira.DefaultJiraApiVersionResolver
import com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfluenceHierarchyAdapterSecurityTest {
    private val adapter =
        ConfluenceHierarchyAdapter(
            httpFactory = org.mockito.kotlin.mock(),
        )

    @Test
    fun `ensureHostMatches rejects mismatched hosts`() {
        val config =
            ConfluenceConfig(
                name = "demo",
                baseUrl = "https://company.atlassian.net/wiki",
                username = "u",
                apiToken = "t",
            )
        assertThrows<IllegalArgumentException> {
            adapter.ensureHostMatches(config, "https://evil.example.com/wiki/spaces/DEV/pages/1")
        }
    }

    @Test
    fun `ensureHostMatches accepts same host`() {
        val config =
            ConfluenceConfig(
                name = "demo",
                baseUrl = "https://company.atlassian.net/wiki",
                username = "u",
                apiToken = "t",
            )
        assertDoesNotThrow {
            adapter.ensureHostMatches(config, "https://company.atlassian.net/wiki/spaces/DEV/pages/1")
        }
    }

    @Test
    fun `ensureHostMatches rejects malformed URLs`() {
        val config = ConfluenceConfig(name = "demo", baseUrl = "not-a-url", username = "u", apiToken = "t")
        assertThrows<Exception> {
            adapter.ensureHostMatches(config, "https://company.atlassian.net/wiki/spaces/DEV/pages/1")
        }
    }
}

class JiraHierarchyAdapterSecurityTest {
    private val adapter =
        JiraHierarchyAdapter(
            httpFactory = org.mockito.kotlin.mock(),
            apiVersionResolver = DefaultJiraApiVersionResolver(),
        )

    @Test
    fun `ensureHostMatches rejects bare keys with non-key characters`() {
        val config = JiraConfig(name = "demo", baseUrl = "https://company.atlassian.net", username = "u", apiToken = "t")
        assertThrows<Exception> {
            adapter.ensureHostMatches(config, "not-a-jira-key")
        }
    }

    @Test
    fun `ensureHostMatches accepts valid keys`() {
        val config = JiraConfig(name = "demo", baseUrl = "https://company.atlassian.net", username = "u", apiToken = "t")
        assertDoesNotThrow { adapter.ensureHostMatches(config, "PROJ-123") }
    }

    @Test
    fun `extractTicketKey recovers key from browse URL`() {
        assertEquals("PROJ-456", adapter.extractTicketKey("https://company.atlassian.net/browse/PROJ-456"))
    }

    @Test
    fun `ensureHostMatches rejects URL from different host`() {
        val config = JiraConfig(name = "demo", baseUrl = "https://company.atlassian.net", username = "u", apiToken = "t")
        assertThrows<IllegalArgumentException> {
            adapter.ensureHostMatches(config, "https://evil.example.com/browse/PROJ-1")
        }
    }
}
