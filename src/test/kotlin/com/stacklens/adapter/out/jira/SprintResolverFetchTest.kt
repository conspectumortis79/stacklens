package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SprintResolverFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: SprintResolver

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = SprintResolver(JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cloudConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "banana",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "DEV",
            username = "user",
            apiToken = "tok",
            cloudMode = true,
        )

    private fun serverConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "dc",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "DEV",
            username = "user",
            apiToken = "tok",
            cloudMode = false,
        )

    private fun singleSprintBody(
        sprintId: Long,
        sprintName: String,
        projectKey: String = "DEV",
    ): String =
        """
        {
          "issues": [
            {
              "key": "$projectKey-1",
              "fields": {
                "project": {"key": "$projectKey"},
                "customfield_10020": [
                  {"id": $sprintId, "name": "$sprintName", "state": "active", "boardId": 4}
                ]
              }
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `resolve returns the first matching sprint on Jira Cloud`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(singleSprintBody(7L, "Sprint 42"))
            }
        val resolved = resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals(7L, resolved.id)
        assertEquals("Sprint 42", resolved.name)
        assertTrue(resolved.url.contains("/jira/software/c/projects/DEV/backlog"))
    }

    @Test
    fun `resolve returns the first matching sprint on Server DC`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(singleSprintBody(7L, "Sprint 42"))
            }
        val resolved = resolver.resolve(serverConfig(), "Sprint 42")
        assertEquals(7L, resolved.id)
        assertTrue(resolved.url.contains("/secure/RapidBoard.jspa"))
    }

    @Test
    fun `resolve deduplicates repeated sprint ids across issues`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "issues":[
                                {"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":7,"name":"Sprint 42","state":"active","boardId":4}]}},
                                {"key":"DEV-2","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":7,"name":"Sprint 42","state":"active","boardId":4}]}}
                              ]
                            }
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals(7L, resolved.id)
    }

    @Test
    fun `resolve picks the first distinct sprint when the name matches multiple boards`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "issues":[
                                {"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":1,"name":"Sprint 42","state":"active","boardId":4}]}},
                                {"key":"DEV-2","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":2,"name":"Sprint 42","state":"closed","boardId":5}]}}
                              ]
                            }
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals(1L, resolved.id)
    }

    @Test
    fun `resolve parses legacy Server DC sprint toString blobs`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "issues":[
                                {"key":"DEV-1","fields":{
                                  "project":{"key":"DEV"},
                                  "customfield_10020":[
                                    "com.atlassian.greenhopper.service.sprint.Sprint@1[id=99,rapidViewId=42,state=ACTIVE,name=Sprint 99,startDate=...,endDate=...,completeDate=...,sequence=42,goal=...,key=...,_links={}]"
                                  ]
                                }}
                              ]
                            }
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(serverConfig(), "Sprint 99")
        assertEquals(99L, resolved.id)
        assertEquals("Sprint 99", resolved.name)
    }

    @Test
    fun `resolve throws when no issue surfaces the requested sprint name`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"issues":[
                              {"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":1,"name":"Other Sprint","state":"active","boardId":4}]}}
                            ]}""",
                        )
            }
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.resolve(cloudConfig(), "Sprint 42")
            }
        assertTrue(ex.message!!.contains("Sprint 42"))
    }

    @Test
    fun `resolve throws when the search response carries zero issues`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"issues":[]}""")
            }
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.resolve(cloudConfig(), "Sprint 42")
            }
        assertTrue(ex.message!!.contains("nicht gefunden"))
    }

    @Test
    fun `resolve treats a 500 from Jira as not-found (no NPE bubbling up)`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(500)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"errorMessages":["down"]}""")
            }
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.resolve(cloudConfig(), "Sprint 42")
            }
        assertTrue(ex.message!!.contains("Sprint 42"))
    }

    @Test
    fun `resolve hits the v3 search-jql endpoint on Cloud`() {
        var seenPath: String? = null
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seenPath = request.path
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(singleSprintBody(7L, "Sprint 42"))
                }
            }
        resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals("/rest/api/3/search/jql", seenPath!!.substringBefore('?'))
    }

    @Test
    fun `resolve hits the v2 search endpoint on Server DC`() {
        var seenPath: String? = null
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seenPath = request.path
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(singleSprintBody(7L, "Sprint 42"))
                }
            }
        resolver.resolve(serverConfig(), "Sprint 42")
        assertEquals("/rest/api/2/search", seenPath!!.substringBefore('?'))
    }

    @Test
    fun `resolve escapes embedded quotes in the JQL name`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"issues":[{"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":1,"name":"Q4","state":"active","boardId":4}]}}]}""",
                        )
            }
        resolver.resolve(cloudConfig(), sprintName = "Q4")
    }

    @Test
    fun `resolve ignores sprint entries whose ids are zero or negative`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"issues":[
                              {"key":"DEV-1","fields":{"project":{"key":"DEV"},
                                "customfield_10020":[
                                  {"id":0,"name":"Sprint 42","state":"active","boardId":4},
                                  {"id":42,"name":"Sprint 42","state":"active","boardId":4}
                                ]
                              }}
                            ]}
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals(42L, resolved.id)
    }

    @Test
    fun `resolve ignores non-matching sprint names even when ids are valid`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"issues":[
                              {"key":"DEV-1","fields":{"project":{"key":"DEV"},
                                "customfield_10020":[
                                  {"id":1,"name":"Sprint 1","state":"active","boardId":4},
                                  {"id":2,"name":"Sprint 42","state":"active","boardId":4}
                                ]
                              }}
                            ]}
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(cloudConfig(), "Sprint 42")
        assertEquals(2L, resolved.id)
    }
}
