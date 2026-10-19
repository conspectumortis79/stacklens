package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GithubPullRequestClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebClient
    private val pr = GithubPullRequestClient()

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
    fun `createBranch throws on error response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        assertThrows(IllegalStateException::class.java) {
            pr.createBranch(client, "owner", "repo", "abc", "x")
        }
    }

    @Test
    fun `createBranch throws on 422 already exists`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("already exists"))
        assertThrows(IllegalStateException::class.java) {
            pr.createBranch(client, "owner", "repo", "abc", "dup")
        }
    }

    @Test
    fun `commitFile throws on error response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        assertThrows(IllegalStateException::class.java) {
            pr.commitFile(
                client,
                "owner",
                "repo",
                "src/Foo.kt",
                "msg",
                "content",
                "main",
                null,
            )
        }
    }

    @Test
    fun `commitFile throws on 422`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("validation failed"))
        assertThrows(IllegalStateException::class.java) {
            pr.commitFile(
                client,
                "owner",
                "repo",
                "src/Foo.kt",
                "msg",
                "content",
                "main",
                "abc123",
            )
        }
    }

    @Test
    fun `openPullRequest returns html_url on success`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"html_url":"https://github.com/owner/repo/pull/1","number":1}"""),
        )
        val url = pr.openPullRequest(client, "owner", "repo", "title", "head", "base", "body")
        assertEquals("https://github.com/owner/repo/pull/1", url)
    }

    @Test
    fun `openPullRequest returns null when response is empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )
        val url = pr.openPullRequest(client, "owner", "repo", "title", "head", "base", "body")
        assertNull(url)
    }

    @Test
    fun `openPullRequest returns null on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val url = pr.openPullRequest(client, "owner", "repo", "title", "head", "base", "body")
        assertNull(url)
    }
}
