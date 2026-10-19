package com.stacklens.application.service.ticketrefinement

import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.DescriptionFormat
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.domain.model.TicketReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

class TicketReviewEngineTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val jira: TicketRefinementJiraPort = mock()
    private val i18n: I18nPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val fieldFormatter: TicketFieldFormatter = mock()

    private fun engine(): TicketReviewEngine =
        TicketReviewEngine(
            copilot = copilot,
            promptLoader = promptLoader,
            jira = jira,
            i18n = i18n,
            jsonCodec = jsonCodec,
            fieldFormatter = fieldFormatter,
            parser =
                TicketRefinementJsonParser(
                    jsonCodec,
                    DefaultAiJsonRepairer(),
                    DefaultAiResponseJsonExtractor(),
                ),
        )

    @Test
    fun `parseReviews returns empty list when json is null`() {
        assertEquals(emptyList<TicketReview>(), engine().parseReviews(null))
    }

    @Test
    fun `parseReviews returns empty list when json is blank`() {
        assertEquals(emptyList<TicketReview>(), engine().parseReviews("   "))
    }

    @Test
    fun `parseReviews returns empty list when json fails to decode`() {
        whenever(
            jsonCodec.decodeList(
                any<String>(),
                any<kotlin.reflect.KClass<TicketReview>>(),
            ),
        ).thenThrow(RuntimeException("bad json"))
        assertEquals(emptyList<TicketReview>(), engine().parseReviews("not valid json"))
    }

    @Test
    fun `parseReviews returns the decoded list on success`() {
        val review = mock<TicketReview>()
        whenever(
            jsonCodec.decodeList(
                any<String>(),
                any<kotlin.reflect.KClass<TicketReview>>(),
            ),
        ).thenReturn(listOf(review))
        assertEquals(listOf(review), engine().parseReviews("[{}]"))
    }

    @Test
    fun `parseAiResponse delegates to the parser`() {
        val result = engine().parseAiResponse("raw text", DescriptionFormat.MARKDOWN)
        assertNotNull(result)
    }

    @Test
    fun `sanitizeAiJson delegates to the parser`() {
        val result = engine().sanitizeAiJson("raw")
        assertNotNull(result)
    }

    @Test
    fun `normalizeReviewsJson returns input unchanged when null`() {
        assertEquals(null, engine().normalizeReviewsJson(null))
    }

    @Test
    fun `normalizeReviewsJson returns input unchanged when blank`() {
        assertEquals("", engine().normalizeReviewsJson(""))
    }

    @Test
    fun `normalizeReviewsJson returns input unchanged when no reviews parse`() {
        whenever(
            jsonCodec.decodeList(
                any<String>(),
                any<kotlin.reflect.KClass<TicketReview>>(),
            ),
        ).thenReturn(emptyList())
        assertEquals("[]", engine().normalizeReviewsJson("[]"))
    }

    @Test
    fun `normalizeReviewsJson repairs an old review whose improvedDescription is raw JSON in a code fence`() {
        val adf = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}"
        val rawUnfenced = "{\"improvedSummary\":\"s\",\"improvedDescriptionAdf\":" + adf + ",\"openPoints\":[]}"
        val rawFenced = "```json\\n" + rawUnfenced + "\\n```"
        val review =
            TicketReview(
                ticketKey = "T-1",
                ticketUrl = "u",
                originalSummary = "",
                originalDescription = "",
                improvedDescription = rawFenced,
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
                improvedFormat = "markdown",
            )
        whenever(
            jsonCodec.decodeList(
                any<String>(),
                any<kotlin.reflect.KClass<TicketReview>>(),
            ),
        ).thenReturn(listOf(review))
        whenever(jsonCodec.decodeMap(rawUnfenced)).thenReturn(
            mapOf(
                "improvedSummary" to "s",
                "improvedDescriptionAdf" to
                    mapOf(
                        "type" to "doc",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "paragraph",
                                    "content" to listOf(mapOf("type" to "text", "text" to "hi")),
                                ),
                            ),
                    ),
                "openPoints" to emptyList<String>(),
            ),
        )
        whenever(jira.renderAdfToHtml(any())).thenReturn("<p class=\"sp-jira-p\">hi</p>")
        whenever(jsonCodec.encode(any<List<TicketReview>>())).thenReturn("ENCODED")
        whenever(jsonCodec.encode(argThat { v -> v is Map<*, *> })).thenAnswer { "MOCKED_NATIVE_JSON" }

        val out = engine().normalizeReviewsJson("ignored")
        assertEquals("ENCODED", out)
        val captor = org.mockito.kotlin.argumentCaptor<List<TicketReview>>()
        org.mockito.kotlin
            .verify(jsonCodec)
            .encode(captor.capture())
        val repaired = captor.firstValue.single()
        assertEquals("hi", repaired.improvedDescription)
        assertEquals("adf", repaired.improvedFormat)
        assertNotNull(repaired.improvedDescriptionHtml)
        assertTrue(repaired.improvedDescriptionHtml!!.contains("<p"))
    }

    @Test
    fun `normalizeReviewsJson repairs an old review whose improvedDescription is bare raw JSON`() {
        val adf = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}"
        val rawBare = "{\"improvedSummary\":\"s\",\"improvedDescriptionAdf\":" + adf + ",\"openPoints\":[]}"
        val review =
            TicketReview(
                ticketKey = "T-2",
                ticketUrl = "u",
                originalSummary = "",
                originalDescription = "",
                improvedDescription = rawBare,
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
                improvedFormat = "markdown",
            )
        whenever(
            jsonCodec.decodeList(
                any<String>(),
                any<kotlin.reflect.KClass<TicketReview>>(),
            ),
        ).thenReturn(listOf(review))
        whenever(jsonCodec.decodeMap(rawBare)).thenReturn(
            mapOf(
                "improvedSummary" to "s",
                "improvedDescriptionAdf" to
                    mapOf(
                        "type" to "doc",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "paragraph",
                                    "content" to listOf(mapOf("type" to "text", "text" to "hi")),
                                ),
                            ),
                    ),
                "openPoints" to emptyList<String>(),
            ),
        )
        whenever(jira.renderAdfToHtml(any())).thenReturn("<p class=\"sp-jira-p\">hi</p>")
        whenever(jsonCodec.encode(any<List<TicketReview>>())).thenReturn("ENCODED")
        whenever(jsonCodec.encode(argThat { v -> v is Map<*, *> })).thenAnswer { "MOCKED_NATIVE_JSON" }

        engine().normalizeReviewsJson("ignored")
        val captor = org.mockito.kotlin.argumentCaptor<List<TicketReview>>()
        org.mockito.kotlin
            .verify(jsonCodec)
            .encode(captor.capture())
        assertEquals("hi", captor.firstValue.single().improvedDescription)
    }
}
