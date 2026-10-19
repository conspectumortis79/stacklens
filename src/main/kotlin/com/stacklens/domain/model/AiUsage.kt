package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

data class AiUsage(
    val promptTokens: Long,
    val completionTokens: Long,
    val filterTokensSaved: Long,
    val model: String,
    val provider: String,
    val durationMs: Long,
) {
    init {
        require(promptTokens >= 0) { "promptTokens must be non-negative (was $promptTokens)" }
        require(completionTokens >= 0) { "completionTokens must be non-negative (was $completionTokens)" }
        require(filterTokensSaved >= 0) { "filterTokensSaved must be non-negative (was $filterTokensSaved)" }
        require(durationMs >= 0) { "durationMs must be non-negative (was $durationMs)" }
    }

    val totalTokens: Long get() = promptTokens + completionTokens

    companion object {
        const val CHARS_PER_TOKEN: Int = 4
    }
}

@Entity
@Table(
    name = "ai_token_usage",
    indexes = [
        Index(columnList = "analysisKind, analysisId"),
        Index(columnList = "createdAt"),
    ],
)
class AiTokenUsageEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 40)
    val analysisKind: String,
    @Column(nullable = false)
    val analysisId: Long,
    @Column(nullable = false)
    val callIndex: Int,
    @Column(nullable = false)
    val promptTokens: Long,
    @Column(nullable = false)
    val completionTokens: Long,
    @Column(nullable = false)
    val filterTokensSaved: Long,
    @Column(nullable = false, length = 120)
    val model: String,
    @Column(nullable = false, length = 40)
    val provider: String,
    @Column(nullable = false)
    val durationMs: Long,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toValue(): AiUsage =
        AiUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            filterTokensSaved = filterTokensSaved,
            model = model,
            provider = provider,
            durationMs = durationMs,
        )

    companion object {
        fun from(
            kind: String,
            analysisId: Long,
            callIndex: Int,
            usage: AiUsage,
        ): AiTokenUsageEntity =
            AiTokenUsageEntity(
                analysisKind = kind,
                analysisId = analysisId,
                callIndex = callIndex,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                filterTokensSaved = usage.filterTokensSaved,
                model = usage.model,
                provider = usage.provider,
                durationMs = usage.durationMs,
            )
    }
}

enum class AiUsageKind(
    val id: String,
) {
    LOADTEST("loadtest"),
    CODE("code"),
    CODEQA("codeqa"),
    SECURITY("security"),
    SQL("sql"),
    PIPELINE("pipeline"),
    REVIEW("review"),
    TICKET_CREATION("ticket_creation"),
    TICKET_REFINEMENT("ticket_refinement"),
    TICKET_IMPLEMENTATION("ticket_implementation"),
    TICKET_SUMMARY("ticket_summary"),
    GRAFANA("grafana"),
    JWT("jwt"),
    AI("ai"),
    KICHAT("kichat"),
    TOOLING("tooling"),
    SEARCH("search"),
    IMAGE_EXTRACTION("image_extraction"),
    LOGGING_FIX("logging_fix"),
    ;

    companion object {
        fun fromId(id: String?): AiUsageKind? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id == id }
        }
    }
}
