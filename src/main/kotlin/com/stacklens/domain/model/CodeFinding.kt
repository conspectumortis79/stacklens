package com.stacklens.domain.model

data class CodeFinding(
    val id: String = "",
    val file: String = "",
    val type: String = "",
    val severity: String = "MEDIUM",
    val line: Int? = null,
    val snippet: String? = null,
    val description: String = "",
    val suggestion: String = "",
    val suggestedCode: String? = null,
)
