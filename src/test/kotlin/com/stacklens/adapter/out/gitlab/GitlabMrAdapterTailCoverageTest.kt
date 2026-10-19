package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class GitlabMrAdapterTailCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GitlabMrAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GitlabMrAdapter(connectTimeoutMs = 2000, readTimeoutMs = 2000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() =
        GitlabConfig(
            name = "gl",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "secret",
        )

    private fun emptyJson(): MockResponse = MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    @Test
    fun `fetchProjectDetail falls back to an empty dto when the body is missing`() {
        server.enqueue(emptyJson())
        assertEquals(0L, adapter.fetchProjectDetail(config(), 1L).id)
    }

    @Test
    fun `createMergeRequest fails when the response body is missing`() {
        server.enqueue(emptyJson())
        assertThrows(IllegalStateException::class.java) {
            adapter.createMergeRequest(config(), 1L, "src", "main", "title", "desc")
        }
    }
}
