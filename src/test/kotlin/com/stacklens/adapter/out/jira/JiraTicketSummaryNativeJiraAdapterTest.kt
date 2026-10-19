package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class JiraTicketSummaryNativeJiraAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: JiraTicketSummaryNativeJiraAdapter
    private lateinit var httpClient: JiraTicketHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = JiraTicketHttpClient()
        adapter = JiraTicketSummaryNativeJiraAdapter(httpClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(body)

    private fun cloudConfig() =
        JiraConfig(
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    private fun serverConfig() =
        JiraConfig(
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            cloudMode = false,
        )

    @Test
    fun `fetchTicketWithComments throws when config is null`() {
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchTicketWithComments(null, "PROJ-1")
        }
    }

    @Test
    fun `fetchTicketWithComments returns null on 404`() {
        server.enqueue(jsonResponse(404, "not found"))
        val result = adapter.fetchTicketWithComments(cloudConfig(), "PROJ-1")
        assertNull(result)
    }

    @Test
    fun `fetchTicketWithComments parses successful cloud response`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-1",
                    "fields": {
                        "summary": "Add login",
                        "description": null,
                        "issuetype": {"name": "Story"},
                        "status": {"name": "Open"},
                        "priority": {"name": "Medium"},
                        "labels": ["auth"],
                        "comment": {"comments": []},
                        "assignee": null,
                        "reporter": {"displayName": "Alice"},
                        "created": "2024-01-01T10:00:00.000+0000",
                        "updated": "2024-01-02T10:00:00.000+0000"
                    }
                }""",
            ),
        )
        val ticket = adapter.fetchTicketWithComments(cloudConfig(), "PROJ-1")
        assertNotNull(ticket)
        assertEquals("PROJ-1", ticket?.key)
        assertEquals("Add login", ticket?.summary)
        assertEquals("Open", ticket?.status)
    }

    @Test
    fun `fetchTicketWithComments parses server-style response`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-2",
                    "fields": {
                        "summary": "Fix bug",
                        "description": null,
                        "issuetype": {"name": "Bug"},
                        "status": {"name": "In Progress"},
                        "priority": null,
                        "labels": [],
                        "comment": {"comments": []}
                    }
                }""",
            ),
        )
        val ticket = adapter.fetchTicketWithComments(serverConfig(), "PROJ-2")
        assertNotNull(ticket)
        assertEquals("PROJ-2", ticket?.key)
        assertEquals("Fix bug", ticket?.summary)
    }

    @Test
    fun `fetchTicketWithComments throws on 401`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchTicketWithComments(cloudConfig(), "PROJ-1")
        }
    }

    @Test
    fun `fetchTicketWithComments throws on 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchTicketWithComments(cloudConfig(), "PROJ-1")
        }
    }

    @Test
    fun `fetchTicketWithComments parses comments`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-3",
                    "fields": {
                        "summary": "T",
                        "comment": {
                            "comments": [
                                {
                                    "id": "1",
                                    "body": "first comment",
                                    "author": {"displayName": "Alice"},
                                    "created": "2024-01-01T10:00:00.000+0000"
                                }
                            ]
                        }
                    }
                }""",
            ),
        )
        val ticket = adapter.fetchTicketWithComments(cloudConfig(), "PROJ-3")
        assertNotNull(ticket)
        assertEquals(1, ticket?.comments?.size)
        assertEquals("first comment", ticket?.comments?.firstOrNull()?.body)
    }

    @Test
    fun `fetchTicketWithComments filters comments with blank body`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-4",
                    "fields": {
                        "summary": "T",
                        "comment": {
                            "comments": [
                                {"body": "", "author": {"displayName": "A"}, "created": "2024-01-01"},
                                {"body": "kept", "author": {"displayName": "B"}, "created": "2024-01-02"}
                            ]
                        }
                    }
                }""",
            ),
        )
        val ticket = adapter.fetchTicketWithComments(cloudConfig(), "PROJ-4")
        assertNotNull(ticket)
        assertEquals(1, ticket?.comments?.size)
        assertEquals("kept", ticket?.comments?.firstOrNull()?.body)
    }

    @Test
    fun `fetchTicketWithComments returns empty comments when comment is not an array`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-5",
                    "fields": {
                        "summary": "T",
                        "comment": {"not_a_comments": []}
                    }
                }""",
            ),
        )
        val ticket = adapter.fetchTicketWithComments(cloudConfig(), "PROJ-5")
        assertNotNull(ticket)
        assertEquals(0, ticket?.comments?.size ?: 0)
    }

    @Test
    fun `fetchTicketWithComments throws on blank summary`() {
        server.enqueue(
            jsonResponse(
                200,
                """{
                    "key": "PROJ-6",
                    "fields": {"summary": ""}
                }""",
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchTicketWithComments(cloudConfig(), "PROJ-6")
        }
    }

    @Test
    fun `fetchTicketWithComments throws when Jira returns an empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.fetchTicketWithComments(cloudConfig(), "PROJ-7")
            }
        assertTrue(ex.message!!.contains("PROJ-7"))
    }
}
