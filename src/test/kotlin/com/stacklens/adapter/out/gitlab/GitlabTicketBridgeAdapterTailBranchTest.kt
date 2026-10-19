package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class GitlabTicketBridgeAdapterTailBranchTest {
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

    private fun config(id: Long): GitlabConfig =
        GitlabConfig(
            id = id,
            name = "cfg$id",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
            active = true,
            groupId = null,
        )

    private fun ticket(
        key: String = "PROJ-123",
        components: List<String> = listOf("my-repo"),
    ) = JiraTicketData(
        key = key,
        summary = "s",
        description = "",
        comments = "",
        issueType = "Story",
        status = "Open",
        priority = "Medium",
        labels = emptyList(),
        components = components,
        url = "https://jira/$key",
    )

    private fun problem(keywords: List<String> = listOf("refactor")) =
        JiraProblemAnalysis(
            problemDescription = "p",
            rootCause = "r",
            affectedComponents = emptyList(),
            searchKeywords = keywords,
            errorMessages = emptyList(),
        )

    private fun similar(key: String) =
        JiraSimilarTicket(
            key = key,
            summary = "s",
            description = "",
            issueType = "Story",
            status = "Open",
            url = "u",
            foundViaKeyword = null,
        )

    private fun projectArray(count: Int): String =
        (1..count).joinToString(",", "[", "]") {
            """{"id":$it,"name":"repo$it","path_with_namespace":"g/repo$it"}"""
        }

    private fun bridgeDispatcher(mrCounts: Map<String, Map<Long, Int>>): Dispatcher =
        object : Dispatcher() {
            private val nextIid = AtomicInteger(1)

            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                val uri = URI(path)
                val term =
                    uri.query
                        ?.split('&')
                        ?.firstOrNull { it.startsWith("search=") }
                        ?.removePrefix("search=")
                        .orEmpty()
                val projectId =
                    Regex("/api/v4/projects/(\\d+)/")
                        .find(path)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull() ?: 0L
                if (path.startsWith("/api/v4/projects?")) {
                    return json(projectArray(6))
                }
                if (path.endsWith("/changes")) {
                    return json("""{"changes":[]}""")
                }
                if (Regex("/api/v4/projects/\\d+/merge_requests(\\?|$)").containsMatchIn(path)) {
                    val count = mrCounts[term]?.get(projectId) ?: 0
                    val body =
                        (1..count).joinToString(",", "[", "]") {
                            val iid = nextIid.getAndIncrement()
                            """{"id":$iid,"iid":$iid,"title":"MR $iid","state":"merged","web_url":"u$iid"}"""
                        }
                    return json(body)
                }
                return MockResponse().setResponseCode(404)
            }

            private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        }

    private fun contextDispatcher(
        filesFor: Map<Long, Int>,
        projectsJson: String,
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                val projectId =
                    Regex("/api/v4/projects/(\\d+)/")
                        .find(path)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull() ?: 0L
                if (path.startsWith("/api/v4/projects?")) {
                    return json(projectsJson)
                }
                if (path.contains("/repository/tree")) {
                    val count = filesFor[projectId] ?: 0
                    val body =
                        (1..count).joinToString(",", "[", "]") {
                            """{"id":"$projectId-$it","name":"File$it.kt","type":"blob","path":"src/File$projectId$it.kt"}"""
                        }
                    return json(body)
                }
                if (path.contains("/repository/files/")) {
                    return MockResponse().setHeader("Content-Type", "text/plain").setBody("class X {}")
                }
                if (Regex("^/api/v4/projects/\\d+(\\?|$)").containsMatchIn(path)) {
                    return json("""{"id":$projectId,"name":"repo$projectId","path_with_namespace":"g/repo$projectId"}""")
                }
                return MockResponse().setResponseCode(404)
            }

            private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        }

    @Test
    fun `fetchRelatedMrs stops the original ticket project loop once the total cap is reached`() {
        server.dispatcher = bridgeDispatcher(mapOf("O-1" to (1L..6L).associateWith { 3 }))
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(config(1L)),
                similarTickets = emptyList(),
                problem = problem(),
                originalTicket = ticket("O-1"),
                selectedProjects = emptyMap(),
            )
        assertEquals(15, result.size)
    }

    @Test
    fun `fetchRelatedMrs stops the keyword project loop once the total cap is reached`() {
        val counts =
            mapOf(
                "S-1" to mapOf(1L to 3, 2L to 3, 3L to 3, 4L to 3, 5L to 0, 6L to 0),
                "O-1" to mapOf(1L to 1, 2L to 1, 3L to 0, 4L to 0, 5L to 0, 6L to 0),
                "k1" to mapOf(1L to 2, 2L to 2),
            )
        server.dispatcher = bridgeDispatcher(counts)
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(config(1L)),
                similarTickets = listOf(similar("S-1")),
                problem = problem(listOf("k1", "k2")),
                originalTicket = ticket("O-1"),
                selectedProjects = emptyMap(),
            )
        assertEquals(16, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen stops before a second config once the repo cap is reached`() {
        server.dispatcher =
            contextDispatcher(
                filesFor = (1L..6L).associateWith { 1 },
                projectsJson = projectArray(3),
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(config(1L), config(2L)),
                ticket = ticket(components = listOf("repo1", "repo2", "repo3")),
                problem = problem(emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(3, result.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen treats an empty explicit selection as no selection`() {
        server.dispatcher =
            contextDispatcher(
                filesFor = mapOf(2L to 1),
                projectsJson = """[{"id":1,"name":"other","path_with_namespace":"g/other"},{"id":2,"name":"want","path_with_namespace":"g/want"}]""",
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(config(1L)),
                ticket = ticket(components = listOf("want")),
                problem = problem(emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(1L to emptySet()),
            )
        assertEquals(listOf(2L), result.map { it.projectId })
    }

    @Test
    fun `fetchRepoContextForCodeGen skips an explicit project already collected from an earlier config`() {
        server.dispatcher =
            contextDispatcher(
                filesFor = mapOf(3L to 1, 4L to 1),
                projectsJson = """[{"id":3,"name":"c","path_with_namespace":"g/c"},{"id":4,"name":"d","path_with_namespace":"g/d"}]""",
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(config(1L), config(2L)),
                ticket = ticket(components = emptyList()),
                problem = problem(emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = mapOf(1L to setOf(3L), 2L to setOf(3L, 4L)),
            )
        assertEquals(listOf(3L, 4L), result.map { it.projectId })
    }

    @Test
    fun `fetchRepoContextForCodeGen skips a candidate project already collected by another candidate`() {
        server.dispatcher =
            contextDispatcher(
                filesFor = mapOf(9L to 1, 10L to 1),
                projectsJson =
                    """[
                        {"id":9,"name":"alpha","path_with_namespace":"g/first"},
                        {"id":10,"name":"beta","path_with_namespace":"g/second"}
                    ]""",
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(config(1L)),
                ticket = ticket(components = listOf("first", "alpha", "second")),
                problem = problem(emptyList()),
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertEquals(listOf(9L, 10L), result.map { it.projectId })
    }

    private fun runGit(
        tmp: Path,
        vararg args: String,
    ) {
        val process = ProcessBuilder("git", *args).directory(tmp.toFile()).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        process.waitFor()
    }

    @Test
    fun `detectGitlabRemote falls back to a name match when no path matches`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    if (path.startsWith("/api/v4/projects/g%2Fmy-repo") || path.startsWith("/api/v4/projects/g/my-repo")) {
                        return MockResponse().setResponseCode(404)
                    }
                    if (path.startsWith("/api/v4/projects?")) {
                        return MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""[{"id":77,"name":"MY-REPO","path_with_namespace":"g/elsewhere"}]""")
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
        val tmp = Files.createTempDirectory("gitlab-remote")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "${server.url("").toString().removeSuffix("/")}/g/my-repo.git")
        val result = adapter.detectGitlabRemote(tmp, listOf(config(1L)))
        assertNotNull(result)
        assertEquals(77L, result!!.second)
    }

    @Test
    fun `detectGitlabRemote returns a null project id when neither lookup nor name search matches`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    if (path.startsWith("/api/v4/projects?")) {
                        return MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""[{"id":77,"name":"other","path_with_namespace":"g/other"}]""")
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
        val tmp = Files.createTempDirectory("gitlab-remote2")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "${server.url("").toString().removeSuffix("/")}/g/my-repo.git")
        val result = adapter.detectGitlabRemote(tmp, listOf(config(1L)))
        assertNotNull(result)
        assertNull(result!!.second)
    }

    @Test
    fun `detectGitlabRemote keeps a null project id when the fallback list carries a null entry`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    if (path.startsWith("/api/v4/projects?")) {
                        return MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("[null]")
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
        val tmp = Files.createTempDirectory("gitlab-remote-null-entry")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "${server.url("").toString().removeSuffix("/")}/g/my-repo.git")
        val result = adapter.detectGitlabRemote(tmp, listOf(config(1L)))
        assertNotNull(result)
        assertNull(result!!.second)
    }

    @Test
    fun `detectGitlabRemote records a null project id when the blocking lookup is rejected`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(404)
            }
        val tmp = Files.createTempDirectory("gitlab-remote3")
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "${server.url("").toString().removeSuffix("/")}/g/my-repo.git")
        val result =
            Mono
                .fromCallable { adapter.detectGitlabRemote(tmp, listOf(config(1L))) }
                .subscribeOn(Schedulers.parallel())
                .block()
        assertNotNull(result)
        assertNull(result!!.second)
    }
}
