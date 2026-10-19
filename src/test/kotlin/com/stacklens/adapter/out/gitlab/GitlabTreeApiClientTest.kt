package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabTreeApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebClient
    private val api = GitlabTreeApiClient()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
                .build()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchRepoTree returns empty list on error response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val tree = api.fetchRepoTree(client, 1L, "main")
        assertTrue(tree.isEmpty())
    }

    @Test
    fun `fetchFileContent returns file content`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody("file content"))
        val content = api.fetchFileContent(client, 1L, "src/foo.kt", "main")
        assertEquals("file content", content)
    }

    @Test
    fun `fetchFileContent returns empty string on error`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertEquals("", api.fetchFileContent(client, 1L, "src/foo.kt", "main"))
    }

    @Test
    fun `fetchFileContent returns empty string when block returns null`() {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("", api.fetchFileContent(client, 1L, "src/foo.kt", "main"))
    }

    @Test
    fun `fetchRepoTree returns empty list when block returns null`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val tree = api.fetchRepoTree(client, 1L, "main")
        assertTrue(tree.isEmpty())
    }

    @Test
    fun `fetchRepoTree paginates through multiple full pages`() {
        val fullPage = (1..100).joinToString(",") { """{"id":"id$it","name":"f$it","path":"f$it","type":"blob"}""" }
        val fullPage2 = (1..100).joinToString(",") { """{"id":"id-${100 + it}","name":"g$it","path":"g$it","type":"blob"}""" }
        val lastPage = """{"id":"id-final","name":"last","path":"last","type":"blob"}"""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("[$fullPage]"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("[$fullPage2]"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("[$lastPage]"))
        val tree = api.fetchRepoTree(client, 1L, "main")
        assertEquals(201, tree.size)
    }

    @Test
    fun `fetchRepoTree stops after 20 full pages even if results continue`() {
        val fullPage = (1..100).joinToString(",") { """{"id":"id$it","name":"f$it","path":"f$it","type":"blob"}""" }
        repeat(21) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$fullPage]"),
            )
        }
        val tree = api.fetchRepoTree(client, 1L, "main")
        assertEquals(2000, tree.size)
    }

    @Test
    fun `isCodeFile returns true for Dockerfile`() {
        assertTrue(api.isCodeFile("Dockerfile"))
    }

    @Test
    fun `isCodeFile returns true for Dockerfile dot variant`() {
        assertTrue(api.isCodeFile("Dockerfile.dev"))
    }

    @Test
    fun `isCodeFile returns true for known code extensions`() {
        assertTrue(api.isCodeFile("src/foo.kt"))
        assertTrue(api.isCodeFile("Foo.java"))
        assertTrue(api.isCodeFile("a/b/c.ts"))
    }

    @Test
    fun `isCodeFile returns false for unknown extensions`() {
        assertFalse(api.isCodeFile("foo.png"))
        assertFalse(api.isCodeFile("foo.zip"))
        assertFalse(api.isCodeFile("README"))
    }

    @Test
    fun `isCodeFile returns false for files with no extension`() {
        assertFalse(api.isCodeFile("LICENSE"))
    }

    @Test
    fun `isCodeFile is case-insensitive on extension`() {
        assertTrue(api.isCodeFile("foo.KT"))
    }

    @Test
    fun `TreeEntry default values`() {
        val entry = TreeEntry()
        assertEquals("", entry.id)
        assertEquals("", entry.name)
        assertEquals("", entry.path)
        assertEquals("", entry.type)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RepoFileEntry(
        val id: String = "",
        val name: String = "",
        val path: String = "",
        val type: String = "",
    )

    @Test
    fun `RepoFileEntry fields are accessible`() {
        val e = RepoFileEntry(id = "1", name = "n", path = "p", type = "blob")
        assertEquals("1", e.id)
        assertEquals("n", e.name)
        assertEquals("p", e.path)
        assertEquals("blob", e.type)
    }
}
