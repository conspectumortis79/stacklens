package com.stacklens.application.service.health

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GithubConnectionProbeAdapterResidualCoverageTest {
    private val repo = mock<GithubRepoConfigPort>()
    private val probe = mock<GithubConnectionProbePort>()
    private val aiRepo = mock<AiConfigPort>()
    private val tracker = mock<MutableConnectionHealthPort>()
    private val adapter = GithubConnectionProbeAdapter(repo, probe, aiRepo, tracker, clock = com.stacklens.adapter.out.time.SystemClockPort)

    private fun config(
        id: Long,
        accessToken: String?,
        apiBase: String?,
    ) = GithubRepoConfig(id = id, name = "c$id", owner = "acme", repo = "shop", accessToken = accessToken, apiBase = apiBase)

    @Test
    fun `probeOne with an ai config but a null github token probes anonymously`() {
        val stored = config(11L, null, null)
        whenever(repo.findGithubRepoConfigById(11L)).thenReturn(stored)
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = null))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(0))

        adapter.probeOne(11L)

        verify(probe).probe(eq("acme"), eq("shop"), eq(null), eq("https://api.github.com"))
    }

    @Test
    fun `probeOne with a blank ai github token probes anonymously`() {
        val stored = config(12L, null, " ")
        whenever(repo.findGithubRepoConfigById(12L)).thenReturn(stored)
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "   "))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(0))

        adapter.probeOne(12L)

        verify(probe).probe(eq("acme"), eq("shop"), eq(null), eq("https://api.github.com"))
    }

    @Test
    fun `probeAll covers custom api base, blank api base and a missing ai config`() {
        val withBase = config(21L, "tok", "https://ghe.example")
        val blankBase = config(22L, null, "  ")
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(withBase, blankBase))
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(0))

        adapter.probeAll()

        verify(probe).probe(eq("acme"), eq("shop"), eq("tok"), eq("https://ghe.example"))
        verify(probe).probe(eq("acme"), eq("shop"), eq(null), eq("https://api.github.com"))
    }

    @Test
    fun `probeAll falls back to the ai token when the stored token is blank`() {
        val stored = config(23L, "", null)
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(stored))
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "ai-token"))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(0))

        adapter.probeAll()

        verify(probe).probe(eq("acme"), eq("shop"), eq("ai-token"), eq("https://api.github.com"))
    }
}
