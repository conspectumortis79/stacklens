package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.util.concurrent.atomic.AtomicInteger

class ContextMapAdapterTailCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ContextMapAdapter
    private val emptyBodyCalls = AtomicInteger()

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

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body)

    private fun emptyJson(): MockResponse {
        emptyBodyCalls.incrementAndGet()
        return MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
    }

    @Test
    fun `blob search and tree listing fall back to empty lists when the body is missing`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/search?")) return emptyJson()
                    if (path.contains("/repository/tree")) return emptyJson()
                    if (path.startsWith("/api/v4/projects?")) {
                        return json("""[{"id":1,"name":"r","path_with_namespace":"g/r","default_branch":"main"}]""")
                    }
                    return json("[]")
                }
            }
        val results = adapter.searchKeyword(config(), "kw", emptyList(), sessionId = 1L)
        assertTrue(results.isEmpty())
        assertEquals(2, emptyBodyCalls.get())
    }

    @Test
    fun `repository filters keep projects matched by full path, name, suffix and substring`() {
        val projects =
            listOf(
                """{"id":1,"name":"one","path_with_namespace":"grp/one","default_branch":"main"}""",
                """{"id":2,"name":"two","path_with_namespace":"grp/deep/two","default_branch":"main"}""",
                """{"id":3,"name":"three","path_with_namespace":"grp/threefold","default_branch":"main"}""",
                """{"id":4,"name":"four","path_with_namespace":"grp/four","default_branch":"main"}""",
            )
        val filters = listOf("grp/one", "two", "threefold", "our", "unmatched")
        val kept = mutableListOf<String>()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.startsWith("/api/v4/projects?")) {
                        return json(projects.joinToString(",", "[", "]"))
                    }
                    val id =
                        Regex("/api/v4/projects/(\\d+)/")
                            .find(path)
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull()
                    kept += id.toString()
                    return json("[]")
                }
            }
        val results = adapter.searchKeyword(config(), "kw", filters, sessionId = 1L)
        assertTrue(results.isEmpty())
        assertEquals(listOf("1", "2", "3", "4"), kept.filter { it != "null" }.distinct())
    }

    @Test
    fun `tree walked context files report component and plain result types`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/search?")) return json("[]")
                    if (path.contains("/repository/tree")) {
                        return json(
                            """[
                                {"id":"1","name":"a.puml","path":"docs/a.puml","type":"blob"},
                                {"id":"2","name":"b.puml","path":"docs/b.puml","type":"blob"}
                            ]""",
                        )
                    }
                    if (path.contains("/repository/files/")) {
                        val body =
                            if (path.contains("a.puml")) {
                                "kw [Widget] shape"
                            } else {
                                "kw plain line"
                            }
                        return MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE).setBody(body)
                    }
                    if (path.startsWith("/api/v4/projects?")) {
                        return json("""[{"id":1,"name":"r","path_with_namespace":"g/r","default_branch":"main"}]""")
                    }
                    return json("[]")
                }
            }
        val results = adapter.searchKeyword(config(), "kw", emptyList(), sessionId = 1L)
        assertEquals(
            setOf(ResultType.CONTEXT_MAP_COMPONENT, ResultType.CONTEXT_MAP_FILE),
            results.map { it.resultType }.toSet(),
        )
    }
}
