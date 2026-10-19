package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

class GitlabBranchCommitApiClientExtraTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebClient
    private lateinit var api: GitlabBranchCommitApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            org.springframework.http.client.reactive.ReactorClientHttpConnector(
                reactor.netty.http.client.HttpClient
                    .create()
                    .responseTimeout(Duration.ofSeconds(5)),
            )
        val baseUrl = server.url("").toString().removeSuffix("/")
        client =
            WebClient
                .builder()
                .clientConnector(connector)
                .baseUrl(baseUrl)
                .build()
        api = GitlabBranchCommitApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun basicTicket() =
        JiraTicketData(
            key = "PROJ-1",
            summary = "Add login",
            description = "",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/PROJ-1",
        )

    private fun basicImpl() =
        JiraCodeImplementation(
            identifiedRepositories = listOf("repo"),
            identifiedDatabases = emptyList(),
            files =
                listOf(
                    JiraCodeFile(
                        repository = "repo",
                        path = "src/Foo.kt",
                        language = "kt",
                        description = "",
                        changeType = FileChangeType.NEW,
                        code = "x",
                    ),
                ),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    @Test
    fun `commitDirectly commits to the non-blank default branch without creating a branch or MR`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"develop","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"develop","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"web_url":"http://gl/commit-with-develop"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val url =
            api.commitDirectly(
                cfg,
                repoName = "repo",
                ticket = basicTicket(),
                codeImpl = basicImpl(),
                gitlabProjectId = 42L,
                monorepoInfo = null,
            )
        assertEquals("http://gl/commit-with-develop", url)

        server.takeRequest()
        server.takeRequest()
        val commitReq = server.takeRequest()
        assertEquals("POST", commitReq.method)
        assertEquals("/api/v4/projects/42/repository/commits", commitReq.path)
        assertTrue(commitReq.body.readUtf8().contains("\"branch\":\"develop\""))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `createMergeRequest uses non-blank default branch when project has one`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"develop","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"develop","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"web_url":"http://gl/mr-develop"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val url =
            api.createMergeRequest(
                cfg,
                repoName = "repo",
                ticket = basicTicket(),
                codeImpl = basicImpl(),
                gitlabProjectId = 42L,
                monorepoInfo = null,
            )
        assertEquals("http://gl/mr-develop", url)
    }

    @Test
    fun `createMergeRequest opens MR via resolveProjectByNameOrPath when gitlabProjectId is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"main","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"main","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"web_url":"http://gl/by-name"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val url =
            api.createMergeRequest(
                cfg,
                repoName = "g/repo",
                ticket = basicTicket(),
                codeImpl = basicImpl(),
                gitlabProjectId = null,
                monorepoInfo = null,
            )
        assertEquals("http://gl/by-name", url)
    }

    @Test
    fun `commitDirectly commits via resolveProjectByNameOrPath when gitlabProjectId is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"main","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":42,"default_branch":"main","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"web_url":"http://gl/commit-by-name"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val url =
            api.commitDirectly(
                cfg,
                repoName = "g/repo",
                ticket = basicTicket(),
                codeImpl = basicImpl(),
                gitlabProjectId = null,
                monorepoInfo = null,
            )
        assertEquals("http://gl/commit-by-name", url)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `fetchRawFile returns null when response body throws during read`() {
        val failing =
            WebClient
                .builder()
                .exchangeFunction { _ ->
                    reactor.core.publisher.Mono
                        .error(RuntimeException("forced"))
                }.build()
        val result = api.fetchRawFile(failing, 1L, "README.md")
        assertEquals(null, result)
    }

    private fun assertTrue(condition: Boolean) {
        if (!condition) throw AssertionError("Expected true")
    }
}
