package com.stacklens.application.service.ai

import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiTokenUsageServiceTest {
    private val usagePort: AiTokenUsagePort = mock()
    private val service = AiTokenUsageService(usagePort)

    @Test
    fun `getUsage delegates to port`() {
        val expected =
            AiUsage(
                promptTokens = 10L,
                completionTokens = 20L,
                filterTokensSaved = 5L,
                model = "x",
                provider = "y",
                durationMs = 100L,
            )
        whenever(usagePort.aggregate("x", 42L)).thenReturn(expected)
        val result = service.getUsage("x", 42L)
        assertEquals(expected, result)
        verify(usagePort).aggregate("x", 42L)
    }

    @Test
    fun `getUsage returns null when port returns null`() {
        whenever(usagePort.aggregate("x", 42L)).thenReturn(null)
        assertNull(service.getUsage("x", 42L))
    }
}
