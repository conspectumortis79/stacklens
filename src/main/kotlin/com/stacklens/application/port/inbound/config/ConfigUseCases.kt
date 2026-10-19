package com.stacklens.application.port.inbound.config

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig

interface GitlabConfigUseCase {
    fun listVisibleGitlabConfigs(): List<GitlabConfig>

    fun findGitlabConfig(id: Long): GitlabConfig?

    fun saveGitlabConfig(config: GitlabConfig): GitlabConfig

    fun deleteGitlabConfig(id: Long)

    fun deleteAllGitlabConfigs()

    fun testGitlabConnection(id: Long): Map<String, Any>?

    fun listGitlabConfigs(): List<GitlabConfig>
}

interface DatabaseConfigUseCase {
    fun listVisibleDatabaseConfigs(): List<DatabaseConfig>

    fun findDatabaseConfig(id: Long): DatabaseConfig?

    fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig

    fun deleteDatabaseConfig(id: Long)

    fun deleteAllDatabaseConfigs()

    fun testDatabaseConnection(id: Long): Map<String, Any>?

    fun listDatabaseConfigs(): List<DatabaseConfig>
}

interface ConfluenceConfigUseCase {
    fun listVisibleConfluenceConfigs(): List<ConfluenceConfig>

    fun findConfluenceConfig(id: Long): ConfluenceConfig?

    fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig

    fun deleteConfluenceConfig(id: Long)

    fun deleteAllConfluenceConfigs()

    fun testConfluenceConnection(
        id: Long,
        keyword: String?,
    ): Map<String, Any>?

    fun listConfluenceConfigs(): List<ConfluenceConfig>
}

interface GrafanaConfigUseCase {
    fun listVisibleGrafanaConfigs(): List<GrafanaConfig>

    fun findGrafanaConfig(id: Long): GrafanaConfig?

    fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig

    fun deleteGrafanaConfig(id: Long)

    fun deleteAllGrafanaConfigs()

    fun testGrafanaConnection(id: Long): Map<String, Any>?

    fun listGrafanaConfigs(): List<GrafanaConfig>
}

interface JiraConfigUseCase {
    fun listVisibleJiraConfigs(): List<JiraConfig>

    fun findJiraConfig(id: Long): JiraConfig?

    fun saveJiraConfig(config: JiraConfig): JiraConfig

    fun deleteJiraConfig(id: Long)

    fun deleteAllJiraConfigs()

    fun testJiraConnection(
        id: Long,
        keyword: String?,
    ): Map<String, Any>?

    fun listJiraConfigs(): List<JiraConfig>
}

data class AiConfigSaveRequest(
    val githubToken: String? = null,
    val openAiApiKey: String? = null,
    val openAiReasoningEffort: String? = null,
    val model: String? = null,
    val aiProvider: String? = null,
    val ollamaUrl: String? = null,
    val ollamaNumCtx: Int = 0,
    val lmStudioUrl: String? = null,
    val openRouterApiKey: String? = null,
    val claudeCodeApiKey: String? = null,
    val compatibleApiKey: String? = null,
    val compatibleBaseUrl: String? = null,
    val explicitClearGithubToken: Boolean = false,
    val explicitClearOpenAiKey: Boolean = false,
    val explicitClearOpenRouterKey: Boolean = false,
    val explicitClearClaudeCodeKey: Boolean = false,
    val explicitClearCompatibleKey: Boolean = false,
)

interface AiConfigUseCase {
    fun saveAiConfig(config: AiConfig): AiConfig

    fun saveAiConfigFrom(request: AiConfigSaveRequest): AiConfig

    fun getAiConfig(): AiConfig?
}

interface GithubConfigUseCase {
    fun listGithubRepoConfigs(): List<GithubRepoConfig>

    fun listVisibleGithubRepoConfigs(): List<GithubRepoConfig>

    fun findGithubRepoConfig(id: Long): GithubRepoConfig?

    fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig

    fun deleteGithubRepoConfig(id: Long)

    fun githubRepoConfigExistsById(id: Long): Boolean

    fun resolveGithubAccessToken(repoConfigId: Long): String?

    fun testGithubRepoConnection(repoConfigId: Long): Map<String, Any>
}

interface ConfigImportUseCase {
    fun hasAnyConfig(): Boolean

    fun importConfigs(
        request: ConfigImportRequest,
        skipIfNotEmpty: Boolean,
    ): Map<String, Int>
}

data class ConfigImportRequest(
    val gitlabConfigs: List<GitlabConfig> = emptyList(),
    val databaseConfigs: List<DatabaseConfig> = emptyList(),
    val confluenceConfigs: List<ConfluenceConfig> = emptyList(),
    val grafanaConfigs: List<GrafanaConfig> = emptyList(),
    val jiraConfigs: List<JiraConfig> = emptyList(),
    val aiConfig: AiConfig? = null,
    val language: String? = null,
    val availableModels: String? = null,
    val replaceAiConfig: Boolean = false,
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
    val mcpServerConfigs: List<McpServerConfig> = emptyList(),
    val theme: String? = null,
    val developerOptions: DeveloperOptionSettings? = null,
    val uiPreferences: Map<String, String> = emptyMap(),
)
