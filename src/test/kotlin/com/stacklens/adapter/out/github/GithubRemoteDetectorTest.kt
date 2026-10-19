package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GithubRemoteDetectorTest {
    private lateinit var githubRepoConfigPort: GithubRepoConfigPort
    private lateinit var detector: GithubRemoteDetector

    @BeforeEach
    fun setup() {
        githubRepoConfigPort = mock()
        detector = GithubRemoteDetector(githubRepoConfigPort)
    }

    @Test
    fun `parseGithubRemoteUrl parses HTTPS URL without dotgit suffix`() {
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
        assertEquals("owner/repo", result.name)
    }

    @Test
    fun `parseGithubRemoteUrl parses HTTPS URL with dotgit suffix`() {
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `parseGithubRemoteUrl parses SSH URL without dotgit suffix`() {
        val result = detector.parseGithubRemoteUrl("git@github.com:owner/repo")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `parseGithubRemoteUrl parses SSH URL with dotgit suffix`() {
        val result = detector.parseGithubRemoteUrl("git@github.com:owner/repo.git")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `parseGithubRemoteUrl returns null for non-github URL`() {
        assertNull(detector.parseGithubRemoteUrl("https://gitlab.com/owner/repo.git"))
    }

    @Test
    fun `parseGithubRemoteUrl returns null for empty string`() {
        assertNull(detector.parseGithubRemoteUrl(""))
    }

    @Test
    fun `parseGithubRemoteUrl returns null for malformed URL`() {
        assertNull(detector.parseGithubRemoteUrl("not a url"))
    }

    @Test
    fun `parseGithubRemoteUrl returns null for github URL without repo`() {
        assertNull(detector.parseGithubRemoteUrl("https://github.com/owner"))
    }

    @Test
    fun `parseGithubRemoteUrl handles repo names with dots`() {
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/my.project")
        assertNotNull(result)
        assertEquals("my.project", result!!.repo)
    }

    @Test
    fun `parseGithubRemoteUrl returns stored config when one exists for owner-repo`() {
        val stored =
            GithubRepoConfig(
                id = 42L,
                name = "my-stored-config",
                owner = "owner",
                repo = "repo",
                accessToken = "TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals(42L, result!!.id)
        assertEquals("TOKEN", result.accessToken)
    }

    @Test
    fun `parseGithubRemoteUrl matching is case-insensitive on owner and repo`() {
        val stored =
            GithubRepoConfig(
                id = 1L,
                name = "config",
                owner = "OWNER",
                repo = "REPO",
                accessToken = "TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals(1L, result!!.id)
    }

    @Test
    fun `parseGithubRemoteUrl constructs new config when no stored match`() {
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals(0L, result!!.id)
        assertEquals("owner/repo", result.name)
    }

    @Test
    fun `parseGithubRemoteUrl prefers stored config with matching owner-repo over others`() {
        val unrelated =
            GithubRepoConfig(
                id = 99L,
                name = "unrelated",
                owner = "other-owner",
                repo = "other-repo",
                accessToken = "OTHER_TOKEN",
                active = true,
            )
        val matching =
            GithubRepoConfig(
                id = 42L,
                name = "matching",
                owner = "owner",
                repo = "repo",
                accessToken = "MATCHING_TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(unrelated, matching))
        val result = detector.parseGithubRemoteUrl("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals(42L, result!!.id)
    }

    @Test
    fun `resolveToken returns config's own token when present`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = "OWN_TOKEN",
                active = true,
            )
        val token = detector.resolveToken(cfg)
        assertEquals("OWN_TOKEN", token)
    }

    @Test
    fun `resolveToken returns null when config has no token and no stored match`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = "",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        assertNull(detector.resolveToken(cfg))
    }

    @Test
    fun `resolveToken looks up stored config with matching owner-repo`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = null,
                active = true,
            )
        val stored =
            GithubRepoConfig(
                id = 2L,
                name = "stored",
                owner = "owner",
                repo = "repo",
                accessToken = "STORED_TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        assertEquals("STORED_TOKEN", detector.resolveToken(cfg))
    }

    @Test
    fun `resolveToken returns null when stored config has blank token`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = null,
                active = true,
            )
        val stored =
            GithubRepoConfig(
                id = 2L,
                name = "stored",
                owner = "owner",
                repo = "repo",
                accessToken = "",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        assertNull(detector.resolveToken(cfg))
    }

    @Test
    fun `resolveToken ignores stored configs with different owner-repo`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = null,
                active = true,
            )
        val stored =
            GithubRepoConfig(
                id = 2L,
                name = "stored",
                owner = "different-owner",
                repo = "different-repo",
                accessToken = "OTHER_TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        assertNull(detector.resolveToken(cfg))
    }

    @Test
    fun `resolveToken stored lookup is case-insensitive on owner and repo`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = null,
                active = true,
            )
        val stored =
            GithubRepoConfig(
                id = 2L,
                name = "stored",
                owner = "OWNER",
                repo = "REPO",
                accessToken = "STORED_TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        assertEquals("STORED_TOKEN", detector.resolveToken(cfg))
    }

    @Test
    fun `resolveToken blank token in config treated as missing`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "c",
                owner = "owner",
                repo = "repo",
                accessToken = "   ",
                active = true,
            )
        val stored =
            GithubRepoConfig(
                id = 2L,
                name = "stored",
                owner = "owner",
                repo = "repo",
                accessToken = "FALLBACK_TOKEN",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        assertEquals("FALLBACK_TOKEN", detector.resolveToken(cfg))
    }

    @Test
    fun `detectGithubRemote returns null when path doesn't exist`() {
        val nonExistentPath =
            java.nio.file.Paths
                .get("/nonexistent/path/that/does/not/exist")
        val result = detector.detectGithubRemote(nonExistentPath)
        assertNull(result)
    }

    @Test
    fun `detectGithubRemote returns null when not a git repo`() {
        val tmp =
            java.nio.file.Files
                .createTempDirectory("no-git")
                .toFile()
        try {
            val result = detector.detectGithubRemote(tmp.toPath())
            assertNull(result)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `detectGithubRemote returns null when the stored config lookup fails`(
        @org.junit.jupiter.api.io.TempDir tmp: java.nio.file.Path,
    ) {
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenThrow(IllegalStateException("db unavailable"))
        val proc =
            ProcessBuilder("git", "init", "-q")
                .directory(tmp.toFile())
                .redirectErrorStream(true)
                .start()
        proc.waitFor()
        val remote =
            ProcessBuilder("git", "remote", "add", "origin", "https://github.com/fail-owner/fail-repo.git")
                .directory(tmp.toFile())
                .redirectErrorStream(true)
                .start()
        remote.waitFor()

        assertNull(detector.detectGithubRemote(tmp))
    }

    @Test
    fun `detectGithubRemote logs debug and returns config when remote matches a stored GitHub config`(
        @org.junit.jupiter.api.io.TempDir tmp: java.nio.file.Path,
    ) {
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(
                GithubRepoConfig(
                    id = 99L,
                    name = "test",
                    owner = "detect-owner",
                    repo = "detect-repo",
                ),
            ),
        )

        val runGit: (java.nio.file.Path, Array<out String>) -> Unit = { dir, args ->
            val proc =
                ProcessBuilder("git", *args)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start()
            proc.waitFor()
        }
        runGit(tmp, arrayOf("init", "-q"))
        runGit(tmp, arrayOf("remote", "add", "origin", "https://github.com/detect-owner/detect-repo.git"))

        val result = detector.detectGithubRemote(tmp)
        assertNotNull(result)
    }
}
