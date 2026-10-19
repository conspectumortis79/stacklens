package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.ConfigPageData
import com.stacklens.application.port.inbound.web.ConfigPageQueryUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.CompletableFuture

@ApplicationService
class ConfigPageQueryService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val aiConfigRepo: AiConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
    private val parallelTasks: ParallelTaskPort,
) : ConfigPageQueryUseCase {
    override fun getConfigPageData(): ConfigPageData {
        val gitlabFuture = parallelTasks.submit { gitlabConfigRepo.findAllGitlabConfigs() }
        val dbFuture = parallelTasks.submit { dbConfigRepo.findAllDatabaseConfigs() }
        val confluenceFuture = parallelTasks.submit { confluenceConfigRepo.findAllConfluenceConfigs() }
        val grafanaFuture = parallelTasks.submit { grafanaConfigRepo.findAllGrafanaConfigs() }
        val jiraFuture = parallelTasks.submit { jiraConfigRepo.findAllJiraConfigs() }
        val githubFuture = parallelTasks.submit { githubRepoConfigRepo.findAllGithubRepoConfigs() }
        val aiFuture = parallelTasks.submit { aiConfigRepo.findAiConfig() }
        CompletableFuture
            .allOf(
                gitlabFuture,
                dbFuture,
                confluenceFuture,
                grafanaFuture,
                jiraFuture,
                githubFuture,
                aiFuture,
            ).join()
        return ConfigPageData(
            gitlabConfigs = demoConfigFilter.apply(gitlabFuture.join()) { it.name },
            dbConfigs = demoConfigFilter.apply(dbFuture.join()) { it.name },
            confluenceConfigs = demoConfigFilter.apply(confluenceFuture.join()) { it.name },
            grafanaConfigs = demoConfigFilter.apply(grafanaFuture.join()) { it.name },
            jiraConfigs = demoConfigFilter.apply(jiraFuture.join()) { it.name },
            githubRepoConfigs = demoConfigFilter.apply(githubFuture.join()) { it.name },
            aiConfig = aiFuture.join(),
        )
    }
}
