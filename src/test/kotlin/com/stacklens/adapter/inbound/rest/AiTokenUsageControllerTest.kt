package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.ai.AiTokenUsageUseCase
import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiTokenUsageControllerTest {
    private val useCase: AiTokenUsageUseCase = mock()

    @Test
    fun `returns 200 and populated DTO when usage exists`() {
        whenever(useCase.getUsage("loadtest", 42L)).thenReturn(
            AiUsage(
                promptTokens = 100,
                completionTokens = 50,
                filterTokensSaved = 18,
                model = "claude-sonnet-4.6",
                provider = "Copilot",
                durationMs = 1_234L,
            ),
        )
        val r = AiTokenUsageController(useCase).getUsage("loadtest", 42L)
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(100L, body.promptTokens)
        assertEquals(50L, body.completionTokens)
        assertEquals(150L, body.totalTokens)
        assertEquals(18L, body.filterTokensSaved)
        assertEquals("claude-sonnet-4.6", body.model)
        assertEquals("Copilot", body.provider)
        assertEquals(1_234L, body.durationMs)
    }

    @Test
    fun `returns 200 and empty DTO when usage is null`() {
        whenever(useCase.getUsage("loadtest", 99L)).thenReturn(null)
        val r = AiTokenUsageController(useCase).getUsage("loadtest", 99L)
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(0L, body.totalTokens)
        assertEquals(0L, body.promptTokens)
        assertEquals(0L, body.completionTokens)
        assertEquals(0L, body.filterTokensSaved)
        assertEquals("", body.model)
        assertEquals("", body.provider)
    }

    @Test
    fun `unknown kind returns 200 and empty DTO (no leak)`() {
        val r = AiTokenUsageController(useCase).getUsage("bogus_kind", 1L)
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(0L, body.totalTokens)
    }
}
