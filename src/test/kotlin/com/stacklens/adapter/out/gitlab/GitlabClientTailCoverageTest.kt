package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class GitlabClientTailCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabClient
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("").toString().removeSuffix("/")
        client = GitlabClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(
        id: Long = 1L,
        groupId: String? = null,
        projectIds: String? = null,
    ): GitlabConfig =
        GitlabConfig(
            id = id,
            name = "g",
            baseUrl = baseUrl,
            accessToken = "TOK",
            active = true,
            groupId = groupId,
            projectIds = projectIds,
        )

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `fetchProjectFilesForPath ignores non blobs, other prefixes and non code files`() {
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/repository/tree")) {
                        return json(
                            """[
                                {"id":"a","name":"src","path":"src","type":"tree"},
                                {"id":"b","name":"readme","path":"docs/readme.md","type":"blob"},
                                {"id":"c","name":"notes","path":"src/main/notes.xyz","type":"blob"},
                                {"id":"d","name":"main","path":"src/main/App.kt","type":"blob"}
                            ]""",
                        )
                    }
                    if (path.contains("/raw")) {
                        return MockResponse()
                            .setHeader("Content-Type", "text/plain")
                            .setBody("class App {}")
                    }
                    return json("""{"id":5,"name":"r","path_with_namespace":"g/r","default_branch":"develop"}""")
                }
            }
        val files = client.fetchProjectFilesForPathForTest(gitlabConfig(), 5L, "src/main", 10, 100)
        assertEquals(listOf("src/main/App.kt"), files.map { it.first })
    }

    @Test
    fun `fetchAllProjects reports the exception class name when no message is available`() {
        val silent =
            WebClient
                .builder()
                .baseUrl(baseUrl)
                .filter { _, _ -> Mono.error(IllegalStateException()) }
                .build()
        val failure =
            assertThrows(RuntimeException::class.java) {
                client.fetchAllProjectsForTest(silent, null)
            }
        assertTrue(failure.message!!.contains("IllegalStateException"))
    }

    @Test
    fun `fetchAllProjects falls back to an empty page when the body is empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"))
        val webClient = WebClient.builder().baseUrl(baseUrl).build()
        assertTrue(client.fetchAllProjectsForTest(webClient, null).isEmpty())
    }

    @Test
    fun `filterProjects keeps path matches, name matches and rejects unrelated selectors`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "alpha", pathWithNamespace = "g/alpha", httpUrlToRepo = "u"),
                GitlabProject(id = 2, name = "beta", pathWithNamespace = "team/beta", httpUrlToRepo = "u"),
                GitlabProject(id = 3, name = "gamma", pathWithNamespace = "g/gamma", httpUrlToRepo = "u"),
                GitlabProject(id = 4, name = "delta", pathWithNamespace = "g/delta", httpUrlToRepo = "u"),
            )
        val byPath = client.filterProjectsForTest(projects, listOf("g/alpha"))
        assertEquals(listOf(1L), byPath.map { it.id })
        val byName = client.filterProjectsForTest(projects, listOf("beta"))
        assertEquals(listOf(2L), byName.map { it.id })
        val byId = client.filterProjectsForTest(projects, listOf("3"))
        assertEquals(listOf(3L), byId.map { it.id })
        assertTrue(client.filterProjectsForTest(projects, listOf("unknown")).isEmpty())
    }

    @Test
    fun `applyRepositoryFilters covers exact, name, suffix, substring and miss matches`() {
        val cases =
            listOf(
                Triple("g/one", GitlabProject(id = 1, name = "aa", pathWithNamespace = "g/one", httpUrlToRepo = "u"), 1),
                Triple("two", GitlabProject(id = 2, name = "two", pathWithNamespace = "team/xc", httpUrlToRepo = "u"), 1),
                Triple("dd", GitlabProject(id = 3, name = "cc", pathWithNamespace = "g/dd", httpUrlToRepo = "u"), 1),
                Triple("foo", GitlabProject(id = 4, name = "ee", pathWithNamespace = "g/eefoo", httpUrlToRepo = "u"), 1),
                Triple("zzz", GitlabProject(id = 5, name = "ff", pathWithNamespace = "g/ff", httpUrlToRepo = "u"), 0),
            )
        cases.forEach { (filter, project, expected) ->
            assertEquals(expected, client.applyRepositoryFiltersForTest(listOf(project), listOf(filter)).size)
        }
        assertEquals(
            2,
            client
                .applyRepositoryFiltersForTest(
                    listOf(
                        GitlabProject(id = 1, name = "aa", pathWithNamespace = "g/one", httpUrlToRepo = "u"),
                        GitlabProject(id = 2, name = "two", pathWithNamespace = "team/xc", httpUrlToRepo = "u"),
                        GitlabProject(id = 3, name = "ff", pathWithNamespace = "g/ff", httpUrlToRepo = "u"),
                    ),
                    listOf("  ", "g/one", "two"),
                ).size,
        )
    }

    @Test
    fun `searchProjectBlobs yields an empty page when the response body is empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"))
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        assertNotNull(client.searchProjectBlobsForTest(webClient, 1L, "kw"))
    }

    @Test
    fun `searchKeyword walks the tree when the blob search is unavailable`() {
        server.enqueue(json("""[{"id":1,"name":"repo","path_with_namespace":"g/repo","default_branch":null}]"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json("""[{"id":"1","name":"App.kt","path":"src/App.kt","type":"blob"}]"""))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("val kw = 1"),
        )
        val results = client.searchKeyword(gitlabConfig(projectIds = "1"), "kw", 7L, null, emptyList())
        assertEquals(1, results.size)
        assertNull(results[0].crossRefParentId)
    }

    @Test
    fun `the http factory keeps building clients for the injected base url`() {
        val factory = mock<ReactiveHttpClientFactory>()
        whenever(factory.buildGitlab(any(), any())).thenReturn(WebClient.builder().baseUrl(baseUrl).build())
        val injected = GitlabClient(httpFactory = factory)
        server.enqueue(json("""[]"""))
        assertTrue(injected.listAccessibleProjects(gitlabConfig()).isEmpty())
    }
}
