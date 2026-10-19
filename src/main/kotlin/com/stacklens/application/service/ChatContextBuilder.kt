
package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun

internal object ChatContextBuilder {
    private const val TICKET_LIMIT = 8_000
    private const val STAGE_LIMIT = 20_000

    fun build(run: AgentPipelineRun): String =
        buildString {
            val ticketContext =
                run.jiraTicketData?.takeIf { it.isNotBlank() }
                    ?: run.rawText.takeIf { it.isNotBlank() }
            if (ticketContext != null) {
                appendLine("## Original Requirement / Ticket")
                appendLine(ticketContext.take(TICKET_LIMIT))
                appendLine()
            }
            for (stage in run.stages) {
                if (stage.output.isBlank()) continue
                appendLine("## ${stage.name} Output")
                appendLine(stage.output.take(STAGE_LIMIT))
                appendLine()
            }
        }
}
