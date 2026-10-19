package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.DescriptionFormat
import com.stacklens.application.service.SubtaskBodyFormatter
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.TicketReview

@ApplicationService
class TicketReviewEngine(
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val jira: TicketRefinementJiraPort,
    private val i18n: I18nPort,
    private val jsonCodec: JsonCodecPort,
    private val fieldFormatter: TicketFieldFormatter,
    private val parser: TicketRefinementJsonParser,
) {
    fun reviewSingleTicket(
        template: String,
        parent: TicketRefinementTicket,
        child: TicketRefinementTicket,
        dbSchema: String,
    ): TicketReview {
        val sourceFormat = DescriptionFormat.fromWire(child.descriptionFormat)
        val nativePromptKey = sourceFormat.promptKey
        val promptText =
            if (nativePromptKey != null && child.descriptionNative.isNotBlank()) {
                promptLoader.get(copilot.resolveLanguage(), nativePromptKey, mapOf("outputLanguage" to outputLanguage()))
            } else {
                template
            }
        val prompt =
            promptText
                .replace("{outputLanguage}", outputLanguage())
                .replace("{parentKey}", parent.key)
                .replace("{parentSummary}", parent.summary)
                .replace("{parentDescription}", parent.description)
                .replace("{ticketKey}", child.key)
                .replace("{ticketSummary}", child.summary)
                .replace("{ticketDescription}", child.description)
                .replace("{ticketDescriptionNative}", child.descriptionNative)
                .replace("{ticketLabels}", fieldFormatter.formatLabels(child))
                .replace("{ticketSubtasks}", fieldFormatter.formatSubtasks(child))
                .replace("{referencedTickets}", fieldFormatter.formatReferencedTickets(child)) +
                if (dbSchema.isNotBlank()) "\n\n## Database Schema\n\n$dbSchema" else ""
        return try {
            val response = copilot.execute(copilot.resolveModel(), prompt)
            val parsed = parser.parseAiResponse(response, sourceFormat, acceptanceLabel())
            val improvedHtml =
                if (parsed.improvedNative.isNotBlank() && parsed.improvedFormat == DescriptionFormat.ADF) {
                    runCatching { jira.renderAdfToHtml(parsed.improvedNative) }.getOrNull()
                } else {
                    null
                }
            TicketReview(
                ticketKey = child.key,
                ticketUrl = child.url,
                originalSummary = child.summary,
                originalDescription = child.description,
                originalDescriptionHtml = child.descriptionHtml,
                improvedDescription = parsed.improvedDescription,
                improvedDescriptionHtml = improvedHtml,
                improvedNative = parsed.improvedNative,
                improvedFormat = parsed.improvedFormat.wire,
                improvedSummary = parsed.improvedSummary,
                openPoints = parsed.openPoints,
                suggestedSubtasks = parsed.suggestedSubtasks,
                state = if (parsed.openPoints.isEmpty()) "OK" else "NEEDS_REVIEW",
            )
        } catch (e: Exception) {
            TicketReview(
                ticketKey = child.key,
                ticketUrl = child.url,
                originalSummary = child.summary,
                originalDescription = child.description,
                originalDescriptionHtml = child.descriptionHtml,
                improvedDescription = "",
                openPoints = emptyList(),
                state = "FAILED",
                errorMessage = i18n.translateError(e),
            )
        }
    }

    internal fun parseReviews(json: String?): List<TicketReview> = jsonCodec.decodeTicketReviews(json)

    internal fun parseAiResponse(
        raw: String,
        sourceFormat: DescriptionFormat = DescriptionFormat.MARKDOWN,
    ) = parser.parseAiResponse(raw, sourceFormat, acceptanceLabel())

    internal fun sanitizeAiJson(s: String) = parser.sanitizeAiJson(s)

    fun normalizeReviewsJson(json: String?): String? {
        if (json.isNullOrBlank()) return json
        val reviews = parseReviews(json)
        if (reviews.isEmpty()) return json
        var changed = false
        val fixed =
            reviews.map { r ->
                val repaired = repairReview(r)
                if (repaired !== r) changed = true
                repaired
            }
        return if (changed) jsonCodec.encode(fixed) else json
    }

    private fun repairReview(r: TicketReview): TicketReview {
        if (!r.improvedDescriptionHtml.isNullOrBlank()) return r
        val text = r.improvedDescription.trim()
        val jsonCandidate = parser.extractJsonBlock(text)
        if (!jsonCandidate.startsWith("{")) return r
        val sanitised = parser.repairUnbalancedQuotes(parser.sanitizeAiJson(jsonCandidate))
        val map =
            runCatching { jsonCodec.decodeMap(sanitised) }
                .recoverCatching {
                    val repaired =
                        parser.tryRepairTruncatedJson(sanitised)
                            ?: throw IllegalStateException(
                                "not a repairable truncated JSON",
                            )
                    jsonCodec.decodeMap(repaired)
                }.getOrNull()
                ?: run {
                    val block = parser.extractAdfBlock(sanitised) ?: return r
                    val adf = runCatching { jsonCodec.decodeMap(block) }.getOrNull() ?: return r
                    if (adf["type"] != "doc") {
                        return r
                    }
                    return enrichWithAdf(r, adf)
                }
        val adfNode = map["improvedDescriptionAdf"]
        if (adfNode is Map<*, *> && adfNode["type"] == "doc") {
            @Suppress("UNCHECKED_CAST")
            return enrichWithAdf(r, adfNode as Map<String, Any?>)
        }
        return r
    }

    private fun enrichWithAdf(
        r: TicketReview,
        adf: Map<String, Any?>,
    ): TicketReview {
        val nativeJson = jsonCodec.encode(adf)
        val plain = parser.flattenAdfPlain(adf)
        val html = runCatching { jira.renderAdfToHtml(nativeJson) }.getOrNull()
        return r.copy(
            improvedDescription = plain,
            improvedNative = nativeJson,
            improvedFormat = DescriptionFormat.ADF.wire,
            improvedDescriptionHtml = html,
        )
    }

    private fun outputLanguage(): String =
        when (copilot.resolveLanguage()) {
            "de" -> "German"
            else -> "English"
        }

    private fun acceptanceLabel(): String = SubtaskBodyFormatter.labelForLanguage(copilot.resolveLanguage())
}
