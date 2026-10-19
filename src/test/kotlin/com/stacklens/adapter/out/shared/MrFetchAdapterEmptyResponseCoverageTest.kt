package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MrFetchAdapterEmptyResponseCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun startAdapter() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(DefaultDiffHunkParser())
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun gitlabConfig(
        baseUrl: String,
        name: String = "g",
    ) = GitlabConfig(name = name, baseUrl = baseUrl, accessToken = "TOK", active = true)

    private fun githubConfig(
        owner: String,
        repo: String,
        token: String? = "GH",
    ) = GithubRepoConfig(id = 1L, name = "$owner/$repo", owner = owner, repo = repo, accessToken = token)

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `a url without a path is reported as an invalid mr url`() {
        val result = adapter.fetchMrData("http://localhost:${server.port}", emptyList(), emptyList())

        assertEquals(
            MrFetchPort.MrFetchResult.Failure("Invalid MR URL: no path"),
            result,
        )
    }

    @Test
    fun `a refused connection is reported with the server host and port`() {
        val url = "http://localhost:1/o/r/-/merge_requests/4"

        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter.fetchMrData(url, listOf(gitlabConfig("http://localhost:1")), emptyList())
            }

        assertEquals("[MR_CONN_REFUSED:localhost:1]", error.message)
    }

    @Test
    fun `a refused connection without an explicit port is reported with the bare host`() {
        val url = "http://[::1]/o/r/-/merge_requests/4"

        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter.fetchMrData(url, listOf(gitlabConfig("http://[::1]")), emptyList())
            }

        assertEquals("[MR_CONN_REFUSED:[::1]]", error.message)
    }

    @Test
    fun `an exception without a message falls back to the generic error reason`() {
        val failing = mock<GitlabConfig>()
        whenever(failing.resolvedUrls()).thenThrow(RuntimeException())

        val result =
            adapter.fetchMrData(
                "http://localhost:${server.port}/o/r/-/merge_requests/4",
                listOf(failing),
                emptyList(),
            )

        assertEquals(MrFetchPort.MrFetchResult.Failure("error"), result)
    }

    @Test
    fun `a gitlab host that only matches by its domain selects that configuration`() {
        val base = server.url("").toString().removeSuffix("/")

        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter.fetchMrData(
                    "$base/o/r/-/merge_requests/9",
                    listOf(gitlabConfig("http://localhost:1")),
                    emptyList(),
                )
            }

        assertEquals("[MR_CONN_REFUSED:localhost:${server.port}]", error.message)
    }

    @Test
    fun `a merge request without a changes payload still reports the merge request`() {
        val base = server.url("").toString().removeSuffix("/")
        server.enqueue(json("""{"title":"No changes","source_branch":"src","target_branch":"main"}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val result = adapter.fetchMrData("$base/o/r/-/merge_requests/11", listOf(gitlabConfig(base)), emptyList())

        val data = assertInstanceOf(MrFetchPort.MrFetchResult.Success::class.java, result).data
        assertEquals("No changes", data.title)
        assertTrue(data.changedFiles.isEmpty())
        assertEquals("", data.diffText)
    }

    @Test
    fun `a github pull request without a file payload reports no changed files`() {
        adapter.githubApiBaseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(json("""{"title":"Pr","body":"text"}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val result =
            adapter.fetchMrData(
                "https://github.com/acme/widget/pull/12",
                emptyList(),
                listOf(githubConfig(owner = "other", repo = "widget"), githubConfig(owner = "acme", repo = "other")),
            )

        val data = assertInstanceOf(MrFetchPort.MrFetchResult.Success::class.java, result).data
        assertEquals("GitHub", data.platform)
        assertTrue(data.changedFiles.isEmpty())
        assertEquals("Pr", data.title)
    }
}
