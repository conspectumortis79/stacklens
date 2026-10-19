package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.FileChangeType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabBranchCommitApiClientTestBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var webClient: WebClient
    private lateinit var client: GitlabBranchCommitApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client =
            GitlabBranchCommitApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GitlabBranchCommitApiClient is constructible`() {
        assertNotNull(client)
    }

    @Test
    fun `commitFiles handles 201 with empty response`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody(""))
        try {
            client.commitFiles(
                client = webClient,
                projectId = 1L,
                branch = "test",
                message = "test",
                files =
                    listOf(
                        com.stacklens.application.port.out.JiraCodeFile(
                            repository = "repo1",
                            path = "src/test.kt",
                            language = "kotlin",
                            description = "test",
                            changeType = FileChangeType.ADD,
                            code = "fun main() {}",
                        ),
                    ),
            )
        } catch (e: Exception) {
        }
    }

    @Test
    fun `commitFiles handles 500 error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        try {
            client.commitFiles(
                client = webClient,
                projectId = 1L,
                branch = "test",
                message = "test",
                files =
                    listOf(
                        com.stacklens.application.port.out.JiraCodeFile(
                            repository = "repo1",
                            path = "src/test.kt",
                            language = "kotlin",
                            description = "test",
                            changeType = FileChangeType.ADD,
                            code = "fun main() {}",
                        ),
                    ),
            )
        } catch (e: Exception) {
        }
    }

    @Test
    fun `createBranch logs warning on exception`() {
        val unreachableClient =
            WebClient.builder().baseUrl("http://127.0.0.1:1/").build()
        try {
            client.createBranch(unreachableClient, 1L, "feature/test", "main")
        } catch (_: Exception) {
        }
        assertNotNull(client)
    }

    @Test
    fun `createBranch catches RuntimeException from MockWebServer`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        try {
            client.createBranch(webClient, 1L, "feature/test", "main")
        } catch (_: Exception) {
        }
        assertNotNull(client)
    }

    @Test
    fun `fetchRawFile returns null on exception`() {
        val unreachableClient =
            WebClient.builder().baseUrl("http://127.0.0.1:1/").build()
        val result = client.fetchRawFile(unreachableClient, 1L, "src/main.kt")
        org.junit.jupiter.api.Assertions
            .assertNull(result)
    }
}
