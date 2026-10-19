package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabRepoFileApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabRepoFileApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitlabRepoFileApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun directClient(): WebClient {
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        return WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(connector)
            .build()
    }

    @Test
    fun `fetchRepoTree returns tree entries`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}]"""),
        )
        val result = client.fetchRepoTree(directClient(), projectId = 42L)
        assertEquals(1, result.size)
        assertEquals("src/Foo.kt", result[0].path)
        assertEquals("blob", result[0].type)
    }

    @Test
    fun `fetchRepoTree paginates until fewer than 100 results`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[" + (1..100).joinToString(",") { """{"id":"a$it","name":"f$it","type":"blob","path":"src/f$it"}""" } + "]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[" + (1..50).joinToString(",") { """{"id":"b$it","name":"g$it","type":"blob","path":"src/g$it"}""" } + "]"),
        )
        val result = client.fetchRepoTree(directClient(), projectId = 42L)
        assertEquals(150, result.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchRepoTree caps at 10 pages even if all pages return 100`() {
        repeat(10) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[" + (1..100).joinToString(",") { """{"id":"a$it","name":"f$it","type":"blob","path":"src/f$it"}""" } + "]"),
            )
        }
        client.fetchRepoTree(directClient(), projectId = 42L)
        assertEquals(10, server.requestCount)
    }

    @Test
    fun `fetchRepoTree returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchRepoTree(directClient(), projectId = 42L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchRawFile returns file content`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("file content"),
        )
        val result = client.fetchRawFile(directClient(), projectId = 42L, filePath = "src/Foo.kt")
        assertEquals("file content", result)
    }

    @Test
    fun `fetchRawFile returns null on error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchRawFile(directClient(), projectId = 42L, filePath = "missing.kt")
        assertNull(result)
    }

    @Test
    fun `fetchRawFile URL-encodes the file path`() {
        server.enqueue(MockResponse().setBody("content"))
        client.fetchRawFile(directClient(), projectId = 42L, filePath = "src/sub dir/File.kt")
        val req = server.takeRequest()
        assertTrue(
            req.path!!.contains("/api/v4/projects/42/repository/files/"),
            "expected path to contain /api/v4/projects/42/repository/files/ but got ${req.path}",
        )
    }

    @Test
    fun `fetchRepoFiles returns JiraRepoFile list for blob entries`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"},
                        {"id":"b","name":"tree","type":"tree","path":"src/"},
                        {"id":"c","name":"README.md","type":"blob","path":"README.md"}
                    ]""",
                ),
        )
        server.enqueue(MockResponse().setBody("class Foo"))
        server.enqueue(MockResponse().setBody("# Readme"))

        val isRelevant: (String) -> Boolean = { it.endsWith(".kt") || it.endsWith(".md") }
        val result = client.fetchRepoFiles(directClient(), projectId = 42L, isRelevant)
        assertEquals(2, result.size)
        assertEquals("src/Foo.kt", result[0].path)
        assertEquals("class Foo", result[0].content)
        assertEquals("README.md", result[1].path)
    }

    @Test
    fun `fetchRepoFiles skips blobs whose raw fetch fails`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"},{"id":"b","name":"Bar.kt","type":"blob","path":"src/Bar.kt"}]""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("class Bar"))

        val result = client.fetchRepoFiles(directClient(), projectId = 42L) { true }
        assertEquals(1, result.size)
        assertEquals("src/Bar.kt", result[0].path)
    }

    @Test
    fun `fetchRepoFiles filters out non-blob entries`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":"a","name":"src","type":"tree","path":"src/"},
                        {"id":"b","name":"sub","type":"tree","path":"src/sub/"}
                    ]""",
                ),
        )
        val result = client.fetchRepoFiles(directClient(), projectId = 42L) { true }
        assertTrue(result.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchRepoFiles applies isRelevantSourceFile filter`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"},
                        {"id":"b","name":"Foo.bin","type":"blob","path":"src/Foo.bin"}
                    ]""",
                ),
        )
        server.enqueue(MockResponse().setBody("class Foo"))

        val isRelevant: (String) -> Boolean = { it.endsWith(".kt") }
        val result = client.fetchRepoFiles(directClient(), projectId = 42L, isRelevant)
        assertEquals(1, result.size)
        assertEquals("src/Foo.kt", result[0].path)
    }

    @Test
    fun `fetchRepoFiles sorts by priority extension (kt first)`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":"a","name":"Foo.md","type":"blob","path":"src/Foo.md"},
                        {"id":"b","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}
                    ]""",
                ),
        )
        server.enqueue(MockResponse().setBody("# md"))
        server.enqueue(MockResponse().setBody("// kt"))

        val result = client.fetchRepoFiles(directClient(), projectId = 42L) { true }
        assertEquals(2, result.size)
        assertEquals("src/Foo.kt", result[0].path)
        assertEquals("src/Foo.md", result[1].path)
    }
}
