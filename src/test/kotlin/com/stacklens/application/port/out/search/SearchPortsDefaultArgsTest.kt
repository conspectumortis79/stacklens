package com.stacklens.application.port.out.search

import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchPortsDefaultArgsTest {
    @Test
    fun `JiraSearchPort searchKeyword default args are exercised when only the required args are passed`() {
        val port: JiraSearchPort = mock()
        val cfg = JiraConfig(id = 1L, name = "j", baseUrl = "https://x", apiToken = "t")
        whenever(port.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList()))
            .thenReturn(emptyList<SearchResult>())

        port.searchKeyword(cfg, "kw", emptyList(), 1L)
        port.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())

        verify(port, org.mockito.kotlin.times(2))
            .searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
    }

    @Test
    fun `PipelineSearchPort searchPipelines default args are exercised when only the required args are passed`() {
        val port: PipelineSearchPort = mock()
        val cfg = GitlabConfig(id = 1L, name = "g", baseUrl = "https://x", accessToken = "t")
        whenever(port.searchPipelines(cfg, listOf(1L, 2L), "kw", 1L, emptyList()))
            .thenReturn(emptyList<SearchResult>())

        port.searchPipelines(cfg, listOf(1L, 2L), "kw", 1L)

        verify(port).searchPipelines(cfg, listOf(1L, 2L), "kw", 1L, emptyList())
    }
}
