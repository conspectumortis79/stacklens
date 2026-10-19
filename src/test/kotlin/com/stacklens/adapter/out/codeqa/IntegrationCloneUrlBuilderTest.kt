package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntegrationCloneUrlBuilderTest {
    private lateinit var githubConfigPort: GithubRepoConfigPort
    private lateinit var gitlabConfigPort: GitlabConfigPort
    private lateinit var builder: IntegrationCloneUrlBuilder

    @BeforeEach
    fun setup() {
        githubConfigPort = mock()
        gitlabConfigPort = mock()
        builder = IntegrationCloneUrlBuilder(githubConfigPort, gitlabConfigPort)
    }

    private fun analysis(
        id: Long = 1L,
        gitConfigId: Long? = null,
    ): CodeQaAnalysis =
        CodeQaAnalysis(
            id = id,
            mrUrl = "https://example.com/mr/1",
            gitConfigId = gitConfigId,
        )

    private fun mrData(
        platform: String = "github",
        url: String = "https://github.com/owner/repo/pull/123",
    ): MrFetchPort.MrData =
        MrFetchPort.MrData(
            mrUrl = url,
            platform = platform,
            title = "t",
            description = "d",
            sourceBranch = "feat",
            targetBranch = "main",
            authorName = "alice",
            state = "opened",
            diffText = "",
            changedFiles = emptyList(),
        )

    @Test
    fun `buildCloneUrl returns null for unknown platform`() {
        val result = builder.buildCloneUrl(mrData(platform = "bitbucket"), analysis())
        assertNull(result)
    }

    @Test
    fun `buildCloneUrl is case-insensitive on platform name`() {
        val result = builder.buildCloneUrl(mrData(platform = "GITHUB"), analysis())
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl returns plain URL when no matching config`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val result = builder.buildCloneUrl(mrData(), analysis())
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl returns null when URL doesn't match GitHub pattern`() {
        val result =
            builder.buildCloneUrl(
                mrData(url = "https://example.com/owner/repo/pull/123"),
                analysis(),
            )
        assertNull(result)
    }

    @Test
    fun `buildGithubCloneUrl prefers preferred config id when present`() {
        val cfg1 =
            GithubRepoConfig(
                id = 1L,
                name = "config1",
                owner = "owner",
                repo = "repo",
                accessToken = "TOKEN1",
                active = true,
            )
        val cfg2 =
            GithubRepoConfig(
                id = 2L,
                name = "config2",
                owner = "different-owner",
                repo = "different-repo",
                accessToken = "TOKEN2",
                active = true,
            )
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg1, cfg2))
        val result = builder.buildCloneUrl(mrData(), analysis(gitConfigId = 2L))
        assertEquals("https://TOKEN2@github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl falls back to matching config by owner and repo when no preferred`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "config",
                owner = "owner",
                repo = "repo",
                accessToken = "TOKEN",
                active = true,
            )
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        val result = builder.buildCloneUrl(mrData(), analysis())
        assertEquals("https://TOKEN@github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl owner matching is case-insensitive`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "config",
                owner = "Owner",
                repo = "Repo",
                accessToken = "TOKEN",
                active = true,
            )
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        val result = builder.buildCloneUrl(mrData(url = "https://github.com/OWNER/REPO/pull/1"), analysis())
        assertEquals("https://TOKEN@github.com/OWNER/REPO.git", result)
    }

    @Test
    fun `buildGithubCloneUrl filters out inactive configs`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "config",
                owner = "owner",
                repo = "repo",
                accessToken = "TOKEN",
                active = false,
            )
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        val result = builder.buildCloneUrl(mrData(), analysis())
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl uses plain URL when config has no token`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "config",
                owner = "owner",
                repo = "repo",
                accessToken = "",
                active = true,
            )
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        val result = builder.buildCloneUrl(mrData(), analysis())
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl handles http (not https) URLs`() {
        val result =
            builder.buildCloneUrl(
                mrData(url = "http://github.com/owner/repo/pull/1"),
                analysis(),
            )
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl returns plain URL when no matching config`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.com/group/project/-/merge_requests/123"),
                analysis(),
            )
        assertEquals("https://gitlab.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl returns null for invalid URL`() {
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "not a url at all"),
                analysis(),
            )
        assertNull(result)
    }

    @Test
    fun `buildGitlabCloneUrl prefers preferred config id`() {
        val cfg1 =
            GitlabConfig(
                id = 1L,
                name = "config1",
                baseUrl = "https://gitlab.com",
                accessToken = "TOKEN1",
                projectIds = "1",
                active = true,
            )
        val cfg2 =
            GitlabConfig(
                id = 2L,
                name = "config2",
                baseUrl = "https://gitlab.example.com",
                accessToken = "TOKEN2",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg1, cfg2))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.example.com/group/project/-/merge_requests/1"),
                analysis(gitConfigId = 2L),
            )
        assertEquals("https://oauth2:TOKEN2@gitlab.example.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl falls back to matching config by host`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "config",
                baseUrl = "https://gitlab.example.com",
                accessToken = "TOKEN",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.example.com/group/project/-/merge_requests/1"),
                analysis(),
            )
        assertEquals("https://oauth2:TOKEN@gitlab.example.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl host matching is case-insensitive`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "config",
                baseUrl = "https://GitLab.Example.com",
                accessToken = "TOKEN",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.example.com/group/project/-/merge_requests/1"),
                analysis(),
            )
        assertEquals("https://oauth2:TOKEN@gitlab.example.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl handles non-standard port in URL`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "config",
                baseUrl = "https://gitlab.example.com:8443",
                accessToken = "TOKEN",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.example.com:8443/group/project/-/merge_requests/1"),
                analysis(),
            )
        assertEquals("https://oauth2:TOKEN@gitlab.example.com:8443/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl ignores configs with invalid baseUrl`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "config",
                baseUrl = "not a url",
                accessToken = "TOKEN",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.com/group/project/-/merge_requests/1"),
                analysis(),
            )
        assertEquals("https://gitlab.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl strips trailing slash from MR URL`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.com/group/project/-/merge_requests/1/"),
                analysis(),
            )
        assertEquals("https://gitlab.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl handles MR URL with additional path segments`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.com/group/project/-/merge_requests/123/diffs"),
                analysis(),
            )
        assertEquals("https://gitlab.com/group/project.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl uses plain URL when config has no token`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "config",
                baseUrl = "https://gitlab.com",
                accessToken = "",
                projectIds = "1",
                active = true,
            )
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData(platform = "gitlab", url = "https://gitlab.com/group/project/-/merge_requests/1"),
                analysis(),
            )
        assertEquals("https://gitlab.com/group/project.git", result)
    }

    @Test
    fun `buildGithubCloneUrl uses preferredConfigId when set and matches a config`() {
        val cfg = GithubRepoConfig(id = 9L, name = "n", owner = "o", repo = "r", accessToken = "tok")
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData("github", "https://github.com/owner/repo/pull/1"),
                analysis(gitConfigId = 9L),
            )
        assertEquals("https://tok@github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGithubCloneUrl returns plain URL when no matching active config exists`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val result =
            builder.buildCloneUrl(
                mrData("github", "https://github.com/owner/repo/pull/1"),
                analysis(),
            )
        assertEquals("https://github.com/owner/repo.git", result)
    }

    @Test
    fun `buildGitlabCloneUrl uses preferred config when set`() {
        val cfg = GitlabConfig(id = 5L, name = "n", baseUrl = "https://gitlab.com", accessToken = "tok")
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(listOf(cfg))
        val result =
            builder.buildCloneUrl(
                mrData("gitlab", "https://gitlab.com/group/project/-/merge_requests/42"),
                analysis(gitConfigId = 5L),
            )
        assertTrue(result!!.contains("oauth2:tok@"))
    }

    @Test
    fun `buildGitlabCloneUrl returns null when URL is not parseable`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val result =
            builder.buildCloneUrl(
                mrData("gitlab", "not-a-valid-url-here"),
                analysis(),
            )
    }
}
