package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import com.stacklens.domain.text.TicketLineFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ApplicationService
class SprintReviewOrchestrator(
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val i18n: I18nPort,
) {
    private val log: Logger = LoggerFactory.getLogger(SprintReviewOrchestrator::class.java)

    fun reviewTicket(
        template: String,
        ticket: SprintReviewTicket,
    ): SprintReviewRow {
        val baseline = baseRow(ticket, state = SprintReviewState.FAILED, summary = "", openPoints = emptyList())
        return try {
            val prompt = buildPrompt(template, ticket)
            val raw = copilot.execute(copilot.resolveModel(), prompt)
            val parsed = parseAiResponse(raw)
            val state = normalizeState(parsed.state)
            val summary = parsed.improvementSummary.take(280)
            val refinementReasons =
                parsed.refinementReasons
                    .map { resolveReasonKey(it) }
                    .distinct()
                    .take(10)
            val openQuestions =
                parsed.openQuestions
                    .distinct()
                    .take(10)
            baseline.copy(
                state = state,
                improvementSummary = summary,
                openPoints = if (state == SprintReviewState.OFFENE_FRAGE) openQuestions else emptyList(),
                refinementReasons = if (state == SprintReviewState.REFINEMENT) refinementReasons else emptyList(),
            )
        } catch (e: Exception) {
            log.warn("[SprintReview] AI review failed for {}: {}", ticket.key, e.message)
            baseline.copy(state = SprintReviewState.FAILED, errorMessage = i18n.translateError(e))
        }
    }

    internal fun normalizeState(state: String): SprintReviewState = SprintReviewState.fromAiState(state)

    internal fun resolveReasonKey(reason: String): String {
        val norm = reason.trim().lowercase()
        return when {
            norm.isBlank() -> ""
            norm in REASON_KEYS_AL -> REASON_KEYS_AL[norm]!!
            norm.contains("story point") || norm.contains("story-point") || norm.contains("schätz") ->
                "missing_story_points"
            norm.contains("acceptance") || norm.contains("akzeptanz") || norm.contains("ak") ->
                "missing_acceptance_criteria"
            norm.contains("user story") || norm.contains("anwenderfall") -> "missing_user_story"
            norm.contains("background") || norm.contains("hintergrund") || norm.contains("kontext") ->
                "missing_background"
            norm.contains("component") || norm.contains("modul") || norm.contains("komponente") ->
                "missing_components"
            norm.contains("goal") || norm.contains("ziel") || norm.contains("objective") ->
                "missing_clear_goal"
            norm.contains("incomplete") ||
                norm.contains("unvollständig") ||
                norm.contains("todo") ||
                norm.contains("tbd") ||
                norm.contains("placeholder") ->
                "description_incomplete"
            else -> reason.trim()
        }
    }

    internal fun buildPrompt(
        template: String,
        ticket: SprintReviewTicket,
    ): String {
        val promptText =
            if (template.contains("{ticketKey}", ignoreCase = true)) {
                template
            } else {
                promptLoader.get(copilot.resolveLanguage(), "sprintreview.review_ticket", emptyMap())
            }
        return promptText
            .replace("{outputLanguage}", outputLanguage())
            .replace("{ticketKey}", ticket.key)
            .replace("{ticketSummary}", ticket.summary)
            .replace("{ticketDescription}", ticket.description)
            .replace("{ticketIssueType}", ticket.issueType)
            .replace("{ticketStatus}", ticket.status)
            .replace("{ticketAssignee}", ticket.assignee.orEmpty())
            .replace("{ticketLabels}", if (ticket.labels.isEmpty()) "(none)" else ticket.labels.joinToString(", "))
            .replace("{ticketSubtasks}", formatSubtasks(ticket))
            .replace("{ticketStoryPoints}", formatStoryPoints(ticket))
            .replace("{ticketNumericCustomFields}", formatNumericCustomFields(ticket))
            .replace("{referencedTickets}", formatReferencedTickets(ticket))
    }

    internal fun formatStoryPoints(ticket: SprintReviewTicket): String = ticket.storyPoints?.toString() ?: "(none — no numeric value found in any common story-points field)"

    internal fun formatNumericCustomFields(ticket: SprintReviewTicket): String =
        if (ticket.numericCustomFields.isEmpty()) {
            "(none)"
        } else {
            ticket.numericCustomFields.joinToString("\n") { "  - $it" }
        }

    internal fun formatSubtasks(ticket: SprintReviewTicket): String = TicketLineFormatter.format(ticket.subtasks)

    internal fun formatReferencedTickets(ticket: SprintReviewTicket): String = TicketLineFormatter.format(ticket.referencedTickets)

    internal fun parseAiResponse(raw: String): ParsedResponse {
        val cleaned =
            raw
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return ParsedResponse(state = "", improvementSummary = "", refinementReasons = emptyList(), openQuestions = emptyList())
        }
        val json = cleaned.substring(firstBrace, lastBrace + 1)
        val state = extractStringField(json, "state").orEmpty()
        val improvementSummary = extractStringField(json, "improvementSummary").orEmpty()
        val refinementReasons = extractStringArray(json, "refinementReasons")
        val openQuestions = extractStringArray(json, "openQuestions")
        return ParsedResponse(state, improvementSummary, refinementReasons, openQuestions)
    }

    internal fun extractStringField(
        json: String,
        key: String,
    ): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val m = pattern.find(json) ?: return null
        return m.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .trim()
            .ifBlank { null }
    }

    internal fun extractStringArray(
        json: String,
        key: String,
    ): List<String> {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val m = pattern.find(json) ?: return emptyList()
        val inner = m.groupValues[1]
        val items = mutableListOf<String>()
        val itemPattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        for (it in itemPattern.findAll(inner)) {
            val v =
                it.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .trim()
            if (v.isNotEmpty()) items += v
        }
        return items
    }

    private fun baseRow(
        ticket: SprintReviewTicket,
        state: SprintReviewState,
        summary: String,
        openPoints: List<String>,
    ): SprintReviewRow =
        SprintReviewRow(
            ticketKey = ticket.key,
            ticketSummary = ticket.summary,
            ticketUrl = ticket.url,
            issueType = ticket.issueType,
            status = ticket.status,
            assignee = ticket.assignee,
            storyPoints = ticket.storyPoints,
            state = state,
            improvementSummary = summary,
            openPoints = openPoints,
        )

    private fun outputLanguage(): String =
        when (copilot.resolveLanguage()) {
            "de" -> "German"
            else -> "English"
        }

    internal data class ParsedResponse(
        val state: String,
        val improvementSummary: String,
        val refinementReasons: List<String>,
        val openQuestions: List<String>,
    )

    companion object {
        private val REASON_KEYS_AL =
            mapOf(
                "missing_story_points" to "missing_story_points",
                "story_points_missing" to "missing_story_points",
                "no_story_points" to "missing_story_points",
                "story_points_not_estimated" to "missing_story_points",
                "story points not estimated" to "missing_story_points",
                "keine story points" to "missing_story_points",
                "story points fehlen" to "missing_story_points",
                "missing_acceptance_criteria" to "missing_acceptance_criteria",
                "acceptance_criteria_missing" to "missing_acceptance_criteria",
                "no_acceptance_criteria" to "missing_acceptance_criteria",
                "akzeptanzkriterien fehlen" to "missing_acceptance_criteria",
                "missing_user_story" to "missing_user_story",
                "no_user_story" to "missing_user_story",
                "keine user story" to "missing_user_story",
                "user story fehlt" to "missing_user_story",
                "missing_background" to "missing_background",
                "no_background" to "missing_background",
                "kein hintergrund" to "missing_background",
                "missing_components" to "missing_components",
                "no_components" to "missing_components",
                "affected_components_missing" to "missing_components",
                "missing_clear_goal" to "missing_clear_goal",
                "no_goal" to "missing_clear_goal",
                "kein klares ziel" to "missing_clear_goal",
                "description_incomplete" to "description_incomplete",
                "incomplete_description" to "description_incomplete",
            )
    }
}
