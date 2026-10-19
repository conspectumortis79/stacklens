package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraCodePatch
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

class GitlabBranchCommitApiClientFullTest {
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

    @Test
    fun `filterRepoFiles matches by simple repository name`() {
        val api =
            GitlabBranchCommitApiClient()
        val method =
            GitlabBranchCommitApiClient::class.java.getDeclaredMethod(
                "filterRepoFiles",
                List::class.java,
                String::class.java,
                com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto::class.java,
            )
        method.isAccessible = true

        val project =
            com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto(
                id = 1L,
                name = "n",
                pathWithNamespace = "grp/n",
            )
        val f1 =
            com.stacklens.application.port.out.JiraCodeFile(
                repository = "grp/n",
                path = "src/A.kt",
                language = "kotlin",
                description = "test",
                changeType = FileChangeType.ADD,
                code = "class A {}",
            )
        val f2 =
            com.stacklens.application.port.out.JiraCodeFile(
                repository = "grp/other",
                path = "src/B.kt",
                language = "kotlin",
                description = "test",
                changeType = FileChangeType.ADD,
                code = "class B {}",
            )
        val filtered =
            method.invoke(api, listOf(f1, f2), "n", project) as List<*>
        assertEquals(1, filtered.size)
        assertEquals("src/A.kt", (filtered[0] as com.stacklens.application.port.out.JiraCodeFile).path)
    }

    @Test
    fun `filterRepoFiles matches by path-with-namespace last segment`() {
        val api =
            GitlabBranchCommitApiClient()
        val method =
            GitlabBranchCommitApiClient::class.java.getDeclaredMethod(
                "filterRepoFiles",
                List::class.java,
                String::class.java,
                com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto::class.java,
            )
        method.isAccessible = true

        val project =
            com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto(
                id = 1L,
                name = "n",
                pathWithNamespace = "grp/target",
            )
        val f =
            com.stacklens.application.port.out.JiraCodeFile(
                repository = "grp/target/sub",
                path = "src/C.kt",
                language = "kotlin",
                description = "test",
                changeType = FileChangeType.ADD,
                code = "class C {}",
            )
        val filtered =
            method.invoke(api, listOf(f), "target", project) as List<*>
        assertEquals(1, filtered.size)
    }

    @Test
    fun `filterRepoFiles returns all files when no match found`() {
        val api =
            GitlabBranchCommitApiClient()
        val method =
            GitlabBranchCommitApiClient::class.java.getDeclaredMethod(
                "filterRepoFiles",
                List::class.java,
                String::class.java,
                com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto::class.java,
            )
        method.isAccessible = true
        val project =
            com.stacklens.adapter.out.gitlab.JiraBridgeGitlabProjectDto(
                id = 1L,
                name = "n",
                pathWithNamespace = "grp/n",
            )
        val f =
            com.stacklens.application.port.out.JiraCodeFile(
                repository = "grp/unrelated",
                path = "src/X.kt",
                language = "kotlin",
                description = "test",
                changeType = FileChangeType.ADD,
                code = "class X {}",
            )
        val filtered =
            method.invoke(api, listOf(f), "n", project) as List<*>
        assertEquals(1, filtered.size)
    }

