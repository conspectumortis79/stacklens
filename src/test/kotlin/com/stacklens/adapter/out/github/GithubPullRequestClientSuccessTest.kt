package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class GithubPullRequestClientSuccessTest {
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
    fun `createBranch returns true on 2xx response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"ref":"refs/heads/feature","sha":"abc"}"""),
        )
        assertTrue(pr.createBranch(client, "owner", "repo", "baseSha", "feature"))
    }

    @Test
    fun `createBranch returns true on empty response body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(""),
        )
        assertTrue(pr.createBranch(client, "owner", "repo", "baseSha", "feature"))
    }

    @Test
    fun `commitFile returns true without baseSha`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"content":{"sha":"abc"}}"""),
        )
        assertTrue(
            pr.commitFile(
                client = client,
                owner = "owner",
                repo = "repo",
                path = "src/New.kt",
                message = "feat: add new file",
                content = "class New",
                branch = "feature",
                baseSha = null,
            ),
        )
    }

    @Test
    fun `commitFile returns true with baseSha`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"commit":{"sha":"abc"}}"""),
        )
        assertTrue(
            pr.commitFile(
                client = client,
                owner = "owner",
                repo = "repo",
                path = "src/Foo.kt",
                message = "fix: update file",
                content = "class Foo",
                branch = "feature",
                baseSha = "baseSha",
            ),
        )
    }

    @Test
    fun `openPullRequest returns html_url from response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"html_url":"https://github.com/o/r/pull/42"}"""),
        )
        val url =
            pr.openPullRequest(
                client = client,
                owner = "owner",
                repo = "repo",
                title = "PR title",
                head = "feature",
                base = "main",
                body = "body text",
            )
        assertEquals("https://github.com/o/r/pull/42", url)
    }

    @Test
    fun `openPullRequest returns empty string when html_url missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"number":42}"""),
        )
        val url =
            pr.openPullRequest(
                client = client,
                owner = "owner",
                repo = "repo",
                title = "PR title",
                head = "feature",
                base = "main",
                body = "body text",
            )
        assertEquals("", url)
    }

    @Test
    fun `openPullRequest returns null on server error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val url =
            pr.openPullRequest(
                client = client,
                owner = "owner",
                repo = "repo",
                title = "PR title",
                head = "feature",
                base = "main",
                body = "body text",
            )
        assertNull(url)
    }

    @Test
    fun `openPullRequest returns null on 422 validation failed`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("validation failed"))
        val url =
            pr.openPullRequest(
                client = client,
                owner = "owner",
                repo = "repo",
                title = "PR title",
                head = "feature",
                base = "main",
                body = "body text",
            )
        assertNull(url)
    }

    @Test
    fun `openPullRequest returns null on malformed body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("not json"),
        )
        val url =
            pr.openPullRequest(
                client = client,
                owner = "owner",
                repo = "repo",
                title = "PR title",
                head = "feature",
                base = "main",
                body = "body text",
            )
        assertNull(url)
    }
}
