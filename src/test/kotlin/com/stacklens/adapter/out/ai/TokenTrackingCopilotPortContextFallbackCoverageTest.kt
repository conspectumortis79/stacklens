package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.port.out.ai.AiUsageExtractor
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TokenTrackingCopilotPortContextFallbackCoverageTest {
    private val delegate: CopilotStreamingPort = mock()
    private val usagePort: AiTokenUsagePort = mock()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)

    private val port =
        TokenTrackingCopilotPort(
            delegate,
            usagePort,
            mock<AiUsageExtractor>(),
            mock(),
            clock,
        )

    init {
        whenever(delegate.providerLabel).thenReturn("Copilot")
        whenever(delegate.execute(any(), any(), any(), any())).thenReturn("answer")
    }

    @Suppress("UNCHECKED_CAST")
    private fun threadLocal(name: String): ThreadLocal<Any?> {
        val field = TokenTrackingCopilotPort::class.java.declaredFields.first { it.name == name }
        field.isAccessible = true
        return field.get(port) as ThreadLocal<Any?>
    }

    @Test
    fun `a context without an analysis id records nothing`() {
        threadLocal("contextKind").set("mr-analysis")

        port.execute("model", "prompt", null, emptyList())

        verify(usagePort, never()).record(any(), any(), any(), any())
    }

    @Test
    fun `a blank model override falls back to the requested model`() {
        port.setContext("mr-analysis", 7L)
        threadLocal("contextExtraModel").set("   ")

        port.execute("model", "prompt", null, emptyList())

        verify(usagePort).record(eq("mr-analysis"), eq(7L), any(), any())
    }

    @Test
    fun `without any context the call is not recorded`() {
        port.execute("model", "prompt", null, emptyList())

        verify(usagePort, never()).record(any(), any(), any(), any())
    }
}
