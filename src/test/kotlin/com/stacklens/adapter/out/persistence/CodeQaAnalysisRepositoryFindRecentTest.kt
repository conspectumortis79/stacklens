package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class CodeQaAnalysisRepositoryFindRecentTest {
    private val repo = mock<CodeQaAnalysisRepository>()

    @Test
    fun `the recent lookup asks for the newest page and returns its content`() {
        val first = CodeQaAnalysis(id = 1L, mrUrl = "https://gitlab/g/r/-/merge_requests/1", status = "DONE")
        val second = CodeQaAnalysis(id = 2L, mrUrl = "https://gitlab/g/r/-/merge_requests/2", status = "DONE")
        whenever(repo.findAll(any<Pageable>())).thenReturn(PageImpl(listOf(first, second), PageRequest.of(0, 2), 2))
        whenever(repo.findRecent(any())).thenCallRealMethod()

        assertEquals(listOf(first, second), repo.findRecent(2))
    }
}
