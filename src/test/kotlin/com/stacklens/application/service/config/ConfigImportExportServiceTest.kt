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
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigImportExportServiceTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val dbConfigRepo: DatabaseConfigPort = mock()
    private val confluenceConfigRepo: ConfluenceConfigPort = mock()
    private val grafanaConfigRepo: GrafanaConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val aiConfigRepo: AiConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val mcpServerConfigRepo: McpServerConfigPort = mock()
    private val tx: TransactionPort = mock()
    private val uiPreferences = RecordingPreferences()
    private val textFilterConfig = ToggleableTextFilter()
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
            uiPreferences,
            textFilterConfig,
            demoUrlAlignment,
        )

    private val gitlab = GitlabConfig(name = "gl", baseUrl = "https://gitlab", accessToken = "t")
    private val database = DatabaseConfig(name = "db", dbType = DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "d", username = "u", password = "p")
    private val confluence = ConfluenceConfig(name = "conf", baseUrl = "https://conf", username = "u", apiToken = "t")
    private val grafana = GrafanaConfig(name = "graf", baseUrl = "https://graf", username = "u", password = "p")
    private val jira = JiraConfig(name = "jira", baseUrl = "https://jira", username = "u", apiToken = "t")
    private val github = GithubRepoConfig(name = "gh", owner = "o", repo = "r")
    private val mcp = McpServerConfig(name = "mcp", baseUrl = "http://mcp")

    private fun emptyRequest() = ConfigImportRequest()

    private fun fullRequest() =
        ConfigImportRequest(
            gitlabConfigs = listOf(gitlab),
            databaseConfigs = listOf(database),
            confluenceConfigs = listOf(confluence),
            grafanaConfigs = listOf(grafana),
            jiraConfigs = listOf(jira),
            githubRepoConfigs = listOf(github),
            mcpServerConfigs = listOf(mcp),
        )

    @Test
    fun `hasAnyConfig true when gitlab configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(listOf(gitlab))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when database configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(listOf(database))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when confluence configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(listOf(confluence))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when grafana configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(listOf(grafana))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when jira configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(listOf(jira))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when ai config exists`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig())
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when github configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig true when mcp configs exist`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(listOf(mcp))
        assertTrue(service.hasAnyConfig())
    }

    @Test
    fun `hasAnyConfig false when nothing exists`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        assertFalse(service.hasAnyConfig())
    }

    @Test
    fun `importConfigs with empty request skips everything`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        val result = service.importConfigs(emptyRequest(), false)
        assertTrue(result.isEmpty())
        verify(gitlabConfigRepo, never()).deleteAllGitlabConfigs()
        verify(dbConfigRepo, never()).deleteAllDatabaseConfigs()
        verify(confluenceConfigRepo, never()).deleteAllConfluenceConfigs()
        verify(grafanaConfigRepo, never()).deleteAllGrafanaConfigs()
        verify(jiraConfigRepo, never()).deleteAllJiraConfigs()
        verify(githubRepoConfigRepo, never()).deleteAllGithubRepoConfigs()
        verify(mcpServerConfigRepo, never()).deleteAllMcpServerConfigs()
        verify(aiConfigRepo, never()).saveAiConfig(any())
    }

    @Test
    fun `importConfigs aligns demo connection urls after importing`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(tx.afterCommit(any<() -> Unit>())).thenAnswer {
            (it.getArgument(0) as () -> Unit)()
        }
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        whenever(demoUrlAlignment.alignAll()).thenReturn(listOf("GitLab:Demo GitLab→http://host.docker.internal:3080"))

        service.importConfigs(fullRequest(), false)

        verify(demoUrlAlignment, times(1)).alignAll()
    }

    @Test
    fun `importConfigs aligns demo urls after the import transaction has finished`() {
        val order = mutableListOf<String>()
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer { call ->
            order += "transaction-start"
            call.getArgument<Function0<*>>(0).invoke()
            order += "transaction-end"
            emptyMap<String, Int>()
        }
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        whenever(demoUrlAlignment.alignAll()).thenAnswer {
            order += "align"
            emptyList<String>()
        }

        service.importConfigs(fullRequest(), false)

        assertEquals(listOf("transaction-start", "transaction-end", "align"), order)
        verify(tx, never()).afterCommit(any())
    }

    @Test
    fun `importConfigs imports all collections when not skipping`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        val result = service.importConfigs(fullRequest(), false)
        assertEquals(1, result["gitlab"])
        assertEquals(1, result["databases"])
        assertEquals(1, result["confluence"])
        assertEquals(1, result["grafana"])
        assertEquals(1, result["jira"])
        assertEquals(1, result["github"])
        assertEquals(1, result["mcp"])
        verify(gitlabConfigRepo).saveGitlabConfig(gitlab)
        verify(dbConfigRepo).saveDatabaseConfig(database)
        verify(confluenceConfigRepo).saveConfluenceConfig(confluence)
        verify(grafanaConfigRepo).saveGrafanaConfig(grafana)
        verify(jiraConfigRepo).saveJiraConfig(jira)
        verify(githubRepoConfigRepo).saveGithubRepoConfig(github)
        verify(mcpServerConfigRepo).saveMcpServerConfig(mcp)
    }

    @Test
    fun `importConfigs skips collections when skipIfNotEmpty and data exists`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(listOf(gitlab))
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mcpServerConfigRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        val result = service.importConfigs(fullRequest(), true)
        assertNull(result["gitlab"])
        assertEquals(1, result["databases"])
        verify(gitlabConfigRepo, never()).deleteAllGitlabConfigs()
        verify(gitlabConfigRepo, never()).saveGitlabConfig(any())
        verify(dbConfigRepo).deleteAllDatabaseConfigs()
        verify(dbConfigRepo).saveDatabaseConfig(database)
    }

    @Test
    fun `importConfigs multiple items per collection counted correctly`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        val request =
            ConfigImportRequest(
                gitlabConfigs = listOf(gitlab, GitlabConfig(name = "gl2", baseUrl = "u", accessToken = "t")),
                mcpServerConfigs = listOf(mcp, McpServerConfig(name = "mcp2", baseUrl = "u")),
            )
        val result = service.importConfigs(request, false)
        assertEquals(2, result["gitlab"])
        assertEquals(2, result["mcp"])
    }

    @Test
    fun `importAiSettings skipped when no ai settings present`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        val request = ConfigImportRequest(gitlabConfigs = listOf(gitlab))
        val result = service.importConfigs(request, false)
        assertEquals(1, result["gitlab"])
        assertNull(result["ai"])
        verify(aiConfigRepo, never()).saveAiConfig(any())
    }

    @Test
    fun `importAiSettings skipped when skipIfNotEmpty and ai config exists`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig())
        val request = ConfigImportRequest(aiConfig = AiConfig(model = "m"))
        val result = service.importConfigs(request, true)
        assertNull(result["ai"])
        verify(aiConfigRepo, never()).saveAiConfig(any())
    }

    @Test
    fun `importAiSettings creates new config when none exists`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val request =
            ConfigImportRequest(
                aiConfig =
                    AiConfig(
                        githubToken = "tok",
                        model = "model",
                        aiProvider = "ollama",
                        ollamaUrl = "url",
                        ollamaNumCtx = 42,
                        lmStudioUrl = "lm",
                        openAiApiKey = "key",
                        openAiReasoningEffort = "high",
                        openRouterApiKey = "or",
                    ),
                language = "de",
                availableModels = "a,b",
            )
        val result = service.importConfigs(request, false)
        assertEquals(1, result["ai"])
        val captor = org.mockito.kotlin.argumentCaptor<AiConfig>()
        verify(aiConfigRepo).saveAiConfig(captor.capture())
        val saved = captor.firstValue
        assertEquals("tok", saved.githubToken)
        assertEquals("model", saved.model)
        assertEquals("de", saved.language)
        assertEquals("a,b", saved.availableModels)
        assertEquals("ollama", saved.aiProvider)
        assertEquals("url", saved.ollamaUrl)
        assertEquals(42, saved.ollamaNumCtx)
        assertEquals("lm", saved.lmStudioUrl)
        assertEquals("key", saved.openAiApiKey)
        assertEquals("high", saved.openAiReasoningEffort)
        assertEquals("or", saved.openRouterApiKey)
    }

    @Test
    fun `importAiSettings replaces values when replaceAiConfig true`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        val existing = AiConfig(githubToken = "old", model = "old-model", language = "en")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(existing)
        val request = ConfigImportRequest(aiConfig = AiConfig(githubToken = "new", model = "new-model"), replaceAiConfig = true)
        service.importConfigs(request, false)
        val captor = org.mockito.kotlin.argumentCaptor<AiConfig>()
        verify(aiConfigRepo).saveAiConfig(captor.capture())
        val saved = captor.firstValue
        assertEquals("new", saved.githubToken)
        assertEquals("new-model", saved.model)
    }

    @Test
    fun `importAiSettings merges non blank values when replaceAiConfig false`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        val existing = AiConfig(githubToken = "old", model = "old-model")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(existing)
        val request = ConfigImportRequest(aiConfig = AiConfig(githubToken = null, model = "new-model"))
        service.importConfigs(request, false)
        val captor = org.mockito.kotlin.argumentCaptor<AiConfig>()
        verify(aiConfigRepo).saveAiConfig(captor.capture())
        val saved = captor.firstValue
        assertEquals("old", saved.githubToken)
        assertEquals("new-model", saved.model)
    }

    @Test
    fun `importAiSettings skips non en or de language`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val request = ConfigImportRequest(language = "fr", availableModels = "x")
        val result = service.importConfigs(request, false)
        assertEquals(1, result["ai"])
        val captor = org.mockito.kotlin.argumentCaptor<AiConfig>()
        verify(aiConfigRepo).saveAiConfig(captor.capture())
        assertEquals("en", captor.firstValue.language)
    }

    @Test
    fun `importAiSettings only language imported`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val request = ConfigImportRequest(language = "en")
        val result = service.importConfigs(request, false)
        assertEquals(1, result["ai"])
        val captor = org.mockito.kotlin.argumentCaptor<AiConfig>()
        verify(aiConfigRepo).saveAiConfig(captor.capture())
        assertEquals("en", captor.firstValue.language)
        assertNull(captor.firstValue.availableModels)
    }

    @Test
    fun `import applies theme developer options and preferences`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        uiPreferences.values["old"] = "kept-away"

        val stats =
            service.importConfigs(
                ConfigImportRequest(
                    theme = "popos",
                    developerOptions =
                        DeveloperOptionSettings(
                            showPromptButtons = true,
                            textFilterEnabled = false,
                            showTokenUsage = false,
                            dockerTestEnv = true,
                            dockerAutoStart = true,
                        ),
                    uiPreferences = mapOf("custom" to "kept"),
                ),
                skipIfNotEmpty = false,
            )

        assertEquals(1, stats["preferences"])
        assertEquals("popos", uiPreferences.values["theme"])
        assertEquals("kept", uiPreferences.values["custom"])
        assertEquals("true", uiPreferences.values["dev_show_prompt_buttons"])
        assertEquals("false", uiPreferences.values["text_filter_enabled"])
        assertFalse(textFilterConfig.enabled)
    }

    @Test
    fun `import normalizes an unknown theme to the default`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }

        service.importConfigs(ConfigImportRequest(theme = "neon"), false)

        assertEquals(UiPreferenceVocabulary.DEFAULT_THEME, uiPreferences.values["theme"])
    }

    @Test
    fun `skipIfNotEmpty protects existing preferences`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }
        uiPreferences.values["theme"] = "legacy"

        val stats = service.importConfigs(ConfigImportRequest(theme = "dark", developerOptions = DeveloperOptionSettings(true, true, true, true, true)), true)

        assertFalse(stats.containsKey("preferences"))
        assertEquals("legacy", uiPreferences.values["theme"])
    }

    @Test
    fun `import without settings leaves preferences untouched`() {
        whenever(tx.inTransaction(any<() -> Map<String, Int>>())).thenAnswer {
            (it.getArgument(0) as () -> Map<String, Int>)()
        }

        val stats = service.importConfigs(emptyRequest(), false)

        assertFalse(stats.containsKey("preferences"))
        assertTrue(uiPreferences.values.isEmpty())
    }

    private class RecordingPreferences : UiPreferencesPort {
        val values: MutableMap<String, String> = mutableMapOf()

        override fun getAll(): Map<String, String> = values.toMap()

        override fun replaceAll(preferences: Map<String, String>) {
            values.clear()
            values.putAll(preferences)
        }
    }

    private inner class ToggleableTextFilter : TextFilterConfigPort {
        private var state: Boolean = true

        override val enabled: Boolean get() = state

        override fun setEnabled(enabled: Boolean) {
            state = enabled
        }
    }
}
