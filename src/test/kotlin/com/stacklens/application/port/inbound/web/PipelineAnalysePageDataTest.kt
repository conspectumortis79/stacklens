package com.stacklens.application.port.inbound.web

import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PipelineAnalysePageDataTest {
    @Test
    fun `getGithubRepoConfigs returns the default empty list when not supplied`() {
        val data =
            PipelineAnalysePageData(
                localRepoConfigs = emptyList(),
                gitlabConfigs = emptyList(),
            )
        assertEquals(emptyList<GithubRepoConfig>(), data.githubRepoConfigs)
    }

    @Test
    fun `getGithubRepoConfigs returns the supplied list when given`() {
        val github = listOf<GithubRepoConfig>()
        val data =
            PipelineAnalysePageData(
                localRepoConfigs = emptyList(),
                gitlabConfigs = emptyList(),
                githubRepoConfigs = github,
            )
        assertSame(github, data.githubRepoConfigs)
    }

    @Test
    fun `getLocalRepoConfigs returns the supplied list`() {
        val local = listOf<LocalRepoConfig>()
        val data =
            PipelineAnalysePageData(
                localRepoConfigs = local,
                gitlabConfigs = emptyList(),
            )
        assertSame(local, data.localRepoConfigs)
    }

    @Test
    fun `getGitlabConfigs returns the supplied list`() {
        val gl = listOf<GitlabConfig>()
        val data =
            PipelineAnalysePageData(
                localRepoConfigs = emptyList(),
                gitlabConfigs = gl,
            )
        assertSame(gl, data.gitlabConfigs)
    }
}
