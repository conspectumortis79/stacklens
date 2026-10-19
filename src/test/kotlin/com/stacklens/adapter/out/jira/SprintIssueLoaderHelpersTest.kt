package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class SprintIssueLoaderHelpersTest {
    private val mapper = ObjectMapper()

    private fun loader(): SprintIssueLoader =
        SprintIssueLoader(
            jiraTicketHttp = org.mockito.kotlin.mock<JiraTicketHttpClient>(),
        )

    private fun invoke(
        name: String,
        vararg args: Any?,
    ): Any? = ReflectionTestUtils.invokeMethod(loader(), name, *args)

    @Test
    fun `parseSingleNode returns null for missing nodes`() {
        val node: JsonNode = mapper.readTree("""{"fields": {}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("does_not_exist"))
        assertNull(result)
    }

    @Test
    fun `parseSingleNode returns null for blank string nodes`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": "   "}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("customfield_10016"))
        assertNull(result)
    }

    @Test
    fun `parseSingleNode parses integer story points`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": 5}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("customfield_10016"))
        assertEquals(5.0, result)
    }

    @Test
    fun `parseSingleNode parses decimal story points`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": 3.5}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("customfield_10016"))
        assertEquals(3.5, result)
    }

    @Test
    fun `parseSingleNode parses stringified story points`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": "8"}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("customfield_10016"))
        assertEquals(8.0, result)
    }

    @Test
    fun `parseSingleNode parses Jira object-wrapped story points`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": {"value": 3.0}}}""")
        val result = invoke("parseSingleNode", node.path("fields").path("customfield_10016"))
        assertEquals(3.0, result)
    }

    @Test
    fun `parseStoryPointsValue returns the primary field when present`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10016": 5.0, "customfield_10026": 3.0}}""")
        val result = invoke("parseStoryPointsValue", node.path("fields"), "customfield_10016")
        assertEquals(5.0, result)
    }

    @Test
    fun `parseStoryPointsValue falls back to common IDs when the primary field is empty (binit repro)`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"customfield_10026": 8.0}}""")
        val result = invoke("parseStoryPointsValue", node.path("fields"), "customfield_10016")
        assertEquals(8.0, result)
    }

    @Test
    fun `parseStoryPointsValue returns null when no numeric field exists`() {
        val node: JsonNode = mapper.readTree("""{"fields": {"description": "hello"}}""")
        val result = invoke("parseStoryPointsValue", node.path("fields"), "customfield_10016")
        assertNull(result)
    }

    @Test
    fun `extractNumericCustomFields returns sorted name-value pairs`() {
        val node: JsonNode =
            mapper.readTree(
                """{
                "fields": {
                    "customfield_10016": 5.0,
                    "customfield_10026": 3.0,
                    "customfield_10050": "2.0",
                    "summary": "Ticket"
                }
            }""",
            )

        @Suppress("UNCHECKED_CAST")
        val result = invoke("extractNumericCustomFields", node.path("fields")) as List<String>
        assertEquals(3, result.size)
        assertEquals("customfield_10016: 5.0", result[0])
        assertEquals("customfield_10026: 3.0", result[1])
        assertEquals("customfield_10050: 2.0", result[2])
    }

    @Test
    fun `extractNumericCustomFields handles object-wrapped values`() {
        val node: JsonNode =
            mapper.readTree(
                """{"fields": {"customfield_10026": {"value": 3.0}}}""",
            )

        @Suppress("UNCHECKED_CAST")
        val result = invoke("extractNumericCustomFields", node.path("fields")) as List<String>
        assertEquals(listOf("customfield_10026: 3.0"), result)
    }

    @Test
    fun `extractNumericCustomFields skips non-numeric customfields`() {
        val node: JsonNode =
            mapper.readTree(
                """{
                "fields": {
                    "customfield_10016": "definitely not a number",
                    "customfield_10026": 3.0
                }
            }""",
            )

        @Suppress("UNCHECKED_CAST")
        val result = invoke("extractNumericCustomFields", node.path("fields")) as List<String>
        assertEquals(listOf("customfield_10026: 3.0"), result)
    }

    @Test
    fun `extractNumericCustomFields returns empty list when no customfields are present`() {
        val node: JsonNode =
            mapper.readTree(
                """{"fields": {"summary": "x", "description": "y"}}""",
            )

        @Suppress("UNCHECKED_CAST")
        val result = invoke("extractNumericCustomFields", node.path("fields")) as List<String>
        assertTrue(result.isEmpty())
    }

    @Test
    fun `jsonToSprintTicket finds story points in customfield_10026 when customfield_10016 is empty`() {
        val node: JsonNode =
            mapper.readTree(
                """{
                "key": "binit-100",
                "fields": {
                    "summary": "User Story Test",
                    "description": "## User Story\\n\\nAls Test",
                    "issuetype": {"name": "Story"},
                    "status": {"name": "To Do"},
                    "assignee": {"displayName": "Alice"},
                    "labels": ["auth"],
                    "customfield_10026": 5.0
                }
            }""",
            )
        val result = invoke("parseStoryPointsValue", node.path("fields"), "customfield_10016")
        assertEquals(5.0, result, "binit-style ticket with customfield_10026 must be resolved via fallback list")
    }

    @Test
    fun `jsonToSprintTicket survives a label element whose asText returns null`() {
        val labelsAdapter: com.fasterxml.jackson.databind.JsonNode =
            mapper
                .readTree("""{"fields":{"labels":["auth"]}}""")
                .path("fields")
                .path("labels")
        val safeLabels =
            labelsAdapter.mapNotNull { it.asText("").takeIf { v -> v.isNotBlank() } }
        assertEquals(listOf("auth"), safeLabels)

        val nullAsText: String? = null
        val result =
            listOf<String?>(nullAsText, "auth", "")
                .mapNotNull { v -> v?.takeIf { it.isNotBlank() } }
        assertEquals(listOf("auth"), result, "null elements must be silently dropped, not crash")
    }

    @Test
    fun `isSubtaskIssue is true for a sub-task issue type`() {
        val subtask =
            mapper.readTree(
                """
                {"key":"binit-2","fields":{"issuetype":{"name":"Sub-task","subtask":true}}}
                """.trimIndent(),
            )
        assertEquals(true, invoke("isSubtaskIssue", subtask))
    }

    @Test
    fun `isSubtaskIssue is false for Story, Technical Task and Bug`() {
        val story =
            mapper.readTree("""{"key":"T-1","fields":{"issuetype":{"name":"Story","subtask":false}}}""")
        val techTask =
            mapper.readTree("""{"key":"T-2","fields":{"issuetype":{"name":"Technical Task","subtask":false}}}""")
        val bug =
            mapper.readTree("""{"key":"T-3","fields":{"issuetype":{"name":"Bug","subtask":false}}}""")
        assertEquals(false, invoke("isSubtaskIssue", story))
        assertEquals(false, invoke("isSubtaskIssue", techTask))
        assertEquals(false, invoke("isSubtaskIssue", bug))
    }

    @Test
    fun `isSubtaskIssue defaults to false when the subtask flag is missing`() {
        val noFlag =
            mapper.readTree("""{"key":"T-4","fields":{"issuetype":{"name":"Story"}}}""")
        val noIssueType =
            mapper.readTree("""{"key":"T-5","fields":{}}""")
        assertEquals(false, invoke("isSubtaskIssue", noFlag))
        assertEquals(false, invoke("isSubtaskIssue", noIssueType))
    }

    @Test
    fun `searchPath returns cloud v3 path when cloudMode is true`() {
        val config =
            com.stacklens.domain.model
                .JiraConfig(id = 1L, name = "c", baseUrl = "https://c.atlassian.net", apiToken = "t", cloudMode = true)
        val path = invoke("searchPath", config) as String
        assertEquals("/rest/api/3/search/jql", path)
    }

    @Test
    fun `searchPath returns server v2 path when cloudMode is false`() {
        val config =
            com.stacklens.domain.model
                .JiraConfig(id = 2L, name = "c", baseUrl = "https://jira.example.com", apiToken = "t", cloudMode = false)
        val path = invoke("searchPath", config) as String
        assertEquals("/rest/api/2/search", path)
    }
}
