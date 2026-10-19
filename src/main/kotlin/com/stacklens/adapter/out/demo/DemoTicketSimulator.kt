package com.stacklens.adapter.out.demo

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.TicketCreationRecord
import org.slf4j.LoggerFactory

class DemoTicketSimulator(
    private val recordPort: TicketCreationRecordPort,
    private val random: () -> Int = { (1000..9999).random() },
) : TicketCreationDemoPort {
    private val log = LoggerFactory.getLogger(DemoTicketSimulator::class.java)

    override fun isDemo(config: JiraConfig): Boolean = DemoJiraSimulation.isDemo(config)

    override fun simulatedSubtasks(
        projectKey: String,
        subtasks: List<SubtaskDraft>,
    ): List<Map<String, Any?>> = DemoJiraSimulation.simulatedSubtasks(projectKey, DemoJiraSimulation.SIMULATED_KEY_BASE, subtasks)

    override fun simulate(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
        labels: List<String>,
        historyId: Long?,
        configName: String,
        subtasks: List<SubtaskDraft>,
    ): Map<String, Any?> {
        val ticketNumber = if (historyId != null) (historyId % 9000 + 1000).toInt() else random()
        val fakeKey = "${projectKey.uppercase()}-$ticketNumber"
        val fakeUrl = if (historyId != null) "/simulated-jira-ticket/$historyId" else ""
        if (historyId != null) {
            val existing = recordPort.findTicketCreationRecordById(historyId)
            if (existing != null) {
                recordPort.saveTicketCreationRecord(
                    TicketCreationRecord(
                        id = existing.id,
                        inputText = existing.inputText,
                        summary = existing.summary,
                        issueType = issueType,
                        labels = existing.labels,
                        resultJson = existing.resultJson,
                        status = existing.status,
                        errorMessage = existing.errorMessage,
                        jiraConfigId = jiraConfigId,
                        jiraProjectKey = projectKey,
                        jiraKey = fakeKey,
                        jiraUrl = fakeUrl,
                        createdAt = existing.createdAt,
                    ),
                )
            }
        }
        log.info("[TicketCreation] Demo mode – simulated ticket {} for config '{}'", fakeKey, configName)
        val fakeSubtasks = DemoJiraSimulation.simulatedSubtasks(projectKey, ticketNumber, subtasks)
        return buildMap<String, Any?> {
            put("key", fakeKey)
            put("id", "DEMO")
            put("url", fakeUrl)
            put("subtasks", fakeSubtasks)
        }
    }
}
