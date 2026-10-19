package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.TicketPageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class TicketWebController(
    private val pageQuery: TicketPageQueryUseCase,
) {
    @GetMapping("/ticket-implementation")
    fun ticketImplementationPage(model: Model): String {
        val data = pageQuery.getTicketImplementationPageData()
        model.addAttribute("jiraConfigs", data.jiraConfigs)
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("recentAnalyses", data.recentAnalyses)
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        model.addAttribute("dbConfigs", data.dbConfigs)
        return "ticket-implementation"
    }

    @GetMapping("/ticket-refinement")
    fun ticketRefinementPage(): String = "ticket-refinement"

    @GetMapping("/ticket-creation")
    fun ticketCreationPage(): String = "ticket-creation"

    @GetMapping("/ticket-summary")
    fun ticketSummaryPage(): String = "ticket-summary"

    @GetMapping("/sprint-review")
    fun sprintReviewPage(): String = "sprint-review"

    @GetMapping("/demo-jira-ticket")
    fun demoJiraTicketPage(): String = "demo-jira-ticket"
}
