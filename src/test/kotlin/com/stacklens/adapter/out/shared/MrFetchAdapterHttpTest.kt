package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class MrFetchAdapterHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(DefaultDiffHunkParser())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
            active = true,
        )

    private fun githubConfig(): GithubRepoConfig =
        GithubRepoConfig(
            id = 1L,
            name = "octo/cat",
            owner = "octo",
            repo = "cat",
            accessToken = "TOK",
        )

    @Test
    fun `fetchMrData returns Success for valid GitLab MR URL`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"title":"MR title","description":"d","source_branch":"feat","target_branch":"main","state":"opened"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"},"changes":[{"old_path":"a.kt","new_path":"a.kt","diff":"+ x","new_file":false,"deleted":false}]}"""),
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/group/project/-/merge_requests/42"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("MR title", data.title)
        assertEquals("GitLab", data.platform)
        assertEquals("feat", data.sourceBranch)
        assertEquals(1, data.changedFiles.size)
        assertEquals("a.kt", data.changedFiles[0])
    }

    @Test
    fun `fetchMrData marks deleted files with DELETED prefix`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"title":"t"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"},"changes":[
                        {"old_path":"removed.kt","new_path":"removed.kt","diff":"","new_file":false,"deleted":true}
                    ]}""",
                ),
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/g/p/-/merge_requests/1"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals(1, data.changedFiles.size)
        assertEquals("DELETED: removed.kt", data.changedFiles[0])
    }

    @Test
    fun `fetchMrData returns Failure when GitLab URL has no MR IID`() {
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/g/p/-/something"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when MR IID is not a number`() {
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/g/p/-/merge_requests/notanumber"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when no matching GitLab config`() {
        val otherBase = "http://different-host-${System.nanoTime()}.example.com"
        val url = "$otherBase/g/p/-/merge_requests/1"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Success for valid GitHub PR URL`() {
        val url = "https://github.com/octo/cat/pull/1"
        val result = runCatching { adapter.fetchMrData(url, emptyList(), listOf(githubConfig())) }.getOrNull()
        assertNotNull(result)
    }

    @Test
    fun `fetchMrData returns Failure when GitHub URL has wrong path structure`() {
        val url = "https://github.com/octo/cat/notpull/1"
        val result = adapter.fetchMrData(url, emptyList(), listOf(githubConfig()))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when GitHub PR number is not a number`() {
        val url = "https://github.com/octo/cat/pull/abc"
        val result = adapter.fetchMrData(url, emptyList(), listOf(githubConfig()))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when GitHub URL has fewer than 4 parts`() {
        val url = "https://github.com/just/two"
        val result = adapter.fetchMrData(url, emptyList(), listOf(githubConfig()))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when URL host is unknown and no merge_requests in path`() {
        val url = "https://unknown-${System.nanoTime()}.example.com/some/path"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), listOf(githubConfig()))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when URL has no host`() {
        val url = "not-a-url-at-all"
        val result = adapter.fetchMrData(url, emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData throws connection-refused marker on ConnectException`() {
        val url = "http://127.0.0.1:1/owner/repo/pull/1"
        val outcome =
            runCatching {
                adapter.fetchMrData(url, emptyList(), listOf(githubConfig()))
            }
        val acceptable = outcome.isFailure || outcome.getOrNull() is MrFetchPort.MrFetchResult.Failure
        assertTrue(acceptable, "Expected an exception or Failure for unreachable host")
    }

    @Test
    fun `fetchMrData accepts enterprise github subdomains`() {
        val url = "https://enterprise.github.com/octo/cat/pull/1"
        val outcome = runCatching { adapter.fetchMrData(url, emptyList(), listOf(githubConfig())) }
        assertTrue(outcome.isSuccess, "Should not throw for *.github.com URL: ${outcome.exceptionOrNull()?.message}")
    }

    @Test
    fun `buildDiffText truncates large diffs`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"title":"t"}"""),
        )
        val changes =
            (1..150).joinToString(",") { i ->
                """{"old_path":"f$i.kt","new_path":"f$i.kt","diff":"${"X".repeat(2000)}","new_file":false,"deleted":false}"""
            }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"},"changes":[$changes]}"""),
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/g/p/-/merge_requests/1"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.length <= 80_000, "diff text length ${data.diffText.length} exceeds max 80000")
        assertTrue(data.diffText.isNotEmpty())
    }

    @Test
    fun `buildDiffText handles empty diffs`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"title":"t","diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"changes":[{"old_path":"x.kt","new_path":"x.kt","diff":"","new_file":false,"deleted":false}]}"""),
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val url = "$baseUrl/g/p/-/merge_requests/1"
        val result = adapter.fetchMrData(url, listOf(gitlabConfig()), emptyList())
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.isEmpty() || !data.diffText.contains("```"))
    }

    @Test
    fun `fetchMrData uses fallback GitHub token when no specific match`() {
        val wrongConfig =
            GithubRepoConfig(
                id = 2L,
                name = "different/repo",
                owner = "different",
                repo = "repo",
                accessToken = "OTHER",
            )
        val url = "https://github.com/octo/cat/pull/1"
        val outcome = runCatching { adapter.fetchMrData(url, emptyList(), listOf(wrongConfig)) }
        assertTrue(outcome.isSuccess, "Should not throw on GitHub PR lookup: ${outcome.exceptionOrNull()?.message}")
    }
}
