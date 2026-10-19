package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AiUsageCoverageTest {
    @Test
    fun `AiUsage stores its arguments verbatim`() {
        val u =
            AiUsage(
                promptTokens = 10L,
                completionTokens = 20L,
                filterTokensSaved = 3L,
                model = "gpt-4o",
                provider = "openai",
                durationMs = 500L,
            )
        assertEquals(10L, u.promptTokens)
        assertEquals(20L, u.completionTokens)
        assertEquals(3L, u.filterTokensSaved)
        assertEquals("gpt-4o", u.model)
        assertEquals("openai", u.provider)
        assertEquals(500L, u.durationMs)
        assertEquals(30L, u.totalTokens)
    }

    @Test
    fun `AiUsage rejects negative promptTokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(
                promptTokens = -1L,
                completionTokens = 0L,
                filterTokensSaved = 0L,
                model = "m",
                provider = "p",
                durationMs = 0L,
            )
        }
    }

    @Test
    fun `AiUsage rejects negative completionTokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(
                promptTokens = 0L,
                completionTokens = -1L,
                filterTokensSaved = 0L,
                model = "m",
                provider = "p",
                durationMs = 0L,
            )
        }
    }

    @Test
    fun `AiUsage rejects negative filterTokensSaved`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(
                promptTokens = 0L,
                completionTokens = 0L,
                filterTokensSaved = -1L,
                model = "m",
                provider = "p",
                durationMs = 0L,
            )
        }
    }

    @Test
    fun `AiUsage rejects negative durationMs`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(
                promptTokens = 0L,
                completionTokens = 0L,
                filterTokensSaved = 0L,
                model = "m",
                provider = "p",
                durationMs = -1L,
            )
        }
    }

    @Test
    fun `AiUsage CHARS_PER_TOKEN constant is the documented value`() {
        assertEquals(4, AiUsage.CHARS_PER_TOKEN)
    }

    @Test
    fun `AiTokenUsageEntity default createdAt is roughly now`() {
        val before = LocalDateTime.now()
        val e =
            AiTokenUsageEntity(analysisKind = "code", analysisId = 1L, callIndex = 0, promptTokens = 0L, completionTokens = 0L, filterTokensSaved = 0L, model = "m", provider = "p", durationMs = 0L)
        assertNotNull(e.createdAt)
        assertTrue(e.createdAt >= before.minusSeconds(2))
    }

    @Test
    fun `AiTokenUsageEntity default id is zero`() {
        val e =
            AiTokenUsageEntity(
                analysisKind = "code",
                analysisId = 1L,
                callIndex = 0,
                promptTokens = 0L,
                completionTokens = 0L,
                filterTokensSaved = 0L,
                model = "m",
                provider = "p",
                durationMs = 0L,
            )
        assertEquals(0L, e.id)
    }

    @Test
    fun `AiTokenUsageEntity toValue copies every token field verbatim`() {
        val u =
            AiUsage(
                promptTokens = 11L,
                completionTokens = 22L,
                filterTokensSaved = 3L,
                model = "gpt",
                provider = "openai",
                durationMs = 999L,
            )
        val e =
            AiTokenUsageEntity(
                id = 99L,
                analysisKind = "code",
                analysisId = 7L,
                callIndex = 4,
                promptTokens = 11L,
                completionTokens = 22L,
                filterTokensSaved = 3L,
                model = "gpt",
                provider = "openai",
                durationMs = 999L,
            )
        val value = e.toValue()
        assertEquals(u, value)
    }

    @Test
    fun `AiTokenUsageEntity from builds an entity whose toValue equals input usage`() {
        val u =
            AiUsage(
                promptTokens = 1L,
                completionTokens = 2L,
                filterTokensSaved = 0L,
                model = "m",
                provider = "p",
                durationMs = 0L,
            )
        val e = AiTokenUsageEntity.from(kind = "code", analysisId = 1L, callIndex = 0, usage = u)
        assertEquals("code", e.analysisKind)
        assertEquals(1L, e.analysisId)
        assertEquals(0, e.callIndex)
        assertEquals(u, e.toValue())
    }

    @Test
    fun `AiUsageKind fromId returns the matching entry`() {
        assertEquals(AiUsageKind.LOADTEST, AiUsageKind.fromId("loadtest"))
        assertEquals(AiUsageKind.CODE, AiUsageKind.fromId("code"))
        assertEquals(AiUsageKind.CODEQA, AiUsageKind.fromId("codeqa"))
        assertEquals(AiUsageKind.SECURITY, AiUsageKind.fromId("security"))
        assertEquals(AiUsageKind.SQL, AiUsageKind.fromId("sql"))
        assertEquals(AiUsageKind.PIPELINE, AiUsageKind.fromId("pipeline"))
        assertEquals(AiUsageKind.REVIEW, AiUsageKind.fromId("review"))
        assertEquals(AiUsageKind.TICKET_CREATION, AiUsageKind.fromId("ticket_creation"))
        assertEquals(AiUsageKind.TICKET_REFINEMENT, AiUsageKind.fromId("ticket_refinement"))
        assertEquals(AiUsageKind.TICKET_IMPLEMENTATION, AiUsageKind.fromId("ticket_implementation"))
        assertEquals(AiUsageKind.TICKET_SUMMARY, AiUsageKind.fromId("ticket_summary"))
        assertEquals(AiUsageKind.GRAFANA, AiUsageKind.fromId("grafana"))
        assertEquals(AiUsageKind.JWT, AiUsageKind.fromId("jwt"))
        assertEquals(AiUsageKind.AI, AiUsageKind.fromId("ai"))
        assertEquals(AiUsageKind.KICHAT, AiUsageKind.fromId("kichat"))
        assertEquals(AiUsageKind.TOOLING, AiUsageKind.fromId("tooling"))
        assertEquals(AiUsageKind.SEARCH, AiUsageKind.fromId("search"))
        assertEquals(AiUsageKind.IMAGE_EXTRACTION, AiUsageKind.fromId("image_extraction"))
    }

    @Test
    fun `AiUsageKind fromId returns null for null blank and unknown input`() {
        assertNull(AiUsageKind.fromId(null))
        assertNull(AiUsageKind.fromId(""))
        assertNull(AiUsageKind.fromId("   "))
        assertNull(AiUsageKind.fromId("not-a-kind"))
    }

    @Test
    fun `AiUsageKind ids are unique`() {
        val ids = AiUsageKind.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
