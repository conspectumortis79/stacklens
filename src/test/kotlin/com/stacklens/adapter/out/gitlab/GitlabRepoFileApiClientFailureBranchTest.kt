package com.stacklens.adapter.out.gitlab

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

class GitlabRepoFileApiClientFailureBranchTest {
    private lateinit var server: MockWebServer

    private val api = GitlabRepoFileApiClient()

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
        whenever(broken.get()).thenThrow(IllegalStateException("channel closed"))
        return broken
    }

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    @Test
    fun `tree paging stops and yields nothing when the transport breaks`() {
        assertEquals(emptyList<GitlabTreeEntry>(), api.fetchRepoTree(brokenClient(), 11L))
    }

    @Test
    fun `tree paging treats an empty response body as no entries`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(emptyList<GitlabTreeEntry>(), api.fetchRepoTree(client(), 11L))
    }

    @Test
    fun `file fetching reports transport failures as missing content`() {
        assertNull(api.fetchRawFile(brokenClient(), 11L, "src/Main.kt"))
    }

    @Test
    fun `file fetching treats an empty response body as missing content`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertNull(api.fetchRawFile(client(), 11L, "src/Main.kt"))
    }

    @Test
    fun `file fetching falls back to an empty list for error responses`() {
        enqueue("""{"message":"404 File Not Found"}""", code = 404)
        assertNull(api.fetchRawFile(client(), 11L, "src/Missing.kt"))
    }

    @Test
    fun `repository listing keeps only readable blobs`() {
        enqueue(
            """
            [
              {"path":"src/Main.kt","type":"blob","name":"Main.kt"},
              {"path":"src/Helper.java","type":"blob","name":"Helper.java"},
              {"path":"docs/readme.md","type":"blob","name":"readme.md"},
              {"path":"src","type":"tree","name":"src"}
            ]
            """.trimIndent(),
        )
        enqueue("fun main() {}")
        enqueue("class Helper {}")
        server.enqueue(MockResponse().setResponseCode(200))

        val files = api.fetchRepoFiles(client(), 11L) { true }

        assertEquals(listOf("src/Main.kt", "src/Helper.java"), files.map { it.path })
        assertEquals("fun main() {}", files[0].content)
    }

    @Test
    fun `repository listing ignores files without a relevant path`() {
        enqueue("""[{"path":"assets/logo.png","type":"blob"},{"path":"App.kt","type":"blob"}]""")
        enqueue("val app = 1")
        val files = api.fetchRepoFiles(client(), 11L) { path -> path.endsWith(".kt") }
        assertEquals(listOf("App.kt"), files.map { it.path })
    }

    @Test
    fun `repository listing reports nothing when the tree cannot be read`() {
        assertEquals(emptyList<com.stacklens.application.port.out.JiraRepoFile>(), api.fetchRepoFiles(brokenClient(), 11L) { true })
    }
}
