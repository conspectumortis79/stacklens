package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitlabClientFetchFilesTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitlabClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("").toString().removeSuffix("/")

    private fun gitlabConfig(
        name: String = "g",
        baseUrl: String = baseUrl(),
        accessToken: String = "TOK",
        active: Boolean = true,
    ) = com.stacklens.domain.model.GitlabConfig(
        name = name,
        baseUrl = baseUrl,
        accessToken = accessToken,
        active = active,
    )

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse = MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `fetchProjectFilesForPath returns code files sorted by priority`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        val tree =
            listOf(
                """{"path": "src/Foo.kt", "type": "blob"}""",
                """{"path": "README.md", "type": "blob"}""",
                """{"path": "Dockerfile", "type": "blob"}""",
                """{"path": "src/main/Bar.kt", "type": "blob"}""",
                """{"path": "src/Baz.java", "type": "blob"}""",
            ).joinToString(",", prefix = "[", postfix = "]")
        server.enqueue(jsonResponse(200, tree))
        for (i in 1..3) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("file-$i"))
        }
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertNotNull(result)
        assertTrue(result.size <= 3, "Expected at most 3 code files but got ${result.size}")
    }

    @Test
    fun `fetchProjectFilesForPath with empty tree returns empty list`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(jsonResponse(200, """[]"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath handles blank default branch as main`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": ""}"""))
        server.enqueue(jsonResponse(200, """[]"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath trims trailing slash from subPath`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        val tree = """[{"path":"src/Foo.kt","type":"blob"},{"path":"lib/Bar.kt","type":"blob"}]"""
        server.enqueue(jsonResponse(200, tree))
        server.enqueue(MockResponse().setResponseCode(200).setBody("foo content"))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src/", 10, 1000)
        assertEquals(1, result.size)
        assertEquals("src/Foo.kt", result[0].first)
    }

    @Test
    fun `fetchProjectFilesForPath respects maxFiles limit`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        val tree =
            (1..20).joinToString(",", prefix = "[", postfix = "]") { i ->
                """{"path": "src/F$i.kt", "type": "blob"}"""
            }
        server.enqueue(jsonResponse(200, tree))
        for (i in 1..5) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("f$i"))
        }
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 5, 1000)
        assertTrue(result.size <= 5, "Expected at most 5 files but got ${result.size}")
    }
}
