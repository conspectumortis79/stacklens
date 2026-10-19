package com.stacklens.application.service.config
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GitlabConnectionTestPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GitRepoConfigServiceTest {
    private val gitlabConnectionTest: GitlabConnectionTestPort = mock()
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val aiConfigRepo: AiConfigPort = mock()
    private val githubConnectionTest: GithubConnectionTestPort = mock()

    private val gitlabService =
        GitlabConfigService(
            gitlabConfigRepo,
            gitlabConnectionTest,
            PassthroughDemoConfigFilter.instance,
        )

    private val githubService =
        GithubConfigService(
            githubRepoConfigRepo,
            aiConfigRepo,
            githubConnectionTest,
            PassthroughDemoConfigFilter.instance,
        )

    private val gitlab = GitlabConfig(name = "gl", baseUrl = "https://gitlab", accessToken = "t")
    private val github = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = "tok")

    @Test
    fun `gitlab config delegates`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(listOf(gitlab))
        assertEquals(listOf(gitlab), gitlabService.listGitlabConfigs())
        whenever(gitlabConfigRepo.findGitlabConfigById(7L)).thenReturn(gitlab)
        assertEquals(gitlab, gitlabService.findGitlabConfig(7L))
        whenever(gitlabConfigRepo.saveGitlabConfig(gitlab)).thenReturn(gitlab)
        assertEquals(gitlab, gitlabService.saveGitlabConfig(gitlab))
        gitlabService.deleteGitlabConfig(7L)
        verify(gitlabConfigRepo).deleteGitlabConfigById(7L)
        gitlabService.deleteAllGitlabConfigs()
        verify(gitlabConfigRepo).deleteAllGitlabConfigs()
    }

    @Test
    fun `github config delegates`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github))
        assertEquals(listOf(github), githubService.listGithubRepoConfigs())
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(7L)).thenReturn(github)
        assertEquals(github, githubService.findGithubRepoConfig(7L))
        whenever(githubRepoConfigRepo.saveGithubRepoConfig(github)).thenReturn(github)
        assertEquals(github, githubService.saveGithubRepoConfig(github))
        githubService.deleteGithubRepoConfig(7L)
        verify(githubRepoConfigRepo).deleteGithubRepoConfigById(7L)
        whenever(githubRepoConfigRepo.githubRepoConfigExistsById(7L)).thenReturn(true)
        assertTrue(githubService.githubRepoConfigExistsById(7L))
    }

    @Test
    fun `resolveGithubAccessToken returns null when config missing`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(null)
        assertNull(githubService.resolveGithubAccessToken(1L))
    }

    @Test
    fun `resolveGithubAccessToken returns config token when present`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(github)
        assertEquals("tok", githubService.resolveGithubAccessToken(1L))
    }

    @Test
    fun `resolveGithubAccessToken falls back to ai config token`() {
        val config = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = "  ")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(config)
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "global"))
        assertEquals("global", githubService.resolveGithubAccessToken(1L))
    }

    @Test
    fun `resolveGithubAccessToken returns null when both blank`() {
        val config = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = null)
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(config)
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = null))
        assertNull(githubService.resolveGithubAccessToken(1L))
    }

    @Test
    fun `testGithubRepoConnection returns error when config missing`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(null)
        val result = githubService.testGithubRepoConnection(1L)
        assertEquals(false, result["success"])
    }

    @Test
    fun `testGithubRepoConnection returns error when no token`() {
        val config = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = null)
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(config)
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = null))
        val result = githubService.testGithubRepoConnection(1L)
        assertEquals(false, result["success"])
    }

    @Test
    fun `testGithubRepoConnection uses default api base when blank`() {
        val config = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = "tok", apiBase = null)
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(config)
        whenever(githubConnectionTest.testConnection(config, "tok", "https://api.github.com")).thenReturn(
            GithubConnectionTestPort.TestResult.Success("ok"),
        )
        val result = githubService.testGithubRepoConnection(1L)
        assertEquals(true, result["success"])
        assertEquals("ok", result["message"])
    }

    @Test
    fun `testGithubRepoConnection uses custom api base`() {
        val config = GithubRepoConfig(name = "gh", owner = "o", repo = "r", accessToken = "tok", apiBase = "https://api.example.com")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(1L)).thenReturn(config)
        whenever(githubConnectionTest.testConnection(config, "tok", "https://api.example.com")).thenReturn(
            GithubConnectionTestPort.TestResult.Failure("nope"),
        )
        val result = githubService.testGithubRepoConnection(1L)
        assertEquals(false, result["success"])
        assertEquals("nope", result["message"])
    }

    @Test
    fun `testGitlabConnection forwards the stored config to the connection port`() {
        val config =
            com.stacklens.domain.model
                .GitlabConfig(name = "gl", baseUrl = "u", accessToken = "t")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitlabConnectionTest.testConnection(config)).thenReturn(GitlabConnectionTestPort.TestResult.Success(payload))

        assertEquals(payload, gitlabService.testGitlabConnection(1L))
    }

    @Test
    fun `testGitlabConnection reports a missing config as null`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)

        assertNull(gitlabService.testGitlabConnection(1L))
    }

    @Test
    fun `listVisibleGitlabConfigs keeps every config when the test environment is on`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .GitlabConfig(name = "gl", baseUrl = "u", accessToken = "t"),
            ),
        )

        assertEquals(1, gitlabService.listVisibleGitlabConfigs().size)
    }

    private val payload: Map<String, Any> = mapOf("success" to true)
}
