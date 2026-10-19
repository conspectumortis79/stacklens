package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitlabClientSearchBlobsTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitlabClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "g",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "TOK",
        )

    @Test
    fun `searchKeyword returns empty when blob search returns 429 (rate-limited)`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u","default_branch":"main"}]""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword falls back to tree-walk when blob search returns 4xx (other than 429)`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u","default_branch":"main"}]""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":"a","name":"x.txt","type":"blob","path":"x.txt"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("contains x"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword continues when blob search returns a non-429, non-4xx HTTP error`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u","default_branch":"main"}]""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }
}
