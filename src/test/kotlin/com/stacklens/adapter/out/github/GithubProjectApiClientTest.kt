package com.stacklens.adapter.out.github

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class GithubProjectApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebClient
    private val api = GithubProjectApiClient()

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
    fun `buildClient returns a WebClient with the configured baseUrl`() {
        val built = api.buildClient("TOK")
        assertNotNull(built)
    }

    @Test
    fun `fetchDefaultBranch returns value from default_branch field`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"default_branch":"develop"}"""),
        )
        assertEquals("develop", api.fetchDefaultBranch(client, "octo", "cat"))
    }

    @Test
    fun `fetchDefaultBranch returns empty string when field missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"id":1}"""),
        )
        assertEquals("", api.fetchDefaultBranch(client, "octo", "cat"))
    }

    @Test
    fun `fetchDefaultBranch returns main fallback when block returns null`() {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("main", api.fetchDefaultBranch(client, "octo", "cat"))
    }

    @Test
    fun `fetchBranchSha returns sha from object branch`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"object":{"sha":"abc123","type":"commit"}}"""),
        )
        assertEquals("abc123", api.fetchBranchSha(client, "octo", "cat", "main"))
    }

    @Test
    fun `fetchBranchSha returns null on error response`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertNull(api.fetchBranchSha(client, "octo", "cat", "missing"))
    }

    @Test
    fun `fetchBranchSha returns empty string when object field missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"ref":"refs/heads/main"}"""),
        )
        assertEquals("", api.fetchBranchSha(client, "octo", "cat", "main"))
    }

    @Test
    fun `fetchBranchSha returns empty string when object has no sha`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"object":{"type":"commit"}}"""),
        )
        assertEquals("", api.fetchBranchSha(client, "octo", "cat", "main"))
    }

    @Test
    fun `fetchBranchSha returns null on malformed body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("not json at all"),
        )
        assertNull(api.fetchBranchSha(client, "octo", "cat", "main"))
    }

    @Test
    fun `fetchExistingFileSha returns sha value`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"sha":"file-sha-123"}"""),
        )
        assertEquals(
            "file-sha-123",
            api.fetchExistingFileSha(client, "octo", "cat", "src/Foo.kt", "main"),
        )
    }

    @Test
    fun `fetchExistingFileSha returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertNull(
            api.fetchExistingFileSha(client, "octo", "cat", "missing.kt", "main"),
        )
    }

    @Test
    fun `fetchExistingFileSha returns null on server error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server"))
        assertNull(
            api.fetchExistingFileSha(client, "octo", "cat", "src/Foo.kt", "main"),
        )
    }

    @Test
    fun `fetchExistingFileSha returns empty string when sha field missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"name":"Foo.kt"}"""),
        )
        assertEquals(
            "",
            api.fetchExistingFileSha(client, "octo", "cat", "src/Foo.kt", "main"),
        )
    }

    @Test
    fun `fetchExistingFileSha returns null on malformed body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("not json"),
        )
        assertNull(
            api.fetchExistingFileSha(client, "octo", "cat", "src/Foo.kt", "main"),
        )
    }
}
