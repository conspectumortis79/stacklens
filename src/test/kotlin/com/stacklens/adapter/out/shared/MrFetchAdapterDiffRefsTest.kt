package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MrFetchAdapterDiffRefsTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(DefaultDiffHunkParser(), connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig() =
        GitlabConfig(
            id = 102L,
            name = "Banana",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "PRIVATE-TOKEN",
        )

    @Test
    fun `SHAs are populated from merge_requests changes endpoint, not from bare merge_requests endpoint`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 264,
                      "iid": 264,
                      "title": "Test MR",
                      "description": null,
                      "source_branch": "feat",
                      "target_branch": "main",
                      "author": {"name": "alice"},
                      "state": "opened"
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 264,
                      "iid": 264,
                      "title": "Test MR",
                      "diff_refs": {
                        "base_sha": "BASE_SHA_abc123",
                        "start_sha": "START_SHA_def456",
                        "head_sha": "HEAD_SHA_789xyz"
                      },
                      "changes": [
                        {"old_path": "src/Foo.kt", "new_path": "src/Foo.kt",
                         "diff": "@@ -1 +1 @@\n-old\n+new", "deleted_file": false}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val cfg = gitlabConfig()
        val result =
            adapter.fetchMrData(
                mrUrl = "${cfg.baseUrl}/ext-erni/-/merge_requests/264",
                gitlabConfigs = listOf(cfg),
                githubConfigs = emptyList(),
            )

        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success, got $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("BASE_SHA_abc123", data.diffBaseSha)
        assertEquals("START_SHA_def456", data.diffStartSha)
        assertEquals("HEAD_SHA_789xyz", data.diffHeadSha)
        assertEquals(listOf("src/Foo.kt"), data.changedFiles)
    }

    @Test
    fun `SHAs are populated even when bare merge_requests response also contains diff_refs (older GitLab or self-hosted quirks)`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 264, "iid": 264, "title": "Test MR",
                      "description": null, "source_branch": "feat", "target_branch": "main",
                      "author": {"name": "alice"}, "state": "opened",
                      "diff_refs": {"base_sha": "BASE_via_bare", "start_sha": "START_via_bare", "head_sha": "HEAD_via_bare"}
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 264, "iid": 264,
                      "diff_refs": {"base_sha": "BASE_via_changes", "start_sha": "START_via_changes", "head_sha": "HEAD_via_changes"},
                      "changes": []
                    }
                    """.trimIndent(),
                ),
        )

        val cfg = gitlabConfig()
        val result =
            adapter.fetchMrData(
                mrUrl = "${cfg.baseUrl}/ext-erni/-/merge_requests/264",
                gitlabConfigs = listOf(cfg),
                githubConfigs = emptyList(),
            )

        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("BASE_via_changes", data.diffBaseSha)
        assertEquals("START_via_changes", data.diffStartSha)
        assertEquals("HEAD_via_changes", data.diffHeadSha)
    }

    @Test
    fun `SHAs stay empty when both endpoints fail to return diff_refs`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":264,"iid":264,"title":"Test MR","description":null,"source_branch":"feat","target_branch":"main","author":{"name":"a"},"state":"opened"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":264,"iid":264,"changes":[]}"""),
        )
        val cfg = gitlabConfig()
        val result =
            adapter.fetchMrData(
                mrUrl = "${cfg.baseUrl}/ext-erni/-/merge_requests/264",
                gitlabConfigs = listOf(cfg),
                githubConfigs = emptyList(),
            )
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("", data.diffBaseSha)
        assertEquals("", data.diffStartSha)
        assertEquals("", data.diffHeadSha)
    }
}

class MrSuggestionAdapterShaForwardingTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrSuggestionAdapter
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrSuggestionAdapter(connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GitLab returns 201 when suggestion carries non-empty SHAs and the position block is complete`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[{"web_url":"https://gl/discussion/123"}]}"""),
        )
        val cfg =
            GitlabConfig(
                id = 102L,
                name = "Banana",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "PRIVATE-TOKEN",
            )
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://gitlab.fly-internal.de/fulfillment/ext-erni/-/merge_requests/264",
                platform = "GitLab",
                gitConfigId = 102L,
                gitlabConfigs = listOf(cfg),
                githubConfigs = emptyList(),
                diffBaseSha = "BASE_SHA_abc123",
                diffStartSha = "START_SHA_def456",
                diffHeadSha = "HEAD_SHA_789xyz",
                filePath = "src/Foo.kt",
                lineNumber = 42,
                suggestedCode = "println(\"hello\")",
            )
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("discussion/123"), "got $url")

        val recorded = server.takeRequest()
        val json = mapper.readTree(recorded.body.readUtf8())
        val position = json.get("position")
        assertNotNull(position, "GitLab requires the position block")
        assertEquals("BASE_SHA_abc123", position.get("base_sha").asText())
        assertEquals("START_SHA_def456", position.get("start_sha").asText())
        assertEquals("HEAD_SHA_789xyz", position.get("head_sha").asText())
        assertEquals("text", position.get("position_type").asText())
        assertEquals(42, position.get("new_line").asInt())
    }

    @Test
    fun `GitLab returns 422 when suggestion carries empty SHAs (regression check)`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":{"position":["sha must be a 40-character SHA1 hash"]}}"""),
        )
        val cfg =
            GitlabConfig(
                id = 102L,
                name = "Banana",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "PRIVATE-TOKEN",
            )
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://gitlab.fly-internal.de/fulfillment/ext-erni/-/merge_requests/264",
                platform = "GitLab",
                gitConfigId = 102L,
                gitlabConfigs = listOf(cfg),
                githubConfigs = emptyList(),
                diffBaseSha = "",
                diffStartSha = "",
                diffHeadSha = "",
                filePath = "src/Foo.kt",
                lineNumber = 42,
                suggestedCode = "println(\"hello\")",
            )
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.reactive.function.client.WebClientResponseException::class.java,
            ) { adapter.postSuggestion(req) }
        assertEquals(422, ex.statusCode.value())
    }
}
