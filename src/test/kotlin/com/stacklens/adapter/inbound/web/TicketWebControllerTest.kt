package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.TicketImplementationPageData
import com.stacklens.application.port.inbound.web.TicketPageQueryUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ui.Model

class TicketWebControllerTest {
    private val ticketPageQuery: TicketPageQueryUseCase = mock()
    private val controller = TicketWebController(ticketPageQuery)

    @Test
    fun `ticketImplementationPage adds attributes and returns template name`() {
        val data =
            TicketImplementationPageData(
                jiraConfigs = emptyList(),
                gitlabConfigs = emptyList(),
                recentAnalyses = emptyList(),
                localRepoConfigs = emptyList(),
                githubRepoConfigs = emptyList(),
                dbConfigs = emptyList(),
            )
        whenever(ticketPageQuery.getTicketImplementationPageData()).thenReturn(data)
        val model: Model = mock()
        val result = controller.ticketImplementationPage(model)
        assertEquals("ticket-implementation", result)
    }

    @Test
    fun `ticketRefinementPage returns template name`() {
        assertEquals("ticket-refinement", controller.ticketRefinementPage())
    }

    @Test
    fun `ticketCreationPage returns template name`() {
        assertEquals("ticket-creation", controller.ticketCreationPage())
    }

    @Test
    fun `ticketSummaryPage returns template name`() {
        assertEquals("ticket-summary", controller.ticketSummaryPage())
    }

    @Test
    fun `sprintReviewPage returns template name`() {
        assertEquals("sprint-review", controller.sprintReviewPage())
    }

    @Test
    fun `demoJiraTicketPage returns template name`() {
        assertEquals("demo-jira-ticket", controller.demoJiraTicketPage())
    }
}
