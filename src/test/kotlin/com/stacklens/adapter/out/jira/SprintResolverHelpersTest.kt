package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class SprintResolverHelpersTest {
    private val mapper = ObjectMapper()

    private fun resolver(): SprintResolver =
        SprintResolver(
            jiraTicketHttp = org.mockito.kotlin.mock<JiraTicketHttpClient>(),
        )

    private fun invoke(
        name: String,
        vararg args: Any?,
    ): Any? = ReflectionTestUtils.invokeMethod(resolver(), name, *args)

    @Test
    fun `searchPath returns the new v3 jql endpoint on Cloud`() {
        val cfg =
            JiraConfig(
                name = "banana",
                baseUrl = "https://banana.atlassian.net",
                apiToken = "tok",
                cloudMode = true,
            )
        val path = invoke("searchPath", cfg) as String
        assertEquals("/rest/api/3/search/jql", path)
    }

    @Test
    fun `searchPath returns the historical v2 search endpoint on Server DC`() {
        val cfg =
            JiraConfig(
                name = "dc",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
                cloudMode = false,
            )
        val path = invoke("searchPath", cfg) as String
        assertEquals("/rest/api/2/search", path)
    }

    @Test
    fun `JQL sprint lookup - sprint data lives in a CUSTOM FIELD not fields dot sprint`() {
        val issue =
            mapper.readTree(
                """
                {"fields":{"customfield_10020":[{"id":1,"name":"Sprint 1","state":"closed"}]}}
                """.trimIndent(),
            )
        assertTrue(issue.path("fields").path("sprint").isMissingNode)
        assertEquals(
            1L,
            issue
                .path("fields")
                .path("customfield_10020")
                .path(0)
                .path("id")
                .asLong(),
        )
    }

    @Test
    fun `extractSprintsFromIssue reads Jira Cloud sprint objects from a custom field`() {
        val issue =
            mapper.readTree(
                """
                {
                  "key": "binit-1",
                  "fields": {
                    "project": {"key": "binit"},
                    "summary": "Some ticket",
                    "customfield_10020": [
                      {"id": 30027, "name": "binit_2026/31-32", "state": "active", "boardId": 212}
                    ]
                  }
                }
                """.trimIndent(),
            )

        @Suppress("UNCHECKED_CAST")
        val sprints = invoke("extractSprintsFromIssue", issue) as List<Any>
        assertEquals(1, sprints.size)
        val sprint = sprints[0]
        assertEquals(30027L, ReflectionTestUtils.getField(sprint, "id"))
        assertEquals("binit_2026/31-32", ReflectionTestUtils.getField(sprint, "name"))
        assertEquals("active", ReflectionTestUtils.getField(sprint, "state"))
        assertEquals(212L, ReflectionTestUtils.getField(sprint, "boardId"))
    }

    @Test
    fun `extractSprintsFromIssue reads legacy Server DC sprint toString blobs`() {
        val issue =
            mapper.readTree(
                """
                {
                  "key": "PROJ-1",
                  "fields": {
                    "project": {"key": "PROJ"},
                    "customfield_10007": [
                      "com.atlassian.greenhopper.service.sprint.Sprint@2a[id=123,rapidViewId=45,state=CLOSED,name=Sprint 42,startDate=2026-01-01,endDate=2026-01-14]"
                    ]
                  }
                }
                """.trimIndent(),
            )

        @Suppress("UNCHECKED_CAST")
        val sprints = invoke("extractSprintsFromIssue", issue) as List<Any>
        assertEquals(1, sprints.size)
        val sprint = sprints[0]
        assertEquals(123L, ReflectionTestUtils.getField(sprint, "id"))
        assertEquals("Sprint 42", ReflectionTestUtils.getField(sprint, "name"))
        assertEquals("closed", ReflectionTestUtils.getField(sprint, "state"))
        assertEquals(45L, ReflectionTestUtils.getField(sprint, "boardId"))
    }

    @Test
    fun `extractSprintsFromIssue ignores non-sprint custom fields`() {
        val issue =
            mapper.readTree(
                """
                {
                  "key": "PROJ-2",
                  "fields": {
                    "project": {"key": "PROJ"},
                    "labels": ["auth", "backend"],
                    "customfield_10016": 5.0,
                    "customfield_99999": [{"value": "not a sprint"}]
                  }
                }
                """.trimIndent(),
            )

        @Suppress("UNCHECKED_CAST")
        val sprints = invoke("extractSprintsFromIssue", issue) as List<Any>
        assertTrue(sprints.isEmpty())
    }

    @Test
    fun `parseLegacySprintBlob returns null for non-sprint strings`() {
        assertNull(invoke("parseLegacySprintBlob", "just a label"))
        assertNull(invoke("parseLegacySprintBlob", ""))
    }

    @Test
    fun `entityProjectKey returns project key when present`() {
        val issue = mapper.readTree("""{"fields":{"project":{"key":"PROJ"}}}""")
        val key = invoke("entityProjectKey", issue) as String
        assertEquals("PROJ", key)
    }

    @Test
    fun `entityProjectKey returns empty string when project is missing`() {
        val issue = mapper.readTree("""{"fields":{}}""")
        val key = invoke("entityProjectKey", issue) as String
        assertEquals("", key)
    }

    @Test
    fun `entityProjectKey returns empty string when key is missing`() {
        val issue = mapper.readTree("""{"fields":{"project":{"id":"100"}}}""")
        val key = invoke("entityProjectKey", issue) as String
        assertEquals("", key)
    }
}
