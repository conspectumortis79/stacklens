package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeMaintenanceUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeReindexUseCase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class KnowledgeOperationsControllerResetTest {
    private val maintenance: KnowledgeMaintenanceUseCase = mock()
    private val reindexService: KnowledgeReindexUseCase = mock()

    private val controller = KnowledgeOperationsController(maintenance, reindexService)

    @Test
    fun `resetKnowledge delegates the wipe and re-seed to the use case`() {
        whenever(maintenance.resetKnowledge()).thenReturn(Instant.EPOCH)

        val response = controller.resetKnowledge()

        verify(maintenance).resetKnowledge()
        assertNotNull(response["resetAt"])
    }
}
