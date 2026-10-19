package com.stacklens.application.port.out

import com.stacklens.application.service.codeanalysis.CodeAnalysisAi
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Test

class CodeAnalysisAiPortTest {
    private val port =
        object : CodeAnalysisAi {
            var buildCalled = false
            var applyCalled = false
            var lastIncludeMermaid = false
            var lastIncludeDrawio = false

            override fun buildAnalysisPrompt(
                projectName: String,
                projectPath: String,
                projectTree: String,
                fileContents: String,
                includeMermaid: Boolean,
                includeDrawio: Boolean,
            ): String {
                buildCalled = true
                lastIncludeMermaid = includeMermaid
                lastIncludeDrawio = includeDrawio
                return "PROMPT($projectName,$projectPath,$projectTree,$fileContents,$includeMermaid,$includeDrawio)"
            }

            override fun applyAiOutput(
                analysis: CodeAnalysis,
                output: String,
                includeMermaid: Boolean,
                includeDrawio: Boolean,
            ) {
                applyCalled = true
                lastIncludeMermaid = includeMermaid
                lastIncludeDrawio = includeDrawio
            }
        }

    @Test
    fun `buildAnalysisPrompt with default booleans uses false for both`() {
        val prompt = port.buildAnalysisPrompt("p", "pp", "tree", "content")
        assert(prompt.startsWith("PROMPT("))
        assert(!port.lastIncludeMermaid)
        assert(!port.lastIncludeDrawio)
    }

    @Test
    fun `buildAnalysisPrompt with explicit booleans forwards them`() {
        port.buildAnalysisPrompt("p", "pp", "tree", "content", includeMermaid = true, includeDrawio = true)
        assert(port.lastIncludeMermaid)
        assert(port.lastIncludeDrawio)
    }

    @Test
    fun `applyAiOutput with default booleans uses false for both`() {
        port.applyAiOutput(CodeAnalysis(), "raw-output")
        assert(port.applyCalled)
        assert(!port.lastIncludeMermaid)
        assert(!port.lastIncludeDrawio)
    }

    @Test
    fun `applyAiOutput with explicit booleans forwards them`() {
        port.applyAiOutput(CodeAnalysis(), "raw-output", includeMermaid = true, includeDrawio = true)
        assert(port.lastIncludeMermaid)
        assert(port.lastIncludeDrawio)
    }
}
