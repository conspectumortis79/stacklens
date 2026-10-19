package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitlabTicketBridgeAdapterFallbackTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GitlabTicketBridgeAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GitlabTicketBridgeAdapter()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `name-based fallback returns null when fetchProjects throws`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://gitlab.example.com/team/repo.git")
        server.enqueue(MockResponse().setResponseCode(404))
        val result =
            adapter.detectGitlabRemote(
                tmp,
                listOf(
                    com.stacklens.domain.model.GitlabConfig(
                        name = "g",
                        baseUrl = "https://gitlab.example.com",
                        accessToken = "TOK",
                    ),
                ),
            )
        assertNotNull(result)
        assertEquals(null, result!!.second)
    }

    @Test
    fun `fallback to name search returns the project when it matches the slug`(
        @TempDir tmp: Path,
    ) {
        runGit(tmp, "init")
        runGit(tmp, "remote", "add", "origin", "https://gitlab.example.com/team/my-repo.git")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("server error"),
        )
        val result =
            adapter.detectGitlabRemote(
                tmp,
                listOf(
                    com.stacklens.domain.model.GitlabConfig(
                        name = "g",
                        baseUrl = "https://gitlab.example.com",
                        accessToken = "TOK",
                    ),
                ),
            )
        assertNotNull(result)
        assertEquals(null, result!!.second)
    }

    private fun runGit(
        dir: Path,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder("git", *args)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
        process.waitFor()
    }
}
