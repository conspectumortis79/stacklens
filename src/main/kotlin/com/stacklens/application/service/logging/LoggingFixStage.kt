package com.stacklens.application.service.logging

import java.io.File

fun interface LoggingFixStage {
    fun execute(ctx: LoggingFixStageContext)
}

data class LoggingFixStageContext(
    val logText: String,
    val selectedCategories: List<String>,
    val repoPath: String?,
    val repoRoot: File?,
    val language: String,
    val diagnoseOutput: StringBuilder = StringBuilder(),
    val planOutput: StringBuilder = StringBuilder(),
    val codeOutput: StringBuilder = StringBuilder(),
    val testsOutput: StringBuilder = StringBuilder(),
    val proposedFiles: MutableList<ProposedFile> = mutableListOf(),
    val onPartial: (stage: String, chunk: String) -> Unit,
    var diagnosisIncomplete: Boolean = false,
    val isCancelled: () -> Boolean = { false },
)

data class ProposedFile(
    val path: String,
    val content: String,
    val description: String? = null,
    val isNew: Boolean = true,
)
