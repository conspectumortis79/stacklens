package com.stacklens.application.port.inbound.knowledge

import java.time.Instant

interface KnowledgeMaintenanceUseCase {
    fun resetKnowledge(): Instant
}
