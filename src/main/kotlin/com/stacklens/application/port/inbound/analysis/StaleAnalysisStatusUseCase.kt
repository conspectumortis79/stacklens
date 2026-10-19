package com.stacklens.application.port.inbound.analysis

import java.time.LocalDateTime

enum class StaleAnalysisKind {
    TICKET_CREATION,
    IMAGE_EXTRACTION,
    SQL_ANALYSIS,
}

interface StaleAnalysisStatusUseCase {
    fun effectiveStatus(
        kind: StaleAnalysisKind,
        status: String,
        createdAt: LocalDateTime,
    ): String
}
