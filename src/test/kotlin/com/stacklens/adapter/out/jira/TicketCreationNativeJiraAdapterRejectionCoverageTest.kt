package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.LocalDateTime

class TicketCreationNativeJiraAdapterRejectionCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: TicketCreationNativeJiraAdapter

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        adapter = TicketCreationNativeJiraAdapter(JiraTicketHttpClient())
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun jiraConfig(cloudMode: Boolean) =
        JiraConfig(
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            projectKey = "PROJ",
            apiToken = "TOK",
            cloudMode = cloudMode,
            active = true,
            createdAt = LocalDateTime.now(),
        )

    private fun createSubtask(cloudMode: Boolean) =
        assertThrows(WebClientResponseException::class.java) {
            adapter.createSubtask(
                config = jiraConfig(cloudMode),
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "Sub",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
            )
        }

    @Test
    fun `a server jira that rejects the payload is not retried with an issue type id`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"errorMessages":["nope"]}"""))

        val failure = createSubtask(cloudMode = false)

        assertEquals(400, failure.statusCode.value())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a cloud jira answer other than bad request is thrown directly`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"errorMessages":["boom"]}"""))

        val failure = createSubtask(cloudMode = true)

        assertEquals(500, failure.statusCode.value())
        assertEquals(1, server.requestCount)
    }
}
