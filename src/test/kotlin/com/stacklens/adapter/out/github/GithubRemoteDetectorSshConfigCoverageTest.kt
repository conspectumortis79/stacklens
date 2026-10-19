package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GithubRemoteDetectorSshConfigCoverageTest {
    private val port = mock<GithubRepoConfigPort>()
    private val detector = GithubRemoteDetector(port)

    @Test
    fun `an ssh remote without a stored config produces a fresh config`() {
        whenever(port.findAllGithubRepoConfigs()).thenReturn(emptyList())

        val config = detector.parseGithubRemoteUrl("git@github.com:acme/shop.git")

        assertEquals("acme/shop to acme/shop", describe(config))
    }

    @Test
    fun `an ssh remote with a matching stored config reuses it`() {
        val stored = GithubRepoConfig(id = 5L, name = "stored", owner = "ACME", repo = "Shop", accessToken = "tok")
        whenever(port.findAllGithubRepoConfigs()).thenReturn(listOf(stored))

        assertEquals(stored, detector.parseGithubRemoteUrl("git@github.com:acme/shop.git"))
    }

    @Test
    fun `an ssh remote whose stored entry only matches the owner is ignored`() {
        whenever(port.findAllGithubRepoConfigs())
            .thenReturn(listOf(GithubRepoConfig(id = 6L, name = "other", owner = "acme", repo = "warehouse")))

        val config = detector.parseGithubRemoteUrl("git@github.com:acme/shop.git")

        assertEquals("acme/shop to acme/shop", describe(config))
    }

    private fun describe(config: GithubRepoConfig?): String? = config?.let { "${it.name} to ${it.owner}/${it.repo}" }
}
