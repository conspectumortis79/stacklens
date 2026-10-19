package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiUsageTest {
    @Test
    fun `totalTokens equals prompt plus completion`() {
        val u =
            AiUsage(
                promptTokens = 100,
                completionTokens = 50,
                filterTokensSaved = 0,
                model = "m",
                provider = "p",
                durationMs = 0,
            )
        assertEquals(150L, u.totalTokens)
    }

    @Test
    fun `totalTokens is zero when both counters are zero`() {
        val u = AiUsage(0, 0, 0, "m", "p", 0)
        assertEquals(0L, u.totalTokens)
    }

    @Test
    fun `negative promptTokens is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(-1, 0, 0, "m", "p", 0)
        }
    }

    @Test
    fun `negative completionTokens is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(0, -1, 0, "m", "p", 0)
        }
    }

    @Test
    fun `negative filterTokensSaved is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(0, 0, -1, "m", "p", 0)
        }
    }

    @Test
    fun `negative durationMs is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(0, 0, 0, "m", "p", -1)
        }
    }

    @Test
    fun `AiTokenUsageEntity roundtrips through toValue`() {
        val u = AiUsage(10, 20, 5, "claude-sonnet-4.6", "Copilot", 1_234L)
        val e = AiTokenUsageEntity.from("loadtest", 42L, 0, u)
        val v = e.toValue()
        assertEquals(10L, v.promptTokens)
        assertEquals(20L, v.completionTokens)
        assertEquals(5L, v.filterTokensSaved)
        assertEquals(30L, v.totalTokens)
        assertEquals("claude-sonnet-4.6", v.model)
        assertEquals("Copilot", v.provider)
        assertEquals(1_234L, v.durationMs)
    }

    @Test
    fun `AiUsageKind roundtrips through fromId for known kinds`() {
        for (k in AiUsageKind.entries) {
            assertEquals(k, AiUsageKind.fromId(k.id))
        }
    }

    @Test
    fun `AiUsageKind fromId returns null for unknown kind`() {
        assertEquals(null, AiUsageKind.fromId("nope"))
        assertEquals(null, AiUsageKind.fromId(""))
    }

    @Test
    fun `AiUsageKind fromId accepts null argument`() {
        val nullable: String? = null

        @Suppress("USELESS_IS_CHECK")
        val result: AiUsageKind? = AiUsageKind.fromId(nullable)
        assertEquals(null, result)
    }

    @Test
    fun `AiTokenUsageEntity preserves all fields after from`() {
        val e = AiTokenUsageEntity.from("sql", 7L, 3, AiUsage(1, 2, 0, "gpt-5.2", "OpenAI", 0L))
        assertEquals("sql", e.analysisKind)
        assertEquals(7L, e.analysisId)
        assertEquals(3, e.callIndex)
        assertNotNull(e.createdAt)
    }
}
