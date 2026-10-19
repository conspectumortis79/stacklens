package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.FileChangeType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabBranchCommitApiClientBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabBranchCommitApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            GitlabBranchCommitApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `stripMonorepoPrefix returns files unchanged when monorepoInfo is null`() {
        val files =
            listOf(
                com.stacklens.application.port.out.JiraCodeFile(
                    repository = "repo1",
                    path = "src/main/kotlin/Foo.kt",
                    language = "kotlin",
                    description = "Test",
                    changeType = FileChangeType.ADD,
                    code = "fun main() {}",
                ),
            )
        val result = client.stripMonorepoPrefix(files, null)
        assertEquals(files, result)
    }

    @Test
    fun `stripMonorepoPrefix strips the prefix from file paths`() {
        val files =
            listOf(
                com.stacklens.application.port.out.JiraCodeFile(
                    repository = "repo1",
                    path = "packages/foo/src/main/kotlin/Foo.kt",
                    language = "kotlin",
                    description = "Test",
                    changeType = FileChangeType.ADD,
                    code = "fun main() {}",
                ),
                com.stacklens.application.port.out.JiraCodeFile(
                    repository = "repo1",
                    path = "src/main/kotlin/Bar.kt",
                    language = "kotlin",
                    description = "Test",
                    changeType = FileChangeType.ADD,
                    code = "fun main() {}",
                ),
            )
        val mono =
            com.stacklens.application.port.out.JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "packages/foo",
                reasoning = "test",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = client.stripMonorepoPrefix(files, mono)
        assertEquals(2, result.size)
        assertEquals("src/main/kotlin/Foo.kt", result[0].path)
        assertEquals("src/main/kotlin/Bar.kt", result[1].path)
    }

    @Test
    fun `stripMonorepoPrefix returns files unchanged when prefix is blank`() {
        val files =
            listOf(
                com.stacklens.application.port.out.JiraCodeFile(
                    repository = "repo1",
                    path = "src/Foo.kt",
                    language = "kotlin",
                    description = "Test",
                    changeType = FileChangeType.ADD,
                    code = "x",
                ),
            )
        val mono =
            com.stacklens.application.port.out.JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "   ",
                reasoning = "test",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = client.stripMonorepoPrefix(files, mono)
        assertEquals(files, result)
    }

    @Test
    fun `fetchRawFile returns null for 404 response`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        val result = client.fetchRawFile(webClient, 1, "missing.txt")
        assertEquals(null, result)
    }

    @Test
    fun `fetchRawFile returns content for 200 response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("hello world"),
        )
        val webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        val result = client.fetchRawFile(webClient, 1, "file.txt")
        assertNotNull(result)
    }
}
