package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeConfigUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeConnectionTestResult
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeConfigControllerDelegationCoverageTest {
    private val configUseCase = mock<KnowledgeConfigUseCase>()
    private val controller = KnowledgeConfigController(configUseCase)

    private val config = mock<KnowledgeEmbeddingConfig>()

    @Test
    fun `getConfig hands over whatever the use case returns`() {
        whenever(configUseCase.getConfig()).thenReturn(config)

        assertSame(config, controller.getConfig())
    }

    @Test
    fun `saveConfig persists the body through the use case`() {
        whenever(configUseCase.saveConfig(config)).thenReturn(config)

        assertSame(config, controller.saveConfig(config))
        verify(configUseCase).saveConfig(config)
    }

    @Test
    fun `testConfig reports the connection test result of the use case`() {
        val result = mock<KnowledgeConnectionTestResult>()
        whenever(configUseCase.testConnection()).thenReturn(result)

        assertSame(result, controller.testConfig())
    }
}

class KnowledgeBasesControllerUpdateCoverageTest {
    private val baseUseCase = mock<KnowledgeBaseUseCase>()
    private val controller = KnowledgeBasesController(baseUseCase)

    @Test
    fun `an empty update request forwards nothing but the identifier`() {
        controller.updateBase(7L, UpdateBaseRequest())

        verify(baseUseCase).updateBase(7L, null, null, null, null)
    }

    @Test
    fun `a filled update request forwards every changed field`() {
        val body =
            UpdateBaseRequest(
                name = "renamed",
                description = "described",
                active = false,
                includeInGlobalChat = true,
            )

        controller.updateBase(3L, body)

        verify(baseUseCase).updateBase(3L, "renamed", "described", false, true)
        assertEquals("renamed", body.name)
        assertNull(body.copy(name = null).name)
        assertEquals(body.copy(), body)
    }
}
