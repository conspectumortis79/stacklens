package com.stacklens.application.service.health

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.GithubRepoConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class GithubConnectionProbeAdapterTest {
    private val repo: GithubRepoConfigPort = mock()
    private val probe: GithubConnectionProbePort = mock()
    private val aiRepo: AiConfigPort = mock()
    private val tracker: MutableConnectionHealthPort = mock()
    private val sut = GithubConnectionProbeAdapter(repo, probe, aiRepo, tracker, clock = com.stacklens.adapter.out.time.SystemClockPort)

    @Test
    fun `probeOne returns null when the repository has no row for the id`() {
        whenever(repo.findGithubRepoConfigById(42L)).thenReturn(null)

        assertNull(sut.probeOne(42L))
        verifyNoInteractions(probe, tracker, aiRepo)
    }

    @Test
    fun `probeOne calls the probe with config access token and saves the result`() {
        val c = GithubRepoConfig(id = 7L, name = "gh", owner = "me", repo = "repo", accessToken = "gh_tok")
        whenever(repo.findGithubRepoConfigById(7L)).thenReturn(c)
        whenever(repo.saveGithubRepoConfig(c)).thenReturn(c)
        whenever(probe.probe(eq("me"), eq("repo"), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok())

        val snapshot = sut.probeOne(7L)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.healthy).isTrue()
        assertThat(snapshot.consecutiveFailures).isZero()
        assertNull(snapshot.lastErrorMessage)
        verify(probe).probe(eq("me"), eq("repo"), anyOrNull(), any())
        verify(repo).saveGithubRepoConfig(c)
        verify(tracker).record(eq("github"), eq(7L), any<ConnectionHealthSnapshot>())
    }

    @Test
    fun `probeOne falls back to ai config github token when config token is blank`() {
        val c = GithubRepoConfig(id = 8L, name = "gh", owner = "x", repo = "y", accessToken = "  ")
        whenever(repo.findGithubRepoConfigById(8L)).thenReturn(c)
        whenever(repo.saveGithubRepoConfig(c)).thenReturn(c)
        whenever(aiRepo.findAiConfig()).thenReturn(AiConfig(aiProvider = AiProvider.OPENAI.id, githubToken = "fallback"))
        whenever(probe.probe(eq("x"), eq("y"), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Ok())

        sut.probeOne(8L)

        verify(probe).probe(eq("x"), eq("y"), anyOrNull(), any())
    }

    @Test
    fun `probeOne increments the failure counter on probe failure`() {
        val c = GithubRepoConfig(id = 9L, name = "gh", owner = "x", repo = "y")
        c.consecutiveFailures = 2
        whenever(repo.findGithubRepoConfigById(9L)).thenReturn(c)
        whenever(repo.saveGithubRepoConfig(c)).thenReturn(c)
        whenever(probe.probe(any(), any(), anyOrNull(), any())).thenReturn(ConnectionProbeResult.Failure("boom"))

        val snapshot = sut.probeOne(9L)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.healthy).isFalse()
        assertThat(snapshot.consecutiveFailures).isEqualTo(3)
        assertThat(snapshot.lastErrorMessage).isEqualTo("boom")
    }
}
