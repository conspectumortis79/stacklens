package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.LmStudioModelResolutionPort
import com.stacklens.application.port.out.config.AiConfigReadPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.enums.LiveModelResolvingProvider
import com.stacklens.domain.enums.ProviderStrategy
import com.stacklens.domain.model.AiConfig
import org.slf4j.LoggerFactory

@ApplicationService
class AiConfigService(
    private val aiConfigRepo: AiConfigPort,
    private val lmStudioModels: LmStudioModelResolutionPort,
    private val secretFields: AiConfigSecretFieldRegistry,
) : AiConfigUseCase,
    AiConfigReadPort {
    private val log = LoggerFactory.getLogger(AiConfigService::class.java)

    override fun getAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    override fun saveAiConfig(config: AiConfig): AiConfig = aiConfigRepo.saveAiConfig(config)

    override fun saveAiConfigFrom(request: AiConfigSaveRequest): AiConfig {
        val config = aiConfigRepo.findAiConfig() ?: AiConfig(id = 1L)
        secretFields.fields().forEach { field ->
            val submission = field.submission(request)
            field.write(
                config,
                resolveSecret(field.read(config), submission.value, clears(submission.explicitClear, submission.value)),
            )
        }
        config.compatibleBaseUrl = request.compatibleBaseUrl?.takeIf { it.isNotBlank() }

        val provider = resolveProvider(request.aiProvider, config.aiProvider)
        val strategy = AiProvider.fromId(provider)?.strategy ?: AiProvider.COPILOT.strategy
        config.model = resolveModel(strategy, provider, request, config)
        config.aiProvider = provider
        config.ollamaUrl = request.ollamaUrl?.takeIf { it.isNotBlank() }
        if (request.ollamaNumCtx > 0) config.ollamaNumCtx = request.ollamaNumCtx
        config.lmStudioUrl = request.lmStudioUrl?.takeIf { it.isNotBlank() }
        strategy.applyRequestFields(config, request.openAiReasoningEffort)

        val saved = aiConfigRepo.saveAiConfig(config)
        logPersistedSecret(saved)
        return saved
    }

    private fun logPersistedSecret(config: AiConfig) {
        if (!log.isDebugEnabled) return
        val stored = config.claudeCodeApiKey
        log.debug(
            "[AiConfig.save] claudeCodeApiKey persisted: length={} first4={} last4={} aiProvider={}",
            stored?.length ?: 0,
            stored?.take(4),
            stored?.takeLast(4),
            config.aiProvider,
        )
    }

    private fun clears(
        explicitClear: Boolean,
        submitted: String?,
    ): Boolean = submitted == null && explicitClear

    private fun resolveProvider(
        submitted: String?,
        stored: String,
    ): String =
        submitted?.takeIf { candidate -> AiProvider.entries.any { it.id == candidate } }
            ?: stored.takeIf { it.isNotBlank() }
            ?: AiProvider.COPILOT.id

    private fun resolveModel(
        strategy: ProviderStrategy,
        provider: String,
        request: AiConfigSaveRequest,
        config: AiConfig,
    ): String =
        run {
            val existing = strategy.existingForModelResolution(config, request.model)
            if (strategy is LiveModelResolvingProvider) {
                strategy.resolveModel(
                    dtoModel = request.model,
                    dtoLmStudioUrl = request.lmStudioUrl,
                    existing = existing,
                    lmStudioLiveModel = strategy.resolveLiveModel(provider, request.model, request.lmStudioUrl, config, lmStudioModels::resolveLmStudioModel),
                )
            } else {
                strategy.resolveModel(request.model, existing)
            }
        }

    private fun resolveSecret(
        storedSecret: String?,
        submitted: String?,
        clearOnNull: Boolean,
    ): String? =
        when {
            submitted == null -> if (clearOnNull) null else storedSecret
            submitted.isBlank() -> null
            submitted == AiConfigMasking.MASKED_SECRET -> storedSecret
            else -> submitted.trim()
        }
}
