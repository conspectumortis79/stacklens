package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.inbound.rest.ExportRequestDto
import com.stacklens.adapter.inbound.rest.ImportFromPathRequest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.port.inbound.config.ConfigImportRequest
import com.stacklens.application.port.inbound.config.ConfigImportUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.config.AiConfigReadPort
import com.stacklens.application.port.out.config.ConfluenceConfigReadPort
import com.stacklens.application.port.out.config.DatabaseConfigReadPort
import com.stacklens.application.port.out.config.GitlabConfigReadPort
import com.stacklens.application.port.out.config.GrafanaConfigReadPort
import com.stacklens.application.port.out.config.JiraConfigReadPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.service.config.ConfigEnvelopeService
import com.stacklens.application.service.config.ConfigExportService
import com.stacklens.application.service.workspace.WorkspaceFileService
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

private const val EXPORT_EPOCH = 1_750_000_000_000L

class ImportExportConfigSettingsTest {
    private val mapper = ObjectMapper()
    private val runtimeTextFilter = StubTextFilter(initialEnabled = false)
    private val gitlabConfigs = mock<GitlabConfigReadPort>()
    private val databaseConfigs = mock<DatabaseConfigReadPort>()
    private val confluenceConfigs = mock<ConfluenceConfigReadPort>()
    private val grafanaConfigs = mock<GrafanaConfigReadPort>()
    private val jiraConfigs = mock<JiraConfigReadPort>()
    private val aiConfig = mock<AiConfigReadPort>()
    private val configImport = mock<ConfigImportUseCase>()
    private val workspaceFiles: WorkspaceFileUseCase =
        WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter())
    private val i18n = mock<I18nUseCase>()
    private val githubPort = mock<GithubRepoConfigPort>()
    private val mcpPort = mock<McpServerConfigPort>()

    @Test
    fun `export includes language all developer options and available models`() {
        val preferences =
            StubUiPreferences(
                mapOf(
                    "theme" to "dark",
                    "dev_show_prompt_buttons" to "1",
                    "text_filter_enabled" to "false",
                    "dev_show_token_usage" to "0",
                    "dev_docker_test_env" to "true",
                    "dev_docker_auto_start" to "unknown",
                ),
            )
        stubEmptyConfig()
        whenever(aiConfig.getAiConfig()).thenReturn(
            AiConfig(
                language = "de",
                availableModels = "model-a,model-b",
            ),
        )
        val controller = controller(preferences, runtimeTextFilter)

        val response = controller.exportConfig(ExportRequestDto("password"))
        val plain =
            ConfigEnvelopeCipherAdapter(mapper).decrypt(response.body!!, "password")!!
        val dto = mapper.readValue(plain, ConfigExportDto::class.java)

        assertEquals("de", dto.language)
        assertEquals("dark", dto.theme)
        assertEquals(listOf("model-a", "model-b"), dto.ai?.availableModels)
        val developer = requireNotNull(dto.developerOptions)
        assertTrue(developer.showPromptButtons)
        assertFalse(developer.textFilterEnabled)
        assertFalse(developer.showTokenUsage)
        assertTrue(developer.dockerTestEnv)
        assertFalse(developer.dockerAutoStart)
        assertEquals("false", dto.uiPreferences["text_filter_enabled"])
    }

    @Test
    fun `export falls back to the default theme for an unknown stored theme`() {
        val preferences = StubUiPreferences(mapOf("theme" to "neon"))
        stubEmptyConfig()
        whenever(aiConfig.getAiConfig()).thenReturn(null)

        val response = controller(preferences, runtimeTextFilter).exportConfig(ExportRequestDto("password"))
        val plain = decryptExport(response.body)

        assertEquals("modern", mapper.readValue(plain, ConfigExportDto::class.java).theme)
    }

    @Test
    fun `import forwards theme, developer options and preferences to the import use case`() {
        stubEmptyConfig()
        whenever(configImport.importConfigs(any(), any())).thenReturn(emptyMap())
        val controller = controller(StubUiPreferences(), FakeTextFilter())
        val content =
            """
            {
              "version": 2,
              "language": "de",
              "theme": "popos",
              "developerOptions": {
                "showPromptButtons": true,
                "textFilterEnabled": false,
                "showTokenUsage": false,
                "dockerTestEnv": true,
                "dockerAutoStart": true
              },
              "uiPreferences": {"custom": "kept"},
              "ai": {"availableModels": ["a", "b"]}
            }
            """.trimIndent()

        val result = controller.importConfig(MockMultipartFile("file", "config.json", "application/json", content.toByteArray()), null)

        assertEquals(200, result.statusCode.value())
        val imported = captureImportRequest()
        assertEquals("de", imported.language)
        assertEquals("a,b", imported.availableModels)
        assertEquals("popos", imported.theme)
        assertEquals(mapOf("custom" to "kept"), imported.uiPreferences)
        val options = requireNotNull(imported.developerOptions)
        assertTrue(options.showPromptButtons)
        assertFalse(options.textFilterEnabled)
        assertFalse(options.showTokenUsage)
        assertTrue(options.dockerTestEnv)
        assertTrue(options.dockerAutoStart)
    }

    @Test
    fun `skipIfNotEmpty is forwarded to the import use case`() {
        stubEmptyConfig()
        whenever(configImport.importConfigs(any(), any())).thenReturn(emptyMap())
        val controller = controller(StubUiPreferences(), FakeTextFilter())
        val content = """{"version":2,"theme":"dark","developerOptions":{"showPromptButtons":true}}"""

        val result = controller.importConfigFromPath(ImportFromPathRequest(writeTempConfig(content), skipIfNotEmpty = true))

        assertEquals(200, result.statusCode.value())
        val skip = argumentCaptor<Boolean>()
        verify(configImport).importConfigs(any(), skip.capture())
        assertTrue(skip.firstValue)
        assertEquals("dark", captureImportRequest().theme)
    }

    private fun controller(
        preferences: UiPreferencesPort,
        filter: TextFilterConfigPort,
    ): ImportExportConfigApiController =
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubPort,
                    mcpPort,
                    preferences,
                    filter,
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(mapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(mapper))),
        )

    private fun decryptExport(body: ByteArray?): ByteArray = ConfigEnvelopeCipherAdapter(mapper).decrypt(body!!, "password")!!

    private fun stubEmptyConfig() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(githubPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpPort.findAllMcpServerConfigs()).thenReturn(emptyList())
    }

    private fun captureImportRequest(): ConfigImportRequest {
        val captor = argumentCaptor<ConfigImportRequest>()
        verify(configImport).importConfigs(captor.capture(), any())
        return captor.firstValue
    }

    private fun writeTempConfig(content: String): String {
        val file =
            kotlin.io.path
                .createTempFile("stacklens-settings", ".json")
                .toFile()
        file.writeText(content)
        file.deleteOnExit()
        return file.absolutePath
    }

    private class FakeTextFilter : TextFilterConfigPort {
        override var enabled: Boolean = true
            private set

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
