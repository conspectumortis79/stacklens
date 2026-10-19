package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.time.ClockPort
import org.slf4j.LoggerFactory
import java.io.File

class AiDispatcherAdapter(
    private val providers: Map<String, CopilotStreamingPort>,
    private val aiConfigRepo: AiConfigPort,
    private val defaultProviderName: String = "copilot",
    private val clock: ClockPort,
    private val providerCacheTtlMs: Long = PROVIDER_CACHE_TTL_MS,
) : CopilotStreamingPort {
    private val log = LoggerFactory.getLogger(AiDispatcherAdapter::class.java)

    @Volatile private var cachedProvider: String = defaultProviderName

    @Volatile private var cacheTimestamp: Long = 0L

    private companion object {
        private const val PROVIDER_CACHE_TTL_MS = 5_000L
    }

    protected fun refreshCacheIfStale(now: Long) {
        if (!isCacheStale(now)) return
        synchronized(this) {
            if (!isCacheStale(now)) return
            cachedProvider = aiConfigRepo.findAiConfig()?.aiProvider ?: defaultProviderName
            cacheTimestamp = now
        }
    }

    private fun isCacheStale(now: Long): Boolean = now - cacheTimestamp > providerCacheTtlMs

    private fun delegateOrNull(): CopilotStreamingPort? {
        val now = clock.epochMillis()
        refreshCacheIfStale(now)
        val provider = cachedProvider
        log.debug("AI provider: {}", provider)
        val resolved = providers[provider] ?: providers[defaultProviderName]
        if (resolved == null) {
            log.warn("No AI provider registered for '{}' (default '{}'), {} provider(s) available", provider, defaultProviderName, providers.size)
        }
        return resolved
    }

    private fun delegate(): CopilotStreamingPort = delegateOrNull() ?: error("No AI provider found for '$cachedProvider'")

    override val providerLabel: String
        get() = delegateOrNull()?.providerLabel ?: "Unknown"

    override val supportsAttachments: Boolean
        get() = delegateOrNull()?.supportsAttachments == true

    override val writesFilesNatively: Boolean
        get() = delegateOrNull()?.writesFilesNatively == true

    override fun resolveToken(): String? = delegateOrNull()?.resolveToken()

    override fun isReady(): Boolean = delegateOrNull()?.isReady() == true

    override fun resolveModel(): String = delegate().resolveModel()

    override fun resolveLanguage(): String = delegate().resolveLanguage()

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String = providerFor(attachments).execute(model, prompt, timeoutOverride, attachments)

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String = providerFor(attachments).executeWithStreaming(model, prompt, timeoutOverride, isCancelled, onPartialOutput, attachments)

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String = providerFor(attachments).executeWithStreamingPerLine(model, prompt, timeoutOverride, repoDir, isCancelled, onPartialOutput, attachments)

    private fun providerFor(attachments: List<AiAttachment>): CopilotStreamingPort {
        val provider = delegate()
        if (attachments.isEmpty() || provider.supportsAttachments) return provider
        val visionCapable = providers.values.firstOrNull { it.supportsAttachments }
        if (visionCapable != null) {
            log.info(
                "AI provider '{}' does not support image attachments; routing to '{}' instead",
                provider.providerLabel,
                visionCapable.providerLabel,
            )
            return visionCapable
        }
        throw IllegalStateException(
            "AI provider '${provider.providerLabel}' does not support image attachments and no vision-capable provider is configured",
        )
    }
}
