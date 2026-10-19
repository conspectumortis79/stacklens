package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64

class GithubRepoFileClientHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebClient
    private val repo = GithubRepoFileClient()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .build()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchRepoTree returns parsed entries from tree field`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"tree":[
                        |{"path":"src/Foo.kt","type":"blob"},
                        |{"path":"src/Bar.java","type":"blob"},
                        |{"path":"src/img.png","type":"blob"}
                        |]}
                    """.trimMargin(),
                ),
        )
        val tree = repo.fetchRepoTree(client, "octo", "cat")
        assertEquals(3, tree.size)
        assertEquals("src/Foo.kt", tree[0].path)
        assertEquals("blob", tree[0].type)
    }

    @Test
    fun `fetchRepoTree returns empty list when tree field missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"sha":"abc"}"""),
        )
        assertTrue(repo.fetchRepoTree(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoTree returns empty list on 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertTrue(repo.fetchRepoTree(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoTree returns empty list on server error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertTrue(repo.fetchRepoTree(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoTree returns empty list when body is empty`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(""),
        )
        assertTrue(repo.fetchRepoTree(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoTree uses default path and type when missing in entry`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"tree":[{"type":"blob"}]}"""),
        )
        val tree = repo.fetchRepoTree(client, "octo", "cat")
        assertEquals(1, tree.size)
        assertEquals("", tree[0].path)
        assertEquals("blob", tree[0].type)
    }

    @Test
    fun `fetchFileContent decodes base64 content`() {
        val original = "class Foo {\n  fun bar() = 1\n}\n"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":"$encoded"}"""),
        )
        val content = repo.fetchFileContent(client, "octo", "cat", "src/Foo.kt")
        assertEquals(original, content)
    }

    @Test
    fun `fetchFileContent returns empty string when content field missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"sha":"abc"}"""),
        )
        assertEquals("", repo.fetchFileContent(client, "octo", "cat", "src/Foo.kt"))
    }

    @Test
    fun `fetchFileContent returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
        assertNull(repo.fetchFileContent(client, "octo", "cat", "missing.kt"))
    }

    @Test
    fun `fetchFileContent returns null on server error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertNull(repo.fetchFileContent(client, "octo", "cat", "src/Foo.kt"))
    }

    @Test
    fun `fetchFileContent returns null on malformed base64`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":"!!! not valid base64 !!!"}"""),
        )
        assertNull(repo.fetchFileContent(client, "octo", "cat", "src/Foo.kt"))
    }

    @Test
    fun `fetchRepoFiles returns list of JiraRepoFile with base64-decoded content`() {
        val original = "class Foo"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"tree":[{"path":"src/Foo.kt","type":"blob"}]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":"$encoded"}"""),
        )
        val files = repo.fetchRepoFiles(client, "octo", "cat")
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals(original, files[0].content)
    }

    @Test
    fun `fetchRepoFiles skips tree entries whose content fetch returns null`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"tree":[
                        |{"path":"src/Good.kt","type":"blob"},
                        |{"path":"src/Missing.kt","type":"blob"}
                        |]}
                    """.trimMargin(),
                ),
        )
        val goodEncoded = Base64.getEncoder().encodeToString("good".toByteArray())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":"$goodEncoded"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
        val files = repo.fetchRepoFiles(client, "octo", "cat")
        assertEquals(1, files.size)
        assertEquals("src/Good.kt", files[0].path)
    }

    @Test
    fun `fetchRepoFiles returns empty list when tree is empty`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"tree":[]}"""),
        )
        assertTrue(repo.fetchRepoFiles(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoFiles returns empty list when tree endpoint fails`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server"))
        assertTrue(repo.fetchRepoFiles(client, "octo", "cat").isEmpty())
    }

    @Test
    fun `fetchRepoFiles filters out non-blob entries`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"tree":[
                        |{"path":"src/Foo.kt","type":"blob"},
                        |{"path":"docs","type":"tree"}
                        |]}
                    """.trimMargin(),
                ),
        )
        val encoded = Base64.getEncoder().encodeToString("code".toByteArray())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":"$encoded"}"""),
        )
        val files = repo.fetchRepoFiles(client, "octo", "cat")
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
    }

    @Test
    fun `fetchRepoFiles caps file count to MAX_REPO_FILES_PER_PROJECT`() {
        val entries =
            (1..150)
                .joinToString(",") { """{"path":"src/File$it.kt","type":"blob"}""" }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"tree":[$entries]}"""),
        )
        val encoded = Base64.getEncoder().encodeToString("x".toByteArray())
        repeat(100) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"content":"$encoded"}"""),
            )
        }
        val files = repo.fetchRepoFiles(client, "octo", "cat")
        assertNotNull(files)
        assertTrue(files.size <= 100, "Expected at most 100 files, got ${files.size}")
    }
}
