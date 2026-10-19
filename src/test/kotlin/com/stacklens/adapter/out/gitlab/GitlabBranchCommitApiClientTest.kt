package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabBranchCommitApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabBranchCommitApiClient
    private lateinit var webClientBuilder: WebClient.Builder

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        webClientBuilder = WebClient.builder().clientConnector(connector)
        client = GitlabBranchCommitApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
            active = true,
        )

    private fun ticketData() =
        JiraTicketData(
            key = "PROJ-123",
            summary = "Add feature",
            description = "Long description of the feature.",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = "Medium",
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/PROJ-123",
        )

    private fun codeImpl(repo: String = "my-repo"): JiraCodeImplementation {
        val files =
            listOf(
                JiraCodeFile(
                    repository = repo,
                    path = "src/Foo.kt",
                    language = "Kotlin",
                    description = "New file",
                    changeType = FileChangeType.NEW,
                    code = "class Foo",
                ),
            )
        return JiraCodeImplementation(
            identifiedRepositories = listOf(repo),
            identifiedDatabases = emptyList(),
            files = files,
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )
    }

    private fun monoInfo(relativeRootPath: String): JiraMonorepoIdentification =
        JiraMonorepoIdentification(
            identifiedApplication = "core",
            relativeRootPath = relativeRootPath,
            reasoning = "test",
            confidence = "HIGH",
            isMonorepo = true,
            techStack = emptyList(),
            codingConventions = "",
            testFramework = "",
            buildTool = "",
            testCommand = "",
        )

    @Test
    fun `stripMonorepoPrefix is a no-op when monorepoInfo is null`() {
        val files =
            listOf(
                JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "Kotlin", description = "x", changeType = FileChangeType.NEW, code = "x"),
            )
        val out = client.stripMonorepoPrefix(files, null)
        assertEquals(files, out)
    }

    @Test
    fun `stripMonorepoPrefix is a no-op when relativeRootPath is empty`() {
        val files =
            listOf(
                JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "Kotlin", description = "x", changeType = FileChangeType.NEW, code = "x"),
            )
        val out = client.stripMonorepoPrefix(files, monoInfo(""))
        assertEquals(files, out)
    }

    @Test
    fun `stripMonorepoPrefix is a no-op when relativeRootPath is only slashes`() {
        val files =
            listOf(
                JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "Kotlin", description = "x", changeType = FileChangeType.NEW, code = "x"),
            )
        val out = client.stripMonorepoPrefix(files, monoInfo("///"))
        assertEquals(files, out)
    }

    @Test
    fun `stripMonorepoPrefix strips the prefix when path starts with prefix`() {
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "packages/core/src/Foo.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val out = client.stripMonorepoPrefix(files, monoInfo("packages/core/"))
        assertEquals(listOf("src/Foo.kt"), out.map { it.path })
    }

    @Test
    fun `stripMonorepoPrefix trims trailing slash from prefix`() {
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "packages/core/src/Foo.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val out = client.stripMonorepoPrefix(files, monoInfo("packages/core///"))
        assertEquals(listOf("src/Foo.kt"), out.map { it.path })
    }

    @Test
    fun `stripMonorepoPrefix returns file unchanged when path does not start with prefix`() {
        val files =
            listOf(
                JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "Kotlin", description = "x", changeType = FileChangeType.NEW, code = "x"),
            )
        val out = client.stripMonorepoPrefix(files, monoInfo("packages/core"))
        assertEquals(listOf("src/Foo.kt"), out.map { it.path })
    }

    @Test
    fun `stripMonorepoPrefix returns new instance with different path reference`() {
        val original =
            JiraCodeFile(
                repository = "r",
                path = "packages/core/src/Foo.kt",
                language = "Kotlin",
                description = "x",
                changeType = FileChangeType.NEW,
                code = "x",
            )
        val out = client.stripMonorepoPrefix(listOf(original), monoInfo("packages/core/"))
        assertNotEquals(original, out[0])
        assertEquals("src/Foo.kt", out[0].path)
    }

    @Test
    fun `stripMonorepoPrefix returns same instance when path unchanged`() {
        val original =
            JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "Kotlin", description = "x", changeType = FileChangeType.NEW, code = "x")
        val out = client.stripMonorepoPrefix(listOf(original), monoInfo("packages/core/"))
        assertEquals(original, out[0])
    }

    private fun directClient(): WebClient {
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        return WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(connector)
            .build()
    }

    @Test
    fun `createBranch posts to the branches endpoint with branch and ref in JSON body`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        client.createBranch(directClient(), projectId = 42L, branch = "PROJ-1", ref = "main")
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/api/v4/projects/42/repository/branches"))
        assertTrue(body.contains("PROJ-1"), "expected branch name in body: $body")
        assertTrue(body.contains("main"), "expected ref name in body: $body")
    }

    @Test
    fun `createBranch swallows exceptions and logs warning`() {
        server.enqueue(MockResponse().setResponseCode(500))
        client.createBranch(directClient(), projectId = 42L, branch = "PROJ-1", ref = "main")
    }

    @Test
    fun `commitFiles encodes NEW files as create action`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Foo.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.NEW,
                    code = "class Foo",
                ),
            )
        client.commitFiles(directClient(), projectId = 42L, branch = "PROJ-1", message = "msg", files = files)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"action\""))
        assertTrue(body.contains("create"))
        assertTrue(body.contains("src/Foo.kt"))
    }

    @Test
    fun `commitFiles encodes UPDATE files as update action`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Bar.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.MODIFIED,
                    code = "x",
                ),
            )
        client.commitFiles(directClient(), projectId = 42L, branch = "b", message = "m", files = files)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("update"))
    }

    @Test
    fun `commitFiles fetches raw file when changeType is PATCH and patches are non-empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("original content").setHeader("Content-Type", "text/plain"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Baz.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.PATCH,
                    code = "new",
                    patches =
                        listOf(
                            com.stacklens.application.port.out
                                .JiraCodePatch("-old", "+new"),
                        ),
                ),
            )
        client.commitFiles(directClient(), projectId = 42L, branch = "b", message = "m", files = files)
        assertEquals(2, server.requestCount)
        val firstReq = server.takeRequest()
        assertEquals("GET", firstReq.method)
        val secondReq = server.takeRequest()
        assertEquals("POST", secondReq.method)
        assertTrue(secondReq.path!!.contains("/repository/commits"))
    }

    @Test
    fun `commitFiles treats PATCH without patches as plain update`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Baz.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.PATCH,
                    code = "new",
                ),
            )
        client.commitFiles(directClient(), projectId = 42L, branch = "b", message = "m", files = files)
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("update"))
    }
}
