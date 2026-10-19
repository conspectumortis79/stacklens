package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class KnowledgeMaintenanceServiceTest {
    private val resetPort: KnowledgeResetPort = mock()

    private val baseUseCase: KnowledgeBaseReader = mock()

    @Test
    fun `reset wipes the knowledge data then re creates the analysis base`() {
        val service = KnowledgeMaintenanceService(resetPort, baseUseCase, FixedClockPort(INSTANT))

        val resetAt = service.resetKnowledge()

        assertEquals(INSTANT, resetAt)
        inOrder(resetPort, baseUseCase) {
            verify(resetPort).resetAllKnowledgeData()
            verify(baseUseCase).ensureAnalysisBase()
        }
    }

    private companion object {
        private val INSTANT: Instant = Instant.parse("2024-05-01T10:00:00Z")
    }
}
