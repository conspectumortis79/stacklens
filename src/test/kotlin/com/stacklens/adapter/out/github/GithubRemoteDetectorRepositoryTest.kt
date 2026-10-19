package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class GithubRemoteDetectorRepositoryTest {
    private val port = mock<GithubRepoConfigPort>()

    private val detector = GithubRemoteDetector(port)

    private fun config(
        owner: String,
        repo: String,
        token: String?,
    ) = GithubRepoConfig(id = 1L, name = "$owner/$repo", owner = owner, repo = repo, accessToken = token, active = true)

    private fun repositoryWithRemote(
        dir: File,
        url: String,
    ): File {
        ProcessBuilder("git", "init", "-q", dir.absolutePath).start().waitFor()
        ProcessBuilder("git", "-C", dir.absolutePath, "remote", "add", "origin", url)
            .start()
            .waitFor()
        return dir
    }

    @Test
    fun `a cloned repository reports the stored configuration for its remote`(
        @TempDir dir: Path,
    ) {
        repositoryWithRemote(dir.toFile(), "https://github.com/acme/widgets.git")
        whenever(port.findAllGithubRepoConfigs()).thenReturn(listOf(config("ACME", "Widgets", "geheim")))

        val detected = detector.detectGithubRemote(dir)

        assertEquals("acme/widgets", detected?.name?.lowercase())
        assertEquals("geheim", detector.resolveToken(detected!!))
    }

    @Test
    fun `a remote without a stored configuration is described by its own identifiers`(
        @TempDir dir: Path,
    ) {
        repositoryWithRemote(dir.toFile(), "git@github.com:other/thing")
        whenever(port.findAllGithubRepoConfigs()).thenReturn(listOf(config("acme", "widgets", "anders")))

        val detected = detector.detectGithubRemote(dir)

        assertEquals("other", detected!!.owner)
        assertEquals("thing", detected.repo)
        assertNull(detected.accessToken)
    }

    @Test
    fun `a remote of another hosting service yields nothing`(
        @TempDir dir: Path,
    ) {
        repositoryWithRemote(dir.toFile(), "https://gitlab.example.com/team/projekt.git")
        whenever(port.findAllGithubRepoConfigs()).thenReturn(emptyList())

        assertNull(detector.detectGithubRemote(dir))
    }

    @Test
    fun `a directory without git metadata cannot answer with a remote`(
        @TempDir dir: Path,
    ) {
        assertNull(detector.detectGithubRemote(dir))
    }

    @Test
    fun `token resolution falls back to any stored configuration of the same project`() {
        whenever(port.findAllGithubRepoConfigs()).thenReturn(listOf(config("Acme", "Widgets", "token")))

        val token = detector.resolveToken(GithubRepoConfig(name = "n", owner = "acme", repo = "widgets", accessToken = "   "))

        assertEquals("token", token)
    }

    @Test
    fun `token resolution gives up when no configuration matches`() {
        whenever(port.findAllGithubRepoConfigs()).thenReturn(listOf(config("acme", "andere", "x")))

        assertNull(detector.resolveToken(GithubRepoConfig(name = "n", owner = "acme", repo = "widgets")))
    }

    @Test
    fun `an inline access token wins over the stored configurations`() {
        assertEquals("direkt", detector.resolveToken(GithubRepoConfig(name = "n", owner = "a", repo = "b", accessToken = "direkt")))
    }
}
