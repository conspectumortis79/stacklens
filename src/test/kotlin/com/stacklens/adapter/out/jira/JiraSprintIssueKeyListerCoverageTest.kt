package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType

class JiraSprintIssueKeyListerCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var lister: JiraSprintIssueKeyLister

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        lister = JiraSprintIssueKeyLister(JiraTicketHttpClient(connectTimeoutMs = 3000, readTimeoutMs = 5000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(cloudMode: Boolean = true) =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            cloudMode = cloudMode,
        )

    private fun body(json: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(json)

    @Test
    fun `a payload without an issues array stops the walk`() {
        server.enqueue(body("""{"issues":"not-an-array"}"""))

        assertEquals(emptyList<String>(), lister.listTopLevelIssueKeys(config(), 7L))
    }

    @Test
    fun `an empty issues array stops the walk`() {
        server.enqueue(body("""{"total":5,"issues":[]}"""))

        assertEquals(emptyList<String>(), lister.listTopLevelIssueKeys(config(), 7L))
    }

    @Test
    fun `blank and duplicate keys are ignored`() {
        server.enqueue(body("""{"total":4,"issues":[{"key":"  "},{"key":"PROJ-1"},{"key":"PROJ-1"},{"key":"PROJ-2"}]}"""))

        assertEquals(listOf("PROJ-1", "PROJ-2"), lister.listTopLevelIssueKeys(config(), 7L, pageSize = 10))
    }

    @Test
    fun `an empty response body ends the paging loop`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(""))

        assertEquals(emptyList<String>(), lister.listTopLevelIssueKeys(config(), 7L))
    }

    @Test
    fun `missing totals fall back to the collected size and short pages end the walk`() {
        server.enqueue(body("""{"issues":[{"key":"PROJ-9"}]}"""))

        assertEquals(listOf("PROJ-9"), lister.listTopLevelIssueKeys(config(), 7L, pageSize = 50))
    }

    @Test
    fun `paging continues until the reported total is reached`() {
        server.enqueue(body("""{"total":3,"issues":[{"key":"A-1"},{"key":"A-2"}]}"""))
        server.enqueue(body("""{"total":3,"issues":[{"key":"A-3"}]}"""))

        assertEquals(listOf("A-1", "A-2", "A-3"), lister.listTopLevelIssueKeys(config(), 7L, pageSize = 2))
    }

    @Test
    fun `an http error is wrapped with its status code`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                lister.listTopLevelIssueKeys(config(), 7L)
            }

        assertTrue(failure.message!!.contains("HTTP 403"), failure.message)
    }

    @Test
    fun `an unreachable jira reports status zero`() {
        val dead =
            JiraConfig(
                name = "dead",
                baseUrl = "http://127.0.0.1:1",
                username = "u",
                apiToken = "t",
                cloudMode = false,
            )

        val failure =
            assertThrows(IllegalStateException::class.java) {
                lister.listTopLevelIssueKeys(dead, 7L)
            }

        assertTrue(failure.message!!.contains("HTTP 0"), failure.message)
    }

    @Test
    fun `server jira uses the api v2 search endpoint`() {
        server.enqueue(body("""{"total":1,"issues":[{"key":"SRV-1"}]}"""))

        assertEquals(listOf("SRV-1"), lister.listTopLevelIssueKeys(config(cloudMode = false), 7L))
        val path = server.takeRequest().path

        assertTrue(path!!.startsWith("/rest/api/2/search"), path)
    }

    @Test
    fun `the injected api version resolver supplies the search path`() {
        server.enqueue(body("""{"total":1,"issues":[{"key":"SRV-1"}]}"""))
        val resolver = mock<JiraApiVersionResolver>()
        whenever(resolver.searchPath(any())).thenReturn("/custom/v9/search")
        val customLister =
            JiraSprintIssueKeyLister(JiraTicketHttpClient(connectTimeoutMs = 3000, readTimeoutMs = 5000), resolver)

        assertEquals(listOf("SRV-1"), customLister.listTopLevelIssueKeys(config(cloudMode = false), 7L))
        assertEquals("/custom/v9/search", server.takeRequest().path!!.substringBefore('?'))
    }
}
