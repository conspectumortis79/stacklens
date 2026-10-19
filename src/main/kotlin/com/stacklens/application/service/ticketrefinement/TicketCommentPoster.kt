package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.DescriptionFormat
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.TicketReview

@ApplicationService
class TicketCommentPoster(
    private val jira: TicketRefinementJiraPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val i18n: I18nPort,
    private val copilot: CopilotPort,
    private val jsonCodec: JsonCodecPort,
) {
    fun postReviewAsComment(
        preparationId: Long,
        ticketKey: String,
        loadEntity: () -> TicketRefinement?,
    ): TicketRefinement {
        val entity =
            loadEntity()
                ?: throw IllegalArgumentException("preparation not found: $preparationId")
        val reviews = parseReviews(entity.reviewsJson)
        val target =
            reviews.find { it.ticketKey == ticketKey }
                ?: throw IllegalArgumentException("ticket not part of preparation: $ticketKey")
        if (target.state != "OK" && target.state != "NEEDS_REVIEW") {
            throw IllegalStateException("ticket review not in OK/NEEDS_REVIEW state: $ticketKey (${target.state})")
        }
        val config = entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
        val markdownBody = buildCommentBody(target)
        val greeting = t("comment_greeting")
        val openPointsLabel = t("comment_open_points")
        val commentFormat = DescriptionFormat.fromWire(target.improvedFormat)
        val (body, format) =
            when (commentFormat) {
                DescriptionFormat.ADF -> target.improvedNative to commentFormat.wire
                DescriptionFormat.WIKI -> prependGreetingWiki(target.improvedNative, target.openPoints) to commentFormat.wire
                DescriptionFormat.MARKDOWN -> markdownBody to commentFormat.wire
            }
        jira.postComment(config, ticketKey, body, format, markdownBody, greeting, openPointsLabel, target.openPoints)
        val updated =
            reviews.map {
                if (it.ticketKey == ticketKey) it.copy(commentPosted = true) else it
            }
        entity.reviewsJson = jsonCodec.encode(updated)
        return entity
    }

    internal fun parseReviews(json: String?): List<TicketReview> = jsonCodec.decodeTicketReviews(json)

    fun buildCommentBody(review: TicketReview): String {
        val sb = StringBuilder()
        sb.append(t("comment_greeting")).append("\n\n")
        sb.append(review.improvedDescription.trim()).append("\n")
        if (review.openPoints.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("## ⚠ ").append(t("comment_open_points")).append("\n\n")
            review.openPoints.forEach { sb.append("- ").append(it).append("\n") }
        }
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun prependGreetingWiki(
        wiki: String,
        openPoints: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append(t("comment_greeting")).append("\n\n")
        sb.append(wiki.trimStart())
        if (openPoints.isNotEmpty()) {
            sb.append("\n\n----\n\n")
            sb.append("h3. ⚠ ").append(t("comment_open_points")).append("\n\n")
            openPoints.forEach { sb.append("* ").append(it).append("\n") }
        }
        return sb.toString()
    }

    private fun t(key: String): String = i18n.tFor(copilot.resolveLanguage(), "ticket_refinement.$key")
}
