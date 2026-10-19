package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class SecurityAnalysisPromptBuilder(
    private val copilotPort: CopilotPort,
    private val promptLoader: PromptReaderPort,
) {
    fun buildIterationPrompt(
        iteration: Int,
        totalIterations: Int,
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
        previousFindingsJson: String,
    ): String {
        val lang = copilotPort.resolveLanguage()
        val promptKey =
            when (iteration) {
                1 -> "securityanalysis.iter1Prompt"
                2 -> "securityanalysis.iter2Prompt"
                else -> "securityanalysis.iter3Prompt"
            }
        val summaryHint =
            if (iteration == 1) {
                promptLoader.get(lang, "securityanalysis.summaryFormatHint")
            } else {
                ""
            }
        return promptLoader.get(
            lang,
            promptKey,
            mapOf(
                "projectName" to projectName,
                "projectPath" to projectPath,
                "projectTree" to projectTree.take(3000),
                "fileContents" to fileContents.take(28_000),
                "previousFindings" to previousFindingsJson.take(8_000),
                "summaryFormatHint" to summaryHint,
                "currentIteration" to iteration.toString(),
                "totalIterations" to totalIterations.toString(),
            ),
        )
    }

    fun buildFixPrompt(
        projectName: String,
        finding: SecurityFinding,
        fileContent: String,
        testContent: String,
        projectTree: String,
    ): String {
        val lang = copilotPort.resolveLanguage()
        return promptLoader.get(
            lang,
            "securityanalysis.fixPrompt",
            mapOf(
                "projectName" to projectName,
                "filePath" to finding.file,
                "findingId" to finding.id,
                "severity" to finding.severity,
                "cwe" to (finding.cwe ?: "N/A"),
                "findingType" to finding.type,
                "line" to (finding.line?.toString() ?: "unknown"),
                "description" to finding.description,
                "suggestion" to finding.suggestion,
                "suggestedFix" to (finding.suggestedFix ?: ""),
                "exploitDetails" to buildExploitDetails(finding),
                "projectTree" to projectTree,
                "fileContent" to fileContent,
                "testFileContent" to testContent.ifBlank { "(no test files found)" },
            ),
        )
    }

    private fun buildExploitDetails(finding: SecurityFinding): String {
        val sb = StringBuilder()
        if (!finding.exploitPayload.isNullOrBlank()) {
            sb.appendLine("Exploit payload: ${finding.exploitPayload}")
        }
        if (!finding.proofOfConcept.isNullOrBlank()) {
            sb.appendLine("Proof-of-Concept:\n${finding.proofOfConcept}")
        }
        if (!finding.attackSteps.isNullOrEmpty()) {
            sb.appendLine("Attack steps:")
            finding.attackSteps.forEach { sb.appendLine("  - $it") }
        }
        return sb.toString().ifBlank { "(no exploit details available)" }
    }
}
