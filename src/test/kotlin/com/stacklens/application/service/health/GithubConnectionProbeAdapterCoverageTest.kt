package com.stacklens.application.service.health

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GithubConnectionProbeAdapterCoverageTest {
    private val repo = mock<GithubRepoConfigPort>()
    private val probe = mock<GithubConnectionProbePort>()
    private val aiRepo = mock<AiConfigPort>()
    private val tracker = mock<MutableConnectionHealthPort>()
    private val adapter = GithubConnectionProbeAdapter(repo, probe, aiRepo, tracker, clock = com.stacklens.adapter.out.time.SystemClockPort)

    private fun config(
        id: Long,
        name: String,
        accessToken: String?,
        apiBase: String?,
    ) = GithubRepoConfig(id = id, name = name, owner = "acme", repo = "shop", accessToken = accessToken, apiBase = apiBase)

    @Test
    fun `probing an unknown id yields no snapshot`() {
        whenever(repo.findGithubRepoConfigById(9L)).thenReturn(null)

        assertNull(adapter.probeOne(9L))
    }

    @Test
    fun `the repository token wins over the ai fallback`() {
        val stored = config(1L, "primary", "repo-token", "https://ghe.example")
        whenever(repo.findGithubRepoConfigById(1L)).thenReturn(stored)
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(4))

        assertNotNull(adapter.probeOne(1L))

        verify(probe).probe(eq("acme"), eq("shop"), eq("repo-token"), eq("https://ghe.example"))
        verify(repo).saveGithubRepoConfig(stored)
        verify(tracker).record(eq("github"), eq(1L), any())
    }

    @Test
    fun `a blank repository token falls back to the ai github token`() {
        val stored = config(2L, "fallback", "   ", "")
        whenever(repo.findGithubRepoConfigById(2L)).thenReturn(stored)
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "ai-token"))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(0))

        adapter.probeOne(2L)

        verify(probe).probe(eq("acme"), eq("shop"), eq("ai-token"), eq("https://api.github.com"))
    }

    @Test
    fun `a missing ai configuration leaves the token unset`() {
        val stored = config(3L, "anonymous", null, null)
        whenever(repo.findGithubRepoConfigById(3L)).thenReturn(stored)
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(1))

        adapter.probeOne(3L)

        verify(probe).probe(any(), any(), eq(null), eq("https://api.github.com"))
    }

    @Test
    fun `probe failures are recorded instead of thrown`() {
        whenever(repo.findGithubRepoConfigById(4L)).thenReturn(config(4L, "broken", "t", null))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenThrow(IllegalStateException("dns failure"))

        assertNull(adapter.probeOne(4L))
    }

    @Test
    fun `probeAll uses the stored token when present and the ai fallback otherwise`() {
        val withToken = config(5L, "a", "tok", "https://ghe.example")
        val withoutToken = config(6L, "b", null, null)
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(withToken, withoutToken))
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "shared"))
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok(2))

        adapter.probeAll()

        verify(probe).probe(eq("acme"), eq("shop"), eq("tok"), eq("https://ghe.example"))
        verify(probe).probe(eq("acme"), eq("shop"), eq("shared"), eq("https://api.github.com"))
        verify(tracker).record(eq("github"), eq(6L), any())
    }

    @Test
    fun `probeAll ignores a blank ai token and survives failing entries`() {
        val first = config(7L, "a", null, null)
        val second = config(8L, "b", null, null)
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(first, second))
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "  "))
        whenever(probe.probe(any(), any(), anyOrNull(), any()))
            .thenReturn(ConnectionProbeResult.Ok(0))
            .thenThrow(IllegalStateException("boom"))

        adapter.probeAll()

        verify(probe, org.mockito.kotlin.times(2)).probe(any(), any(), anyOrNull(), any())
    }

    @Test
    fun `the adapter announces its connection kind`() {
        assertEquals("github", adapter.kind.id)
    }
}
