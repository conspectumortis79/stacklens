package com.stacklens.adapter.out.pipeline

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineFilesAdapterClassifierResidualCoverageTest {
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()

    private val adapter =
        PipelineFilesAdapter(
            localRepoConfigRepo = mock(),
            gitlabConfigRepo = mock(),
            githubRepoConfigRepo = githubRepoConfigRepo,
            scanService = mock(),
            gitlabService = mock(),
            githubBridge = mock(),
        )

    @Test
    fun `a buildkite file must carry a yaml suffix`() {
        assertTrue(adapter.looksLikePipelinePath(".github/buildkite.yaml"))
        assertFalse(adapter.looksLikePipelinePath(".github/buildkite.txt"))
    }

    @Test
    fun `only the docker-compose prefix variant short-circuits the compose check`() {
        assertTrue(adapter.looksLikePipelinePath("deploy/compose.yml"))
        assertFalse(adapter.looksLikePipelinePath("deploy/composetest.yml"))
    }

    @Test
    fun `workflow and circleci parents are matched by their suffix only`() {
        assertFalse(adapter.looksLikePipelinePath("workflows/ci.yml"))
        assertFalse(adapter.looksLikePipelinePath(".circleci/config.json"))
        assertTrue(adapter.looksLikePipelinePath(".circleci/config.yaml"))
    }

    @Test
    fun `a backslash separated path is normalised before the basename is taken`() {
        assertTrue(adapter.looksLikePipelinePath("infra\\Dockerfile"))
        assertFalse(adapter.looksLikePipelinePath(""))
    }

    @Test
    fun `loadGithub reports the missing configuration`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs())
            .thenReturn(listOf(GithubRepoConfig(name = "other", owner = "other", repo = "repo")))

        val failure = assertThrows(IllegalStateException::class.java) { adapter.loadGithub(7L) }

        assertTrue(failure.message!!.contains("id=7"))
    }
}
