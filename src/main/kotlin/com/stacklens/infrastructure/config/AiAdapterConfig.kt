package com.stacklens.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.AiConnectionTestAdapter
import com.stacklens.adapter.out.ai.AiDispatcherAdapter
import com.stacklens.adapter.out.ai.AiHttpClients
import com.stacklens.adapter.out.ai.AnthropicOAuthTokenAdapter
import com.stacklens.adapter.out.ai.ClaudeCodeAdapter
import com.stacklens.adapter.out.ai.ClaudeCodeCredentialCodec
import com.stacklens.adapter.out.ai.ClaudeCodeCredentialFileStore
import com.stacklens.adapter.out.ai.ClaudeCodeCredentialKeychainStore
import com.stacklens.adapter.out.ai.ClaudeCodeCredentialProvider
import com.stacklens.adapter.out.ai.ClaudeCodeTokenLookupPortAdapter
import com.stacklens.adapter.out.ai.CopilotCliAdapter
import com.stacklens.adapter.out.ai.DefaultAiUsageExtractor
import com.stacklens.adapter.out.ai.HttpSendRetry
import com.stacklens.adapter.out.ai.LmStudioAdapter
import com.stacklens.adapter.out.ai.OllamaAdapter
import com.stacklens.adapter.out.ai.OpenAiAdapter
import com.stacklens.adapter.out.ai.OpenAiCompatibleAdapter
import com.stacklens.adapter.out.ai.OpenAiCompatibleEndpoint
import com.stacklens.adapter.out.ai.OpenRouterAdapter
import com.stacklens.adapter.out.ai.PromptLoader
import com.stacklens.adapter.out.ai.TokenTrackingCopilotPort
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.ClaudeCodeCredentialAccessPort
import com.stacklens.application.port.out.ai.ClaudeCodeOAuthRefreshPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.enums.AiProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader

