
package com.stacklens.application.service

import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import java.time.format.DateTimeFormatter

@ApplicationService
class ReviewAnalysisPromptBuilder(
    private val promptLoader: PromptReaderPort,
    private val clock: ClockPort,
) {
    fun buildPrompt(
        lang: String,
        sprintInfo: SprintInfo,
        issues: List<SprintIssue>,
        analysisInstructions: String,
    ): String {
        val agentInput = buildAgentInput(lang, sprintInfo, issues)
        val dashboardInstruction = promptLoader.get(lang, "review.dashboardInstruction")
        val langDirective = promptLoader.get(lang, "review.dashboardLangDirective")
        return "$analysisInstructions\n\n---\n\n$agentInput\n\n---\n\n$dashboardInstruction\n\n$langDirective"
    }

    fun buildAgentInput(
        lang: String,
        sprintInfo: SprintInfo,
        issues: List<SprintIssue>,
    ): String {
        val p = promptLoader.context(lang)
        val now = clock.localDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        val ticketsBlock =
            issues.joinToString("\n") { t ->
                buildString {
                    appendLine("---")
                    appendLine("${p.get("review.agentFieldKey")}         ${t.key.ifBlank { p.get("review.agentFieldNoKey") }}")
                    appendLine("${p.get("review.agentFieldSummary")}     ${t.summary.ifBlank { p.get("review.agentFieldNoSummary") }}")
                    appendLine("${p.get("review.agentFieldType")}         ${t.issueType.ifBlank { p.get("review.agentFieldNoType") }}")
                    appendLine("${p.get("review.agentFieldStatus")}      ${t.status.ifBlank { p.get("review.agentFieldNoStatus") }}")
                    appendLine("${p.get("review.agentFieldPriority")}   ${t.priority ?: p.get("review.agentFieldNoPriority")}")
                    appendLine("${p.get("review.agentFieldAssignee")}    ${t.assignee ?: p.get("review.agentFieldNoAssignee")}")
                    appendLine("Created At: ${t.createdAt ?: "(unknown)"}")
                    appendLine("Started At: ${t.startedAt ?: "(unknown)"}")
                    appendLine("Resolved At: ${t.resolvedAt ?: "(unknown)"}")
                    appendLine("QA Loop Count: ${t.qaLoopCount}")
                    if (t.labels.isNotEmpty()) appendLine("${p.get("review.agentFieldLabels")}      ${t.labels.joinToString(", ")}")
                    if (t.description.isNotBlank()) appendLine("${p.get("review.agentFieldDescription")} ${t.description.take(300)}")
                }.trimEnd()
            }

        val sprintBlock =
            buildString {
                appendLine(p.get("review.agentSprintHeader"))
                appendLine("${p.get("review.agentSprintNameLabel")} ${sprintInfo.name}")
                appendLine("${p.get("review.agentSprintStatusLabel")} ${sprintInfo.state}")
                appendLine(
                    "${p.get("review.agentSprintPeriodLabel")} " +
                        "${sprintInfo.startDate ?: ""} ${p.get("review.agentSprintPeriodTo")} ${sprintInfo.endDate ?: ""}",
                )
                appendLine("${p.get("review.agentSprintGoalLabel")} ${sprintInfo.goal ?: p.get("review.agentSprintNoGoal")}")
            }.trimEnd()

        val withComments = issues.filter { it.issueType in listOf("Story", "Bug") && it.comments.isNotBlank() }
        val commentsBlock =
            if (withComments.isEmpty()) {
                p.get("review.agentNoComments")
            } else {
                withComments
                    .take(20)
                    .joinToString("\n\n") { t ->
                        val truncated = t.comments.take(1_500)
                        "--- ${t.key} – ${t.summary} ---\n$truncated"
                    }
            }

        return buildString {
            appendLine(p.get("review.agentInputHeader"))
            appendLine(p.get("review.agentFetchedAt", mapOf("date" to now)))
            appendLine(p.get("review.agentSource"))
            appendLine(p.get("review.agentTotalTickets", mapOf("total" to "${issues.size}", "loaded" to "${issues.size}")))
            appendLine()
            appendLine(sprintBlock)
            appendLine()
            appendLine(p.get("review.agentTicketsHeader"))
            appendLine(ticketsBlock)
            appendLine()
            appendLine(p.get("review.agentCommentsHeader"))
            append(commentsBlock.trimEnd())
        }
    }
}
