package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.ConfluenceDemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeederStrategy
import com.stacklens.adapter.out.demo.DemoGrafanaEndpointResolver
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.GithubDemoDataSeeder
import com.stacklens.adapter.out.demo.GitlabDemoDataSeeder
import com.stacklens.adapter.out.demo.GrafanaDemoDataSeeder
import com.stacklens.adapter.out.demo.JiraDemoDataSeeder
import com.stacklens.adapter.out.demo.McpDemoDataSeeder
import com.stacklens.adapter.out.demo.MysqlDemoDataSeeder
import org.mockito.kotlin.mock

internal object DemoDataSeederTestHelper {
    fun buildLegacy(
        gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort,
        jiraConfigRepo: com.stacklens.application.port.out.JiraConfigPort,
        confluenceConfigRepo: com.stacklens.application.port.out.ConfluenceConfigPort,
        grafanaConfigRepo: com.stacklens.application.port.out.GrafanaConfigPort,
        databaseConfigRepo: com.stacklens.application.port.out.DatabaseConfigPort,
        mcpConfigRepo: com.stacklens.application.port.out.McpServerConfigPort,
        githubRepoConfigRepo: com.stacklens.application.port.out.GithubRepoConfigPort,
        hostResolver: DemoHostResolver = DemoHostResolver(),
    ): DemoDataSeeder =
        DemoDataSeeder(
            legacySeeders(
                gitlabConfigRepo = gitlabConfigRepo,
                jiraConfigRepo = jiraConfigRepo,
                confluenceConfigRepo = confluenceConfigRepo,
                grafanaConfigRepo = grafanaConfigRepo,
                databaseConfigRepo = databaseConfigRepo,
                mcpConfigRepo = mcpConfigRepo,
                githubRepoConfigRepo = githubRepoConfigRepo,
                hostResolver = hostResolver,
            ),
        )

    fun legacySeeders(
        gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort,
        jiraConfigRepo: com.stacklens.application.port.out.JiraConfigPort,
        confluenceConfigRepo: com.stacklens.application.port.out.ConfluenceConfigPort,
        grafanaConfigRepo: com.stacklens.application.port.out.GrafanaConfigPort,
        databaseConfigRepo: com.stacklens.application.port.out.DatabaseConfigPort,
        mcpConfigRepo: com.stacklens.application.port.out.McpServerConfigPort,
        githubRepoConfigRepo: com.stacklens.application.port.out.GithubRepoConfigPort,
        hostResolver: DemoHostResolver,
        grafanaEndpoint: com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort =
            DemoGrafanaEndpointResolver(hostResolver),
    ): List<DemoDataSeederStrategy> =
        listOf(
            GitlabDemoDataSeeder(gitlabConfigRepo, hostResolver),
            GithubDemoDataSeeder(githubRepoConfigRepo, hostResolver),
            JiraDemoDataSeeder(jiraConfigRepo, hostResolver),
            ConfluenceDemoDataSeeder(confluenceConfigRepo, hostResolver),
            GrafanaDemoDataSeeder(grafanaConfigRepo, grafanaEndpoint),
            MysqlDemoDataSeeder(databaseConfigRepo, hostResolver),
            McpDemoDataSeeder(mcpConfigRepo, hostResolver),
        )

    fun stubRepos(): Repos =
        Repos(
            gitlabConfigRepo = mock(),
            jiraConfigRepo = mock(),
            confluenceConfigRepo = mock(),
            grafanaConfigRepo = mock(),
            databaseConfigRepo = mock(),
            mcpConfigRepo = mock(),
            githubRepoConfigRepo = mock(),
        )

    data class Repos(
        val gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort,
        val jiraConfigRepo: com.stacklens.application.port.out.JiraConfigPort,
        val confluenceConfigRepo: com.stacklens.application.port.out.ConfluenceConfigPort,
        val grafanaConfigRepo: com.stacklens.application.port.out.GrafanaConfigPort,
        val databaseConfigRepo: com.stacklens.application.port.out.DatabaseConfigPort,
        val mcpConfigRepo: com.stacklens.application.port.out.McpServerConfigPort,
        val githubRepoConfigRepo: com.stacklens.application.port.out.GithubRepoConfigPort,
    ) {
        fun toSeeder(): DemoDataSeeder =
            DemoDataSeeder(
                legacySeeders(
                    gitlabConfigRepo = gitlabConfigRepo,
                    jiraConfigRepo = jiraConfigRepo,
                    confluenceConfigRepo = confluenceConfigRepo,
                    grafanaConfigRepo = grafanaConfigRepo,
                    databaseConfigRepo = databaseConfigRepo,
                    mcpConfigRepo = mcpConfigRepo,
                    githubRepoConfigRepo = githubRepoConfigRepo,
                    hostResolver = DemoHostResolver(),
                ),
            )
    }
}
