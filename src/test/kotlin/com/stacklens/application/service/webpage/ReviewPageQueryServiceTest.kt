package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReviewPageQueryServiceTest {
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort = mock()

    private val service =
        ReviewPageQueryService(
            jiraConfigRepo = jiraConfigRepo,
            reviewAnalysisConfigRepo = reviewAnalysisConfigRepo,
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
        )

    private fun jiraConfig() = JiraConfig(id = 2L, name = "jira", baseUrl = "https://jira.example.com", apiToken = "t")

    @Test
    fun `getReviewAnalysePageData returns jira configs and saved config`() {
        val jiraConfigs = listOf(jiraConfig())
        val saved = ReviewAnalysisConfig(jiraSprintUrl = "https://jira.example.com/board/1")
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(jiraConfigs)
        whenever(reviewAnalysisConfigRepo.findReviewAnalysisConfig()).thenReturn(saved)

        val data = service.getReviewAnalysePageData()

        assertEquals(jiraConfigs, data.jiraConfigs)
        assertEquals(saved, data.savedConfig)
    }

    @Test
    fun `getReviewAnalysePageData returns null savedConfig when none exists`() {
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(reviewAnalysisConfigRepo.findReviewAnalysisConfig()).thenReturn(null)

        val data = service.getReviewAnalysePageData()

        assertNull(data.savedConfig)
    }
}