    @Test
    fun `stripMonorepoPrefix returns input unchanged when monorepoInfo is null`() {
        val files =
            listOf(JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "kt", description = "", changeType = FileChangeType.NEW, code = "x"))
        val result = api.stripMonorepoPrefix(files, null)
        assertEquals(files, result)
    }

    @Test
    fun `stripMonorepoPrefix is a no-op when relativeRootPath is blank`() {
        val files =
            listOf(JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "kt", description = "", changeType = FileChangeType.NEW, code = "x"))
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "ui",
                relativeRootPath = "",
                reasoning = "",
                confidence = "HIGH",
                isMonorepo = false,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = api.stripMonorepoPrefix(files, mono)
        assertEquals(files, result)
    }

    @Test
    fun `stripMonorepoPrefix strips prefix when path starts with prefix slash`() {
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "packages/ui/src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "ui",
                relativeRootPath = "packages/ui",
                reasoning = "",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = api.stripMonorepoPrefix(files, mono)
        assertEquals("src/Foo.kt", result[0].path)
    }

    @Test
    fun `stripMonorepoPrefix trims trailing slash from prefix`() {
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "apps/web/src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "web",
                relativeRootPath = "apps/web/",
                reasoning = "",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = api.stripMonorepoPrefix(files, mono)
        assertEquals("src/Foo.kt", result[0].path)
    }

    @Test
    fun `stripMonorepoPrefix leaves file unchanged when path doesn't start with prefix`() {
        val files =
            listOf(JiraCodeFile(repository = "r", path = "src/Foo.kt", language = "kt", description = "", changeType = FileChangeType.NEW, code = "x"))
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "web",
                relativeRootPath = "apps/web",
                reasoning = "",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = api.stripMonorepoPrefix(files, mono)
        assertEquals("src/Foo.kt", result[0].path)
    }

    @Test
    fun `stripMonorepoPrefix handles files with same prefix in name but not path`() {
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "packages/ui/src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "ui2",
                relativeRootPath = "packages/ui2",
                reasoning = "",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        val result = api.stripMonorepoPrefix(files, mono)
        assertEquals("packages/ui/src/Foo.kt", result[0].path)
    }

    @Test
    fun `createBranch posts to the branches endpoint`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        api.createBranch(client, 42L, "feature/x", "main")
        val req = server.takeRequest()
        assertEquals("/api/v4/projects/42/repository/branches", req.path)
        assertEquals("POST", req.method)
    }

    @Test
    fun `createBranch swallows exceptions`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        api.createBranch(client, 42L, "feature/x", "main")
    }

    @Test
    fun `commitFiles maps NEW changeType to create action`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        api.commitFiles(client, 42L, "feature/x", "msg", files)
        val req = server.takeRequest()
        assertEquals("/api/v4/projects/42/repository/commits", req.path)
        val body = req.body.readUtf8()
        assertEquals(true, body.contains("\"action\":\"create\""))
    }

    @Test
    fun `commitFiles maps UPDATE changeType to update action`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.UPDATE,
                    code = "x",
                ),
            )
        api.commitFiles(client, 42L, "feature/x", "msg", files)
        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertEquals(true, body.contains("\"action\":\"update\""))
    }

    @Test
    fun `createMergeRequest throws when repository not found by ID`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found").setHeader("Content-Type", "application/json"))
        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val ticket = basicTicket()
        val impl = basicImpl()
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                api.createMergeRequest(cfg, "repo", ticket, impl, gitlabProjectId = 999L, monorepoInfo = null)
            }
        assertEquals(true, ex.message!!.contains("999"))
    }

    @Test
    fun `createMergeRequest throws when repository not found by name`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("not found")
                .setHeader("Content-Type", "application/json"),
        )
        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val ticket = basicTicket()
        val impl = basicImpl()
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                api.createMergeRequest(cfg, "missing-repo", ticket, impl, gitlabProjectId = null, monorepoInfo = null)
            }
        assertEquals(true, ex.message!!.contains("missing-repo"))
    }

    @Test
    fun `createMergeRequest uses main as default branch when project returns blank`() {
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
                    """{"id":42,"default_branch":"","name":"repo","path_with_namespace":"g/repo","web_url":"u"}""",
                ).setHeader("Content-Type", "application/json"),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """{"web_url":"https://gitlab.example.com/g/repo/-/merge_requests/1","iid":1}""",
                ).setHeader("Content-Type", "application/json"),
        )
        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result =
            api.createMergeRequest(cfg, "repo", basicTicket(), basicImpl(), gitlabProjectId = 42L, monorepoInfo = null)
        assertNotNull(result)
        server.takeRequest()
        server.takeRequest()
        val branchReq = server.takeRequest()
        assertEquals("POST", branchReq.method)
        assertTrue(branchReq.body.readUtf8().contains("\"ref\":\"main\""))
    }

    @Test
    fun `createMergeRequest succeeds with non-blank ticket description`() {
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
                .setBody(
                    """{"web_url":"https://gitlab.example.com/g/repo/-/merge_requests/1","iid":1}""",
                ).setHeader("Content-Type", "application/json"),
        )

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val ticket =
            basicTicket().copy(description = "This is a detailed description that exercises the description branch.")
        val impl = basicImpl()
        val result =
            api.createMergeRequest(cfg, "repo", ticket, impl, gitlabProjectId = 42L, monorepoInfo = null)
        assertNotNull(result)
    }

    @Test
    fun `createMergeRequest throws when no files match the repo`() {
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

        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("other-repo"),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                api.createMergeRequest(cfg, "repo", basicTicket(), impl, gitlabProjectId = 42L, monorepoInfo = null)
            }
        assertEquals(true, ex.message!!.contains("No files"))
    }

    @Test
    fun `commitDirectly commits to the default branch and returns the commit web url`() {
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
            MockResponse().setResponseCode(201).setBody("""{"web_url":"http://gl/commit"}""").setHeader("Content-Type", "application/json"),
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
        assertEquals("http://gl/commit", url)
        server.takeRequest()
        server.takeRequest()
        val commitReq = server.takeRequest()
        assertEquals("POST", commitReq.method)
        assertEquals("/api/v4/projects/42/repository/commits", commitReq.path)
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
    fun `fetchRawFile returns null when client errors via onErrorResume`() {
        val failingClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .exchangeFunction { _ ->
                    reactor.core.publisher.Mono
                        .error(RuntimeException("upstream error"))
                }.build()
        val api = GitlabBranchCommitApiClient()
        val result = api.fetchRawFile(failingClient, 1L, "README.md")
        assertNull(result)
    }

    @Test
    fun `fetchRawFile returns content from successful bodyToMono`() {
        val okClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .exchangeFunction { _ ->
                    reactor.core.publisher.Mono.just(
                        org.springframework.web.reactive.function.client.ClientResponse
                            .create(org.springframework.http.HttpStatus.OK)
                            .body("the file content")
                            .build(),
                    )
                }.build()
        val result = api.fetchRawFile(okClient, 1L, "README.md")
        assertEquals("the file content", result)
    }

    @Test
    fun `fetchRawFile catches Exception and returns null`() {
        val api = GitlabBranchCommitApiClient()
        val result = api.fetchRawFile(WebClient.builder().build(), 1L, "x".repeat(100))
        assertNull(result)
    }

    @Test
    fun `commitFiles applies patches when changeType is PATCH`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("original content").setHeader("Content-Type", "text/plain"),
        )
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"),
        )
        val files =
            listOf(
                JiraCodeFile(
                    repository = "r",
                    path = "src/Foo.kt",
                    language = "kt",
                    description = "",
                    changeType = FileChangeType.PATCH,
                    code = "unused",
                    patches =
                        listOf(
                            JiraCodePatch(search = "original content", replace = "patched content"),
                        ),
                ),
            )
        api.commitFiles(client, 42L, "feature/x", "msg", files)
        Thread.sleep(50)
    }
}
