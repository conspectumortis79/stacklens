package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GithubRepoFileClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = GithubRepoFileClient()

    @Test
    fun `isRelevantSourceFile accepts Kotlin files`() {
        assertTrue(client().isRelevantSourceFile("src/main/Foo.kt"))
    }

    @Test
    fun `isRelevantSourceFile accepts KTS files`() {
        assertTrue(client().isRelevantSourceFile("build.gradle.kts"))
    }

    @Test
    fun `isRelevantSourceFile accepts Java files`() {
        assertTrue(client().isRelevantSourceFile("src/main/Foo.java"))
    }

    @Test
    fun `isRelevantSourceFile accepts TypeScript files`() {
        assertTrue(client().isRelevantSourceFile("src/app.ts"))
        assertTrue(client().isRelevantSourceFile("src/app.tsx"))
    }

    @Test
    fun `isRelevantSourceFile accepts JavaScript files`() {
        assertTrue(client().isRelevantSourceFile("src/app.js"))
        assertTrue(client().isRelevantSourceFile("src/app.jsx"))
    }

    @Test
    fun `isRelevantSourceFile accepts Python files`() {
        assertTrue(client().isRelevantSourceFile("src/app.py"))
    }

    @Test
    fun `isRelevantSourceFile accepts Go files`() {
        assertTrue(client().isRelevantSourceFile("src/main.go"))
    }

    @Test
    fun `isRelevantSourceFile accepts Rust files`() {
        assertTrue(client().isRelevantSourceFile("src/main.rs"))
    }

    @Test
    fun `isRelevantSourceFile accepts Vue files`() {
        assertTrue(client().isRelevantSourceFile("src/App.vue"))
    }

    @Test
    fun `isRelevantSourceFile accepts PHP files`() {
        assertTrue(client().isRelevantSourceFile("src/index.php"))
    }

    @Test
    fun `isRelevantSourceFile rejects Markdown files`() {
        assertFalse(client().isRelevantSourceFile("README.md"))
    }

    @Test
    fun `isRelevantSourceFile rejects text files`() {
        assertFalse(client().isRelevantSourceFile("notes.txt"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in node_modules`() {
        assertFalse(client().isRelevantSourceFile("src/node_modules/lodash/index.js"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in dot_git directory`() {
        assertFalse(client().isRelevantSourceFile("src/.git/HEAD"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in build directory`() {
        assertFalse(client().isRelevantSourceFile("src/build/classes/main/Foo.class"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in target directory`() {
        assertFalse(client().isRelevantSourceFile("src/target/classes/Foo.class"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in dist directory`() {
        assertFalse(client().isRelevantSourceFile("src/dist/bundle.js"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in dot_gradle directory`() {
        assertFalse(client().isRelevantSourceFile("src/.gradle/file.kt"))
        assertFalse(client().isRelevantSourceFile("module/.gradle/script.gradle"))
    }

    @Test
    fun `isRelevantSourceFile accepts nested Kotlin files`() {
        assertTrue(client().isRelevantSourceFile("src/main/kotlin/com/example/Foo.kt"))
    }

    @Test
    fun `isRelevantSourceFile is case-insensitive for extension`() {
        assertTrue(client().isRelevantSourceFile("src/main.FOO.KT"))
    }

    @Test
    fun `isRelevantSourceFile rejects files without extension`() {
        assertFalse(client().isRelevantSourceFile("LICENSE"))
        assertFalse(client().isRelevantSourceFile("Dockerfile"))
    }

    @Test
    fun `fetchRepoFiles returns empty when tree is empty`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{"tree":[]}""")
                .addHeader("Content-Type", "application/json"),
        )
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchRepoFiles returns empty on tree fetch error`() {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(500))
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchRepoFiles returns empty when tree response has no body`() {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204))
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchRepoFiles returns empty when tree response has no tree field`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{}""")
                .addHeader("Content-Type", "application/json"),
        )
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchRepoFiles skips blob with non-relevant extension`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{"tree":[{"path":"README.md","type":"blob"},{"path":"src/A.kt","type":"blob"},{"path":"src/folder","type":"tree"}]}""")
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody(
                    """{"content":"${java.util.Base64.getEncoder().encodeToString("class A".toByteArray())}"}""",
                ).addHeader("Content-Type", "application/json"),
        )
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertEquals(1, files.size)
        assertEquals("src/A.kt", files[0].path)
    }

    @Test
    fun `fetchRepoFiles returns empty when file content fetch fails`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{"tree":[{"path":"src/A.kt","type":"blob"}]}""")
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(500))
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchRepoFiles returns empty when file content has no content field`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{"tree":[{"path":"src/A.kt","type":"blob"}]}""")
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setBody("""{}""")
                .addHeader("Content-Type", "application/json"),
        )
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val files = GithubRepoFileClient().fetchRepoFiles(webClient, "octo", "cat")
        assertEquals(1, files.size)
        assertEquals("src/A.kt", files[0].path)
        assertEquals("", files[0].content)
    }

    @Test
    fun `fetchFileContent returns null when response body is empty`() {
        server.enqueue(
            okhttp3.mockwebserver
                .MockResponse()
                .setResponseCode(200)
                .setBody("")
                .removeHeader("Content-Type"),
        )
        val webClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl(server.url("/").toString().removeSuffix("/"))
                .build()
        val result = GithubRepoFileClient().fetchFileContent(webClient, "octo", "cat", "src/A.kt")
        assertNull(result)
    }
}
