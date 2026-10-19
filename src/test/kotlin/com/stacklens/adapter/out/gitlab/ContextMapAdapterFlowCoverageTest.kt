package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class ContextMapAdapterFlowCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ContextMapAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ContextMapAdapter(connectTimeoutMs = 2000, readTimeoutMs = 2000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        projectIds: String? = null,
        groupId: String? = null,
    ) = GitlabConfig(
        name = "ctx",
        baseUrl = server.url("/").toString().trimEnd('/'),
        accessToken = "token",
        groupId = groupId,
        projectIds = projectIds,
    )

    private fun jsonList(body: String) = MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body)

    private fun emptyPage() = jsonList("[]")

    private fun project(
        id: Long,
        name: String,
        path: String,
    ) = """{"id":$id,"name":"$name","path_with_namespace":"$path","default_branch":"main"}"""

    @Test
    fun `repository filters keep projects matched by path, name, suffix and substring`() {
        server.enqueue(
            jsonList(
                listOf(
                    project(1, "Alpha", "grp/alpha"),
                    project(2, "Beta", "grp/beta"),
                    project(3, "Gamma", "grp/gamma"),
                    project(4, "Delta", "grp/x/delta-extra"),
                    project(5, "Eps", "grp/eps"),
                ).joinToString(prefix = "[", postfix = "]"),
            ),
        )
        repeat(8) { server.enqueue(emptyPage()) }

        val results = adapter.searchKeyword(config(), "widget", listOf("grp/alpha", "beta", "gamma", "delta"), 7L)

        assertEquals(0, results.size)
        assertEquals(9, server.requestCount)
        assertTrue(
            server
                .takeRequest()
                .path
                .orEmpty()
                .startsWith("/api/v4/projects?membership=true"),
        )
    }

    @Test
    fun `a filter list of blank entries keeps every project`() {
        server.enqueue(jsonList("[${project(1, "Alpha", "grp/alpha")}]"))
        server.enqueue(emptyPage())
        server.enqueue(emptyPage())

        assertEquals(0, adapter.searchKeyword(config(), "widget", listOf("  ", ""), 7L).size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `explicit project ids are resolved one by one and a missing project is skipped`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(jsonList(project(2, "Beta", "grp/beta")))
        server.enqueue(emptyPage())
        server.enqueue(emptyPage())

        assertEquals(0, adapter.searchKeyword(config(projectIds = "1,2"), "widget", emptyList(), 7L).size)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `blob hits, tree hits and an empty raw file body are merged into the result list`() {
        server.enqueue(jsonList("[${project(1, "Alpha", "grp/alpha")}]"))
        server.enqueue(
            jsonList("""[{"project_id":1,"path":"docs/context.md","ref":"main","startline":3,"data":"[Payment Service]"}]"""),
        )
        server.enqueue(
            jsonList(
                """[{"id":"a","name":"context.md","path":"docs/context.md","type":"blob"},""" +
                    """{"id":"b","name":"architecture.md","path":"docs/architecture.md","type":"blob"},""" +
                    """{"id":"c","name":"src","path":"src","type":"tree"},""" +
                    """{"id":"d","name":"system-map.md","path":"docs/system-map.md","type":"blob"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(jsonList("line one\nKEYWORD token line\n"))

        val results = adapter.searchKeyword(config(), "KEYWORD", emptyList(), 7L)

        assertEquals(2, results.size)
        assertEquals(listOf(ResultType.CONTEXT_MAP_COMPONENT, ResultType.CONTEXT_MAP_FILE), results.map { it.resultType })
        assertEquals("docs/system-map.md", results.last().filePath)
    }

    @Test
    fun `a group based search keeps paging while full pages arrive`() {
        val full = "[" + (1..100).joinToString(",") { project(it.toLong(), "p$it", "g$it/p$it") } + "]"
        server.enqueue(jsonList(full))
        server.enqueue(jsonList(full))
        server.enqueue(jsonList("""[${project(500, "last", "g/last")}]"""))
        repeat(2) { server.enqueue(emptyPage()) }

        assertEquals(0, adapter.searchKeyword(config(groupId = "42"), "never-matches", listOf("g/last"), 7L).size)

        assertEquals(5, server.requestCount)
        assertTrue(
            server
                .takeRequest()
                .path
                .orEmpty()
                .startsWith("/api/v4/groups/42/projects"),
        )
    }

    @Test
    fun `paging stops at the page ceiling even while pages stay full`() {
        val full = "[" + (1..100).joinToString(",") { project(it.toLong(), "p$it", "g$it/p$it") } + "]"
        repeat(50) { server.enqueue(jsonList(full)) }

        assertEquals(0, adapter.searchKeyword(config(), "never-matches", listOf("nothing"), 7L).size)

        assertEquals(50, server.requestCount)
    }

    @Test
    fun `an empty response body on every endpoint degrades to an empty result`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(jsonList("[${project(1, "Alpha", "grp/alpha")}]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertEquals(0, adapter.searchKeyword(config(), "KEYWORD", emptyList(), 7L).size)
    }

    @Test
    fun `a failing project page is answered with an empty list`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertEquals(0, adapter.searchKeyword(config(), "KEYWORD", emptyList(), 7L).size)
    }

    @Test
    fun `an inactive configuration never reaches the api`() {
        val inactive = config().also { it.active = false }

        assertEquals(0, adapter.searchKeyword(inactive, "KEYWORD", emptyList(), 7L).size)
        assertEquals(0, server.requestCount)
    }
}
