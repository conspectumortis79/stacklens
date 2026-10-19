package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.ai.OpenRouterProperties
import com.stacklens.adapter.out.jira.JiraEndpoints
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.domain.enums.AiProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.net.http.HttpClient

data class AiProperties(
    @NestedConfigurationProperty
    val openRouter: OpenRouterProperties = OpenRouterProperties(),
    @NestedConfigurationProperty
    val compatible: OpenAiCompatibleProperties = OpenAiCompatibleProperties(),
    @NestedConfigurationProperty
    val claude: ClaudeCodeProperties = ClaudeCodeProperties(),
)

data class ClaudeThinkingProperties(
    val mode: String = DEFAULT_MODE,
    val effort: String = DEFAULT_EFFORT,
) {
    companion object {
        const val MODE_ADAPTIVE: String = "adaptive"

        const val MODE_DISABLED: String = "disabled"

        const val MODE_PROVIDER_DEFAULT: String = "provider-default"

        const val DEFAULT_MODE: String = MODE_ADAPTIVE

        const val DEFAULT_EFFORT: String = "low"

        val SUPPORTED_EFFORTS: List<String> = listOf("low", "medium", "high", "xhigh", "max")
    }
}

data class ClaudeCodeProperties(
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val maxOutputTokensCap: Int = DEFAULT_MAX_OUTPUT_TOKENS_CAP,
    val truncationRetries: Int = DEFAULT_TRUNCATION_RETRIES,
    @NestedConfigurationProperty
    val thinking: ClaudeThinkingProperties = ClaudeThinkingProperties(),
) {
    val effectiveMaxOutputTokens: Int
        get() = maxOutputTokens.coerceIn(MIN_OUTPUT_TOKENS, effectiveMaxOutputTokensCap)

    val effectiveMaxOutputTokensCap: Int
        get() = maxOutputTokensCap.coerceAtLeast(MIN_OUTPUT_TOKENS)

    val effectiveTruncationRetries: Int
        get() = truncationRetries.coerceIn(0, MAX_TRUNCATION_RETRIES)

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 32_000

        const val DEFAULT_MAX_OUTPUT_TOKENS_CAP: Int = 128_000

        const val DEFAULT_TRUNCATION_RETRIES: Int = 1

        const val MIN_OUTPUT_TOKENS: Int = 1_024

        const val MAX_TRUNCATION_RETRIES: Int = 3
    }
}

data class OpenAiCompatibleProperties(
    val defaultBaseUrl: String = DEFAULT_BASE_URL,
    val defaultModel: String = AiProvider.COMPATIBLE.defaultModel,
    val apiKeyEnvVars: List<String> = DEFAULT_API_KEY_ENV_VARS,
    val nonToolCapablePatterns: List<String> = OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS,
    val httpVersion: HttpClient.Version = OpenRouterProperties.DEFAULT_HTTP_VERSION,
    val httpMaxRetries: Int = OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES,
    val httpRetryBackoffMs: Long = OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS,
) {
    companion object {
        const val DEFAULT_BASE_URL: String = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"

        val DEFAULT_API_KEY_ENV_VARS: List<String> = listOf("OPENAI_COMPATIBLE_API_KEY", "DASHSCOPE_API_KEY")
    }
}

data class JiraProperties(
    @NestedConfigurationProperty
    val endpoints: JiraEndpoints = JiraEndpoints(),
)

data class DockerAutoStartProperties(
    val composeUpTimeoutSeconds: Long = DEFAULT_COMPOSE_UP_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_COMPOSE_UP_TIMEOUT_SECONDS: Long = 120L
    }
}

data class DockerProperties(
    val autoStart: DockerAutoStartProperties = DockerAutoStartProperties(),
    val composeQueryTimeoutSeconds: Long = DEFAULT_COMPOSE_QUERY_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_COMPOSE_QUERY_TIMEOUT_SECONDS: Long = 5L
    }
}

@ConfigurationProperties(prefix = "stacklens")
data class StackLensProperties(
    override val dataDir: String = "./data",
    val ai: AiProperties = AiProperties(),
    val jira: JiraProperties = JiraProperties(),
    val dockerSettings: DockerProperties = DockerProperties(),
) : StackLensConfigPort {
    override val dockerComposeUpTimeoutSeconds: Long get() = dockerSettings.autoStart.composeUpTimeoutSeconds

    override val dockerComposeQueryTimeoutSeconds: Long get() = dockerSettings.composeQueryTimeoutSeconds
}
