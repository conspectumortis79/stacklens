package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.DeveloperOptionSettings

object UiPreferenceVocabulary {
    const val THEME_KEY = "theme"
    const val DEFAULT_THEME = "modern"
    val SUPPORTED_THEMES: Set<String> = setOf("modern", "legacy", "dark", "popos")
    const val SHOW_PROMPT_BUTTONS_KEY = "dev_show_prompt_buttons"
    const val TEXT_FILTER_ENABLED_KEY = "text_filter_enabled"
    const val SHOW_TOKEN_USAGE_KEY = "dev_show_token_usage"
    const val DOCKER_TEST_ENV_KEY = "dev_docker_test_env"
    const val DOCKER_AUTO_START_KEY = "dev_docker_auto_start"

    fun normalizeTheme(theme: String): String = theme.takeIf { it in SUPPORTED_THEMES } ?: DEFAULT_THEME

    fun booleanPreference(
        preferences: Map<String, String>,
        key: String,
        default: Boolean,
    ): Boolean =
        when (preferences[key]?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }

    fun developerOptions(
        preferences: Map<String, String>,
        runtimeTextFilterEnabled: Boolean?,
    ): DeveloperOptionSettings =
        DeveloperOptionSettings(
            showPromptButtons = booleanPreference(preferences, SHOW_PROMPT_BUTTONS_KEY, false),
            textFilterEnabled = runtimeTextFilterEnabled ?: booleanPreference(preferences, TEXT_FILTER_ENABLED_KEY, true),
            showTokenUsage = booleanPreference(preferences, SHOW_TOKEN_USAGE_KEY, true),
            dockerTestEnv = booleanPreference(preferences, DOCKER_TEST_ENV_KEY, false),
            dockerAutoStart = booleanPreference(preferences, DOCKER_AUTO_START_KEY, false),
        )

    fun toPreferenceEntries(options: DeveloperOptionSettings): Map<String, String> =
        mapOf(
            SHOW_PROMPT_BUTTONS_KEY to options.showPromptButtons.toString(),
            TEXT_FILTER_ENABLED_KEY to options.textFilterEnabled.toString(),
            SHOW_TOKEN_USAGE_KEY to options.showTokenUsage.toString(),
            DOCKER_TEST_ENV_KEY to options.dockerTestEnv.toString(),
            DOCKER_AUTO_START_KEY to options.dockerAutoStart.toString(),
        )
}
