package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocalRepoConfigRepositoryByPathAndScopeCoverageTest {
    private val repository = mock<LocalRepoConfigRepository>()

    init {
        whenever(repository.findFirstByPathAndScope(any(), any())).thenCallRealMethod()
    }

    @Test
    fun `the stored config matching path and scope is returned`() {
        val stored = LocalRepoConfig(id = 7L, name = "repo", path = "/src/repo", scope = "REPO_ANALYSIS")
        whenever(repository.findAll()).thenReturn(listOf(stored))

        assertEquals(stored, repository.findFirstByPathAndScope("/src/repo", "REPO_ANALYSIS"))
    }

    @Test
    fun `a config with the right path but another scope is not returned`() {
        whenever(repository.findAll()).thenReturn(listOf(LocalRepoConfig(id = 8L, name = "repo", path = "/src/repo", scope = "CODE_QA")))

        assertNull(repository.findFirstByPathAndScope("/src/repo", "REPO_ANALYSIS"))
    }

    @Test
    fun `a config with another path is not returned`() {
        whenever(repository.findAll()).thenReturn(listOf(LocalRepoConfig(id = 9L, name = "repo", path = "/elsewhere", scope = "REPO_ANALYSIS")))

        assertNull(repository.findFirstByPathAndScope("/src/repo", "REPO_ANALYSIS"))
    }
}
