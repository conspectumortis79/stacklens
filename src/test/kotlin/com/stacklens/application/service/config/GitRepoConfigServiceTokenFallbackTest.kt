package com.stacklens.application.service.config
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GitRepoConfigServiceTokenFallbackTest {
    private val githubRepo: GithubRepoConfigPort = mock()
    private val aiConfigRepo: AiConfigPort = mock()
    private val githubConnectionTest: GithubConnectionTestPort = mock()
    private val service =
        GithubConfigService(
            githubRepoConfigRepo = githubRepo,
            aiConfigRepo = aiConfigRepo,
            githubConnectionTest = githubConnectionTest,
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
        )

    private fun stubConfig(config: GithubRepoConfig?) {
        whenever(githubRepo.findGithubRepoConfigById(ID)) doReturn config
    }

    private fun config(
        accessToken: String?,
        apiBase: String? = null,
    ) = GithubRepoConfig(id = ID, name = "repo", owner = "acme", repo = "shop", accessToken = accessToken, apiBase = apiBase)

    private fun stubAiConfig(aiConfig: AiConfig?) {
        whenever(aiConfigRepo.findAiConfig()) doReturn aiConfig
    }

    private fun stubTestResult(result: GithubConnectionTestPort.TestResult) {
        whenever(githubConnectionTest.testConnection(any(), any(), any())) doReturn result
    }

    @Test
    fun `an absent repo config yields no access token`() {
        stubConfig(null)

        assertNull(service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `the repo specific access token wins over the global one`() {
        stubConfig(config(accessToken = "repo-token"))
        stubAiConfig(AiConfig(githubToken = "global-token"))

        assertEquals("repo-token", service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `a blank repo access token falls through to the global token`() {
        stubConfig(config(accessToken = "   "))
        stubAiConfig(AiConfig(githubToken = "global-token"))

        assertEquals("global-token", service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `a missing repo access token falls through to the global token`() {
        stubConfig(config(accessToken = null))
        stubAiConfig(AiConfig(githubToken = "global-token"))

        assertEquals("global-token", service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `no ai config at all yields no access token`() {
        stubConfig(config(accessToken = null))
        stubAiConfig(null)

        assertNull(service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `an ai config without a github token yields no access token`() {
        stubConfig(config(accessToken = null))
        stubAiConfig(AiConfig(githubToken = null))

        assertNull(service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `an ai config with a blank github token yields no access token`() {
        stubConfig(config(accessToken = "  "))
        stubAiConfig(AiConfig(githubToken = "  "))

        assertNull(service.resolveGithubAccessToken(ID))
    }

    @Test
    fun `testing an unknown repo config reports a missing configuration`() {
        stubConfig(null)

        val result = service.testGithubRepoConnection(ID)

        assertEquals(false, result["success"])
        assertEquals("GitHub repo config not found", result["message"])
    }

    @Test
    fun `testing without any token reports the missing token`() {
        stubConfig(config(accessToken = null))
        stubAiConfig(null)

        val result = service.testGithubRepoConnection(ID)

        assertEquals(false, result["success"])
        assertEquals("No GitHub access token configured", result["message"])
    }

    @Test
    fun `the repo token and the configured api base are handed to the connection test`() {
        stubConfig(config(accessToken = "repo-token", apiBase = "https://github.acme.test/api/v3"))
        stubTestResult(GithubConnectionTestPort.TestResult.Success("reachable"))

        val result = service.testGithubRepoConnection(ID)

        assertTrue(result["success"] as Boolean)
        assertEquals("reachable", result["message"])
        val captor = org.mockito.kotlin.argumentCaptor<String>()
        org.mockito.kotlin
            .verify(githubConnectionTest)
            .testConnection(any(), org.mockito.kotlin.eq("repo-token"), captor.capture())
        assertEquals("https://github.acme.test/api/v3", captor.lastValue)
    }

    @Test
    fun `a blank api base falls back to the public github api`() {
        stubConfig(config(accessToken = "   ", apiBase = " "))
        stubAiConfig(AiConfig(githubToken = "global-token"))
        stubTestResult(GithubConnectionTestPort.TestResult.Failure("401"))

        val result = service.testGithubRepoConnection(ID)

        assertFalse(result["success"] as Boolean)
        assertEquals("401", result["message"])
        org.mockito.kotlin
            .verify(githubConnectionTest)
            .testConnection(any(), org.mockito.kotlin.eq("global-token"), org.mockito.kotlin.eq("https://api.github.com"))
    }

    @Test
    fun `a missing api base falls back to the public github api`() {
        stubConfig(config(accessToken = "repo-token", apiBase = null))
        stubTestResult(GithubConnectionTestPort.TestResult.Success("ok"))

        service.testGithubRepoConnection(ID)

        org.mockito.kotlin
            .verify(githubConnectionTest)
            .testConnection(any(), any(), org.mockito.kotlin.eq("https://api.github.com"))
    }

    companion object {
        private const val ID = 12L
    }
}
