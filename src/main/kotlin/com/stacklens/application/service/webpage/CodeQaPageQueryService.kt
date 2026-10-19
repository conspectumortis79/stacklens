package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.CodeQaAnalysisPageData
import com.stacklens.application.port.inbound.web.CodeQaPageQueryUseCase
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class CodeQaPageQueryService(
    private val jiraConfigRepo: JiraConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val codeQaAnalysisRepo: CodeQaAnalysisPort,
    private val demoConfigFilter: DemoConfigPort,
) : CodeQaPageQueryUseCase {
    override fun getCodeQaAnalysisPageData(): CodeQaAnalysisPageData =
        CodeQaAnalysisPageData(
            jiraConfigs = demoConfigFilter.apply(jiraConfigRepo.findByActiveTrue()) { it.name },
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            githubConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs().filter { it.active }) { it.name },
            recentAnalyses = codeQaAnalysisRepo.findAllByOrderByCreatedAtDesc().take(10),
        )
}
