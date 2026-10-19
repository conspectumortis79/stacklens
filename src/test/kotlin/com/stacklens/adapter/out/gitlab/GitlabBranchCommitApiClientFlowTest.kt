package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraCodePatch
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI

class GitlabBranchCommitApiClientFlowTest {
    private val server = MockWebServer()
    private val client = GitlabBranchCommitApiClient()

    private fun config() =
        GitlabConfig(
            name = "gl",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "secret",
        )

    private fun body(json: String) = MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(json)

    private fun ticket() =
        JiraTicketData(
            key = "PROJ-1",
            summary = "Implement the widget",
            description = "details",
            comments = "",
            issueType = "Story",
            status = "To Do",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/browse/PROJ-1",
        )

    private fun code(
        vararg files: JiraCodeFile,
        repositories: List<String> = listOf("shop"),
    ) = JiraCodeImplementation(
        identifiedRepositories = repositories,
        identifiedDatabases = emptyList(),
        files = files.toList(),
        testFiles = emptyList(),
        databaseChanges = emptyList(),
        implementationNotes = "",
    )

    private fun file(
        repository: String,
        path: String,
        changeType: FileChangeType = FileChangeType.NEW,
        patches: List<JiraCodePatch> = emptyList(),
    ) = JiraCodeFile(
        repository = repository,
        path = path,
        language = "kotlin",
        description = "",
        changeType = changeType,
        code = "content",
        patches = patches,
    )

    @Test
    fun `merge request creation resolves the project by id and returns the web url`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"trunk"}"""))
        server.enqueue(body("""{"name":"PROJ-1"}"""))
        server.enqueue(body("""{"id":99}"""))
        server.enqueue(body("""{"web_url":"http://gitlab/mr/1"}"""))

        val url =
            client.createMergeRequest(
                config(),
                "acme/shop",
                ticket(),
                code(file("other/repo", "src/A.kt")),
                gitlabProjectId = 7L,
                monorepoInfo = null,
            )

        assertEquals("http://gitlab/mr/1", url)
    }

    @Test
    fun `a project id that gitlab does not know aborts with an explanatory error`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                client.createMergeRequest(config(), "shop", ticket(), code(), gitlabProjectId = 7L, monorepoInfo = null)
            }

        assertEquals(true, failure.message?.contains("Repository with ID 7"))
    }

    @Test
    fun `an unknown project name aborts with an explanatory error`() {
        server.enqueue(body("[]"))

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                client.commitDirectly(config(), "missing", ticket(), code(), gitlabProjectId = null, monorepoInfo = null)
            }

        assertEquals(true, failure.message?.contains("Repository 'missing'"))
    }

    @Test
    fun `a commit run without any generated file aborts before touching the repository`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"   "}"""))
        server.enqueue(body("""{"name":"PROJ-1"}"""))

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                client.commitDirectly(config(), "shop", ticket(), code(), gitlabProjectId = 7L, monorepoInfo = null)
            }

        assertEquals(true, failure.message?.contains("No files found"))
    }

    @Test
    fun `a generated file from a different repository still reaches gitlab`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"main"}"""))
        server.enqueue(body("""{"web_url":"http://gitlab/commit/3"}"""))

        assertEquals(
            "http://gitlab/commit/3",
            client.commitDirectly(
                config(),
                "shop",
                ticket(),
                code(file("acme/other", "src/A.kt", FileChangeType.UPDATE), repositories = listOf("ignored")),
                7L,
                JiraMonorepoIdentification(
                    identifiedApplication = "other",
                    relativeRootPath = "/",
                    reasoning = "",
                    confidence = "low",
                    isMonorepo = false,
                    techStack = emptyList(),
                    codingConventions = "",
                    testFramework = "",
                    buildTool = "",
                    testCommand = "",
                ),
            ),
        )
    }

    @Test
    fun `patch changes are fetched, applied and committed with a monorepo prefix stripped`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"main"}"""))
        server.enqueue(body("""{"name":"PROJ-1"}"""))
        server.enqueue(body("\"old-value\""))
        server.enqueue(body("""{"id":99}"""))
        server.enqueue(body("""{"web_url":"http://gitlab/mr/2"}"""))

        val patch = JiraCodePatch(search = "old-value", replace = "new-value")
        val files =
            code(
                file("apps/shop", "src/A.kt", FileChangeType.PATCH, listOf(patch)),
                file("apps/shop", "src/B.kt"),
            )
        val monorepo =
            JiraMonorepoIdentification(
                identifiedApplication = "shop",
                relativeRootPath = "apps/shop/",
                reasoning = "",
                confidence = "high",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )

        assertEquals("http://gitlab/mr/2", client.createMergeRequest(config(), "shop", ticket(), files, 7L, monorepo))
        assertEquals(6, server.requestCount)
        val paths = (1..6).map { URI(server.takeRequest().path.orEmpty()).rawPath }
        assertEquals("/api/v4/projects/7/repository/files/src%252FA.kt/raw", paths[3])
        assertEquals("/api/v4/projects/7/repository/commits", paths[4])
    }

    @Test
    fun `an mr endpoint without a body surfaces an illegal state error`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"main"}"""))
        server.enqueue(body("""{"name":"PROJ-1"}"""))
        server.enqueue(body("""{"id":99}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertThrows(IllegalStateException::class.java) {
            client.createMergeRequest(config(), "shop", ticket(), code(file("shop", "src/A.kt")), 7L, null)
        }
    }

    @Test
    fun `a commit endpoint without a body surfaces an illegal state error`() {
        server.enqueue(body("""{"id":7,"name":"Shop","path_with_namespace":"acme/shop"}"""))
        server.enqueue(body("""{"id":7,"default_branch":"main"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertThrows(IllegalStateException::class.java) {
            client.commitDirectly(config(), "shop", ticket(), code(file("shop", "src/A.kt")), 7L, null)
        }
    }

    @Test
    fun `branch creation logs a warning when the uri cannot be built`() {
        val broken = brokenClient()

        client.createBranch(broken, 1L, "PROJ-1", "main")

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `raw file fetch returns null when the uri cannot be built`() {
        assertEquals(null, client.fetchRawFile(brokenClient(), 1L, "src/A.kt"))
    }

    private fun brokenClient(): WebClient =
        WebClient
            .builder()
            .uriBuilderFactory(ThrowingUriBuilderFactory)
            .build()

    private object ThrowingUriBuilderFactory : org.springframework.web.util.DefaultUriBuilderFactory("http://localhost:1") {
        override fun uriString(uriTemplate: String): org.springframework.web.util.UriBuilder = throw IllegalStateException("no uri")
    }
}
