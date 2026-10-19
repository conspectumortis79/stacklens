package com.stacklens.adapter.out.pipeline

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineFilesAdapterGithubTest {
    private val githubRepoConfigRepo = mock<GithubRepoConfigPort>()
    private val githubBridge = mock<GithubTicketBridgePort>()

    private val adapter =
        PipelineFilesAdapter(
            localRepoConfigRepo = mock(),
            gitlabConfigRepo = mock(),
            githubRepoConfigRepo = githubRepoConfigRepo,
            scanService = mock(),
            gitlabService = mock(),
            githubBridge = githubBridge,
        )

    @Test
    fun `a github configuration that is gone aborts the pipeline load`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.loadGithub(3L)
            }

        assertTrue(failure.message!!.contains("not found"))
    }

    @Test
    fun `an inaccessible github repository aborts the pipeline load`() {
        val config = GithubRepoConfig(id = 3L, name = "widgets", owner = "acme", repo = "widgets", active = true)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(config))
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(null)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.loadGithub(3L)
            }

        assertTrue(failure.message!!.contains("is not accessible"))
    }
}
