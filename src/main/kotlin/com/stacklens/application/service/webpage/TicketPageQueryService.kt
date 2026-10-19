package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.TicketImplementationPageData
import com.stacklens.application.port.inbound.web.TicketPageQueryUseCase
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class TicketPageQueryService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val jiraTicketImplementationRepo: JiraTicketImplementationPort,
    private val demoConfigFilter: DemoConfigPort,
) : TicketPageQueryUseCase {
    override fun getTicketImplementationPageData(): TicketImplementationPageData =
        TicketImplementationPageData(
            jiraConfigs = demoConfigFilter.apply(jiraConfigRepo.findByActiveTrue()) { it.name },
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            recentAnalyses = jiraTicketImplementationRepo.findAllByOrderByCreatedAtDesc().take(10),
            localRepoConfigs = localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS").filter { it.active },
            githubRepoConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs().filter { it.active }) { it.name },
            dbConfigs = demoConfigFilter.apply(dbConfigRepo.findByActiveTrue()) { it.name },
        )
}
