package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.port.inbound.knowledge.KnowledgeMaintenanceUseCase
import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import java.time.Instant

@ApplicationService
class KnowledgeMaintenanceService(
    private val knowledgeResetPort: KnowledgeResetPort,
    private val knowledgeBaseUseCase: KnowledgeBaseReader,
    private val clock: ClockPort,
) : KnowledgeMaintenanceUseCase {
    override fun resetKnowledge(): Instant {
        knowledgeResetPort.resetAllKnowledgeData()
        knowledgeBaseUseCase.ensureAnalysisBase()
        return clock.now()
    }
}
