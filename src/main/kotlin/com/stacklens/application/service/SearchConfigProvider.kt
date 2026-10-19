
package com.stacklens.application.service

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchSession

@ApplicationService
class SearchConfigProvider(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
) : SearchConfigResolver {
    override fun resolveGitlabConfigs(session: SearchSession): List<GitlabConfig> =
        if (session.gitlabConfigIds.isNullOrBlank()) {
            gitlabConfigRepo.findByActiveTrue()
        } else {
            gitlabConfigRepo.findAllGitlabConfigsByIds(session.gitlabConfigIdList())
        }

    override fun resolveDbConfigs(session: SearchSession): List<DatabaseConfig> =
        if (session.databaseConfigIds.isNullOrBlank()) {
            dbConfigRepo.findByActiveTrue()
        } else {
            dbConfigRepo.findAllDatabaseConfigsByIds(session.databaseConfigIdList())
        }

    override fun resolveConfluenceConfigs(session: SearchSession): List<ConfluenceConfig> =
        if (session.confluenceConfigIds.isNullOrBlank()) {
            confluenceConfigRepo.findByActiveTrue()
        } else {
            confluenceConfigRepo.findAllConfluenceConfigsByIds(session.confluenceConfigIdList())
        }

    override fun resolveGrafanaConfigs(session: SearchSession): List<GrafanaConfig> =
        if (session.grafanaConfigIds.isNullOrBlank()) {
            grafanaConfigRepo.findByActiveTrue()
        } else {
            grafanaConfigRepo.findAllGrafanaConfigsByIds(session.grafanaConfigIdList())
        }

    override fun resolveJiraConfigs(session: SearchSession): List<JiraConfig> =
        if (session.jiraConfigIds.isNullOrBlank()) {
            jiraConfigRepo.findByActiveTrue()
        } else {
            jiraConfigRepo.findAllJiraConfigsByIds(session.jiraConfigIdList())
        }

    override fun resolveGithubConfigs(session: SearchSession): List<GithubRepoConfig> =
        if (session.githubConfigIds.isNullOrBlank()) {
            githubRepoConfigRepo.findByActiveTrue()
        } else {
            githubRepoConfigRepo.findAllGithubRepoConfigsByIds(session.githubConfigIdList())
        }
}
