package com.stacklens.adapter.inbound.rest

import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiUsageDtoMappingTest {
    @Test
    fun `the empty payload carries zero counters and blank labels`() {
        val dto = AiUsageDto.empty()

        assertEquals(0L, dto.promptTokens)
        assertEquals(0L, dto.completionTokens)
        assertEquals(0L, dto.totalTokens)
        assertEquals(0L, dto.filterTokensSaved)
        assertEquals("", dto.model)
        assertEquals("", dto.provider)
        assertEquals(0L, dto.durationMs)
    }

    @Test
    fun `the payload mirrors every usage field`() {
        val usage =
            AiUsage(
                promptTokens = 11L,
                completionTokens = 22L,
                filterTokensSaved = 44L,
                model = "gpt-test",
                provider = "copilot",
                durationMs = 55L,
            )

        val dto = AiUsageDto.from(usage)

        assertEquals(AiUsageDto(11L, 22L, 33L, 44L, "gpt-test", "copilot", 55L), dto)
    }
}
