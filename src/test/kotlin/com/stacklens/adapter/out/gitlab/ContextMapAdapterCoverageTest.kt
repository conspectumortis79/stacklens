package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.lang.reflect.InvocationTargetException

class ContextMapAdapterCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ContextMapAdapter
    private lateinit var client: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
                .build()
        adapter = ContextMapAdapter(connectTimeoutMs = 2000, readTimeoutMs = 2000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun json(items: List<String>) = MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody("[${items.joinToString(",")}]")

    private fun projectJson(
        id: Long,
        name: String,
        path: String,
    ) = """{"id":$id,"name":"$name","path_with_namespace":"$path","default_branch":"main"}"""

    private fun projectJsonPage(
        count: Int,
        offset: Int = 0,
    ) = (1..count).map { projectJson((offset + it).toLong(), "proj${offset + it}", "group${offset + it}/proj${offset + it}") }

    private fun invokePrivate(
        name: String,
        vararg args: Any?,
    ): Any? {
        val method = ContextMapAdapter::class.java.declaredMethods.first { it.name == name }
        method.isAccessible = true
        return try {
            val parameters = ArrayList<Any?>()
            parameters.add(client)
            parameters.addAll(args.toList())
            method.invoke(adapter, *(parameters.toTypedArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun `project paging follows the group endpoint while pages stay full`() {
        server.enqueue(json(projectJsonPage(100)))
        server.enqueue(json(projectJsonPage(2, offset = 100)))

        @Suppress("UNCHECKED_CAST")
        val projects = invokePrivate("fetchAllProjects", "42") as List<ContextMapAdapter.ContextProject>

        assertEquals(102, projects.size)
        assertEquals(2, server.requestCount)
        val firstPath = server.takeRequest().path
        val secondPath = server.takeRequest().path

        assertTrue(firstPath!!.startsWith("/api/v4/groups/42/projects"), firstPath)
        assertTrue(secondPath!!.contains("page=2"), secondPath)
    }

    @Test
    fun `project paging stops after the first short page`() {
        server.enqueue(json(projectJsonPage(3)))

        @Suppress("UNCHECKED_CAST")
        val projects = invokePrivate("fetchAllProjects", null) as List<ContextMapAdapter.ContextProject>

        assertEquals(3, projects.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failing project lookup yields no projects`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("gateway down"))

        @Suppress("UNCHECKED_CAST")
        val projects = invokePrivate("fetchAllProjects", null) as List<ContextMapAdapter.ContextProject>

        assertTrue(projects.isEmpty())
    }

    @Test
    fun `repository filters match path, name, suffix and substring`() {
        server.enqueue(
            json(
                listOf(
                    projectJson(1, "alpha", "group/alpha"),
                    projectJson(2, "beta", "team/beta"),
                    projectJson(3, "gamma", "gamma"),
                    projectJson(4, "delta", "group/other-delta-x"),
                    projectJson(5, "unrelated", "org/none"),
                ),
            ),
        )
        val config =
            GitlabConfig(
                name = "gitlab",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                accessToken = "tok",
            )

        @Suppress("UNCHECKED_CAST")
        val resolved =
            invokePrivate(
                "resolveProjects",
                config,
                listOf("group/alpha", "BETA", "gamma", "delta", "  ", ""),
            ) as List<ContextMapAdapter.ContextProject>

        assertEquals(listOf(1L, 2L, 3L, 4L), resolved.map { it.id })
    }

    @Test
    fun `configured project ids are fetched one by one`() {
        server.enqueue(MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(projectJson(7, "seven", "g/seven")))
        server.enqueue(MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(projectJson(8, "eight", "g/eight")))
        val config =
            GitlabConfig(
                name = "gitlab",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                accessToken = "tok",
                projectIds = "7,8",
            )

        @Suppress("UNCHECKED_CAST")
        val resolved = invokePrivate("resolveProjects", config, emptyList<String>()) as List<ContextMapAdapter.ContextProject>

        assertEquals(listOf(7L, 8L), resolved.map { it.id })
    }

    @Test
    fun `repository tree paging walks every full page`() {
        val entry = """{"id":"i","name":"ctx","path":"docs/context.md","type":"blob"}"""
        server.enqueue(json(List(100) { entry }))
        server.enqueue(json(listOf(entry)))

        @Suppress("UNCHECKED_CAST")
        val tree = invokePrivate("fetchRepoTree", 5L, "main") as List<ContextMapAdapter.TreeEntry>

        assertEquals(101, tree.size)
        assertEquals("docs/context.md", tree.first().path)
    }

    @Test
    fun `an unreadable tree page ends the walk`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("no repo"))

        @Suppress("UNCHECKED_CAST")
        val tree = invokePrivate("fetchRepoTree", 6L, "main") as List<ContextMapAdapter.TreeEntry>

        assertTrue(tree.isEmpty())
    }

    @Test
    fun `raw file content is read verbatim`() {
        server.enqueue(MockResponse().setBody("```mermaid\ngraph TD\n```"))

        assertEquals("```mermaid\ngraph TD\n```", invokePrivate("fetchFileContent", 9L, "docs/c.md", "main"))
    }

    @Test
    fun `blob search pages until a short page arrives`() {
        val blob = """{"project_id":3,"path":"a/context.md","ref":"main","startline":4,"data":"keyword here"}"""
        server.enqueue(json(List(100) { blob }))
        server.enqueue(json(listOf(blob)))

        @Suppress("UNCHECKED_CAST")
        val blobs = invokePrivate("searchProjectBlobs", 3L, "keyword") as List<ContextMapAdapter.BlobResult>

        assertEquals(101, blobs.size)
        assertEquals("a/context.md", blobs.first().path)
    }

    @Test
    fun `a failing blob search yields nothing`() {
        server.enqueue(MockResponse().setResponseCode(503))

        @Suppress("UNCHECKED_CAST")
        assertTrue((invokePrivate("searchProjectBlobs", 3L, "keyword") as List<ContextMapAdapter.BlobResult>).isEmpty())
    }

    @Test
    fun `inactive configurations short circuit the keyword search`() {
        val config =
            GitlabConfig(
                name = "inactive",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                accessToken = "tok",
                active = false,
            )

        assertTrue(adapter.searchKeyword(config, "keyword", emptyList(), 1L).isEmpty())
    }
}
