package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KnowledgeBaseStartupRunnerTest {
    private val baseUseCase: KnowledgeBaseUseCase = mock()

    @Test
    fun `onReady creates only the system-managed analysis base`() {
        val runner = KnowledgeBaseStartupRunner(baseUseCase)

        runner.onReady()

        verify(baseUseCase).ensureAnalysisBase()
    }
}
