package com.stacklens.adapter.inbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.config.ConfigEnvelopeCodec
import com.stacklens.adapter.inbound.rest.config.ConfigEnvelopeOutcome
import com.stacklens.adapter.inbound.rest.config.ConfigEnvelopeRejection
import com.stacklens.adapter.inbound.startup.KnowledgeBaseStartupRunner
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.service.config.ConfigEnvelopeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigEnvelopeCodecPasswordCoverageTest {
    private val mapper = ObjectMapper()

    private val codec = ConfigEnvelopeCodec(mapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(mapper)))

    private val envelope = """{"version":1,"encrypted":true,"iterations":10,"salt":"AAAA","iv":"AAAA","data":"AAAA"}"""

    @Test
    fun `an encrypted envelope without a password asks for one`() {
        val outcome = codec.decode(envelope.toByteArray(), null)

        assertEquals(ConfigEnvelopeRejection.NEEDS_PASSWORD, (outcome as ConfigEnvelopeOutcome.Rejected).reason)
    }

    @Test
    fun `an encrypted envelope with a blank password asks for one as well`() {
        val outcome = codec.decode(envelope.toByteArray(), "   ")

        assertEquals(ConfigEnvelopeRejection.NEEDS_PASSWORD, (outcome as ConfigEnvelopeOutcome.Rejected).reason)
    }

    @Test
    fun `a password unlocks the decryption attempt of the envelope`() {
        val outcome = codec.decode(envelope.toByteArray(), "secret")

        assertTrue(outcome is ConfigEnvelopeOutcome.Rejected)
    }

    @Test
    fun `a plain export is loaded without a password`() {
        val outcome = codec.decode("""{"language":"de"}""".toByteArray(), null)

        assertTrue(outcome is ConfigEnvelopeOutcome.Loaded)
    }
}

class KnowledgeBaseStartupRunnerCoverageTest {
    private val useCase = mock<KnowledgeBaseUseCase>()

    @Test
    fun `the analysis base is created once the application is ready`() {
        KnowledgeBaseStartupRunner(useCase).onReady()

        verify(useCase).ensureAnalysisBase()
    }

    @Test
    fun `a failing bootstrap only logs and never breaks the startup`() {
        doThrow(IllegalStateException("database locked")).whenever(useCase).ensureAnalysisBase()

        KnowledgeBaseStartupRunner(useCase).onReady()
    }
}
