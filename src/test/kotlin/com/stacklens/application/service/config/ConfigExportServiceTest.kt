package com.stacklens.application.service.config

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
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigExportServiceTest {
    private val gitlabConfigs = mock<GitlabConfigReadPort>()

    private val databaseConfigs = mock<DatabaseConfigReadPort>()

    private val confluenceConfigs = mock<ConfluenceConfigReadPort>()

    private val grafanaConfigs = mock<GrafanaConfigReadPort>()

    private val jiraConfigs = mock<JiraConfigReadPort>()

    private val aiConfigs = mock<AiConfigReadPort>()

    private val githubRepoConfigs = mock<GithubRepoConfigPort>()

    private val mcpServerConfigs = mock<McpServerConfigPort>()

    private val preferences = InMemoryUiPreferences()

    private val textFilter = InMemoryTextFilter()

    private val service =
        ConfigExportService(
            gitlabConfigs,
            databaseConfigs,
            confluenceConfigs,
            grafanaConfigs,
            jiraConfigs,
            aiConfigs,
            githubRepoConfigs,
            mcpServerConfigs,
            preferences,
            textFilter,
            FixedClockPort(EPOCH),
        )

    @Test
    fun `snapshot aggregates every configured integration`() {
        val gitlab = GitlabConfig(name = "gl", baseUrl = "https://gitlab", accessToken = "t")
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(listOf(gitlab))

        val snapshot = service.snapshot(emptyMap())

        assertEquals(listOf(gitlab), snapshot.gitlab)
        assertTrue(snapshot.databases.isEmpty())
        assertTrue(snapshot.jira.isEmpty())
    }

    @Test
    fun `snapshot merges request overrides over the stored preferences`() {
        preferences.stored["language"] = "de"
        preferences.stored["theme"] = "legacy"

        val snapshot = service.snapshot(mapOf("theme" to "dark", "extra" to "1"))

        assertEquals("de", snapshot.uiPreferences["language"])
        assertEquals("dark", snapshot.uiPreferences["theme"])
        assertEquals("1", snapshot.uiPreferences["extra"])
    }

    @Test
    fun `snapshot stamps the injected clock and normalises the theme`() {
        preferences.stored[UiPreferenceVocabulary.THEME_KEY] = "neon"

        val snapshot = service.snapshot(emptyMap())

        assertEquals(
            EPOCH,
            snapshot.exportedAt
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        assertEquals(UiPreferenceVocabulary.DEFAULT_THEME, snapshot.theme)
    }

    @Test
    fun `snapshot derives developer options from the stored preferences`() {
        preferences.stored[UiPreferenceVocabulary.SHOW_PROMPT_BUTTONS_KEY] = "1"
        preferences.stored[UiPreferenceVocabulary.SHOW_TOKEN_USAGE_KEY] = "0"

        val snapshot = service.snapshot(emptyMap())

        assertTrue(snapshot.developerOptions.showPromptButtons)
        assertFalse(snapshot.developerOptions.showTokenUsage)
        assertEquals("true", snapshot.uiPreferences[UiPreferenceVocabulary.SHOW_PROMPT_BUTTONS_KEY])
        assertEquals("false", snapshot.uiPreferences[UiPreferenceVocabulary.SHOW_TOKEN_USAGE_KEY])
    }

    @Test
    fun `runtime text filter state wins over the stored preference`() {
        preferences.stored[UiPreferenceVocabulary.TEXT_FILTER_ENABLED_KEY] = "true"
        textFilter.setEnabled(false)

        val snapshot = service.snapshot(emptyMap())

        assertFalse(snapshot.developerOptions.textFilterEnabled)
    }

    @Test
    fun `snapshot exposes ai credentials and the derived language`() {
        whenever(aiConfigs.getAiConfig()).thenReturn(AiConfig(language = "en", model = "gpt-4", availableModels = "a,b"))

        val snapshot = service.snapshot(emptyMap())

        assertEquals("en", snapshot.language)
        assertEquals("gpt-4", snapshot.ai?.model)
    }

    private class InMemoryUiPreferences : UiPreferencesPort {
        var stored: MutableMap<String, String> = mutableMapOf()

        override fun getAll(): Map<String, String> = stored.toMap()

        override fun replaceAll(preferences: Map<String, String>) {
            stored = preferences.toMutableMap()
        }
    }

    private class InMemoryTextFilter : TextFilterConfigPort {
        private var state: Boolean = true

        override val enabled: Boolean get() = state

        override fun setEnabled(enabled: Boolean) {
            state = enabled
        }
    }

    private companion object {
        const val EPOCH = 1_750_000_000_000L
    }
}
