package com.stacklens.adapter.out.demo

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.domain.model.JiraConfig

object DemoJiraSimulation {
    const val DEMO_MARKER: String = "Demo"

    const val SIMULATED_KEY_BASE: Int = 9000

    fun isDemo(config: JiraConfig): Boolean = config.name?.contains(DEMO_MARKER, ignoreCase = true) == true

    fun simulatedSubtasks(
        projectKey: String,
        parentNumber: Int,
        subtasks: List<SubtaskDraft>,
    ): List<Map<String, Any?>> =
        subtasks.mapIndexed { idx, draft ->
            mapOf<String, Any?>(
                "summary" to draft.summary,
                "key" to "${projectKey.uppercase()}-${parentNumber + idx + 1}",
                "id" to "DEMO-${idx + 1}",
                "url" to "",
                "issueType" to "Subtask",
            )
        }
}
