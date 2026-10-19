package com.stacklens.application.service

import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.infrastructure.config.ApplicationCoreConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

class KnowledgeFactoryResetHandlerTest {
    @Test
    fun `handler delegates to KnowledgeResetPort`() {
        val port: KnowledgeResetPort = mock()
        KnowledgeFactoryResetHandler(port).reset()
        verify(port).resetAllKnowledgeData()
    }

    @Test
    fun `handler is opt-in via reset-on-factory-reset property`() {
        val method =
            ApplicationCoreConfig::class.java.getDeclaredMethod(
                "knowledgeFactoryResetHandler",
                KnowledgeResetPort::class.java,
            )
        val annotation = method.getAnnotation(ConditionalOnProperty::class.java)
        assertNotNull(annotation, "handler must be ConditionalOnProperty-gated")
        assertEquals(
            listOf("stacklens.knowledge.reset-on-factory-reset"),
            annotation.name.toList(),
            "the property name is the documented contract; renaming it silently would break user opt-in",
        )
        assertEquals("true", annotation.havingValue, "handler must require an explicit `true`")
        assertEquals(false, annotation.matchIfMissing, "default off: the RAG database must survive a normal factory reset")
    }

    @Test
    fun `config method builds the handler for the enabled property`() {
        val port: KnowledgeResetPort = mock()
        assertNotNull(ApplicationCoreConfig().knowledgeFactoryResetHandler(port))
    }

    @Test
    fun `handler is a FactoryResetHandler so Spring picks it up when enabled`() {
        assertTrue(
            FactoryResetHandler::class.java.isAssignableFrom(KnowledgeFactoryResetHandler::class.java),
            "handler must implement FactoryResetHandler to be wired into FactoryResetService",
        )
    }
}
