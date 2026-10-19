package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.AiUsageExtractor
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.ai.TokenEstimator
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.domain.model.AiUsage
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger

class TokenTrackingCopilotPort(
    private val delegate: CopilotStreamingPort,
    private val usagePort: AiTokenUsagePort,
    private val extractor: AiUsageExtractor,
    private val tokenEstimator: TokenEstimator = TokenEstimator.Default,
    private val clock: Clock = Clock.systemUTC(),
) : TokenTrackingCopilotPortInterface,
    AiUsageContextPort {
    private val log = LoggerFactory.getLogger(TokenTrackingCopilotPort::class.java)

    private val contextKind = ThreadLocal<String>()
    private val contextId = ThreadLocal<Long>()
    private val contextCallIndex = ThreadLocal.withInitial { AtomicInteger(0) }
    private val contextExtraSavings = ThreadLocal<Long>()
    private val contextExtraModel = ThreadLocal<String>()

    override val providerLabel: String get() = delegate.providerLabel

    override val writesFilesNatively: Boolean get() = delegate.writesFilesNatively

    override fun setContext(
        kind: String,
        analysisId: Long,
    ) {
        contextKind.set(kind)
        contextId.set(analysisId)
        contextCallIndex.get().set(0)
        contextExtraSavings.remove()
    }

    override fun clearContext() {
        contextKind.remove()
        contextId.remove()
        contextExtraSavings.remove()
        contextExtraModel.remove()
    }

    override fun <T> withContext(
        kind: String,
        analysisId: Long,
        block: () -> T,
    ): T {
        setContext(kind, analysisId)
        return try {
            block()
        } finally {
            clearContext()
        }
    }

    override fun addFilterSavings(tokens: Long) {
        require(tokens >= 0) { "tokens must be non-negative (was $tokens)" }
        val current = contextExtraSavings.get() ?: 0L
        contextExtraSavings.set(current + tokens)
    }

    override fun setModelOverride(model: String) {
        if (model.isNotBlank()) contextExtraModel.set(model)
    }

    override fun resolveToken(): String? = delegate.resolveToken()

    override fun resolveModel(): String = delegate.resolveModel()

    override fun resolveLanguage(): String = delegate.resolveLanguage()

    override fun isReady(): Boolean = delegate.isReady()

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String {
        val start = clock.millis()
        val output = delegate.execute(model, prompt, timeoutOverride, attachments)
        recordIfActive(model, prompt, output, start)
        return output
    }

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        val start = clock.millis()
        val output = delegate.executeWithStreaming(model, prompt, timeoutOverride, isCancelled, onPartialOutput, attachments)
        recordIfActive(model, prompt, output, start)
        return output
    }

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        val start = clock.millis()
        val output = delegate.executeWithStreamingPerLine(model, prompt, timeoutOverride, repoDir, isCancelled, onPartialOutput, attachments)
        recordIfActive(model, prompt, output, start)
        return output
    }

    private fun recordIfActive(
        model: String,
        prompt: String,
        output: String,
        startMs: Long,
    ) {
        val kind = contextKind.get() ?: return
        val id = contextId.get() ?: return
        val callIndex = contextCallIndex.get().getAndIncrement()
        val duration = (clock.millis() - startMs).coerceAtLeast(0L)

        val extracted = extractor.extract(output, providerLabel)
        val inTokens: Long
        val outTokens: Long
        if (extracted != null) {
            inTokens = extracted.promptTokens
            outTokens = extracted.completionTokens
        } else {
            inTokens = tokenEstimator.estimateTokens(prompt)
            outTokens = tokenEstimator.estimateTokens(output)
        }

        val savings = contextExtraSavings.get() ?: 0L
        contextExtraSavings.remove()
        val effectiveModel = contextExtraModel.get()?.takeIf { it.isNotBlank() } ?: model
        contextExtraModel.remove()

        val usage =
            AiUsage(
                promptTokens = inTokens,
                completionTokens = outTokens,
                filterTokensSaved = savings,
                model = effectiveModel,
                provider = providerLabel,
                durationMs = duration,
            )
        try {
            usagePort.record(kind, id, callIndex, usage)
        } catch (e: Exception) {
            log.warn("Failed to record AI usage for kind={} id={} idx={}: {}", kind, id, callIndex, e.message)
        }
    }
}
