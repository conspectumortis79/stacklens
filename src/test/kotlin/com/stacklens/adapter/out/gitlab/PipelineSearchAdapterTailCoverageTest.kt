package com.stacklens.adapter.out.gitlab

import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter.GitlabProject
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PipelineSearchAdapterTailCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: PipelineSearchAdapter
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("").toString().removeSuffix("/")
        server.dispatcher = QueueDispatcher().apply { setFailFast(true) }
        adapter =
            PipelineSearchAdapter(
                connectTimeoutMs = 1000,
                readTimeoutMs = 1000,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): GitlabConfig =
        GitlabConfig(
            id = 1L,
            name = "g",
            baseUrl = baseUrl,
            accessToken = "TOK",
            active = true,
            groupId = null,
        )

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun blobPage(size: Int): String =
        (1..size).joinToString(",", "[", "]") {
            """{"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":$it,"data":"stage: build"}"""
        }

    @Test
    fun `blob search and tree listing fall back to empty lists when the body is missing`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"))
        server.enqueue(
            json(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"))
        server.enqueue(json("[]"))
        val results = adapter.searchPipelines(config(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `blob search stops after five full pages`() {
        val seen = AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.startsWith("/api/v4/search")) {
                        seen.incrementAndGet()
                        return json(blobPage(100))
                    }
                    if (Regex("/api/v4/projects/\\d+/repository/tree").containsMatchIn(path)) {
                        return json("[]")
                    }
                    return json("[]")
                }
            }
        val results = adapter.searchPipelines(config(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(5, seen.get())
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `scanProjectCiFiles uses main when the project carries a null default branch`() {
        server.enqueue(json("[]"))
        server.enqueue(
            json(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u","default_branch":null}""",
            ),
        )
        server.enqueue(json("[]"))
        repeat(9) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/plain")
                    .setBody("build:\n  script: kw"),
            )
        }
        val results = adapter.searchPipelines(config(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(9, results.size)
        assertEquals("main", results.first().branch)
    }

    @Test
    fun `the project dto keeps its declared default branch when built in kotlin`() {
        assertEquals("dev", GitlabProject(1, "n", "g/n", "u", "dev").defaultBranch)
    }
}
