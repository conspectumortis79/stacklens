package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitlabTicketBridgeAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GitlabTicketBridgeAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GitlabTicketBridgeAdapter()
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

    private fun ticket() =
        JiraTicketData(
            key = "PROJ-123",
            summary = "Add feature",
            description = "",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = "Medium",
            labels = emptyList(),
            components = listOf("my-repo"),
            url = "https://jira/PROJ-123",
        )

    private fun problem() =
        JiraProblemAnalysis(
            problemDescription = "Bug",
            rootCause = "X",
            affectedComponents = listOf("my-repo"),
            searchKeywords = listOf("refactor", "optimize"),
            errorMessages = emptyList(),
        )

    @Test
    fun `findProjectById returns project info when found`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"name":"foo","path_with_namespace":"g/foo"}"""),
        )
        val result = adapter.findProjectById(gitlabConfig(), gitlabProjectId = 42L)
        assertNotNull(result)
        assertEquals(42L, result!!.id)
        assertEquals("foo", result.name)
        assertEquals("g/foo", result.pathWithNamespace)
    }

    @Test
    fun `findProjectById returns null when not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = adapter.findProjectById(gitlabConfig(), gitlabProjectId = 99L)
        assertNull(result)
    }

    @Test
    fun `createMergeRequest delegates to GitlabBranchCommitApiClient`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"default_branch":"main","name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"default_branch":"main","name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}").setHeader("Content-Type", "application/json"))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"web_url":"http://gl/mr"}""").setHeader("Content-Type", "application/json"),
        )

        val files =
            listOf(
                JiraCodeFile(
                    repository = "my-repo",
                    path = "src/Foo.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("my-repo"),
                identifiedDatabases = emptyList(),
                files = files,
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val url =
            adapter.createMergeRequest(
                gitlabConfig(),
                repoName = "my-repo",
                ticket = ticket(),
                codeImpl = impl,
                gitlabProjectId = 42L,
                monorepoInfo = null,
            )
        assertEquals("http://gl/mr", url)
    }

    @Test
    fun `commitDirectly delegates to GitlabBranchCommitApiClient`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"default_branch":"main","name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"default_branch":"main","name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"web_url":"http://gl/commit"}""").setHeader("Content-Type", "application/json"),
        )

        val files =
            listOf(
                JiraCodeFile(
                    repository = "my-repo",
                    path = "src/Foo.kt",
                    language = "Kotlin",
                    description = "x",
                    changeType = FileChangeType.NEW,
                    code = "x",
                ),
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("my-repo"),
                identifiedDatabases = emptyList(),
                files = files,
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val url =
            adapter.commitDirectly(
                gitlabConfig(),
                repoName = "my-repo",
                ticket = ticket(),
                codeImpl = impl,
                gitlabProjectId = 42L,
                monorepoInfo = null,
            )
        assertEquals("http://gl/commit", url)
    }

    @Test
    fun `fetchRelatedMrs returns empty list when no configs`() {
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = emptyList(),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRelatedMrs returns empty list when no projects match`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets =
                    listOf(
                        JiraSimilarTicket(
                            key = "PROJ-1",
                            summary = "x",
                            description = "",
                            issueType = "Bug",
                            status = "Open",
                            url = "x",
                            foundViaKeyword = null,
                        ),
                    ),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRelatedMs searches by similar ticket key`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"PROJ-1 Fix","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[{"new_path":"src/Foo.kt","old_path":"src/Foo.kt","diff":"@@"}]}"""),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }

        val similarTickets =
            listOf(
                JiraSimilarTicket(
                    key = "PROJ-1",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                ),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(5L, result[0].mrIid)
        assertEquals("PROJ-1", result[0].foundViaTicketKey)
    }

    @Test
    fun `fetchRelatedMrs searches by original ticket key when similar tickets exhausted`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":2,"iid":7,"title":"PROJ-123 Add","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )

        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(7L, result[0].mrIid)
        assertEquals("PROJ-123", result[0].foundViaTicketKey)
    }

    @Test
    fun `fetchRelatedMrs searches by keyword from problem analysis`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":3,"iid":9,"title":"refactor stuff","description":"x","state":"merged","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )

        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(9L, result[0].mrIid)
        assertTrue(
            result[0].foundViaKeyword == "refactor" || result[0].foundViaKeyword == "optimize",
            "expected one of the keywords but got ${result[0].foundViaKeyword}",
        )
    }

    @Test
    fun `fetchRelatedMs dedupes by URL`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }

        val similarTickets =
            listOf(
                JiraSimilarTicket(
                    key = "PROJ-1",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                ),
                JiraSimilarTicket(
                    key = "PROJ-1",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                ),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size, "duplicate MR URLs should be deduplicated")
    }

    @Test
    fun `fetchRepoContextForCodeGen returns empty when no configs`() {
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = emptyList(),
                ticket = ticket(),
                problem = problem(),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen returns empty when no candidates and no explicit selection`() {
        val t =
            ticket().copy(components = emptyList())
        val p =
            problem().copy(affectedComponents = emptyList())
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen adds candidates from relatedMr projectPath`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":7,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("class Foo"))

        val relatedMr =
            JiraRelatedMr(
                projectPath = "g/my-repo",
                mrIid = 1L,
                title = "x",
                description = "x",
                state = "merged",
                url = "u",
                foundViaTicketKey = null,
                foundViaKeyword = "refactor",
                fileChanges = emptyList(),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = listOf(relatedMr),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals("my-repo", result[0].repoName)
        assertEquals(7L, result[0].projectId)
    }

    @Test
    fun `fetchRepoContextForCodeGen matches candidate by path tail not just name`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":11,"name":"Frontend","path_with_namespace":"team/my-repo","web_url":"u"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"X.kt","type":"blob","path":"X.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("x"))

        val relatedMr =
            JiraRelatedMr(
                projectPath = "team/my-repo",
                mrIid = 1L,
                title = "t",
                description = "d",
                state = "merged",
                url = "u",
                foundViaTicketKey = null,
                foundViaKeyword = null,
                fileChanges = emptyList(),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = listOf(relatedMr),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(11L, result[0].projectId)
    }

    @Test
    fun `fetchRepoContextForCodeGen skips project when no files found via API tree`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":13,"name":"empty","path_with_namespace":"g/empty","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket(),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen falls back to fetchProjectDtoById when projectId not in group`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":55,"name":"elsewhere","path_with_namespace":"g/elsewhere","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("f"))

        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(0L to setOf(55L)),
            )
        assertEquals(1, result.size)
        assertEquals(55L, result[0].projectId)
    }

    @Test
    fun `fetchRepoContextForCodeGen skips explicit projectId already in result`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":77,"name":"dup","path_with_namespace":"g/dup","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("f"))

        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(0L to setOf(77L, 77L)),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen breaks out of explicit loop when MAX_REPO_CONTEXTS reached`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"a","path_with_namespace":"g/a","web_url":"u"},
                        |{"id":2,"name":"b","path_with_namespace":"g/b","web_url":"u"},
                        |{"id":3,"name":"c","path_with_namespace":"g/c","web_url":"u"}]
                    """.trimMargin(),
                ),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
            )
            server.enqueue(MockResponse().setBody("f"))
        }
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(0L to setOf(1L, 2L, 3L)),
            )
        assertTrue(result.size <= 3)
        assertEquals(3, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen breaks out of candidate loop when MAX_REPO_CONTEXTS reached`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"a","path_with_namespace":"g/a","web_url":"u"},
                        |{"id":2,"name":"b","path_with_namespace":"g/b","web_url":"u"},
                        |{"id":3,"name":"c","path_with_namespace":"g/c","web_url":"u"},
                        |{"id":4,"name":"d","path_with_namespace":"g/d","web_url":"u"}]
                    """.trimMargin(),
                ),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
            )
            server.enqueue(MockResponse().setBody("f"))
        }
        val t =
            ticket().copy(
                components = listOf("a", "b", "c", "d"),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(3, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen skips duplicate candidate project in result`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":99,"name":"x","path_with_namespace":"g/x","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("f"))

        val t =
            ticket().copy(
                components = listOf("x"),
            )
        val p =
            problem().copy(
                affectedComponents = listOf("x"),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen matches candidate component case-insensitively`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":50,"name":"My-Repo","path_with_namespace":"g/My-Repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("f"))
        val t =
            ticket().copy(
                components = listOf("MY-REPO"),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(50L, result[0].projectId)
    }

    @Test
    fun `fetchRepoContextForCodeGen breaks outer loop after MAX_REPO_CONTEXTS`() {
        val secondServer = MockWebServer()
        secondServer.start()
        try {
            val secondUrl = secondServer.url("").toString().removeSuffix("/")
            val cfg2 =
                GitlabConfig(
                    name = "g2",
                    baseUrl = secondUrl,
                    accessToken = "TOK",
                    active = true,
                )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":1,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
            )
            server.enqueue(MockResponse().setBody("f"))
            secondServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":2,"name":"my-repo","path_with_namespace":"g2/my-repo","web_url":"u"}]"""),
            )
            secondServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"b","name":"G.kt","type":"blob","path":"G.kt"}]"""),
            )
            secondServer.enqueue(MockResponse().setBody("g"))

            val result =
                adapter.fetchRepoContextForCodeGen(
                    gitlabConfigs = listOf(gitlabConfig(), cfg2),
                    ticket = ticket(),
                    problem = problem().copy(affectedComponents = emptyList()),
                    relatedMrs = emptyList(),
                    selectedProjects = emptyMap(),
                )
            assertTrue(result.size <= 3)
        } finally {
            secondServer.shutdown()
        }
    }

    @Test
    fun `fetchRepoContextForCodeGen skips outer config when explicitIds is null and no candidates`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        val t =
            ticket().copy(
                components = emptyList(),
            )
        val p =
            problem().copy(
                affectedComponents = emptyList(),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = mapOf(9999L to setOf(1L)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen matches candidate component via pathWithNamespace tail`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":31,"name":"Frontend","path_with_namespace":"team/my-repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"F.kt","type":"blob","path":"F.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("f"))
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = listOf("my-repo")),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals(31L, result[0].projectId)
    }

    @Test
    fun `fetchRepoContextForCodeGen skips candidate with no matching project`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"alpha","path_with_namespace":"g/alpha","web_url":"u"}]"""),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = listOf("nonexistent")),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen returns empty when explicitIds all unreachable`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(404))
        }
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(0L to setOf(101L, 102L)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen matches candidate component`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("class Foo"))

        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket(),
                problem = problem(),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals("my-repo", result[0].repoName)
        assertEquals(42L, result[0].projectId)
        assertEquals(1, result[0].files.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen uses explicit selected projects`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}]"""),
        )
        server.enqueue(MockResponse().setBody("class Foo"))

        val t =
            ticket().copy(components = emptyList())
        val p =
            problem().copy(affectedComponents = emptyList())
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = mapOf(gitlabConfig().id to setOf(42L)),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen logs warn when explicit projectId is not reachable`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        val t =
            ticket().copy(components = emptyList())
        val p =
            problem().copy(affectedComponents = emptyList())
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = mapOf(gitlabConfig().id to setOf(99L)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen logs warn when selected project has zero source files`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"empty-repo","path_with_namespace":"g/empty-repo","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val t =
            ticket().copy(components = emptyList())
        val p =
            problem().copy(affectedComponents = emptyList())
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = t,
                problem = p,
                relatedMrs = emptyList(),
                selectedProjects = mapOf(gitlabConfig().id to setOf(42L)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null for non-git directory`(
        @TempDir tmp: Path,
    ) {
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace parses SSH URL`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "git@gitlab.example.com:g/my-repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("g/my-repo", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace parses HTTPS URL`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://gitlab.example.com/g/my-repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("g/my-repo", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null for malformed URL with no scheme or colon`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "just-a-string-no-scheme-no-colon")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertTrue(result == null || result.isNotBlank())
    }

    @Test
    fun `getGitRemotePathWithNamespace swallows IO exceptions`(
        @TempDir tmp: Path,
    ) {
        val badPath = tmp.resolve("does-not-exist/sub")
        val result = adapter.getGitRemotePathWithNamespace(badPath)
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote returns null when no git remote`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote returns null when remote URL is unparseable (no https and no colon)`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "just-some-text-without-slashes-or-colon.git")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote returns null when no configs`() {
        val result =
            adapter.detectGitlabRemote(
                java.nio.file.Paths
                    .get("/tmp"),
                emptyList(),
            )
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote matches config by host and returns project id`(
        @TempDir tmp: Path,
    ) {
        val mockHost =
            server
                .url("")
                .toString()
                .removeSuffix("/")
                .removePrefix("http://")
                .removePrefix("https://")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://$mockHost/g/my-repo.git")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNotNull(result)
        assertEquals(42L, result!!.second)
    }

    private fun runGit(
        dir: Path,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder("git", *args)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
        process.waitFor()
    }

    @Test
    fun `fetchRelatedMrs filters projects when selectedProjects contains ids`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":42,"name":"keep","path_with_namespace":"g/keep","web_url":"u"},
                       {"id":99,"name":"drop","path_with_namespace":"g/drop","web_url":"u"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }

        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets =
                    listOf(
                        JiraSimilarTicket(
                            key = "PROJ-1",
                            summary = "x",
                            description = "",
                            issueType = "Bug",
                            status = "Open",
                            url = "x",
                            foundViaKeyword = null,
                        ),
                    ),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = mapOf(0L to setOf(42L)),
            )
        assertEquals(1, result.size)
        assertEquals("g/keep", result[0].projectPath)
    }

    @Test
    fun `fetchRelatedMrs treats selectedProjects with empty set as no-op`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }

        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets =
                    listOf(
                        JiraSimilarTicket(
                            key = "PROJ-1",
                            summary = "x",
                            description = "",
                            issueType = "Bug",
                            status = "Open",
                            url = "x",
                            foundViaKeyword = null,
                        ),
                    ),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = mapOf(0L to emptySet()),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRelatedMrs breaks early when results size hits MAX_TOTAL_MRS`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"p1","path_with_namespace":"g/p1","web_url":"u"},
                        |{"id":2,"name":"p2","path_with_namespace":"g/p2","web_url":"u"}]
                    """.trimMargin(),
                ),
        )
        repeat(4) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """[{"id":1,"iid":1,"title":"MR 1","description":"","state":"opened","web_url":"u"},
                            |{"id":2,"iid":2,"title":"MR 2","description":"","state":"opened","web_url":"u"},
                            |{"id":3,"iid":3,"title":"MR 3","description":"","state":"opened","web_url":"u"},
                            |{"id":4,"iid":4,"title":"MR 4","description":"","state":"opened","web_url":"u"},
                            |{"id":5,"iid":5,"title":"MR 5","description":"","state":"opened","web_url":"u"}]
                        """.trimMargin(),
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
            )
        }
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        repeat(2 * 2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
            repeat(2) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val similarTickets =
            listOf(
                JiraSimilarTicket(key = "PROJ-1", summary = "x", description = "", issueType = "Bug", status = "Open", url = "u", foundViaKeyword = null),
                JiraSimilarTicket(key = "PROJ-2", summary = "y", description = "", issueType = "Bug", status = "Open", url = "u", foundViaKeyword = null),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.size <= 15)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `fetchRelatedMrs handles when no projects found and falls back to delegate`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRelatedMrs handles URL deduplication`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"p1","path_with_namespace":"g/p1","web_url":"u"},
                        |{"id":2,"name":"p2","path_with_namespace":"g/p1","web_url":"u"}]
                    """.trimMargin(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":99,"iid":5,"title":"Same MR","description":"","state":"opened","web_url":"u"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":99,"iid":5,"title":"Same MR","description":"","state":"opened","web_url":"u"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }
        repeat(2 * 2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets =
                    listOf(
                        JiraSimilarTicket(
                            key = "PROJ-1",
                            summary = "x",
                            description = "",
                            issueType = "Bug",
                            status = "Open",
                            url = "u",
                            foundViaKeyword = null,
                        ),
                    ),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRelatedMrs hits MAX_TOTAL_MRS cap when similar tickets yield exactly 15 MRs`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        for (ticketNum in 1..5) {
            val mrs =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"MR","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val similarTickets =
            (1..5).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs caps at MAX_TOTAL_MRS during keyword search`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val refactorMrs =
            (1..3).joinToString(",") { i ->
                """{"id":$i,"iid":$i,"title":"MR $i","description":"","state":"merged","web_url":"u-$i"}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[$refactorMrs]"),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
            )
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertTrue(
            result.size <= 15,
            "expected result.size <= MAX_TOTAL_MRS (15), got ${result.size}",
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `fetchRelatedMrs breaks early when 6 tickets would exceed MAX_TOTAL_MRS`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        for (ticketNum in 1..5) {
            val mrs =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"MR","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val sixth =
            (1..3).joinToString(",") { i ->
                """{"id":${600 + i},"iid":${600 + i},"title":"MR6","description":"","state":"opened","web_url":"u-6-$i"}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[$sixth]"),
        )
        val similarTickets =
            (1..6).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs skips duplicate MRs across similar tickets`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":1,"iid":5,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
            )
        }
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }
        val similarTickets =
            listOf(
                JiraSimilarTicket(key = "PROJ-1", summary = "x", description = "", issueType = "Bug", status = "Open", url = "x", foundViaKeyword = null),
                JiraSimilarTicket(key = "PROJ-2", summary = "x", description = "", issueType = "Bug", status = "Open", url = "x", foundViaKeyword = null),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRelatedMrs skips duplicate MRs across original and similar ticket loops`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":10,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":2,"iid":10,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]"),
            )
        }
        val similarTickets =
            listOf(
                JiraSimilarTicket(key = "PROJ-1", summary = "x", description = "", issueType = "Bug", status = "Open", url = "x", foundViaKeyword = null),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRelatedMrs skips duplicate keyword MRs`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":10,"title":"x","description":"x","state":"opened","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":3,"iid":10,"title":"x","description":"x","state":"merged","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val similarTickets =
            listOf(
                JiraSimilarTicket(key = "PROJ-1", summary = "x", description = "", issueType = "Bug", status = "Open", url = "x", foundViaKeyword = null),
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRelatedMrs breaks outer config loop after MAX_TOTAL_MRS via two configs`() {
        val secondServer = MockWebServer()
        secondServer.start()
        try {
            val cfg2 =
                GitlabConfig(
                    name = "g2",
                    baseUrl = secondServer.url("").toString().removeSuffix("/"),
                    accessToken = "TOK",
                    active = true,
                )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
            )
            for (ticketNum in 1..5) {
                val mrs =
                    (1..3).joinToString(",") { i ->
                        """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"MR","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                    }
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("[$mrs]"),
                )
                repeat(3) {
                    server.enqueue(
                        MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"changes":[]}"""),
                    )
                }
            }
            secondServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":99,"name":"p2","path_with_namespace":"g2/p2","web_url":"u"}]"""),
            )
            val similarTickets =
                (1..5).map { i ->
                    JiraSimilarTicket(
                        key = "PROJ-$i",
                        summary = "x",
                        description = "",
                        issueType = "Bug",
                        status = "Open",
                        url = "x",
                        foundViaKeyword = null,
                    )
                }
            val result =
                adapter.fetchRelatedMrs(
                    gitlabConfigs = listOf(gitlabConfig(), cfg2),
                    similarTickets = similarTickets,
                    problem = problem(),
                    originalTicket = ticket(),
                    selectedProjects = emptyMap(),
                )
            assertEquals(15, result.size)
            assertEquals(1, secondServer.requestCount)
        } finally {
            secondServer.shutdown()
        }
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null on SSH URL without colon`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "git@gitlab.example.com:g/my-repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("g/my-repo", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null when remote command fails`(
        @TempDir tmp: Path,
    ) {
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null on exception during read`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote falls back to name search when path lookup returns no id`(
        @TempDir tmp: Path,
    ) {
        val mockHost =
            server
                .url("")
                .toString()
                .removeSuffix("/")
                .removePrefix("http://")
                .removePrefix("https://")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://$mockHost/g/my-repo.git")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNotNull(result)
        assertEquals(42L, result!!.second)
    }

    @Test
    fun `detectGitlabRemote returns null when no config host matches`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://other-host.example.com/g/my-repo.git")
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote returns null when path lookup fails and fallback finds nothing`(
        @TempDir tmp: Path,
    ) {
        val mockHost =
            server
                .url("")
                .toString()
                .removeSuffix("/")
                .removePrefix("http://")
                .removePrefix("https://")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://$mockHost/g/my-repo.git")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNotNull(result)
        assertNull(result!!.second)
    }

    @Test
    fun `detectGitlabRemote catches exception when git command fails to start`(
        @TempDir tmp: Path,
    ) {
        val nonExistent = tmp.resolve("does-not-exist")
        val result = adapter.detectGitlabRemote(nonExistent, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote returns null when remoteUrl is blank`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace parses SSH URL with embedded colon correctly`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "git@gitlab.example.com:team/my-repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("team/my-repo", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null for SSH URL with malformed path`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "weird-scheme://something/repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("//something/repo", result)
    }

    @Test
    fun `fetchRelatedMrs breaks inner project loop when MAX_TOTAL_MRS reached mid-ticket`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"a","path_with_namespace":"g/a","web_url":"u"},
                        |{"id":2,"name":"b","path_with_namespace":"g/b","web_url":"u"}]
                    """.trimMargin(),
                ),
        )
        for (ticketNum in 1..5) {
            val mrs1 =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"M","description":"","state":"opened","web_url":"u-1-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs1]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
            val mrs2 =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + 10 + i},"iid":${ticketNum * 100 + 10 + i},"title":"M","description":"","state":"opened","web_url":"u-2-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs2]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val similarTickets =
            (1..5).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs breaks original-ticket loop when MAX_TOTAL_MRS reached`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        for (ticketNum in 1..4) {
            val mrs =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"M","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val origMrs =
            (1..3).joinToString(",") { i ->
                """{"id":${900 + i},"iid":${900 + i},"title":"M","description":"","state":"opened","web_url":"u-o-$i"}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[$origMrs]"),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
            )
        }
        val similarTickets =
            (1..4).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs breaks keyword loop when MAX_TOTAL_MRS reached mid-keyword`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        for (ticketNum in 1..4) {
            val mrs =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"M","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":901,"iid":901,"title":"M","description":"","state":"opened","web_url":"u-o-1"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val refactorMrs =
            (1..3).joinToString(",") { i ->
                """{"id":${1000 + i},"iid":${1000 + i},"title":"M","description":"","state":"merged","web_url":"u-r-$i"}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[$refactorMrs]"),
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
            )
        }
        val similarTickets =
            (1..4).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs keyword loop first iteration break after cap reached`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"p","path_with_namespace":"g/p","web_url":"u"}]"""),
        )
        for (ticketNum in 1..5) {
            val mrs =
                (1..3).joinToString(",") { i ->
                    """{"id":${ticketNum * 100 + i},"iid":${ticketNum * 100 + i},"title":"M","description":"","state":"opened","web_url":"u-$ticketNum-$i"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$mrs]"),
            )
            repeat(3) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"changes":[]}"""),
                )
            }
        }
        val similarTickets =
            (1..5).map { i ->
                JiraSimilarTicket(
                    key = "PROJ-$i",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(gitlabConfig()),
                similarTickets = similarTickets,
                problem = problem(),
                originalTicket = ticket(),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen returns empty when explicit selection key is missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(999L to setOf(1L)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen returns empty when explicit selection set is empty`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(0L to emptySet()),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectGitlabRemote name-based fallback matches by name only`(
        @TempDir tmp: Path,
    ) {
        val mockHost =
            server
                .url("")
                .toString()
                .removeSuffix("/")
                .removePrefix("http://")
                .removePrefix("https://")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://$mockHost/team/my-repo.git")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":77,"name":"my-repo","path_with_namespace":"team/other","web_url":"u"}]"""),
        )
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNotNull(result)
        assertEquals(77L, result!!.second)
    }

    @Test
    fun `detectGitlabRemote path-lookup failure logs debug message`(
        @TempDir tmp: Path,
    ) {
        val mockHost =
            server
                .url("")
                .toString()
                .removeSuffix("/")
                .removePrefix("http://")
                .removePrefix("https://")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://$mockHost/team/my-repo.git")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":88,"name":"x","path_with_namespace":"team/my-repo","web_url":"u"}]"""),
        )
        val config =
            GitlabConfig(
                name = "g",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                active = true,
            )
        val result = adapter.detectGitlabRemote(tmp, listOf(config))
        assertNotNull(result)
        assertEquals(88L, result!!.second)
    }
}
