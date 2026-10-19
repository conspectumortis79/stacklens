package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.CodeAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.CodeQaPageQueryUseCase
import com.stacklens.application.port.inbound.web.LastTestAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.PipelineAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.SecurityAnalysePageQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AnalysisWebController(
    private val codeAnalysePageQuery: CodeAnalysePageQueryUseCase,
    private val lastTestAnalysePageQuery: LastTestAnalysePageQueryUseCase,
    private val securityAnalysePageQuery: SecurityAnalysePageQueryUseCase,
    private val codeQaPageQuery: CodeQaPageQueryUseCase,
    private val pipelineAnalysePageQuery: PipelineAnalysePageQueryUseCase,
) {
    @GetMapping("/code-analysis")
    fun codeAnalysePage(model: Model): String {
        val data = codeAnalysePageQuery.getCodeAnalysePageData()
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        return "code-analysis"
    }

    @GetMapping("/lasttest-analysis")
    fun lastTestAnalysePage(model: Model): String {
        val data = lastTestAnalysePageQuery.getLastTestAnalysePageData()
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        return "lasttest-analysis"
    }

    @GetMapping("/security-analysis")
    fun securityAnalysePage(model: Model): String {
        val data = securityAnalysePageQuery.getSecurityAnalysePageData()
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        return "security-analysis"
    }

    @GetMapping("/code-qa")
    fun codeQaPage(model: Model): String {
        val data = codeQaPageQuery.getCodeQaAnalysisPageData()
        model.addAttribute("jiraConfigs", data.jiraConfigs)
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("recentAnalyses", data.recentAnalyses)
        return "code-qa"
    }

    @GetMapping("/pipeline-analysis")
    fun pipelineAnalysePage(model: Model): String {
        val data = pipelineAnalysePageQuery.getPipelineAnalysePageData()
        model.addAttribute("localRepoConfigs", data.localRepoConfigs)
        model.addAttribute("gitlabConfigs", data.gitlabConfigs)
        model.addAttribute("githubRepoConfigs", data.githubRepoConfigs)
        return "pipeline-analysis"
    }
}
