package com.stacklens.application.service.codeanalysis

import com.stacklens.domain.model.CodeAnalysis

interface CodeAnalysisAi {
    fun buildAnalysisPrompt(
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ): String

    fun applyAiOutput(
        analysis: CodeAnalysis,
        output: String,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    )
}
