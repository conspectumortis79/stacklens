package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class KnowledgeBaseStartupRunner(
    private val knowledgeBaseUseCase: KnowledgeBaseUseCase,
) {
    private val log = LoggerFactory.getLogger(KnowledgeBaseStartupRunner::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            knowledgeBaseUseCase.ensureAnalysisBase()
        } catch (e: Exception) {
            log.warn("Failed to ensure default knowledge bases: {}", e.message)
        }
    }
}
