package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.JsonTree
import com.stacklens.application.port.out.JsonTreeReaderPort
import com.stacklens.application.port.out.knowledge.ReviewKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.SprintKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketCreationKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketRefinementKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketSummaryKnowledgePresentationPort
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class AnalysisKnowledgeMapper(
    private val jsonCodec: JsonTreeReaderPort,
    private val reviewPresentation: ReviewKnowledgePresentationPort,
    private val sprintPresentation: SprintKnowledgePresentationPort,
    private val ticketCreationPresentation: TicketCreationKnowledgePresentationPort,
    private val ticketRefinementPresentation: TicketRefinementKnowledgePresentationPort,
    private val ticketSummaryPresentation: TicketSummaryKnowledgePresentationPort,
) {
    fun fromReviewAnalysis(analysis: ReviewAnalysis): KnowledgePublicationCandidate? {
        val md = analysis.resultMarkdown?.takeIf { it.isNotBlank() } ?: return null
        val body =
            buildString {
                append("${reviewPresentation.reviewAnalysisHeader()}\n\n")
                append("${reviewPresentation.sprintUrlLabel()}: ${analysis.jiraSprintUrl}\n")
                analysis.jiraConfigId?.let { append("${reviewPresentation.jiraConfigLabel()}: #$it\n") }
                analysis.analysisInstructions.takeIf { it.isNotBlank() }?.let {
                    append("\n${reviewPresentation.instructionsHeading()}\n").append(it).append("\n")
                }
                append("\n${reviewPresentation.resultHeading()}\n").append(md.trim())
            }
        return KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalId = "review-analysis:${analysis.id}",
            title = reviewPresentation.reviewAnalysisTitle(analysis.id),
            sourceUri = reviewPresentation.reviewAnalysisRoute(analysis.id),
            content = body,
            metadata =
                mapOf(
                    "sprintUrl" to analysis.jiraSprintUrl,
                    "status" to analysis.status,
                ),
        )
    }

    fun fromSprintReview(review: SprintReview): KnowledgePublicationCandidate? {
        val md = renderSprintReview(review) ?: return null
        return KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.SPRINT_REVIEW,
            externalId = "sprint-review:${review.id}",
            title = sprintPresentation.sprintReviewTitle(review.sprintName),
            sourceUri = review.targetTicketUrl ?: review.sprintUrl,
            content = md,
            metadata =
                buildMap {
                    put("sprint", review.sprintName)
                    put("targetTicket", review.targetTicketKey)
                    put("state", review.sprintState ?: "")
                    put("status", review.status)
                },
        )
    }

    fun fromTicketCreation(record: TicketCreationRecord): KnowledgePublicationCandidate? {
        val resultJson = record.resultJson?.takeIf { it.isNotBlank() } ?: return null
        val node = jsonCodec.decodeTree(resultJson) as? JsonTree.ObjectNode
        val description = (node?.textField("description") ?: "").ifBlank { record.inputText }
        val aiSummary = node?.textField("summary") ?: ""
        val summary = (record.summary ?: aiSummary).ifBlank { record.inputText.take(80) }
        val labels = joinFieldAsString(node?.entries?.get("labels")).ifBlank { record.labels.orEmpty() }
        val openPoints = joinFieldAsString(node?.entries?.get("openPoints"))
        val ac = joinFieldAsString(node?.entries?.get("acceptanceCriteria"))
        val body =
            buildString {
                append("${ticketCreationPresentation.ticketCreationHeader()}\n\n")
                append("${ticketCreationPresentation.summaryLabel()}: ").append(summary).append('\n')
                if (record.issueType != null) append("${ticketCreationPresentation.typeLabel()}: ").append(record.issueType).append('\n')
                if (labels.isNotBlank()) append("${ticketCreationPresentation.labelsLabel()}: ").append(labels).append('\n')
                record.jiraKey?.let { append("${ticketCreationPresentation.jiraKeyLabel()}: ").append(it).append('\n') }
                record.jiraUrl?.let { append("${ticketCreationPresentation.jiraUrlLabel()}: ").append(it).append('\n') }
                if (ac.isNotBlank()) {
                    append("\n${ticketCreationPresentation.acceptanceCriteriaHeading()}\n")
                    ac.lines().filter { it.isNotBlank() }.forEach { append(sprintPresentation.bullet(it)).append('\n') }
                }
                if (description.isNotBlank()) {
                    append("\n${ticketCreationPresentation.descriptionHeading()}\n").append(description.trim())
                }
                if (openPoints.isNotBlank()) {
                    append("\n${ticketCreationPresentation.openPointsHeading()}\n")
                    openPoints.lines().filter { it.isNotBlank() }.forEach { append(sprintPresentation.bullet(it)).append('\n') }
                }
            }
        return KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.TICKET_CREATION,
            externalId = "ticket-creation:${record.id}",
            title = ticketCreationPresentation.ticketCreationTitle(summary),
            sourceUri = record.jiraUrl,
            content = body,
            metadata =
                mapOf(
                    "issueType" to (record.issueType ?: ""),
                    "jiraKey" to (record.jiraKey ?: ""),
                ),
        )
    }

    fun fromTicketRefinement(refinement: TicketRefinement): List<KnowledgePublicationCandidate> {
        val reviewsJson = refinement.reviewsJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        val nodes = jsonCodec.decodeTree(reviewsJson) as? JsonTree.ArrayNode ?: return emptyList()
        val parentKey = refinement.parentTicketKey
        return nodes.elements.mapNotNull { reviewNode ->
            val obj = reviewNode as? JsonTree.ObjectNode ?: return@mapNotNull null
            val ticketKey = obj.textField("ticketKey").ifBlank { return@mapNotNull null }
            val title = obj.textField("originalSummary").ifBlank { ticketKey }
            val improved = obj.textField("improvedDescription")
            val openPoints = joinFieldAsString(obj.entries["openPoints"])
            val suggested = obj.entries["suggestedSubtasks"] as? JsonTree.ArrayNode
            val body =
                buildString {
                    append("${ticketRefinementPresentation.ticketRefinementHeader()}: ").append(ticketKey).append("\n\n")
                    append("${ticketRefinementPresentation.parentLabel()}: ").append(parentKey).append('\n')
                    append("\n${ticketRefinementPresentation.originalTitleHeading()}\n").append(title).append('\n')
                    val origDesc = obj.textField("originalDescription")
                    if (origDesc.isNotBlank()) {
                        append("\n${ticketRefinementPresentation.originalDescriptionHeading()}\n").append(origDesc.take(2000))
                    }
                    if (improved.isNotBlank()) {
                        append("\n${ticketRefinementPresentation.improvedDescriptionHeading()}\n").append(improved.take(4000))
                    }
                    if (openPoints.isNotBlank()) {
                        append("\n${ticketCreationPresentation.openPointsHeading()}\n")
                        openPoints
                            .lines()
                            .filter { it.isNotBlank() }
                            .forEach { append(sprintPresentation.bullet(it)).append('\n') }
                    }
                    if (suggested != null && suggested.elements.isNotEmpty()) {
                        append("\n${ticketRefinementPresentation.suggestedSubtasksHeading()}\n")
                        for (sub in suggested.elements) {
                            val s = (sub as? JsonTree.ObjectNode)?.textField("summary") ?: continue
                            if (s.isNotBlank()) append(sprintPresentation.bullet(s)).append('\n')
                        }
                    }
                }
            val ticketUrl = (obj.entries["ticketUrl"] as? JsonTree.TextNode)?.value
            KnowledgePublicationCandidate(
                sourceType = KnowledgeSourceType.TICKET_REFINEMENT,
                externalId = "ticket-refinement:${refinement.id}:$ticketKey",
                title = ticketRefinementPresentation.ticketRefinementTitle(ticketKey),
                sourceUri = ticketUrl,
                content = body,
                metadata = mapOf("parentKey" to parentKey, "ticketKey" to ticketKey),
            )
        }
    }

    fun fromTicketSummary(summary: JiraTicketSummary): KnowledgePublicationCandidate? {
        val md = summary.summaryMarkdown?.takeIf { it.isNotBlank() } ?: return null
        val title = ticketSummaryPresentation.ticketSummaryTitle(summary.ticketKey)
        return KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.TICKET_SUMMARY,
            externalId = "ticket-summary:${summary.id}",
            title = title,
            sourceUri = null,
            content = "# $title\n\n$md",
            metadata = mapOf("ticketKey" to summary.ticketKey),
        )
    }

    private fun renderSprintReview(review: SprintReview): String? {
        val rowsJson = review.rowsJson?.takeIf { it.isNotBlank() } ?: return null
        val nodes = jsonCodec.decodeTree(rowsJson) as? JsonTree.ArrayNode
        if (nodes == null) return null
        val body =
            buildString {
                append("${sprintPresentation.sprintReviewHeader()}: ").append(review.sprintName).append("\n\n")
                append("${sprintPresentation.targetTicketLabel()}: ").append(review.targetTicketKey).append('\n')
                review.sprintState?.let { append("${sprintPresentation.sprintStateLabel()}: ").append(it).append('\n') }
                append("${sprintPresentation.issueCountLabel()}: ").append(review.totalIssues).append('\n')
                for (row in nodes.elements) {
                    val obj = row as? JsonTree.ObjectNode ?: continue
                    val key = obj.textField("ticketKey")
                    val summary = obj.textField("ticketSummary")
                    val state = obj.textField("state")
                    val improvement = obj.textField("improvementSummary")
                    val open = joinFieldAsString(obj.entries["openPoints"])
                    if (key.isBlank()) continue
                    append("\n").append(sprintPresentation.rowHeading(key, summary)).append('\n')
                    append("${sprintPresentation.statusLabel()}: ").append(state).append('\n')
                    if (improvement.isNotBlank()) append("${sprintPresentation.improvementLabel()}: ").append(improvement).append('\n')
                    if (open.isNotBlank()) {
                        append("\n${sprintPresentation.openQuestionsHeading()}:\n")
                        open.lines().filter { it.isNotBlank() }.forEach { append(sprintPresentation.bullet(it)).append('\n') }
                    }
                }
            }
        return body
    }

    private fun JsonTree.ObjectNode.textField(field: String): String =
        when (val node = entries[field]) {
            is JsonTree.TextNode -> node.value
            is JsonTree.NumberNode -> node.value.toString()
            is JsonTree.BooleanNode -> node.value.toString()
            else -> ""
        }

    private fun JsonTree.ArrayNode.joinAsString(): String =
        elements
            .map { node ->
                when (node) {
                    is JsonTree.TextNode -> node.value
                    is JsonTree.NumberNode -> node.value.toString()
                    is JsonTree.BooleanNode -> node.value.toString()
                    is JsonTree.ArrayNode -> node.joinAsString()
                    else -> ""
                }
            }.filter { it.isNotBlank() }
            .joinToString("\n")

    private fun joinFieldAsString(field: JsonTree?): String =
        when (field) {
            is JsonTree.ArrayNode -> field.joinAsString()
            is JsonTree.TextNode -> field.value
            is JsonTree.NumberNode -> field.value.toString()
            is JsonTree.BooleanNode -> field.value.toString()
            else -> ""
        }
}
