package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SprintIssueLoaderFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var loader: SprintIssueLoader

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000)
        loader = SprintIssueLoader(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun routeWith(
        fieldsBody: String,
        searchBody: String,
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/field") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(fieldsBody)
                        path.contains("/search") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(searchBody)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    private fun replyWith(body: String): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
        }

    private fun failSearchWith(body: String = """{"errorMessages":["Server Error"]}"""): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().contains("/search")) {
                    MockResponse()
                        .setResponseCode(500)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[]")
                }
        }

    @Test
    fun `fetch returns empty list when search returns no issues`() {
        server.dispatcher = replyWith("""{"total":0,"issues":[]}""")
        val result = loader.fetch(cloudConfig(), sprintId = 42L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetch returns tickets from a single page`() {
        server.dispatcher =
            replyWith(
                """
                {
                  "total": 2,
                  "issues": [
                    {
                      "key": "PROJ-1",
                      "fields": {
                        "summary": "Do the thing",
                        "description": null,
                        "labels": ["a"],
                        "issuetype": {"name": "Story", "subtask": false},
                        "status": {"name": "Done"},
                        "assignee": {"displayName": "Alice"},
                        "priority": {"name": "High"},
                        "customfield_10016": 3.0
                      }
                    },
                    {
                      "key": "PROJ-2",
                      "fields": {
                        "summary": "Other thing",
                        "description": null,
                        "labels": [],
                        "issuetype": {"name": "Task", "subtask": false},
                        "status": {"name": "Done"},
                        "assignee": null,
                        "priority": null,
                        "customfield_10016": 5.0
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        val result = loader.fetch(cloudConfig(), sprintId = 42L)
        assertEquals(2, result.size)
        assertEquals("PROJ-1", result[0].key)
        assertEquals("Do the thing", result[0].summary)
        assertEquals("Story", result[0].issueType)
        assertEquals("Done", result[0].status)
        assertEquals("Alice", result[0].assignee)
        assertEquals("High", result[0].priority)
        assertEquals(3.0, result[0].storyPoints)
        assertTrue(result[0].labels.contains("a"))
        assertNotNull(result[0].url)
        assertEquals("PROJ-2", result[1].key)
        assertEquals(null, result[1].assignee)
        assertEquals(null, result[1].priority)
    }

    @Test
    fun `fetch paginates until total is reached`() {
        var pageIndex = 0
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    pageIndex++
                    val body =
                        if (pageIndex == 1) {
                            """
                            {"total":2,"issues":[
                              {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                                "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                                "customfield_10016":1.0}}
                            ]}
                            """.trimIndent()
                        } else {
                            """
                            {"total":2,"issues":[
                              {"key":"P-2","fields":{"summary":"B","description":null,"labels":[],
                                "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                                "customfield_10016":2.0}}
                            ]}
                            """.trimIndent()
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].key)
    }

    @Test
    fun `fetch filters out sub-tasks via subtask flag`() {
        server.dispatcher =
            replyWith(
                """
                {"total":2,"issues":[
                  {"key":"P-1","fields":{"summary":"Parent","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10016":1.0}},
                  {"key":"P-2","fields":{"summary":"Sub","description":null,"labels":[],
                    "issuetype":{"name":"Sub-task","subtask":true},"status":{"name":"Done"},
                    "customfield_10016":0.5}}
                ]}
                """.trimIndent(),
            )
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].key)
    }

    @Test
    fun `fetch throws IllegalStateException on HTTP 500`() {
        server.dispatcher = failSearchWith()
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                loader.fetch(cloudConfig(), sprintId = 7L)
            }
        assertTrue(ex.message!!.contains("HTTP 500"))
    }

    @Test
    fun `fetch sends jql with sprintId and subTaskIssueTypes filter`() {
        server.dispatcher =
            replyWith(
                """{"total":1,"issues":[{"key":"X-1","fields":{"summary":"Y","description":null,"labels":[],
                "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                "customfield_10016":1.0}}]}""",
            )
        loader.fetch(cloudConfig(), sprintId = 13L)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("jql="), "Expected jql in path, got: ${recorded.path}")
        assertTrue(recorded.path!!.contains("sprint"), "Expected sprint in path, got: ${recorded.path}")
        assertTrue(recorded.path!!.contains("13"), "Expected 13 in path, got: ${recorded.path}")
    }

    @Test
    fun `fetch uses cloud v3 search-jql endpoint when cloudMode is true`() {
        server.dispatcher = replyWith("""{"total":0,"issues":[]}""")
        loader.fetch(cloudConfig(), sprintId = 7L)
        val recorded = server.takeRequest()
        assertEquals("/rest/api/3/search/jql", recorded.path!!.substringBefore('?'))
    }

    @Test
    fun `fetch uses server v2 search endpoint when cloudMode is false`() {
        server.dispatcher = replyWith("""{"total":0,"issues":[]}""")
        loader.fetch(serverConfig(), sprintId = 7L)
        val recorded = server.takeRequest()
        assertEquals("/rest/api/2/search", recorded.path!!.substringBefore('?'))
    }

    @Test
    fun `fetch auto-detects story points field when no explicit override is configured`() {
        routeWith(
            fieldsBody =
                """
                [{"id":"customfield_10026","name":"Story Points",
                 "schema":{"type":"number"}}]
                """.trimIndent(),
            searchBody =
                """
                {"total":1,"issues":[
                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10026": 8.0}}
                ]}
                """.trimIndent(),
        )
        val result = loader.fetch(cloudConfig(storyPointsFieldId = ""), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals(8.0, result[0].storyPoints)
    }

    @Test
    fun `fetch falls back to common IDs when the auto-detected field is empty`() {
        routeWith(
            fieldsBody = "[]",
            searchBody =
                """
                {"total":1,"issues":[
                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10026": 8.0}}
                ]}
                """.trimIndent(),
        )
        val result = loader.fetch(cloudConfig(storyPointsFieldId = ""), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals(8.0, result[0].storyPoints)
    }

    @Test
    fun `fetch respects explicit storyPointsFieldId on JiraConfig`() {
        server.dispatcher =
            replyWith(
                """
                {"total":1,"issues":[
                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10999": 21.0}}
                ]}
                """.trimIndent(),
            )
        val result = loader.fetch(cloudConfig(storyPointsFieldId = "customfield_10999"), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals(21.0, result[0].storyPoints)
    }

    @Test
    fun `fetch collects numeric customfields for the safety-net prompt`() {
        server.dispatcher =
            replyWith(
                """
                {"total":1,"issues":[
                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10016": null,
                    "customfield_10026": 5.0,
                    "customfield_10050": "12.5"}}
                ]}
                """.trimIndent(),
            )
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertTrue(result[0].numericCustomFields.contains("customfield_10026: 5.0"))
        assertTrue(result[0].numericCustomFields.contains("customfield_10050: 12.5"))
    }

    @Test
    fun `fetch breaks out when issues returned is smaller than pageSize`() {
        server.dispatcher =
            replyWith(
                """
                {"total":5,"issues":[
                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                    "customfield_10016":1.0}}
                ]}
                """.trimIndent(),
            )
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetch falls back to default story-points ID when the field endpoint is unreachable`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().contains("/field")) {
                        MockResponse()
                            .setResponseCode(500)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"errorMessages":["down"]}""")
                    } else {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {"total":1,"issues":[
                                  {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                                    "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                                    "customfield_10016": 3.0}}
                                ]}
                                """.trimIndent(),
                            )
                    }
            }
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals(3.0, result[0].storyPoints)
    }

    @Test
    fun `fetch returns empty list when search returns no issues path`() {
        server.dispatcher = replyWith("""{"issues":[],"total":0}""")
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetch stops paginating when an empty page is returned`() {
        var hits = 0
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    hits++
                    val body =
                        if (hits == 1) {
                            """
                            {"total":10,"issues":[
                              {"key":"P-1","fields":{"summary":"A","description":null,"labels":[],
                                "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                                "customfield_10016":1.0}}
                            ]}
                            """.trimIndent()
                        } else {
                            """{"total":10,"issues":[]}"""
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val result = loader.fetch(cloudConfig(), sprintId = 7L)
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].key)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetch supports an explicit empty-result body`() {
        server.dispatcher = replyWith("""{"issues":[],"total":0}""")
        val result = loader.fetch(cloudConfig(), sprintId = 1L)
        assertTrue(result.isEmpty())
    }

    private fun cloudConfig(storyPointsFieldId: String = "customfield_10016"): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "",
            username = "user",
            apiToken = "token",
            cloudMode = true,
            storyPointsFieldId = storyPointsFieldId,
        )

    private fun serverConfig(storyPointsFieldId: String = "customfield_10016"): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "",
            username = "user",
            apiToken = "token",
            cloudMode = false,
            storyPointsFieldId = storyPointsFieldId,
        )
}
