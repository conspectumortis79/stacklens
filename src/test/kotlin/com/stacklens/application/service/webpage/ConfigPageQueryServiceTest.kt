package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.ConfigPageData
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigPageQueryServiceTest {
    private val ai = AiConfig()
    private val gitlab = GitlabConfig(name = "g", baseUrl = "u", accessToken = "t", groupId = "g")
    private val db = DatabaseConfig(name = "d", dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "n", username = "u", password = "p")
    private val conf = ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t")
    private val grafana = GrafanaConfig(name = "gr", baseUrl = "u", username = "u", password = "p")
    private val jira = JiraConfig(name = "j", baseUrl = "u", username = "u", apiToken = "t", projectKey = "P")
    private val gh = GithubRepoConfig(name = "gh", owner = "o", repo = "r")

    private fun gitlabRepo(list: List<GitlabConfig>): GitlabConfigPort =
        object : GitlabConfigPort {
            override fun findByActiveTrue(): List<GitlabConfig> = list

            override fun findGitlabConfigById(id: Long): GitlabConfig? = null

            override fun findAllGitlabConfigs(): List<GitlabConfig> = list

            override fun findAllGitlabConfigsByIds(ids: Iterable<Long>): List<GitlabConfig> = emptyList()

            override fun existsActiveGitlabConfig(): Boolean = list.any { it.active }

            override fun saveGitlabConfig(config: GitlabConfig): GitlabConfig = config

            override fun deleteGitlabConfigById(id: Long) {}

            override fun deleteAllGitlabConfigs() {}
        }

    private fun dbRepo(list: List<DatabaseConfig>): DatabaseConfigPort =
        object : DatabaseConfigPort {
            override fun findByActiveTrue(): List<DatabaseConfig> = list

            override fun findDatabaseConfigById(id: Long): DatabaseConfig? = null

            override fun findAllDatabaseConfigs(): List<DatabaseConfig> = list

            override fun findAllDatabaseConfigsByIds(ids: Iterable<Long>): List<DatabaseConfig> = emptyList()

            override fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig = config

            override fun deleteDatabaseConfigById(id: Long) {}

            override fun deleteAllDatabaseConfigs() {}
        }

    private fun confluenceRepo(list: List<ConfluenceConfig>): ConfluenceConfigPort =
        object : ConfluenceConfigPort {
            override fun findByActiveTrue(): List<ConfluenceConfig> = list

            override fun findConfluenceConfigById(id: Long): ConfluenceConfig? = null

            override fun findAllConfluenceConfigs(): List<ConfluenceConfig> = list

            override fun findAllConfluenceConfigsByIds(ids: Iterable<Long>): List<ConfluenceConfig> = emptyList()

            override fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig = config

            override fun deleteConfluenceConfigById(id: Long) {}

            override fun deleteAllConfluenceConfigs() {}
        }

    private fun grafanaRepo(list: List<GrafanaConfig>): GrafanaConfigPort =
        object : GrafanaConfigPort {
            override fun findAllGrafanaConfigs(): List<GrafanaConfig> = list

            override fun findGrafanaConfigById(id: Long): GrafanaConfig? = null

            override fun findByActiveTrue(): List<GrafanaConfig> = list

            override fun findAllGrafanaConfigsByIds(ids: Iterable<Long>): List<GrafanaConfig> = emptyList()

            override fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig = config

            override fun deleteGrafanaConfigById(id: Long) {}

            override fun deleteAllGrafanaConfigs() {}
        }

    private fun jiraRepo(list: List<JiraConfig>): JiraConfigPort =
        object : JiraConfigPort {
            override fun findByActiveTrue(): List<JiraConfig> = list

            override fun findFirstByActiveTrue(): JiraConfig? = null

            override fun findJiraConfigById(id: Long): JiraConfig? = null

            override fun findAllJiraConfigs(): List<JiraConfig> = list

            override fun findAllJiraConfigsByIds(ids: Iterable<Long>): List<JiraConfig> = emptyList()

            override fun saveJiraConfig(config: JiraConfig): JiraConfig = config

            override fun jiraConfigExistsById(id: Long): Boolean = false

            override fun deleteJiraConfigById(id: Long) {}

            override fun deleteAllJiraConfigs() {}
        }

    private fun aiRepo(value: AiConfig?): AiConfigPort =
        object : AiConfigPort {
            override fun findAiConfig(): AiConfig? = value

            override fun saveAiConfig(config: AiConfig): AiConfig = config

            override fun deleteAiConfig() {}
        }

    private fun ghRepo(list: List<GithubRepoConfig>): GithubRepoConfigPort =
        object : GithubRepoConfigPort {
            override fun findAllGithubRepoConfigs(): List<GithubRepoConfig> = list

            override fun findByActiveTrue(): List<GithubRepoConfig> = list

            override fun findGithubRepoConfigById(id: Long): GithubRepoConfig? = null

            override fun findAllGithubRepoConfigsByIds(ids: Iterable<Long>): List<GithubRepoConfig> = emptyList()

            override fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig = config

            override fun githubRepoConfigExistsById(id: Long): Boolean = false

            override fun deleteGithubRepoConfigById(id: Long) {}

            override fun deleteAllGithubRepoConfigs() {}
        }

    private fun service(
        gitlabList: List<GitlabConfig> = listOf(gitlab),
        dbList: List<DatabaseConfig> = listOf(db),
        confList: List<ConfluenceConfig> = listOf(conf),
        grafanaList: List<GrafanaConfig> = listOf(grafana),
        jiraList: List<JiraConfig> = listOf(jira),
        ghList: List<GithubRepoConfig> = listOf(gh),
        aiValue: AiConfig? = ai,
    ): ConfigPageQueryService =
        ConfigPageQueryService(
            gitlabConfigRepo = gitlabRepo(gitlabList),
            dbConfigRepo = dbRepo(dbList),
            confluenceConfigRepo = confluenceRepo(confList),
            grafanaConfigRepo = grafanaRepo(grafanaList),
            jiraConfigRepo = jiraRepo(jiraList),
            aiConfigRepo = aiRepo(aiValue),
            githubRepoConfigRepo = ghRepo(ghList),
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
        )

    @Test
    fun `getConfigPageData returns the lists and aiConfig from the ports`() {
        val data = service().getConfigPageData()
        assertEquals(listOf(gitlab), data.gitlabConfigs)
        assertEquals(listOf(db), data.dbConfigs)
        assertEquals(listOf(conf), data.confluenceConfigs)
        assertEquals(listOf(grafana), data.grafanaConfigs)
        assertEquals(listOf(jira), data.jiraConfigs)
        assertEquals(listOf(gh), data.githubRepoConfigs)
        assertEquals(ai, data.aiConfig)
    }

    @Test
    fun `getConfigPageData returns empty lists and a null aiConfig when no rows exist`() {
        val data =
            service(
                gitlabList = emptyList(),
                dbList = emptyList(),
                confList = emptyList(),
                grafanaList = emptyList(),
                jiraList = emptyList(),
                ghList = emptyList(),
                aiValue = null,
            ).getConfigPageData()
        assertEquals(emptyList<GitlabConfig>(), data.gitlabConfigs)
        assertEquals(emptyList<DatabaseConfig>(), data.dbConfigs)
        assertEquals(emptyList<ConfluenceConfig>(), data.confluenceConfigs)
        assertEquals(emptyList<GrafanaConfig>(), data.grafanaConfigs)
        assertEquals(emptyList<JiraConfig>(), data.jiraConfigs)
        assertEquals(emptyList<GithubRepoConfig>(), data.githubRepoConfigs)
        assertNull(data.aiConfig)
    }

    @Test
    fun `ConfigPageData carries all fields`() {
        val data =
            ConfigPageData(
                gitlabConfigs = listOf(gitlab),
                dbConfigs = listOf(db),
                confluenceConfigs = listOf(conf),
                grafanaConfigs = listOf(grafana),
                jiraConfigs = listOf(jira),
                githubRepoConfigs = listOf(gh),
                aiConfig = ai,
            )
        assertEquals(1, data.gitlabConfigs.size)
        assertEquals(ai, data.aiConfig)
    }
}
