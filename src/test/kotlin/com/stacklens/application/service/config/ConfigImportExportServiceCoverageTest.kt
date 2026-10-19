package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.ConfigImportRequest
import com.stacklens.application.port.inbound.config.DeveloperOptionSettings
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.config.DemoUrlAlignmentPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigImportExportServiceCoverageTest {
    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val dbConfigRepo = mock<DatabaseConfigPort>()
    private val confluenceConfigRepo = mock<ConfluenceConfigPort>()
    private val grafanaConfigRepo = mock<GrafanaConfigPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val aiConfigRepo = mock<AiConfigPort>()
    private val githubRepoConfigRepo = mock<GithubRepoConfigPort>()
    private val mcpServerConfigRepo = mock<McpServerConfigPort>()
    private val tx = mock<TransactionPort>()
    private val uiPreferences = mock<UiPreferencesPort>()
    private val textFilterConfig = mock<TextFilterConfigPort>()
    private val demoUrlAlignment = mock<DemoUrlAlignmentPort>()

    private val service =
        ConfigImportExportService(
            gitlabConfigRepo,
            dbConfigRepo,
            confluenceConfigRepo,
            grafanaConfigRepo,
            jiraConfigRepo,
            aiConfigRepo,
            githubRepoConfigRepo,
            mcpServerConfigRepo,
            tx,
            uiPreferences,
            textFilterConfig,
            demoUrlAlignment,
        )

    private val databaseConfig =
        DatabaseConfig(
            name = "pg",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "app",
            username = "u",
            password = "p",
        )

    init {
        whenever(tx.inTransaction(any<() -> Any?>())).thenAnswer { inv -> inv.getArgument<() -> Any?>(0).invoke() }
        whenever(tx.afterCommit(any())).thenAnswer { inv -> inv.getArgument<() -> Unit>(0).invoke() }
        whenever(demoUrlAlignment.alignAll()).thenReturn(emptyList())
    }

    @Test
    fun `an empty import touches no repository and aligns nothing`() {
        assertEquals(0, service.importConfigs(ConfigImportRequest(), skipIfNotEmpty = false).size)
        assertTrue(!service.hasAnyConfig())
    }

    @Test
    fun `collections are replaced when the target is empty`() {
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())

        val stats = service.importConfigs(ConfigImportRequest(databaseConfigs = listOf(databaseConfig)), skipIfNotEmpty = false)

        assertEquals(1, stats["databases"])
        verify(dbConfigRepo).deleteAllDatabaseConfigs()
        verify(dbConfigRepo).saveDatabaseConfig(databaseConfig)
    }

    @Test
    fun `existing collections are kept when skipping non empty targets`() {
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(listOf(databaseConfig))

        val stats = service.importConfigs(ConfigImportRequest(databaseConfigs = listOf(databaseConfig)), skipIfNotEmpty = true)

        assertEquals(null, stats["databases"])
        verify(dbConfigRepo, never()).deleteAllDatabaseConfigs()
    }

    @Test
    fun `reporting any configuration covers every store including mcp and github`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .GithubRepoConfig(name = "g", owner = "o", repo = "r"),
            ),
        )

        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `ai settings keep an existing configuration when skipping non empty targets`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig())

        service.importConfigs(ConfigImportRequest(language = "de"), skipIfNotEmpty = true)

        verify(aiConfigRepo, never()).saveAiConfig(any())
    }

    @Test
    fun `ai settings replace every credential when requested`() {
        val existing = AiConfig(githubToken = "old", model = "old-model", language = "de")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(existing)

        val stats =
            service.importConfigs(
                ConfigImportRequest(
                    aiConfig = AiConfig(githubToken = "new", model = "new-model"),
                    replaceAiConfig = true,
                    language = "en",
                    availableModels = "a,b",
                ),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["ai"])
        assertEquals("new", existing.githubToken)
        assertEquals("new-model", existing.model)
        assertEquals("en", existing.language)
        assertEquals("a,b", existing.availableModels)
    }

    @Test
    fun `blank ai credentials are ignored when merging`() {
        val existing = AiConfig(githubToken = "keep", model = "keep-model", language = "de")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(existing)

        service.importConfigs(
            ConfigImportRequest(
                aiConfig = AiConfig(githubToken = "  ", model = "", aiProvider = "ollama", ollamaUrl = "http://ollama:11434"),
                replaceAiConfig = false,
            ),
            skipIfNotEmpty = false,
        )

        assertEquals("keep", existing.githubToken)
        assertEquals("keep-model", existing.model)
        assertEquals("ollama", existing.aiProvider)
    }

    @Test
    fun `a new ai configuration is created when none exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        service.importConfigs(ConfigImportRequest(language = "en"), skipIfNotEmpty = true)

        verify(aiConfigRepo).saveAiConfig(any())
    }

    @Test
    fun `ui settings import theme and developer options together`() {
        val developer = DeveloperOptionSettings(showPromptButtons = true, textFilterEnabled = false, showTokenUsage = true, dockerTestEnv = false, dockerAutoStart = false)
        whenever(uiPreferences.getAll()).thenReturn(mapOf("theme" to "legacy"))

        val stats =
            service.importConfigs(
                ConfigImportRequest(theme = "dark", developerOptions = developer, uiPreferences = mapOf("dev_show_token_usage" to "true")),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["preferences"])
        verify(textFilterConfig).setEnabled(false)
        verify(uiPreferences).replaceAll(
            mapOf(
                "theme" to "dark",
                "dev_show_prompt_buttons" to "true",
                "text_filter_enabled" to "false",
                "dev_show_token_usage" to "true",
                "dev_docker_test_env" to "false",
                "dev_docker_auto_start" to "false",
            ),
        )
    }

    @Test
    fun `existing ui preferences are kept when skipping non empty targets`() {
        whenever(uiPreferences.getAll()).thenReturn(mapOf("theme" to "legacy"))

        service.importConfigs(ConfigImportRequest(uiPreferences = mapOf("a" to "b")), skipIfNotEmpty = true)

        verify(uiPreferences, never()).replaceAll(any())
    }

    @Test
    fun `imported demo urls are rewritten when the alignment reports changes`() {
        whenever(demoUrlAlignment.alignAll()).thenReturn(listOf("Jira→http://localhost:8081"))

        service.importConfigs(ConfigImportRequest(theme = "modern"), skipIfNotEmpty = false)

        verify(demoUrlAlignment).alignAll()
    }
}
