package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.service.JiraSubtaskCreator

class TicketRefinementSubtaskCoordinator(
    private val ticketRefinementRepo: TicketRefinementPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val subtaskCreator: JiraSubtaskCreator,
    private val subtaskLedger: SubtaskLedger,
) {
    fun createSuggestedSubtasks(
        preparationId: Long,
        ticketKey: String,
        subtasks: List<SubtaskDraft>,
    ): Map<String, Any?> {
        val entity =
            ticketRefinementRepo.findTicketRefinementById(preparationId)
                ?: throw IllegalArgumentException("Ticket refinement $preparationId not found.")
        val config =
            entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
                ?: throw IllegalStateException("No Jira configuration linked to refinement $preparationId.")
        if (subtasks.isEmpty()) {
            return buildEmptySubtaskResponse(ticketKey)
        }

        val projectKey = deriveProjectKey(ticketKey)

        val created = subtaskCreator.createAll(config, ticketKey, projectKey, subtasks)
        subtaskLedger.mergeAndPersist(entity, ticketKey, created)
        return buildSubtaskResponse(ticketKey, created)
    }

    private fun buildEmptySubtaskResponse(ticketKey: String): Map<String, Any?> =
        buildMap {
            put("key", ticketKey)
            put("subtasks", emptyList<Map<String, Any?>>())
        }

    private fun deriveProjectKey(ticketKey: String): String =
        ticketKey
            .substringBeforeLast('-', missingDelimiterValue = "")
            .ifBlank {
                throw IllegalArgumentException(
                    "Cannot derive project key from ticket '$ticketKey'. " +
                        "Expected format '<PROJECT>-<NUMBER>'.",
                )
            }

    private fun buildSubtaskResponse(
        ticketKey: String,
        created: List<Map<String, Any?>>,
    ): Map<String, Any?> =
        buildMap {
            put("key", ticketKey)
            put("subtasks", created)
        }
}
