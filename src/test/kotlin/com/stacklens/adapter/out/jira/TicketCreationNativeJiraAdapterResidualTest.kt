package com.stacklens.adapter.out.jira

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.LocalDateTime

class TicketCreationNativeJiraAdapterResidualTest {
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
    fun `createTicket complains when jira answers without a body`() {
        server.enqueue(MockResponse().setResponseCode(201))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.createTicket(
                    config = jiraConfig(),
                    projectKey = "PROJ",
                    summary = "Test",
                    description = "",
                    issueType = "Story",
                    labels = emptyList(),
                    priority = "",
                )
            }

        assertEquals("Jira returned no response.", failure.message)
    }

    @Test
    fun `createSubtask complains when jira answers without a body`() {
        server.enqueue(MockResponse().setResponseCode(201))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.createSubtask(
                    config = jiraConfig(),
                    parentKey = "PROJ-1",
                    projectKey = "PROJ",
                    summary = "Sub",
                    description = "",
                    issueType = "Subtask",
                    labels = emptyList(),
                    priority = "",
                )
            }

        assertTrue(failure.message!!.contains("subtask under PROJ-1"), failure.message)
    }

    @Test
    fun `subtask retry gives up when the createmeta request fails`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorMessages":["invalid issuetype"]}"""),
        )
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        assertThrows(WebClientResponseException::class.java) {
            adapter.createSubtask(
                config = jiraConfig(cloudMode = true),
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "Sub",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
            )
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `subtask fields reference the issue type by id when one is given`() {
        val fields = adapter.buildSubtaskFieldsMap("PROJ-1", "PROJ", "s", "", "Subtask", emptyList(), "", cloudMode = false, issueTypeId = "10042")

        assertEquals(mapOf("id" to "10042"), fields["issuetype"])
    }

    @Test
    fun `subtask fields reference the issue type by name when the id is blank`() {
        val fields = adapter.buildSubtaskFieldsMap("PROJ-1", "PROJ", "s", "", "Unteraufgabe", emptyList(), "", cloudMode = false, issueTypeId = " ")

        assertEquals(mapOf("name" to "Unteraufgabe"), fields["issuetype"])
    }

    @Test
    fun `subtask issue type lookup ignores entries without a usable name`() {
        val metadata =
            mapOf(
                "issueTypes" to
                    listOf(
                        mapOf("id" to "1", "subtask" to true),
                        mapOf("id" to "2", "name" to 42, "subtask" to true),
                        mapOf("id" to "3", "name" to "SubTask", "subtask" to true),
                    ),
            )

        assertEquals("3", adapter.findSubtaskIssueTypeId(metadata, "subtask"))
    }

    @Test
    fun `subtask issue type lookup falls back to the first subtask entry`() {
        val metadata =
            mapOf(
                "issueTypes" to
                    listOf(
                        mapOf("id" to "7", "name" to "Unteraufgabe", "subtask" to true),
                        mapOf("id" to "8", "name" to "Bug", "subtask" to false),
                    ),
            )

        assertEquals("7", adapter.findSubtaskIssueTypeId(metadata, "Unknown"))
    }

    @Test
    fun `subtask issue type lookup returns nothing for a nameless first entry`() {
        val metadata = mapOf("issueTypes" to listOf(mapOf("subtask" to true)))

        assertNull(adapter.findSubtaskIssueTypeId(metadata, "Subtask"))
    }
}
