package com.stacklens.adapter.out.jira

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class TicketCreationNativeJiraAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: JiraTicketHttpClient
    private lateinit var adapter: TicketCreationNativeJiraAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = JiraTicketHttpClient()
        adapter = TicketCreationNativeJiraAdapter(httpClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jiraConfig(cloudMode: Boolean = false) =
        com.stacklens.domain.model.JiraConfig(
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            projectKey = "PROJ",
            apiToken = "TOK",
            cloudMode = cloudMode,
            active = true,
            createdAt = LocalDateTime.now(),
        )

    @Test
    fun `createTicket returns CreatedJiraTicket with parsed fields`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1","id":"10000"}"""),
        )
        val ticket =
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "Body",
                issueType = "Story",
                labels = listOf("bug"),
                priority = "Medium",
            )
        assertEquals("PROJ-1", ticket.key)
        assertEquals("10000", ticket.id)
        assertTrue(ticket.url.contains("/browse/PROJ-1"))
    }

    @Test
    fun `createTicket uses v2 endpoint for Server DC`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1","id":"10000"}"""),
        )
        adapter.createTicket(
            config = jiraConfig(cloudMode = false),
            projectKey = "PROJ",
            summary = "Test",
            description = "Body",
            issueType = "Story",
            labels = emptyList(),
            priority = "Medium",
        )
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/rest/api/2/issue"))
    }

    @Test
    fun `createTicket uses v3 endpoint for Cloud`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1","id":"10000"}"""),
        )
        adapter.createTicket(
            config = jiraConfig(cloudMode = true),
            projectKey = "PROJ",
            summary = "Test",
            description = "Body",
            issueType = "Story",
            labels = emptyList(),
            priority = "Medium",
        )
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/rest/api/3/issue"))
    }

    @Test
    fun `createTicket sets Authorization header`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1","id":"1"}"""),
        )
        adapter.createTicket(
            config = jiraConfig(),
            projectKey = "PROJ",
            summary = "Test",
            description = "x",
            issueType = "Story",
            labels = emptyList(),
            priority = "Medium",
        )
        val req = server.takeRequest()
        assertNotNull(req.getHeader("Authorization"))
    }

    @Test
    fun `createTicket throws when no response`() {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        } catch (_: IllegalStateException) {
            return
        } catch (_: Exception) {
            return
        }
        throw AssertionError("expected exception to be thrown")
    }

    @Test
    fun `createTicket builds URL from baseUrl and key`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-99","id":"1"}"""),
        )
        val ticket =
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        assertTrue(ticket.url.endsWith("/browse/PROJ-99"), "expected URL to end with /browse/PROJ-99 but was ${ticket.url}")
    }

    @Test
    fun `createTicket returns empty key and URL when response has no key`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"1"}"""),
        )
        val ticket =
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        assertEquals("", ticket.key)
        assertEquals("", ticket.url)
    }

    @Test
    fun `createTicket returns empty id when response has no id`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1"}"""),
        )
        val ticket =
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        assertEquals("PROJ-1", ticket.key)
        assertEquals("", ticket.id)
    }

    @Test
    fun `createTicket builds URL when key is non-blank`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-99","id":"9001"}"""),
        )
        val ticket =
            adapter.createTicket(
                config = jiraConfig(),
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        assertEquals("PROJ-99", ticket.key)
        assertEquals("9001", ticket.id)
        assertEquals("${jiraConfig().baseUrl}/browse/PROJ-99", ticket.url)
    }

    @Test
    fun `createTicket builds URL with trailing slash stripped from baseUrl`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-1","id":"1"}"""),
        )
        val baseUrl = jiraConfig().baseUrl
        val cfg =
            com.stacklens.domain.model.JiraConfig(
                name = "test",
                baseUrl = "$baseUrl/",
                projectKey = "PROJ",
                apiToken = "TOK",
                cloudMode = false,
                active = true,
                createdAt = LocalDateTime.now(),
            )
        val ticket =
            adapter.createTicket(
                config = cfg,
                projectKey = "PROJ",
                summary = "Test",
                description = "x",
                issueType = "Story",
                labels = emptyList(),
                priority = "Medium",
            )
        assertEquals("$baseUrl/browse/PROJ-1", ticket.url)
    }

    @Test
    fun `createSubtask returns CreatedJiraTicket with parsed fields`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-101","id":"10001"}"""),
        )
        val ticket =
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-100",
                projectKey = "PROJ",
                summary = "Subtask one",
                description = "Body of subtask",
                issueType = "Subtask",
                labels = listOf("backend"),
                priority = "Minor",
            )
        assertEquals("PROJ-101", ticket.key)
        assertEquals("10001", ticket.id)
        assertTrue(ticket.url.contains("/browse/PROJ-101"))
    }

    @Test
    fun `createSubtask uses v2 endpoint for Server DC`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-101","id":"10001"}"""),
        )
        adapter.createSubtask(
            config = jiraConfig(cloudMode = false),
            parentKey = "PROJ-100",
            projectKey = "PROJ",
            summary = "S",
            description = "Body",
            issueType = "Subtask",
            labels = emptyList(),
            priority = "",
        )
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/rest/api/2/issue"))
    }

    @Test
    fun `createSubtask uses v3 endpoint for Cloud`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-101","id":"10001"}"""),
        )
        adapter.createSubtask(
            config = jiraConfig(cloudMode = true),
            parentKey = "PROJ-100",
            projectKey = "PROJ",
            summary = "S",
            description = "Body",
            issueType = "Subtask",
            labels = emptyList(),
            priority = "",
        )
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/rest/api/3/issue"))
    }

    @Test
    fun `createSubtask sends parent field in payload`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-101","id":"10001"}"""),
        )
        adapter.createSubtask(
            config = jiraConfig(cloudMode = false),
            parentKey = "PROJ-100",
            projectKey = "PROJ",
            summary = "Sub",
            description = "Body",
            issueType = "Subtask",
            labels = emptyList(),
            priority = "",
        )
        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"PROJ-100\""), "expected parent key in payload but was: $body")
        assertTrue(body.contains("\"Subtask\""), "expected subtask issue type in payload but was: $body")
    }

    @Test
    fun `createSubtask returns empty key and URL when response has no key`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"12345"}"""),
        )
        val ticket =
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-100",
                projectKey = "PROJ",
                summary = "Subtask",
                description = "x",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )
        assertEquals("", ticket.key)
        assertEquals("", ticket.url)
    }

    @Test
    fun `createSubtask returns empty id when response has no id`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-200"}"""),
        )
        val ticket =
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-100",
                projectKey = "PROJ",
                summary = "Subtask",
                description = "x",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )
        assertEquals("PROJ-200", ticket.key)
        assertEquals("", ticket.id)
        assertEquals("${jiraConfig().baseUrl}/browse/PROJ-200", ticket.url)
    }

    @Test
    fun `createSubtask builds URL with trailing slash stripped from baseUrl`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-7","id":"7"}"""),
        )
        val baseUrl = jiraConfig().baseUrl
        val cfg =
            com.stacklens.domain.model.JiraConfig(
                name = "test",
                baseUrl = "$baseUrl/",
                projectKey = "PROJ",
                apiToken = "TOK",
                cloudMode = true,
                active = true,
                createdAt = LocalDateTime.now(),
            )
        val ticket =
            adapter.createSubtask(
                config = cfg,
                parentKey = "PROJ-6",
                projectKey = "PROJ",
                summary = "Subtask",
                description = "x",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )
        assertEquals("$baseUrl/browse/PROJ-7", ticket.url)
    }

    @Test
    fun `createSubtask retries Jira V3 with project-specific subtask issue type id`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":{"issuetype":"Issue type is invalid"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"issueTypes":[{"id":"10001","name":"Story","subtask":false},{"id":"10042","name":"Unteraufgabe","subtask":true}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"key":"PROJ-8","id":"8"}"""),
        )

        val ticket =
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-7",
                projectKey = "PROJ",
                summary = "Localised subtask",
                description = "Body",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )

        assertEquals("PROJ-8", ticket.key)
        assertEquals("/rest/api/3/issue", server.takeRequest().path)
        assertTrue(server.takeRequest().path!!.startsWith("/rest/api/3/issue/createmeta/PROJ/issuetypes"))
        val retryBody = server.takeRequest().body.readUtf8()
        assertTrue(retryBody.contains("\"issuetype\":{\"id\":\"10042\"}"), retryBody)
        assertTrue(retryBody.contains("\"parent\":{\"key\":\"PROJ-7\"}"), retryBody)
    }

    @Test
    fun `createSubtask preserves original Jira error when metadata has no subtask type`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":{"issuetype":"Issue type is invalid"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issueTypes":[{"id":"10001","name":"Story","subtask":false}]}"""),
        )

        assertThrows<org.springframework.web.reactive.function.client.WebClientResponseException> {
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-7",
                projectKey = "PROJ",
                summary = "Subtask",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `createSubtask does not query V3 metadata for Server DC errors`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorMessages":["bad request"]}"""),
        )

        assertThrows<org.springframework.web.reactive.function.client.WebClientResponseException> {
            adapter.createSubtask(
                config = jiraConfig(cloudMode = false),
                parentKey = "PROJ-7",
                projectKey = "PROJ",
                summary = "Subtask",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
            )
        }
        assertEquals(1, server.requestCount)
    }
}
