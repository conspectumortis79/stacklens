package com.stacklens.adapter.inbound.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class SimulationWebController {
    @GetMapping("/simulated-mr/{id}")
    fun simulatedMrPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("analysisId", id)
        return "simulated-mr"
    }

    @GetMapping("/simulated-github-pr/{id}")
    fun simulatedGithubPrPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("analysisId", id)
        return "simulated-github-pr"
    }

    @GetMapping("/simulated-pipeline-mr/{id}")
    fun simulatedPipelineMrPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("analysisId", id)
        return "simulated-pipeline-mr"
    }

    @GetMapping("/simulated-pipeline-pr/{id}")
    fun simulatedPipelinePrPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("analysisId", id)
        return "simulated-pipeline-pr"
    }

    @GetMapping("/simulated-jira-ticket/{id}")
    fun simulatedJiraTicketPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("recordId", id)
        return "simulated-jira-ticket"
    }

    @GetMapping("/ki-code-plan/{id}")
    fun kiCodePlanPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("analysisId", id)
        return "ki-code-plan"
    }
}
