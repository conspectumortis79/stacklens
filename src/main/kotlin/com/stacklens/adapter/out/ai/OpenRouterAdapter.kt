package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import java.net.http.HttpClient

class OpenRouterAdapter(
    defaultBaseUrl: String = "https://openrouter.ai/api/v1",
    timeoutSeconds: Long = 180L,
    aiConfigRepo: AiConfigPort,
    objectMapper: ObjectMapper,
    promptLoader: PromptReaderPort,
    nonToolCapablePatterns: List<String> = OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS,
    httpVersion: HttpClient.Version = OpenRouterProperties.DEFAULT_HTTP_VERSION,
    httpSendRetry: HttpSendRetry = HttpSendRetry(),
    aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor(),
) : OpenAiCompatibleAdapter(
        endpoint =
            OpenAiCompatibleEndpoint(
                label = "OpenRouter",
                fallbackBaseUrl = defaultBaseUrl,
                defaultModel = DEFAULT_MODEL,
                nonToolCapablePatterns = nonToolCapablePatterns,
                extraHeaders =
                    mapOf(
                        "HTTP-Referer" to "https://stacklens.app",
                        "X-Title" to "StackLens",
                    ),
                envVarNames = listOf(API_KEY_ENV_VAR),
                apiKeyOf = { config -> config?.openRouterApiKey },
            ),
        timeoutSeconds = timeoutSeconds,
        aiConfigRepo = aiConfigRepo,
        objectMapper = objectMapper,
        promptLoader = promptLoader,
        httpVersion = httpVersion,
        httpSendRetry = httpSendRetry,
        aiHttpClientExecutor = aiHttpClientExecutor,
    ) {
    companion object {
        const val DEFAULT_MODEL: String = "openai/gpt-4.1"

        const val API_KEY_ENV_VAR: String = "OPENROUTER_API_KEY"
    }
}
