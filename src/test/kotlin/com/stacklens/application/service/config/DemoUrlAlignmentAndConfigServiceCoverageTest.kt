package com.stacklens.application.service.config
import com.stacklens.adapter.out.demo.DemoGrafanaEndpointResolver
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DemoUrlAlignmentAndConfigServiceCoverageTest {
    private val runtimeHosts = mock<DemoRuntimeHostPort>()
    private val aligner = DemoUrlAligner(runtimeHosts)
    private val grafanaEndpoint = DemoGrafanaEndpointResolver(runtimeHosts) { null }

    @Test
    fun `the ai config service delegates reads and writes to its repository`() {
        val repo = mock<AiConfigPort>()
        val config = AiConfig()
        whenever(repo.findAiConfig()).thenReturn(config)
        whenever(repo.saveAiConfig(config)).thenReturn(config)
        val service = AiConfigService(repo, mock(), AiConfigSecretFieldRegistry())

        assertEquals(config, service.getAiConfig())
        assertEquals(config, service.saveAiConfig(config))
    }

    @Test
    fun `the ai config service reports no configuration when nothing is stored`() {
        val repo = mock<AiConfigPort>()

        assertEquals(null, repo.findAiConfig())
        assertEquals(null, AiConfigService(repo, mock(), AiConfigSecretFieldRegistry()).getAiConfig())
    }

    @Test
    fun `confluence demo urls are aligned and persisted`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://aligned:8090")
        val repo = mock<ConfluenceConfigPort>()
        val config = ConfluenceConfig(name = "Demo Confluence", baseUrl = "http://localhost:8090", username = "u", apiToken = "t")
        val custom = ConfluenceConfig(name = "Custom", baseUrl = "http://localhost:8090", username = "u", apiToken = "t")
        whenever(repo.findAllConfluenceConfigs()).thenReturn(listOf(custom, config))

        val section = ConfluenceDemoUrlAlignment(repo, aligner)

        assertEquals("Confluence", section.label)
        assertTrue(section.align().isNotEmpty())
        assertEquals("http://aligned:8090", config.baseUrl)
        verify(repo).saveConfluenceConfig(config)
    }

    @Test
    fun `confluence demo urls stay untouched when they already match`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://already:8090")
        val repo = mock<ConfluenceConfigPort>()
        val config = ConfluenceConfig(name = "Demo Confluence", baseUrl = "http://already:8090", username = "u", apiToken = "t")
        whenever(repo.findAllConfluenceConfigs()).thenReturn(listOf(config))

        assertTrue(ConfluenceDemoUrlAlignment(repo, aligner).align().isEmpty())
    }

    @Test
    fun `grafana demo urls are aligned and persisted`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://aligned:3000")
        val repo = mock<GrafanaConfigPort>()
        val config = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://localhost:3000", username = "u", password = "p")
        val custom = GrafanaConfig(name = "Custom", baseUrl = "http://localhost:3000", username = "u", password = "p")
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(custom, config))

        val section = GrafanaDemoUrlAlignment(repo, aligner, grafanaEndpoint)

        assertEquals("Grafana", section.label)
        assertTrue(section.align().isNotEmpty())
        assertEquals("http://aligned:3000", config.baseUrl)
        verify(repo).saveGrafanaConfig(config)
    }

    @Test
    fun `grafana demo urls stay untouched when they already match`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://already:3000")
        val repo = mock<GrafanaConfigPort>()
        val config = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://already:3000", username = "u", password = "p")
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(config))

        assertTrue(GrafanaDemoUrlAlignment(repo, aligner, grafanaEndpoint).align().isEmpty())
    }

    @Test
    fun `grafana demo urls are moved to the configured demo port`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenAnswer { invocation -> invocation.arguments[0] }
        val repo = mock<GrafanaConfigPort>()
        val config = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://localhost:3000", username = "u", password = "p")
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(config))
        val endpoint = DemoGrafanaEndpointResolver(runtimeHosts) { "3001" }

        assertEquals(
            listOf("Demo Grafana→http://localhost:3001"),
            GrafanaDemoUrlAlignment(repo, aligner, endpoint).align(),
        )
        assertEquals("http://localhost:3001", config.baseUrl)
        verify(repo).saveGrafanaConfig(config)
    }

    @Test
    fun `mcp alignment rewrites only the urls that actually change`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://aligned:3000")
        whenever(runtimeHosts.alignedMcpServerUrl(any())).thenReturn("http://aligned:7456")
        val repo = mock<McpServerConfigPort>()
        val changed =
            McpServerConfig(
                name = "changed",
                baseUrl = "http://localhost:7456",
                jiraBaseUrl = "http://localhost:8081",
                confluenceBaseUrl = "http://localhost:8090",
                gitlabBaseUrl = "http://localhost:3080",
                githubApiBase = "http://localhost:3001",
                grafanaBaseUrl = "http://localhost:3000",
            )
        val untouched =
            McpServerConfig(
                name = "untouched",
                baseUrl = "http://aligned:7456",
                jiraBaseUrl = "http://aligned:3000",
                confluenceBaseUrl = "http://aligned:3000",
                gitlabBaseUrl = "http://aligned:3000",
                githubApiBase = "http://aligned:3000",
                grafanaBaseUrl = "http://aligned:3000",
            )
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(changed, untouched))

        val changes = McpDemoUrlAlignment(repo, aligner, grafanaEndpoint).align()

        assertEquals("http://aligned:7456", changed.baseUrl)
        assertEquals("http://aligned:3000", changed.grafanaBaseUrl)
        assertEquals(1, changes.size)
        assertTrue(changes.single().startsWith("changed("), changes.toString())
        verify(repo).saveMcpServerConfig(changed)
    }

    @Test
    fun `mcp alignment skips blank urls`() {
        whenever(runtimeHosts.alignedServiceUrl(any())).thenReturn("http://aligned:3000")
        whenever(runtimeHosts.alignedMcpServerUrl(any())).thenReturn("http://aligned:7456")
        val repo = mock<McpServerConfigPort>()
        val blank =
            McpServerConfig(
                name = "blank",
                baseUrl = "",
                jiraBaseUrl = "",
                confluenceBaseUrl = "",
                gitlabBaseUrl = "",
                githubApiBase = "",
                grafanaBaseUrl = "",
            )
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(blank))

        assertEquals(emptyList<String>(), McpDemoUrlAlignment(repo, aligner, grafanaEndpoint).align())
        assertTrue(blank.baseUrl.isEmpty())
    }

    @Test
    fun `theme normalisation keeps supported values and replaces the rest`() {
        assertEquals("dark", UiPreferenceVocabulary.normalizeTheme("dark"))
        assertEquals(UiPreferenceVocabulary.DEFAULT_THEME, UiPreferenceVocabulary.normalizeTheme("neon"))
        assertTrue(UiPreferenceVocabulary.SUPPORTED_THEMES.containsAll(listOf("modern", "legacy", "dark", "popos")))
    }

    @Test
    fun `developer options prefer the runtime text filter flag over the stored one`() {
        val stored = mapOf(UiPreferenceVocabulary.TEXT_FILTER_ENABLED_KEY to "false")

        val fromRuntime = UiPreferenceVocabulary.developerOptions(stored, runtimeTextFilterEnabled = true)
        val fromStored = UiPreferenceVocabulary.developerOptions(stored, runtimeTextFilterEnabled = null)

        assertTrue(fromRuntime.textFilterEnabled)
        assertEquals(false, fromStored.textFilterEnabled)
        assertEquals(stored, UiPreferenceVocabulary.toPreferenceEntries(fromStored).filterKeys { it == UiPreferenceVocabulary.TEXT_FILTER_ENABLED_KEY })
    }

    @Test
    fun `developer options fall back to their documented defaults`() {
        val options = UiPreferenceVocabulary.developerOptions(emptyMap(), null)

        assertEquals(false, options.showPromptButtons)
        assertTrue(options.textFilterEnabled)
        assertTrue(options.showTokenUsage)
        assertEquals(false, options.dockerTestEnv)
        assertEquals(false, options.dockerAutoStart)
        assertEquals(5, UiPreferenceVocabulary.toPreferenceEntries(options).size)
    }
}