@Configuration
class AiAdapterConfig(
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
    private val stackLensProperties: StackLensProperties,
) {
    @Value("\${copilot.binary:}")
    private val configuredBinary: String = ""

    @Value("\${copilot.cli-bin:}")
    private val legacyCliBin: String = ""

    @Value("\${copilot.timeout-seconds:600}")
    private val copilotTimeoutSeconds: Long = 600L

    @Value("\${copilot.model:gpt-5.4-mini}")
    private val defaultModel: String = ""

    @Value("\${stacklens.data-dir:./data}")
    private val dataDir: String = ""

    internal fun resolveOllamaUrl(envValue: String? = System.getenv("OLLAMA_URL")): String = envValue?.takeIf { it.isNotBlank() } ?: "http://localhost:11434"

    internal fun resolveLmStudioUrl(envValue: String? = System.getenv("LM_STUDIO_URL")): String = envValue?.takeIf { it.isNotBlank() } ?: "http://localhost:1234"

    @Bean
    fun aiUsageExtractor(): com.stacklens.application.port.out.ai.AiUsageExtractor = DefaultAiUsageExtractor(objectMapper)

    @Bean
    fun claudeCodeCredentialCodec(): ClaudeCodeCredentialCodec = ClaudeCodeCredentialCodec(objectMapper)

    @Bean
    fun claudeCodeCredentialFileStore(codec: ClaudeCodeCredentialCodec): ClaudeCodeCredentialFileStore = ClaudeCodeCredentialFileStore(codec = codec)

    @Bean
    fun claudeCodeCredentialKeychainStore(
        processPort: ProcessPort,
        codec: ClaudeCodeCredentialCodec,
    ): ClaudeCodeCredentialKeychainStore = ClaudeCodeCredentialKeychainStore(process = processPort, codec = codec)

    @Bean
    fun claudeCodeOAuthRefreshPort(
        settings: ClaudeCodeSettingsPort,
        clock: ClockPort,
    ): ClaudeCodeOAuthRefreshPort =
        AnthropicOAuthTokenAdapter(
            settings = settings,
            objectMapper = objectMapper,
            clock = clock,
        )

    @Bean
    fun claudeCodeCredentialAccessPort(
        fileStore: ClaudeCodeCredentialFileStore,
        keychainStore: ClaudeCodeCredentialKeychainStore,
        refreshPort: ClaudeCodeOAuthRefreshPort,
        clock: ClockPort,
    ): ClaudeCodeCredentialAccessPort = ClaudeCodeCredentialProvider(stores = listOf(fileStore, keychainStore), refreshPort = refreshPort, clock = clock)

    @Bean
    fun claudeCodeTokenLookupPort(
        aiConfigRepo: AiConfigPort,
        settings: ClaudeCodeSettingsPort,
        credentialAccess: ClaudeCodeCredentialAccessPort,
    ): ClaudeCodeTokenLookupPort = ClaudeCodeTokenLookupPortAdapter(aiConfigRepo, settings, credentialAccess)

    @Bean
    fun lmStudioAdapter(
        aiConfigRepo: AiConfigPort,
        containerRuntimeProbe: ContainerRuntimeProbePort,
    ): LmStudioAdapter =
        LmStudioAdapter(
            defaultLmStudioUrl = resolveLmStudioUrl(),
            timeoutSeconds = copilotTimeoutSeconds,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoaderPort(),
            containerRuntimeProbe = containerRuntimeProbe,
        )

    @Bean
    fun openAiAdapter(aiConfigRepo: AiConfigPort): OpenAiAdapter =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = "https://api.openai.com",
            timeoutSeconds = copilotTimeoutSeconds,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoaderPort(),
            httpClient = AiHttpClients().http2,
        )

    @Bean
    fun copilotStreamingPort(
        aiConfigRepo: AiConfigPort,
        containerRuntimeProbe: ContainerRuntimeProbePort,
        lmStudio: LmStudioAdapter,
        openAi: OpenAiAdapter,
        claudeCodeTokenLookupPort: ClaudeCodeTokenLookupPort,
        claudeCodeSettings: ClaudeCodeSettingsPort,
        clock: ClockPort,
    ): CopilotStreamingPort {
        val copilot = CopilotCliAdapter(configuredBinary, legacyCliBin, copilotTimeoutSeconds, defaultModel, aiConfigRepo)
        val defaultOllamaUrl = resolveOllamaUrl()
        val aiHttpClients = AiHttpClients()
        val ollama =
            OllamaAdapter(
                defaultOllamaUrl = defaultOllamaUrl,
                timeoutSeconds = copilotTimeoutSeconds,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoaderPort(),
                containerRuntimeProbe = containerRuntimeProbe,
                httpClient = aiHttpClients.http2,
            )
        val openRouter =
            OpenRouterAdapter(
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                timeoutSeconds = copilotTimeoutSeconds,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoaderPort(),
                nonToolCapablePatterns = stackLensProperties.ai.openRouter.nonToolCapablePatterns,
                httpVersion = stackLensProperties.ai.openRouter.httpVersion,
                httpSendRetry =
                    HttpSendRetry(
                        maxAttempts = stackLensProperties.ai.openRouter.httpMaxRetries,
                        initialBackoffMs = stackLensProperties.ai.openRouter.httpRetryBackoffMs,
                    ),
            )
        val claudeProperties = stackLensProperties.ai.claude
        val claudeCode =
            ClaudeCodeAdapter(
                defaultBaseUrl = "https://api.anthropic.com",
                timeoutSeconds = copilotTimeoutSeconds,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoaderPort(),
                tokenLookup = claudeCodeTokenLookupPort,
                settings = claudeCodeSettings,
                httpClient = aiHttpClients.http2,
                maxTokens = claudeProperties.effectiveMaxOutputTokens,
                maxTokensCap = claudeProperties.effectiveMaxOutputTokensCap,
                truncationRetries = claudeProperties.effectiveTruncationRetries,
                thinkingMode = claudeProperties.thinking.mode,
                thinkingEffort = claudeProperties.thinking.effort,
            )
        val compatibleProperties = stackLensProperties.ai.compatible
        val compatible =
            OpenAiCompatibleAdapter(
                endpoint =
                    OpenAiCompatibleEndpoint(
                        label = "OpenAI-compatible",
                        fallbackBaseUrl = compatibleProperties.defaultBaseUrl,
                        defaultModel = compatibleProperties.defaultModel,
                        nonToolCapablePatterns = compatibleProperties.nonToolCapablePatterns,
                        envVarNames = compatibleProperties.apiKeyEnvVars,
                        apiKeyOf = { config -> config?.compatibleApiKey },
                        baseUrlOf = { config -> config?.compatibleBaseUrl },
                    ),
                timeoutSeconds = copilotTimeoutSeconds,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoaderPort(),
                httpVersion = compatibleProperties.httpVersion,
                httpSendRetry =
                    HttpSendRetry(
                        maxAttempts = compatibleProperties.httpMaxRetries,
                        initialBackoffMs = compatibleProperties.httpRetryBackoffMs,
                    ),
            )
        return AiDispatcherAdapter(
            providers =
                buildMap {
                    put(AiProvider.COPILOT.id, copilot)
                    put(AiProvider.OLLAMA.id, ollama)
                    put(AiProvider.LMSTUDIO.id, lmStudio)
                    put(AiProvider.OPENAI.id, openAi)
                    put(AiProvider.OPENROUTER.id, openRouter)
                    put(AiProvider.CLAUDECODE.id, claudeCode)
                    put(AiProvider.COMPATIBLE.id, compatible)
                },
            aiConfigRepo = aiConfigRepo,
            clock = clock,
        )
    }

    @Bean
    @org.springframework.context.annotation.Primary
    fun tokenTrackingCopilotPort(
        copilotStreamingPort: CopilotStreamingPort,
        aiTokenUsagePort: AiTokenUsagePort,
        aiUsageExtractor: com.stacklens.application.port.out.ai.AiUsageExtractor,
    ): TokenTrackingCopilotPort =
        TokenTrackingCopilotPort(
            delegate = copilotStreamingPort,
            usagePort = aiTokenUsagePort,
            extractor = aiUsageExtractor,
        )

    @Bean
    fun aiUsageContextPort(tokenTrackingCopilotPort: TokenTrackingCopilotPort): AiUsageContextPort = tokenTrackingCopilotPort

    @Bean
    fun tokenTrackingCopilotPortInterface(tokenTrackingCopilotPort: TokenTrackingCopilotPort): TokenTrackingCopilotPortInterface = tokenTrackingCopilotPort

    @Bean
    fun promptLoaderPort(): PromptLoaderPort = PromptLoader(resourceLoader, objectMapper, dataDir)

    @Bean
    fun aiConnectionTestAdapter(
        testers: List<com.stacklens.adapter.out.ai.AiProviderTester<*>>,
        lmStudioService: LmStudioModelLookupPort,
        containerRuntimeProbe: ContainerRuntimeProbePort,
    ): AiConnectionTestAdapter =
        AiConnectionTestAdapter(
            testers = testers,
            lmStudioService = lmStudioService,
            containerRuntimeProbe = containerRuntimeProbe,
        )

    @Bean
    @org.springframework.context.annotation.Primary
    fun aiConnectionTestGateway(adapter: AiConnectionTestAdapter): AiConnectionTestGateway = adapter

    @Bean
    fun aiConnectionTestPort(adapter: AiConnectionTestAdapter): AiConnectionTestPort = adapter
}
