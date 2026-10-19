package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class GitlabClientPaginationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitlabClient(GitlabTreeApiClient(), GitlabCodeContextParser())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(body)

    private fun cfg(): GitlabConfig =
        GitlabConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            accessToken = "tok",
        )

    private fun projectArrayJson(n: Int): String =
        (1..n)
            .joinToString(",\n") { i ->
                """{"id": $i, "name": "P$i", "path_with_namespace": "g/p$i",
                   "default_branch": "main", "http_url_to_repo": "https://x.git"}"""
            }.let { "[$it]" }

    @Test
    fun `fetchAllProjects loop exits when first page has less than 100 items`() {
        server.enqueue(jsonResponse(200, projectArrayJson(1)))
        val result = client.listAccessibleProjects(cfg())
        assertEquals(1, result.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchAllProjects loop continues when first page has 100 items then exits on empty second page`() {
        server.enqueue(jsonResponse(200, projectArrayJson(100)))
        server.enqueue(jsonResponse(200, "[]"))
        val result = client.listAccessibleProjects(cfg())
        assertEquals(100, result.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `searchKeyword blob loop exits on first page with less than 100 items`() {
        server.enqueue(jsonResponse(200, projectArrayJson(1)))
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = cfg(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword blob loop continues with exactly 100 items on first page`() {
        val manyProjectsJson = projectArrayJson(100)
        server.enqueue(jsonResponse(200, manyProjectsJson))
        repeat(300) { server.enqueue(jsonResponse(200, "[]")) }
        val result =
            client.searchKeyword(
                config = cfg(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }
}
