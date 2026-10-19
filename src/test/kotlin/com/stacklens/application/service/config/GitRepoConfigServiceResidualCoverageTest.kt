package com.stacklens.application.service.config
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GitRepoConfigServiceResidualCoverageTest {
    private val githubRepoConfigRepo = mock<GithubRepoConfigPort>()
    private val aiConfigRepo = mock<AiConfigPort>()
    private val githubConnectionTest = mock<GithubConnectionTestPort>()
    private val service = GithubConfigService(githubRepoConfigRepo, aiConfigRepo, githubConnectionTest, PassthroughDemoConfigFilter.instance)

    private fun config(token: String?) = GithubRepoConfig(id = 5L, name = "gh", owner = "acme", repo = "shop", accessToken = token)

    @Test
    fun `resolveGithubAccessToken returns null when no ai config exists`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(config(null))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        assertNull(service.resolveGithubAccessToken(5L))
    }

    @Test
    fun `resolveGithubAccessToken ignores a blank ai token`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(config("  "))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "   "))

        assertNull(service.resolveGithubAccessToken(5L))
    }

    @Test
    fun `testGithubRepoConnection reports a missing token when every source is empty`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(config(null))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        val result = service.testGithubRepoConnection(5L)

        assertEquals(false, result["success"])
        assertEquals("No GitHub access token configured", result["message"])
    }

    @Test
    fun `testGithubRepoConnection uses the ai token and the default api base`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(config(null))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "ai-token"))
        whenever(githubConnectionTest.testConnection(any(), eq("ai-token"), eq("https://api.github.com")))
            .thenReturn(GithubConnectionTestPort.TestResult.Success("ok"))

        val result = service.testGithubRepoConnection(5L)

        assertEquals(true, result["success"])
        verify(githubConnectionTest).testConnection(any(), eq("ai-token"), eq("https://api.github.com"))
    }
}
