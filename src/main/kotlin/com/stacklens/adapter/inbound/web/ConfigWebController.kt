package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.ConfigPageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ConfigWebController(
    private val pageQuery: ConfigPageQueryUseCase,
) {
    @GetMapping("/config")
    fun configPage(model: Model): String {
        val data = pageQuery.getConfigPageData()
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("dbConfigs", data.dbConfigs)
        model.addAttribute("confluenceConfigs", data.confluenceConfigs)
        model.addAttribute("grafanaConfigs", data.grafanaConfigs)
        model.addAttribute("jiraConfigs", data.jiraConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        model.addAttribute("aiConfig", data.aiConfig)
        return "config"
    }
}
