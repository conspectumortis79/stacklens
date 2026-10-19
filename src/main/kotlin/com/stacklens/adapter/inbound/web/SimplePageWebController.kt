package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.LoggingAnalysePageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SimplePageWebController(
    private val loggingAnalysePageQuery: LoggingAnalysePageQueryUseCase,
) {
    @GetMapping("/")
    fun root(): String = "redirect:/search"

    @GetMapping("/docs")
    fun docsPage(): String = "docs"

    @GetMapping("/sql-analysis")
    fun sqlAnalysePage(): String = "sql-analysis"

    @GetMapping("/logging")
    fun loggingPage(model: Model): String {
        val data = loggingAnalysePageQuery.getLoggingAnalysePageData()
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("recentFixAnalyses", data.recentAnalyses)
        return "logging"
    }

    @GetMapping("/image-extraction")
    fun imageExtractionPage(): String = "image-extraction"

    @GetMapping("/docker-test")
    fun dockerTestPage(): String = "docker-test"
}
