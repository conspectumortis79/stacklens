package com.stacklens.application.service

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.application.service.ticketcreation.TicketCreationDefaults
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory

class SubtaskCreationException(
    val subtaskSummary: String,
    val parentKey: String,
    cause: Throwable,
) : RuntimeException("Failed to create subtask '$subtaskSummary' under parent $parentKey: ${cause.message ?: "unknown"}", cause)

class JiraSubtaskCreator(
    private val jiraPort: TicketCreationJiraPort,
    private val demoSimulation: TicketCreationDemoPort,
    private val languageProvider: () -> String = { "" },
) {
    private val log = LoggerFactory.getLogger(JiraSubtaskCreator::class.java)

    fun createAll(
        config: JiraConfig,
        parentKey: String,
        projectKey: String,
        subtasks: List<SubtaskDraft>,
    ): List<Map<String, Any?>> {
        if (subtasks.isEmpty() || parentKey.isBlank()) return emptyList()
        val cleanedSubtasks =
            subtasks.mapIndexed { idx, draft ->
                draft.copy(summary = draft.summary.trim().ifBlank { "Subtask ${idx + 1}" })
            }
        if (demoSimulation.isDemo(config)) {
            log.info(
                "[JiraSubtaskCreator] Demo-Modus – {} Subtasks unter {} simuliert (Jira-Schreibpfad wird nicht verwendet)",
                cleanedSubtasks.size,
                parentKey,
            )
            return demoSimulation.simulatedSubtasks(
                projectKey = projectKey,
                subtasks = cleanedSubtasks,
            )
        }
        return cleanedSubtasks.map { draft ->
            val cleanedSummary = draft.summary
            val cleanedDescription =
                ensureTrailingSeparator(
                    SubtaskBodyFormatter.format(
                        draft.description,
                        SubtaskBodyFormatter.labelForLanguage(languageProvider()),
                    ),
                )
            try {
                val created =
                    jiraPort.createSubtask(
                        config = config,
                        parentKey = parentKey,
                        projectKey = projectKey,
                        summary = cleanedSummary,
                        description = cleanedDescription,
                        issueType = TicketCreationDefaults.SUBTASK_ISSUE_TYPE,
                        labels = draft.labels,
                        priority = draft.priority.ifBlank { TicketCreationDefaults.DEFAULT_PRIORITY },
                    )
                log.info(
                    "[JiraSubtaskCreator] Created subtask '{}' under parent {} (key={})",
                    cleanedSummary,
                    parentKey,
                    created.key,
                )
                mapOf<String, Any?>(
                    "summary" to cleanedSummary,
                    "key" to created.key,
                    "id" to created.id,
                    "url" to created.url,
                    "issueType" to TicketCreationDefaults.SUBTASK_ISSUE_TYPE,
                )
            } catch (e: Exception) {
                log.warn(
                    "[JiraSubtaskCreator] Failed to create subtask '{}' under parent {}: {}",
                    cleanedSummary,
                    parentKey,
                    e.message,
                )
                throw SubtaskCreationException(cleanedSummary, parentKey, e)
            }
        }
    }

    fun ensureTrailingSeparator(description: String): String {
        if (description.isBlank()) return description
        val trimmed = description.trimEnd()
        return if (trimmed.endsWith("---")) trimmed else "$trimmed\n\n---"
    }
}
