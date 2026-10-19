package com.stacklens.application.port.out

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig

interface GitlabConfigPort {
    fun findByActiveTrue(): List<GitlabConfig>

    fun findGitlabConfigById(id: Long): GitlabConfig?

    fun findAllGitlabConfigs(): List<GitlabConfig>

    fun existsActiveGitlabConfig(): Boolean

    fun findAllGitlabConfigsByIds(ids: Iterable<Long>): List<GitlabConfig>

    fun saveGitlabConfig(config: GitlabConfig): GitlabConfig

    fun deleteGitlabConfigById(id: Long)

    fun deleteAllGitlabConfigs()
}

interface DatabaseConfigPort {
    fun findByActiveTrue(): List<DatabaseConfig>

    fun findDatabaseConfigById(id: Long): DatabaseConfig?

    fun findAllDatabaseConfigs(): List<DatabaseConfig>

    fun findAllDatabaseConfigsByIds(ids: Iterable<Long>): List<DatabaseConfig>

    fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig

    fun deleteDatabaseConfigById(id: Long)

    fun deleteAllDatabaseConfigs()
}

interface ConfluenceConfigPort {
    fun findByActiveTrue(): List<ConfluenceConfig>

    fun findConfluenceConfigById(id: Long): ConfluenceConfig?

    fun findAllConfluenceConfigs(): List<ConfluenceConfig>

    fun findAllConfluenceConfigsByIds(ids: Iterable<Long>): List<ConfluenceConfig>

    fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig

    fun deleteConfluenceConfigById(id: Long)

    fun deleteAllConfluenceConfigs()
}

interface GrafanaConfigPort {
    fun findAllGrafanaConfigs(): List<GrafanaConfig>

    fun findGrafanaConfigById(id: Long): GrafanaConfig?

    fun findByActiveTrue(): List<GrafanaConfig>

    fun findAllGrafanaConfigsByIds(ids: Iterable<Long>): List<GrafanaConfig>

    fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig

    fun deleteGrafanaConfigById(id: Long)

    fun deleteAllGrafanaConfigs()
}

interface JiraConfigPort {
    fun findByActiveTrue(): List<JiraConfig>

    fun findFirstByActiveTrue(): JiraConfig?

    fun findJiraConfigById(id: Long): JiraConfig?

    fun findAllJiraConfigs(): List<JiraConfig>

    fun findAllJiraConfigsByIds(ids: Iterable<Long>): List<JiraConfig>

    fun saveJiraConfig(config: JiraConfig): JiraConfig

    fun jiraConfigExistsById(id: Long): Boolean

    fun deleteJiraConfigById(id: Long)

    fun deleteAllJiraConfigs()
}

interface AiConfigPort {
    fun findAiConfig(): AiConfig?

    fun saveAiConfig(config: AiConfig): AiConfig

    fun deleteAiConfig()
}

interface LocalRepoConfigPort {
    fun findAllByScope(scope: String): List<LocalRepoConfig>

    fun findLocalRepoConfigById(id: Long): LocalRepoConfig?

    fun findAllLocalRepoConfigs(): List<LocalRepoConfig>

    fun saveLocalRepoConfig(config: LocalRepoConfig): LocalRepoConfig

    fun localRepoConfigExistsById(id: Long): Boolean

    fun findFirstByPathAndScope(
        path: String,
        scope: String,
    ): LocalRepoConfig?

    fun deleteLocalRepoConfigById(id: Long)

    fun deleteAllLocalRepoConfigs()
}

interface GithubRepoConfigPort {
    fun findAllGithubRepoConfigs(): List<GithubRepoConfig>

    fun findByActiveTrue(): List<GithubRepoConfig>

    fun findGithubRepoConfigById(id: Long): GithubRepoConfig?

    fun findAllGithubRepoConfigsByIds(ids: Iterable<Long>): List<GithubRepoConfig>

    fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig

    fun githubRepoConfigExistsById(id: Long): Boolean

    fun deleteGithubRepoConfigById(id: Long)

    fun deleteAllGithubRepoConfigs()
}
