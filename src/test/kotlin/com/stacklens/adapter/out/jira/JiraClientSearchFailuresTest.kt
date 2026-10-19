package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraClientSearchFailuresTest {
    private lateinit var server: MockWebServer

    private lateinit var issueMapper: JiraIssueMapper

    private lateinit var client: JiraClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        issueMapper = mock()
        client =
            JiraClient(
                objectMapper = jacksonObjectMapper(),
                jqlBuilder = JiraJqlBuilder(),
                issueMapper = issueMapper,
                endpoints = JiraEndpoints(server = listOf(JiraEndpointSpec("GET", "/rest/api/2/search"))),
                httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
            )
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun cfg() =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            apiToken = "tok",
            username = "u",
            projectKey = "PROJ",
            cloudMode = false,
            active = true,
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun search(): List<com.stacklens.domain.model.SearchResult> {
        val config = cfg()
        return client.searchIssues(client.buildClient(config), config, "jql", "keyword", 1L)
    }

    @Test
    fun `a first page that is no array of issues ends the search`() {
        server.enqueue(json("""{"issues":{"oops":true}}"""))

        assertTrue(search().isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `paging stops when a follow up page carries no issue payload`() {
        server.enqueue(json("""{"issues":[{"key":"K-1","fields":{"summary":"s"}}],"total":3,"startAt":0}"""))
        server.enqueue(json("""{"noise":true}"""))

        assertTrue(search().isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `paging stops when a follow up page cannot be parsed`() {
        server.enqueue(json("""{"issues":[{"key":"K-1","fields":{"summary":"s"}}],"total":3,"startAt":0}"""))
        server.enqueue(json("""{"issues":"kein array"}"""))

        assertTrue(search().isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an unmappable issue is dropped instead of failing the search`() {
        server.enqueue(json("""{"issues":[{"key":"K-1","fields":{"summary":"s"}}],"total":1,"startAt":0}"""))
        whenever(issueMapper.mapIssueToResult(any(), any(), any(), any())).thenThrow(RuntimeException("fields missing"))

        assertTrue(search().isEmpty())
    }

    @Test
    fun `a redirection answer is not accepted as a search result`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )

        assertTrue(search().isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an error answer makes the client try the next endpoint`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertTrue(search().isEmpty())
        assertEquals(1, server.requestCount)
    }
}
