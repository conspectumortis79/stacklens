package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.ReviewPageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ReviewWebController(
    private val pageQuery: ReviewPageQueryUseCase,
) {
    @GetMapping("/review-analysis")
    fun reviewAnalysePage(model: Model): String {
        val data = pageQuery.getReviewAnalysePageData()
        model.addAttribute("jiraConfigs", data.jiraConfigs)
        model.addAttribute("savedConfig", data.savedConfig)
        return "review-analysis"
    }
}
