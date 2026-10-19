package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.service.review.ReviewAnalysisDecodingService
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReviewAnalysisDtoMapperTest {
    private val decoding: ReviewAnalysisDecodingUseCase = ReviewAnalysisDecodingService(JacksonJsonCodecAdapter(ObjectMapper()))

    private val mapper = ReviewAnalysisDtoMapper(decoding)

    @Test
    fun `toSummaryMap includes all expected keys with default entity`() {
        val a = ReviewAnalysis(jiraSprintUrl = "https://jira.example.com/sprint/1")
        val m = mapper.toSummaryMap(a)
        assertEquals(0L, m["id"])
        assertEquals("https://jira.example.com/sprint/1", m["jiraSprintUrl"])
        assertNull(m["jiraConfigId"])
        assertEquals("PENDING", m["status"])
        assertNull(m["currentStep"])
        assertNull(m["errorMessage"])
        assertNull(m["pptxFileName"])
        assertNull(m["pptxSlideCount"])
        assertNull(m["pptxDetectedLanguage"])
        assertEquals(11, m.size)
    }

    @Test
    fun `toSummaryMap formats completedAt as string`() {
        val now = java.time.LocalDateTime.now()
        val a =
            ReviewAnalysis(
                jiraSprintUrl = "u",
                status = "COMPLETED",
                completedAt = now,
            )
        val m = mapper.toSummaryMap(a)
        assertEquals("COMPLETED", m["status"])
        assertEquals(now.toString(), m["completedAt"])
    }

    @Test
    fun `toDetailMap includes all expected keys with default entity`() {
        val a = ReviewAnalysis(jiraSprintUrl = "https://jira.example.com/sprint/1")
        val m = mapper.toDetailMap(a)
        assertEquals(0L, m["id"])
        assertEquals("https://jira.example.com/sprint/1", m["jiraSprintUrl"])
        assertNull(m["jiraConfigId"])
        assertEquals("", m["analysisInstructions"])
        assertEquals("PENDING", m["status"])
        assertNull(m["currentStep"])
        assertNull(m["errorMessage"])
        assertNull(m["resultMarkdown"])
        assertEquals(emptyList<Any>(), m["processLog"])
        assertNull(m["sprintName"])
        assertNull(m["pptxFileName"])
        assertNull(m["pptxSlideCount"])
        assertNull(m["resultHtmlPath"])
        assertNull(m["pptxQuestionsJson"])
        assertNull(m["pptxAnswersJson"])
        assertNull(m["pptxDetectedLanguage"])
        assertEquals(18, m.size)
    }

    @Test
    fun `toDetailMap extracts sprint name from sprintInfoJson`() {
        val a =
            ReviewAnalysis(
                jiraSprintUrl = "u",
                sprintInfoJson = """{"name":"Sprint 42","state":"active"}""",
            )
        val m = mapper.toDetailMap(a)
        assertEquals("Sprint 42", m["sprintName"])
    }

    @Test
    fun `toDetailMap returns null sprint name when sprintInfoJson is null`() {
        val a = ReviewAnalysis(jiraSprintUrl = "u", sprintInfoJson = null)
        val m = mapper.toDetailMap(a)
        assertNull(m["sprintName"])
    }

    @Test
    fun `toDetailMap parses processLogJson as list`() {
        val a =
            ReviewAnalysis(
                jiraSprintUrl = "u",
                processLogJson = """["step1","step2"]""",
            )
        val m = mapper.toDetailMap(a)

        @Suppress("UNCHECKED_CAST")
        val log = m["processLog"] as List<String>
        assertEquals(listOf("step1", "step2"), log)
    }

    @Test
    fun `toDetailMap formats completedAt as string when populated`() {
        val now = java.time.LocalDateTime.now()
        val a = ReviewAnalysis(jiraSprintUrl = "u", completedAt = now)
        val m = mapper.toDetailMap(a)
        assertEquals(now.toString(), m["completedAt"])
    }
}
