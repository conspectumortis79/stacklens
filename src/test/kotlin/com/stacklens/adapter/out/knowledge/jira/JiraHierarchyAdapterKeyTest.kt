package com.stacklens.adapter.out.knowledge.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraHierarchyAdapterKeyTest {
    private val resolver = mock<JiraApiVersionResolver>()

    private val adapter =
        JiraHierarchyAdapter(
            ReactiveHttpClientFactory(connectTimeoutMs = 200, readTimeoutMs = 200),
            resolver,
        )

    private fun config(baseUrl: String): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "jira",
            baseUrl = baseUrl,
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `extractTicketKey normalises the first key found in the input`() {
        assertEquals("PROJ-12", adapter.extractTicketKey("https://x.atlassian.net/browse/PROJ-12?selectedIssue=PROJ-12"))
        assertEquals("PROJ-12", adapter.extractTicketKey("  proj-12  "))
        assertEquals("A_B-1", adapter.extractTicketKey("A_B-1"))
        assertNull(adapter.extractTicketKey("kein schluessel"))
        assertNull(adapter.extractTicketKey(""))
    }

    @Test
    fun `ensureHostMatches accepts keys and urls of the configured host`() {
        val config = config("https://jira.example.com")

        adapter.ensureHostMatches(config, "PROJ-1")
        adapter.ensureHostMatches(config, "https://JIRA.example.com/browse/PROJ-1")
        adapter.ensureHostMatches(config("http://localhost:8080"), "PROJ-1")
    }

    @Test
    fun `ensureHostMatches rejects an input without a jira key`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.ensureHostMatches(config("https://jira.example.com"), "keine gueltige angabe")
            }

        assertTrue(failure.message!!.startsWith("Ticket key is not a valid Jira key"), "unexpected: ${failure.message}")
    }

    @Test
    fun `ensureHostMatches rejects a ticket from a different host`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                adapter.ensureHostMatches(config("https://jira.example.com"), "https://anderer.example.org/browse/PROJ-1")
            }

        assertTrue(failure.message!!.contains("does not match"), "unexpected: ${failure.message}")
    }

    @Test
    fun `ensureHostMatches reports a base url without a host`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.ensureHostMatches(config("https:/jira.example.com"), "https://jira.example.com/browse/PROJ-1")
            }

        assertTrue(failure.message!!.startsWith("Jira base URL is not a valid absolute URL"), "unexpected: ${failure.message}")
    }

    @Test
    fun `ensureHostMatches reports a ticket url without a host`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.ensureHostMatches(config("https://jira.example.com"), "https:/jira.example.com/browse/PROJ-1")
            }

        assertTrue(failure.message!!.startsWith("Jira ticket URL is not a valid absolute URL"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a walk without a resolvable ticket key stays empty`() {
        whenever(resolver.apiVersion(any())).thenReturn("2")

        val snapshots =
            adapter
                .walkHierarchy(
                    config("https://jira.example.com"),
                    "kein schluessel",
                    includeChildren = true,
                    includeComments = true,
                    maxDepth = 1,
                    maxIssues = 5,
                    includeLinkedTickets = true,
                    includeParent = true,
                ).toList()

        assertEquals(emptyList<Any>(), snapshots)
        verify(resolver).apiVersion(any())
    }
}
