package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.SearchPageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SearchWebController(
    private val pageQuery: SearchPageQueryUseCase,
) {
    @GetMapping("/search")
    fun searchPage(model: Model): String {
        val data = pageQuery.getSuchePageData()
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("dbConfigs", data.dbConfigs)
        model.addAttribute("jiraConfigs", data.jiraConfigs)
        model.addAttribute("confluenceConfigs", data.confluenceConfigs)
        model.addAttribute("githubConfigs", data.githubConfigs)
        return "search"
    }
}
