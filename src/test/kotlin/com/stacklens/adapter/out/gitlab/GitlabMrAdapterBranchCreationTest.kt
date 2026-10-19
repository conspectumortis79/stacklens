package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType

class GitlabMrAdapterBranchCreationTest {
    private val server = MockWebServer()

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun config() =
        GitlabConfig(
            name = "gl",
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "secret",
        )

    @Test
    fun `a rejected branch answer is swallowed`() {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody("""{"message":"already exists"}"""))

        GitlabMrAdapter().createBranch(config(), 1L, "feature/x", "main")
    }

    @Test
    fun `a server error while creating a branch is only logged`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        GitlabMrAdapter().createBranch(config(), 2L, "feature/y", "main")
    }

    @Test
    fun `a client that cannot be built aborts the branch creation quietly`() {
        val factory = mock<ReactiveHttpClientFactory>()
        whenever(factory.buildGitlab(any(), any())).thenThrow(IllegalStateException("no client"))

        GitlabMrAdapter(httpFactory = factory).createBranch(config(), 3L, "feature/z", "main")
    }
}
