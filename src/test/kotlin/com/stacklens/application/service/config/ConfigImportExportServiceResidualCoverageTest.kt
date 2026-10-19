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
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigImportExportServiceResidualCoverageTest {
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

    init {
        whenever(tx.inTransaction(any<() -> Any?>())).thenAnswer { inv -> inv.getArgument<() -> Any?>(0).invoke() }
        whenever(tx.afterCommit(any())).thenAnswer { inv -> inv.getArgument<() -> Unit>(0).invoke() }
        whenever(demoUrlAlignment.alignAll()).thenReturn(emptyList())
    }

    private fun developerOptions() =
        DeveloperOptionSettings(
            showPromptButtons = true,
            textFilterEnabled = false,
            showTokenUsage = true,
            dockerTestEnv = false,
            dockerAutoStart = false,
        )

    @Test
    fun `developer options alone count as ui settings`() {
        whenever(uiPreferences.getAll()).thenReturn(emptyMap())

        val stats =
            service.importConfigs(
                ConfigImportRequest(developerOptions = developerOptions()),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["preferences"])
        verify(uiPreferences).replaceAll(any())
        verify(textFilterConfig).setEnabled(false)
    }

    @Test
    fun `skipIfNotEmpty leaves existing preferences untouched`() {
        whenever(uiPreferences.getAll()).thenReturn(mapOf("theme" to "dark"))

        val stats =
            service.importConfigs(
                ConfigImportRequest(theme = "light"),
                skipIfNotEmpty = true,
            )

        assertEquals(null, stats["preferences"])
    }

    @Test
    fun `without skipIfNotEmpty existing preferences are replaced`() {
        whenever(uiPreferences.getAll()).thenReturn(mapOf("theme" to "dark"))

        val stats =
            service.importConfigs(
                ConfigImportRequest(developerOptions = developerOptions()),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["preferences"])
        verify(uiPreferences).replaceAll(any())
    }

    @Test
    fun `blank ai model and token in merge mode keep the stored values`() {
        val existing = AiConfig(id = 1L, githubToken = "keep-token", model = "keep-model")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(existing)
        whenever(uiPreferences.getAll()).thenReturn(emptyMap())

        val stats =
            service.importConfigs(
                ConfigImportRequest(
                    aiConfig = AiConfig(id = 9L, githubToken = "", model = "  "),
                    replaceAiConfig = false,
                ),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["ai"])
        assertEquals("keep-token", existing.githubToken)
        assertEquals("keep-model", existing.model)
    }
}
