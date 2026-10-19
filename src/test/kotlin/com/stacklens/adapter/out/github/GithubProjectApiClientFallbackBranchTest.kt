package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GithubProjectApiClientFallbackBranchTest {
    private lateinit var server: MockWebServer

    private val api = GithubProjectApiClient()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun client(): WebClient =
        WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
            .build()

    private fun brokenClient(): WebClient {
        val broken = mock<WebClient>()
        whenever(broken.get()).thenThrow(IllegalStateException("dns failure"))
        return broken
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `default branch answers main when github returns nothing`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals("main", api.fetchDefaultBranch(client(), "acme", "widget"))

        enqueue("""{"default_branch":"trunk"}""")
        assertEquals("trunk", api.fetchDefaultBranch(client(), "acme", "widget"))

        enqueue("""{"id":1}""")
        assertEquals("", api.fetchDefaultBranch(client(), "acme", "widget"))
    }

    @Test
    fun `branch sha reports broken transports and empty bodies`() {
        assertNull(api.fetchBranchSha(brokenClient(), "acme", "widget", "main"))

        server.enqueue(MockResponse().setResponseCode(200))
        assertNull(api.fetchBranchSha(client(), "acme", "widget", "main"))

        enqueue("""{"object":{"sha":"abcdef"}}""")
        assertEquals("abcdef", api.fetchBranchSha(client(), "acme", "widget", "main"))
    }

    @Test
    fun `file sha reports broken transports and empty bodies`() {
        assertNull(api.fetchExistingFileSha(brokenClient(), "acme", "widget", "README.md", "main"))

        server.enqueue(MockResponse().setResponseCode(200))
        assertNull(api.fetchExistingFileSha(client(), "acme", "widget", "README.md", "main"))

        enqueue("""{"sha":"filesha"}""")
        assertEquals("filesha", api.fetchExistingFileSha(client(), "acme", "widget", "README.md", "main"))
    }
}
