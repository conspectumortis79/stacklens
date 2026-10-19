package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitlabBranchCommitApiClientMissingProjectTest {
    private val server = MockWebServer()
    private val client = GitlabBranchCommitApiClient()

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun config() =
        GitlabConfig(
            name = "gl",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "secret",
        )

    private fun ticket() =
        JiraTicketData(
            key = "PROJ-1",
            summary = "Implement the widget",
            description = "details",
            comments = "",
            issueType = "Story",
            status = "To Do",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/browse/PROJ-1",
        )

    private val noCode =
        JiraCodeImplementation(
            identifiedRepositories = emptyList(),
            identifiedDatabases = emptyList(),
            files = emptyList(),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    @Test
    fun `a project id that gitlab cannot resolve aborts the commit`() {
        repeat(8) { server.enqueue(MockResponse().setResponseCode(404).setBody("{}")) }

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                client.commitDirectly(config(), "shop", ticket(), noCode, 99L, null)
            }

        assertEquals(true, error.message!!.contains("99"))
    }

    @Test
    fun `a repository name that gitlab cannot resolve aborts the commit`() {
        repeat(8) { server.enqueue(MockResponse().setResponseCode(404).setBody("{}")) }

        assertThrows(Exception::class.java) {
            client.commitDirectly(config(), "shop", ticket(), noCode, null, null)
        }
    }
}
