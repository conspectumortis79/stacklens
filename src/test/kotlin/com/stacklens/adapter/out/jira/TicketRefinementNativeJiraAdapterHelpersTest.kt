package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketRefinementNativeJiraAdapterHelpersTest {
    private val objectMapper = ObjectMapper()
    private val jiraTicketHttp = org.mockito.kotlin.mock<JiraTicketHttpClient>()
    private val mdConverter = org.mockito.kotlin.mock<JiraMarkdownConverter>()
    private val adfToHtml = org.mockito.kotlin.mock<AdfToHtmlConverter>()

    private fun adapter() =
        TicketRefinementNativeJiraAdapter(
            jiraTicketHttp = jiraTicketHttp,
            objectMapper = objectMapper,
            mdConverter = mdConverter,
            adfToHtml = adfToHtml,
        )

    @Test
    fun `apiVersion returns 3 for cloud mode`() {
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = true, baseUrl = "https://x", name = "t", apiToken = "k")
        assertEquals("3", adapter().apiVersion(cfg))
    }

    @Test
    fun `apiVersion returns 2 for server mode`() {
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = false, baseUrl = "https://x", name = "t", apiToken = "k")
        assertEquals("2", adapter().apiVersion(cfg))
    }

    @Test
    fun `jsonToTicket parses basic fields`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("body")
        val node =
            objectMapper.readTree(
                """{"key":"PROJ-1","fields":{"summary":"Title","description":"body","labels":["bug","urgent"]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = false, baseUrl = "https://jira", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("PROJ-1", ticket.key)
        assertEquals("Title", ticket.summary)
        assertEquals("body", ticket.description)
        assertEquals("https://jira/browse/PROJ-1", ticket.url)
        assertEquals(listOf("bug", "urgent"), ticket.labels)
    }

    @Test
    fun `jsonToTicket handles missing description gracefully`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("")
        val node =
            objectMapper.readTree(
                """{"key":"P-2","fields":{"summary":"S","labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = false, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("P-2", ticket.key)
        assertEquals("S", ticket.summary)
        assertEquals("", ticket.description)
    }

    @Test
    fun `jsonToTicket captures ADF description for cloud mode`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("flattened")
        val adfJson =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""
        val node =
            objectMapper.readTree(
                """{"key":"C-1","fields":{"summary":"S","description":$adfJson,"labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = true, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("adf", ticket.descriptionFormat)
        assertNotNull(ticket.descriptionNative)
        assertTrue(ticket.descriptionNative.contains("paragraph"))
    }

    @Test
    fun `jsonToTicket renders ADF to HTML when renderer available`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("flattened")
        org.mockito.kotlin
            .whenever(adfToHtml.render(org.mockito.kotlin.any()))
            .thenReturn("<p>hello</p>")
        val adfJson =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""
        val node =
            objectMapper.readTree(
                """{"key":"C-1","fields":{"summary":"S","description":$adfJson,"labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = true, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("<p>hello</p>", ticket.descriptionHtml)
    }

    @Test
    fun `jsonToTicket returns null HTML when renderer throws`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("flattened")
        org.mockito.kotlin
            .whenever(adfToHtml.render(org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("render failed"))
        val adfJson =
            """{"type":"doc","version":1,"content":[{"type":"paragraph"}]}"""
        val node =
            objectMapper.readTree(
                """{"key":"C-1","fields":{"summary":"S","description":$adfJson,"labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = true, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertNull(ticket.descriptionHtml)
    }

    @Test
    fun `jsonToTicket captures wiki markup for server mode`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("body")
        org.mockito.kotlin
            .whenever(mdConverter.looksLikeMarkdown(org.mockito.kotlin.any()))
            .thenReturn(false)
        val node =
            objectMapper.readTree(
                """{"key":"S-1","fields":{"summary":"T","description":"h1. Wiki body","labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = false, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("wiki", ticket.descriptionFormat)
        assertEquals("h1. Wiki body", ticket.descriptionNative)
    }

    @Test
    fun `jsonToTicket treats markdown-looking text as empty in server mode`() {
        org.mockito.kotlin
            .whenever(jiraTicketHttp.flattenDescriptionNode(org.mockito.kotlin.any()))
            .thenReturn("body")
        org.mockito.kotlin
            .whenever(mdConverter.looksLikeMarkdown(org.mockito.kotlin.any()))
            .thenReturn(true)
        val node =
            objectMapper.readTree(
                """{"key":"S-1","fields":{"summary":"T","description":"# markdown body","labels":[]}}""",
            )
        val cfg =
            com.stacklens.domain.model
                .JiraConfig(cloudMode = false, baseUrl = "https://j", name = "t", apiToken = "k")
        val ticket = adapter().jsonToTicket(node, cfg)
        assertEquals("text", ticket.descriptionFormat)
        assertEquals("", ticket.descriptionNative)
    }

    @Test
    fun `linkedKeysFrom returns empty when no links`() {
        val node = objectMapper.readTree("""{"fields":{"issuelinks":[],"subtasks":[]}}""")
        val keys = adapter().linkedKeysFrom(node, "", "", "PARENT-1")
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `linkedKeysFrom collects inwardIssue and outwardIssue keys`() {
        val node =
            objectMapper.readTree(
                """{"fields":{"issuelinks":[
                  {"inwardIssue":{"key":"IN-1"}},
                  {"outwardIssue":{"key":"OUT-1"}}
                ],"subtasks":[]}}""",
            )
        val keys = adapter().linkedKeysFrom(node, "", "", "PARENT-1")
        assertTrue(keys.contains("IN-1"))
        assertTrue(keys.contains("OUT-1"))
    }

    @Test
    fun `linkedKeysFrom collects subtask keys`() {
        val node =
            objectMapper.readTree(
                """{"fields":{"issuelinks":[],"subtasks":[{"key":"SUB-1"},{"key":"SUB-2"}]}}""",
            )
        val keys = adapter().linkedKeysFrom(node, "", "", "PARENT-1")
        assertTrue(keys.contains("SUB-1"))
        assertTrue(keys.contains("SUB-2"))
    }

    @Test
    fun `linkedKeysFrom collects mentioned keys from summary and description`() {
        val node = objectMapper.readTree("""{"fields":{"issuelinks":[],"subtasks":[]}}""")
        val keys = adapter().linkedKeysFrom(node, "see ABC-9 and XYZ-42", "links to QQ-7", "PARENT-1")
        assertTrue(keys.contains("ABC-9"))
        assertTrue(keys.contains("XYZ-42"))
        assertTrue(keys.contains("QQ-7"))
    }

    @Test
    fun `linkedKeysFrom excludes parent key from mentions`() {
        val node = objectMapper.readTree("""{"fields":{"issuelinks":[],"subtasks":[]}}""")
        val keys = adapter().linkedKeysFrom(node, "see PARENT-1 mentioned", "", "PARENT-1")
        assertTrue(!keys.contains("PARENT-1"))
    }

    @Test
    fun `linkedKeysFrom deduplicates keys`() {
        val node =
            objectMapper.readTree(
                """{"fields":{"issuelinks":[
                  {"inwardIssue":{"key":"DUP-1"}},
                  {"outwardIssue":{"key":"DUP-1"}}
                ],"subtasks":[{"key":"DUP-1"}]}}""",
            )
        val keys = adapter().linkedKeysFrom(node, "", "", "PARENT-1")
        assertEquals(1, keys.count { it == "DUP-1" })
    }
}
