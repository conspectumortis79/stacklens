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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigImportExportServiceMergeRulesCoverageTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val dbConfigRepo: DatabaseConfigPort = mock()
    private val confluenceConfigRepo: ConfluenceConfigPort = mock()
    private val grafanaConfigRepo: GrafanaConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val aiConfigRepo: AiConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val mcpServerConfigRepo: McpServerConfigPort = mock()
    private val tx: TransactionPort = mock()

    init {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
    }

    private val preferences = InMemoryPreferences()
    private val textFilterConfig: TextFilterConfigPort = mock()
    private val demoUrlAlignment: DemoUrlAlignmentPort = mock()

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
            preferences,
            textFilterConfig,
            demoUrlAlignment,
        )

    private class InMemoryPreferences : UiPreferencesPort {
        val values = mutableMapOf<String, String>()

        override fun getAll(): Map<String, String> = values.toMap()

        override fun replaceAll(preferences: Map<String, String>) {
            values.clear()
            values.putAll(preferences)
        }
    }

    @Test
    fun `an empty preference store is filled although the import skips non empty targets`() {
        preferences.values["existing"] = "value"
        val stats =
            service.importConfigs(
                ConfigImportRequest(uiPreferences = mapOf("language" to "de")),
                skipIfNotEmpty = true,
            )

        assertEquals("value", preferences.values["existing"])
        assertFalse(stats.containsKey("preferences"), stats.toString())
    }

    @Test
    fun `an empty preference store is filled when the store has nothing yet`() {
        val stats =
            service.importConfigs(
                ConfigImportRequest(uiPreferences = mapOf("language" to "de")),
                skipIfNotEmpty = true,
            )

        assertEquals(1, stats["preferences"])
        assertEquals("de", preferences.values["language"])
    }

    @Test
    fun `a given model from the import overwrites the stored one`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, model = "stored-model"))

        service.importConfigs(
            ConfigImportRequest(aiConfig = AiConfig(id = 1L, model = "imported-model"), replaceAiConfig = false),
            skipIfNotEmpty = false,
        )

        assertEquals("imported-model", aiConfigRepo.findAiConfig()?.model)
    }

    @Test
    fun `a missing model in the import keeps the stored one`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, model = "stored-model"))

        service.importConfigs(
            ConfigImportRequest(aiConfig = AiConfig(id = 1L, model = null), replaceAiConfig = false),
            skipIfNotEmpty = false,
        )

        assertEquals("stored-model", aiConfigRepo.findAiConfig()?.model)
    }

    @Test
    fun `an empty model string in the import keeps the stored one`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, model = "stored-model"))

        service.importConfigs(
            ConfigImportRequest(aiConfig = AiConfig(id = 1L, model = ""), replaceAiConfig = false),
            skipIfNotEmpty = false,
        )

        assertEquals("stored-model", aiConfigRepo.findAiConfig()?.model)
    }

    @Test
    fun `a blank imported model keeps the stored one`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, model = "stored-model"))

        service.importConfigs(
            ConfigImportRequest(
                aiConfig = AiConfig(id = 1L, model = "   "),
                replaceAiConfig = false,
            ),
            skipIfNotEmpty = false,
        )

        assertEquals("stored-model", aiConfigRepo.findAiConfig()?.model)
    }

    @Test
    fun `developer options without preferences still count as ui settings`() {
        val stats =
            service.importConfigs(
                ConfigImportRequest(
                    developerOptions = DeveloperOptionSettings(showPromptButtons = false, textFilterEnabled = true, showTokenUsage = false, dockerTestEnv = false, dockerAutoStart = false),
                ),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["preferences"])
    }
}
