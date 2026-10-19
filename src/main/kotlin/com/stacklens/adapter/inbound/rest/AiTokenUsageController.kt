package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.ai.AiTokenUsageUseCase
import com.stacklens.domain.model.AiUsage
import com.stacklens.domain.model.AiUsageKind
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ai-usage")
open class AiTokenUsageController(
    private val useCase: AiTokenUsageUseCase,
) {
    @GetMapping("/{kind}/{id}")
    fun getUsage(
        @PathVariable kind: String,
        @PathVariable id: Long,
    ): ResponseEntity<AiUsageDto> {
        if (AiUsageKind.fromId(kind) == null) {
            return ResponseEntity.ok(AiUsageDto.empty())
        }
        val usage = useCase.getUsage(kind, id) ?: return ResponseEntity.ok(AiUsageDto.empty())
        return ResponseEntity.ok(AiUsageDto.from(usage))
    }
}

data class AiUsageDto(
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val filterTokensSaved: Long,
    val model: String,
    val provider: String,
    val durationMs: Long,
) {
    companion object {
        fun empty(): AiUsageDto =
            AiUsageDto(
                promptTokens = 0L,
                completionTokens = 0L,
                totalTokens = 0L,
                filterTokensSaved = 0L,
                model = "",
                provider = "",
                durationMs = 0L,
            )

        fun from(usage: AiUsage): AiUsageDto =
            AiUsageDto(
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens,
                filterTokensSaved = usage.filterTokensSaved,
                model = usage.model,
                provider = usage.provider,
                durationMs = usage.durationMs,
            )
    }
}
