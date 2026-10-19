package com.stacklens.application.port.model

data class SubtaskDraft(
    val summary: String,
    val description: String,
    val issueType: String = "Subtask",
    val priority: String = "Minor",
    val labels: List<String> = emptyList(),
)
