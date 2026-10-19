package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.TicketReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCommentPosterTest {
    private val jira = mock<TicketRefinementJiraPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val i18n = mock<I18nPort>()
    private val copilot = mock<CopilotPort>()
    private val jsonCodec = mock<JsonCodecPort>()

    private val poster =
        TicketCommentPoster(jira, jiraConfigRepo, i18n, copilot, jsonCodec)

    @Test
    fun `parseReviews returns empty list when json is null`() {
        assertEquals(emptyList<TicketReview>(), poster.parseReviews(null))
    }

    @Test
    fun `parseReviews returns empty list when json is blank`() {
        assertEquals(emptyList<TicketReview>(), poster.parseReviews(""))
        assertEquals(emptyList<TicketReview>(), poster.parseReviews("   "))
    }

    @Test
    fun `parseReviews returns empty list when decoding throws`() {
        whenever(jsonCodec.decodeList(org.mockito.kotlin.any<String>(), eq(TicketReview::class))).thenThrow(RuntimeException("bad json"))
        assertEquals(emptyList<TicketReview>(), poster.parseReviews("[]"))
    }

    @Test
    fun `parseReviews returns decoded list on success`() {
        val reviews =
            listOf(
                TicketReview(
                    ticketKey = "P-1",
                    ticketUrl = "",
                    originalSummary = "",
                    originalDescription = "",
                    improvedDescription = "",
                    openPoints = emptyList(),
                    state = "OK",
                ),
            )
        whenever(jsonCodec.decodeList(org.mockito.kotlin.any<String>(), eq(TicketReview::class))).thenReturn(reviews)
        val result = poster.parseReviews("[{\"ticketKey\":\"P-1\",\"state\":\"OK\"}]")
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].ticketKey)
        assertEquals("OK", result[0].state)
    }

    @Test
    fun `prependGreetingWiki adds greeting to plain wiki`() {
        whenever(i18n.tFor("en", "ticket_refinement.comment_greeting")).thenReturn("Hi!")
        whenever(i18n.tFor("en", "ticket_refinement.comment_open_points")).thenReturn("Open Points")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        val result = poster.prependGreetingWiki("Body text")
        assertTrue(result.contains("Hi!"))
        assertTrue(result.contains("Body text"))
    }

    @Test
    fun `prependGreetingWiki adds open points section when provided`() {
        whenever(i18n.tFor("en", "ticket_refinement.comment_greeting")).thenReturn("Hi!")
        whenever(i18n.tFor("en", "ticket_refinement.comment_open_points")).thenReturn("Open Points")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        val result = poster.prependGreetingWiki("Body", listOf("Q1", "Q2"))
        assertTrue(result.contains("Hi!"))
        assertTrue(result.contains("Body"))
        assertTrue(result.contains("Open Points"))
        assertTrue(result.contains("Q1"))
        assertTrue(result.contains("Q2"))
    }

    @Test
    fun `postReviewAsComment passes the raw ADF body and typed greeting to the port`() {
        val adf = """{"type":"doc","version":1,"content":[]}"""
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "od",
                improvedDescription = "impl",
                openPoints = listOf("Q1", "Q2"),
                state = "OK",
                improvedNative = adf,
                improvedFormat = "adf",
            )
        val entity = TicketRefinement(id = 1L, parentTicketKey = "P-1", reviewsJson = "[]")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.tFor("en", "ticket_refinement.comment_greeting")).thenReturn("Hi!")
        whenever(i18n.tFor("en", "ticket_refinement.comment_open_points")).thenReturn("Open Points")
        whenever(jsonCodec.decodeList(anyOrNull(), eq(TicketReview::class))).thenReturn(listOf(review))

        poster.postReviewAsComment(1L, "P-1") { entity }

        verify(jira).postComment(anyOrNull(), eq("P-1"), eq(adf), eq("adf"), any(), eq("Hi!"), eq("Open Points"), eq(listOf("Q1", "Q2")))
    }
}
