package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabClientBranchCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var treeClient: GitlabTreeApiClient
    private lateinit var codeParser: GitlabCodeContextParser
    private lateinit var client: GitlabClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClientBuilder =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024) }
        treeClient = GitlabTreeApiClient()
        codeParser = GitlabCodeContextParser()
        client =
            GitlabClient(
                treeClient = treeClient,
                codeParser = codeParser,
            )
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
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "test-token",
        )

    @Test
    fun `fetchProjectFilesForPath uses main when project has no default_branch field`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "name": "P"}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath uses main when default_branch is null`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": null}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath uses main when default_branch is empty string`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": ""}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFiles returns empty when project is not in the list`() {
        server.enqueue(jsonResponse(200, "[]"))
        val result = client.fetchProjectFiles(gitlabConfig(), 999L, 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listAccessibleProjects throws when API returns 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))
        assertThrows(RuntimeException::class.java) {
            client.listAccessibleProjects(gitlabConfig())
        }
    }
}
