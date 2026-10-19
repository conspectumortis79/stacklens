package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodePatch
import com.stacklens.domain.enums.FileChangeType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class GitlabBranchCommitApiClientTailCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GitlabBranchCommitApiClient
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GitlabBranchCommitApiClient()
        webClient = WebClient.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body)

    @Test
    fun `commitFiles starts from an empty source when the raw fetch is unavailable`() {
        var rawRequested = false
        var committedBody = ""
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/raw")) {
                        rawRequested = true
                        return MockResponse().setResponseCode(500)
                    }
                    committedBody = request.body.readUtf8()
                    return json("{}")
                }
            }
        api.commitFiles(
            client = webClient,
            projectId = 7L,
            branch = "main",
            message = "msg",
            files =
                listOf(
                    JiraCodeFile(
                        repository = "g/r",
                        path = "src/App.kt",
                        language = "kotlin",
                        description = "d",
                        changeType = FileChangeType.PATCH,
                        code = "",
                        patches = listOf(JiraCodePatch(search = "class", replace = "object")),
                    ),
                ),
        )
        assertEquals(true, rawRequested)
        assertEquals(true, committedBody.contains("\"content\":\"\""))
    }

    @Test
    fun `resolveProjectByNameOrPath matches the whole path ignoring case`() {
        val projectApi = GitlabProjectApiClient()
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.startsWith("/api/v4/projects/g%2Falpha")) {
                        return MockResponse().setResponseCode(404)
                    }
                    return json("""[{"id":11,"name":"Other","path_with_namespace":"G/alpha"}]""")
                }
            }
        val resolved = projectApi.resolveProjectByNameOrPath(webClient, groupId = null, repoName = "g/alpha")
        assertEquals(11L, resolved?.id)
    }
}
