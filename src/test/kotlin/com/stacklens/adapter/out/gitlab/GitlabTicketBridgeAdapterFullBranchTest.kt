package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.nio.file.Path

class GitlabTicketBridgeAdapterFullBranchTest {
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

    private fun gitlabConfig(
        id: Long = 1L,
        groupId: String? = null,
    ): GitlabConfig =
        GitlabConfig(
            id = id,
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
            active = true,
            groupId = groupId,
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

    private fun runGit(
        tmp: Path,
        vararg args: String,
    ) {
        val pb = ProcessBuilder("git", *args).directory(tmp.toFile()).redirectErrorStream(true)
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    private fun setupRemote(
        tmp: Path,
        remoteUrl: String,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", remoteUrl)
    }

    @Test
    fun `detectGitlabRemote returns null when the project client cannot be built`(
        @TempDir tmp: Path,
    ) {
        val brokenClient =
            mock<GitlabProjectApiClient> {
                on { buildClient(any(), any()) } doThrow IllegalStateException("no client")
            }
        setupRemote(tmp, "${server.url("").toString().removeSuffix("/")}/g/my-repo.git")

        val result = GitlabTicketBridgeAdapter(projectClient = brokenClient).detectGitlabRemote(tmp, listOf(gitlabConfig(id = 1L)))

        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote with valid https URL and matching project id returns Pair`(
        @TempDir tmp: Path,
    ) {
        val baseUrl = server.url("").toString().removeSuffix("/")
        val remoteUrl = "$baseUrl/g/my-repo.git"
        setupRemote(tmp, remoteUrl)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo"}"""),
        )
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig(id = 1L)))
        assertNotNull(result)
        assertEquals(1L, result!!.first.id)
        assertEquals(42L, result.second)
    }

    @Test
    fun `detectGitlabRemote with valid https URL and direct lookup returning 0 id falls back to search`(
        @TempDir tmp: Path,
    ) {
        val baseUrl = server.url("").toString().removeSuffix("/")
        val remoteUrl = "$baseUrl/g/my-repo.git"
        setupRemote(tmp, remoteUrl)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":0,"name":"my-repo","path_with_namespace":"g/my-repo"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":99,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig(id = 1L)))
        assertNotNull(result)
        assertEquals(99L, result!!.second)
    }

    @Test
    fun `detectGitlabRemote with ssh colon-style remote URL parses correctly`(
        @TempDir tmp: Path,
    ) {
        val baseUrl = server.url("").toString().removeSuffix("/")
        val hostWithPort = baseUrl.removePrefix("http://").removePrefix("https://")
        val remoteUrl = "git@$hostWithPort:group/my-repo.git"
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", remoteUrl)
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":123,"name":"my-repo","path_with_namespace":"group/my-repo","web_url":"u"}]""",
                ),
        )
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig(id = 1L)))
        assertNotNull(result)
        assertEquals(123L, result!!.second)
    }

    @Test
    fun `detectGitlabRemote with non-matching host returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://other-host.example.com/g/repo.git")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig(id = 1L)))
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote with blank remote URL returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "config", "remote.origin.url", "")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace with valid https URL returns the path`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://gitlab.example.com/group/sub/proj.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("group/sub/proj", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace with SSH URL returns the path`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "git@gitlab.example.com:group/proj.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertEquals("group/proj", result)
    }

    @Test
    fun `getGitRemotePathWithNamespace with no colon and no https returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "/local/path/repo.git")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace with non-zero exit returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote with exit code 0 but blank remote URL returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "config", "remote.origin.url", "   ")
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace with whitespace-only remote URL returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "config", "remote.origin.url", "   ")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `detectGitlabRemote with API error on direct lookup falls back to name search and finds by path`(
        @TempDir tmp: Path,
    ) {
        val baseUrl = server.url("").toString().removeSuffix("/")
        val remoteUrl = "$baseUrl/g/my-repo.git"
        setupRemote(tmp, remoteUrl)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":77,"name":"my-repo","path_with_namespace":"g/my-repo","web_url":"u"}]"""),
        )
        val result = adapter.detectGitlabRemote(tmp, listOf(gitlabConfig()))
        assertNotNull(result)
        assertEquals(77L, result!!.second)
    }

    @Test
    fun `getGitRemotePathWithNamespace with URL that yields blank path returns null`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://x.example.com:")
        val result = adapter.getGitRemotePathWithNamespace(tmp)
        assertNull(result)
    }

    @Test
    fun `fetchRelatedMrs when results already at cap skips original-key loop`(
        @TempDir tmp: Path,
    ) {
        val similarTickets =
            (1..15).map { idx ->
                JiraSimilarTicket(
                    key = "PROJ-$idx",
                    summary = "x",
                    description = "",
                    issueType = "Bug",
                    status = "Open",
                    url = "x",
                    foundViaKeyword = null,
                )
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        repeat(15) { idx ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """[{"id":$idx,"iid":${100 + idx},"title":"x","description":"x","state":"opened","web_url":"u-$idx"}]""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"changes":[]}"""),
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
        assertTrue(result.size <= 15)
    }

    @Test
    fun `fetchRelatedMrs keyword loop runs with state merged when below cap`(
        @TempDir tmp: Path,
    ) {
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
                .setBody(
                    """[{"id":7,"iid":77,"title":"r","description":"x","state":"merged","web_url":"u"}]""",
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
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen breaks out of explicit selection loop when cap reached`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":1,"name":"a","path_with_namespace":"g/a","web_url":"u"},
                        {"id":2,"name":"b","path_with_namespace":"g/b","web_url":"u"},
                        {"id":3,"name":"c","path_with_namespace":"g/c","web_url":"u"},
                        {"id":4,"name":"d","path_with_namespace":"g/d","web_url":"u"}
                    ]""",
                ),
        )
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"path":"src/A.kt","type":"blob"}]"""),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/plain")
                    .setBody("class A {}"),
            )
        }
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig(id = 5L)),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(5L to setOf(1L, 2L, 3L, 4L)),
            )
        assertEquals(3, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen skips duplicate projectId via relatedMrs`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val mr =
            JiraRelatedMr(
                projectPath = "g/r",
                mrIid = 1L,
                title = "x",
                description = "x",
                state = "opened",
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
                relatedMrs = listOf(mr, mr),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.size <= 1)
    }

    @Test
    fun `fetchRepoContextForCodeGen candidate with no matching project returns empty`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"different","path_with_namespace":"g/different","web_url":"u"}]"""),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket = ticket().copy(components = listOf("missing-repo")),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRepoContextForCodeGen when files empty on first project tries next candidate`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":1,"name":"empty-repo","path_with_namespace":"g/empty-repo","web_url":"u"},
                        {"id":2,"name":"good-repo","path_with_namespace":"g/good-repo","web_url":"u"}
                    ]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"path":"src/B.kt","type":"blob"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("class B {}"),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig()),
                ticket =
                    ticket().copy(
                        components = listOf("empty-repo", "good-repo"),
                    ),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(1, result.size)
        assertEquals("good-repo", result[0].repoName)
    }

    @Test
    fun `fetchRepoContextForCodeGen with explicit selection and project found via fetchProjectDtoById`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":7,"name":"explicit-repo","path_with_namespace":"g/explicit-repo","web_url":"u"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"path":"src/X.kt","type":"blob"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("class X {}"),
        )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(gitlabConfig(id = 5L)),
                ticket = ticket().copy(components = emptyList()),
                problem = problem().copy(affectedComponents = emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(5L to setOf(7L)),
            )
        assertEquals(1, result.size)
        assertEquals("explicit-repo", result[0].repoName)
    }
}
