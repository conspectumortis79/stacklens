package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabMergeRequestApiClientFailureBranchTest {
    private lateinit var server: MockWebServer

    private val api = GitlabMergeRequestApiClient()

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
        whenever(broken.get()).thenThrow(IllegalStateException("connection reset"))
        return broken
    }

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    @Test
    fun `mr search reports transport failures as no results`() {
        assertEquals(emptyList<JiraBridgeGitlabMrDto>(), api.searchMrs(brokenClient(), 7L, "login"))
    }

    @Test
    fun `mr search treats an empty body as no results`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(emptyList<JiraBridgeGitlabMrDto>(), api.searchMrs(client(), 7L, "login", "opened"))
    }

    @Test
    fun `mr search resumes from error responses with an empty list`() {
        enqueue("""{"message":"500 Internal Server Error"}""", code = 500)
        assertEquals(emptyList<JiraBridgeGitlabMrDto>(), api.searchMrs(client(), 7L, "login"))
    }

    @Test
    fun `mr search returns the decoded merge requests`() {
        enqueue("""[{"iid":12,"title":"Add login","state":"merged","web_url":"https://gitlab/mr/12"}]""")
        val results = api.searchMrs(client(), 7L, "log in?")
        assertEquals(listOf(12L), results.map { it.iid })
    }

    @Test
    fun `mr changes report transport failures as no changes`() {
        assertEquals(emptyList<com.stacklens.application.port.out.JiraMrFileChange>(), api.fetchMrChanges(brokenClient(), 7L, 3L))
    }

    @Test
    fun `mr changes treat an empty body as no changes`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(api.fetchMrChanges(client(), 7L, 3L).isEmpty())
    }

    @Test
    fun `mr changes resume error responses to no changes`() {
        enqueue("""{"message":"boom"}""", code = 503)
        assertTrue(api.fetchMrChanges(client(), 7L, 3L).isEmpty())
    }

    @Test
    fun `mr changes keep the new path and fall back to the old path`() {
        enqueue(
            """
            {"changes":[
              {"new_path":"src/A.kt","old_path":"src/A.kt","diff":"+a"},
              {"new_path":"","old_path":"src/B.kt","diff":"${"-".repeat(700)}"},
              {"new_path":"src/C.kt","old_path":"src/C.kt"}
            ]}
            """.trimIndent(),
        )
        val changes = api.fetchMrChanges(client(), 7L, 3L)
        assertEquals(listOf("src/A.kt", "src/B.kt", "src/C.kt"), changes.map { it.filePath })
        assertEquals("+a", changes[0].diffExcerpt)
        assertEquals(600, changes[1].diffExcerpt.length)
        assertEquals("", changes[2].diffExcerpt)
    }
}
