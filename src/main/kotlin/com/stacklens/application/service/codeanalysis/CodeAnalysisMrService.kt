package com.stacklens.application.service.codeanalysis

import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.analysis.GitlabConfigLookupPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.CodeFileChange
import com.stacklens.domain.model.CodeFinding
import com.stacklens.domain.model.FindingSeverity

@ApplicationService
class CodeAnalysisMrService(
    private val analysisRepo: CodeAnalysisPort,
    private val codeRepoPort: GitlabConfigLookupPort,
    private val gitlabMrPort: GitlabMrPort,
    private val jsonCodec: JsonReaderPort,
    private val i18n: I18nPort,
) {
    fun createMr(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
    ): String {
        val analysis =
            analysisRepo.findCodeAnalysisById(analysisId)
                ?: throw IllegalArgumentException(i18n.t("code_analysis.be_analysis_not_found", analysisId))

        val config =
            codeRepoPort.findGitlabConfig(gitlabConfigId)
                ?: throw IllegalArgumentException(i18n.t("code_analysis.be_gitlab_config_not_found", gitlabConfigId))

        val project = gitlabMrPort.fetchProjectDetail(config, projectId)
        val defaultBranch = project.defaultBranch.ifBlank { "main" }
        val branch = "code-analyse-$analysisId"

        val fileChanges: List<CodeFileChange> =
            analysis.fileChangesJson
                ?.takeIf { it.isNotBlank() }
                ?.let { json ->
                    try {
                        jsonCodec.decodeList(json, CodeFileChange::class)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } ?: emptyList()

        gitlabMrPort.createBranch(config, projectId, branch, defaultBranch)

        if (fileChanges.isNotEmpty()) {
            val actions =
                fileChanges.map { fc ->
                    mapOf(
                        "action" to if (fc.changeType == com.stacklens.domain.enums.FileChangeType.CREATE) "create" else "update",
                        "file_path" to fc.path,
                        "content" to fc.code,
                        "encoding" to "text",
                    )
                }
            gitlabMrPort.commitFiles(config, projectId, branch, "code-analysis: AI-assisted refactoring suggestions", actions)
        }

        val mrTitle = "Code Analysis: Refactoring Suggestions (Analysis #$analysisId)"
        val mrDescription = buildMrDescription(analysis, project.webUrl, branch)
        val mrUrl = gitlabMrPort.createMergeRequest(config, projectId, branch, defaultBranch, mrTitle, mrDescription)

        analysis.mrUrl = mrUrl
        analysis.mrBranch = branch
        analysisRepo.saveCodeAnalysis(analysis)

        return mrUrl
    }

    fun buildMrDescription(
        analysis: CodeAnalysis,
        webUrl: String = "",
        branch: String = "",
    ): String =
        buildString {
            appendLine("## Code Analysis – Refactoring Suggestions")
            appendLine()
            if (!analysis.summaryText.isNullOrBlank()) {
                appendLine("### Zusammenfassung")
                appendLine(analysis.summaryText)
                appendLine()
            }
            val findings: List<CodeFinding> =
                analysis.findingsJson?.let {
                    try {
                        jsonCodec.decodeList(it, CodeFinding::class)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } ?: emptyList()

            if (findings.isNotEmpty()) {
                appendLine("### Befunde (${findings.size})")
                appendLine()
                findings.forEach { f ->
                    val badge = FindingSeverity.badgeFor(f.severity)
                    val fileRef = renderFindingFileReference(f.file, f.line, webUrl, branch)
                    appendLine("**$badge ${f.type}** – $fileRef")
                    appendLine(f.description)
                    if (f.suggestion.isNotBlank()) appendLine("*Vorschlag:* ${f.suggestion}")
                    appendLine()
                }
            }
            appendLine("---")
            appendLine("*Automatisch generiert von StackLens Code-Analyse*")
        }

    internal fun renderFindingFileReference(
        filePath: String,
        line: Int?,
        webUrl: String,
        branch: String,
    ): String {
        if (filePath.isBlank()) return ""
        val label =
            if (line != null) "$filePath (Zeile $line)" else filePath
        if (webUrl.isBlank() || branch.isBlank()) {
            return label
        }
        val anchor = if (line != null) "#L$line" else ""
        val url = "${webUrl.trimEnd('/')}/-/blob/$branch/$filePath$anchor"
        return "[$label]($url)"
    }
}
