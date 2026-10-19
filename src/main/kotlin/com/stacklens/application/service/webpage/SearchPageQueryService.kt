package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.SearchPageQueryUseCase
import com.stacklens.application.port.inbound.web.SuchePageData
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class SearchPageQueryService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : SearchPageQueryUseCase {
    override fun getSuchePageData(): SuchePageData =
        SuchePageData(
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            dbConfigs = demoConfigFilter.apply(dbConfigRepo.findByActiveTrue()) { it.name },
            jiraConfigs = demoConfigFilter.apply(jiraConfigRepo.findByActiveTrue()) { it.name },
            confluenceConfigs = demoConfigFilter.apply(confluenceConfigRepo.findByActiveTrue()) { it.name },
            githubConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findByActiveTrue()) { it.name },
        )
}
