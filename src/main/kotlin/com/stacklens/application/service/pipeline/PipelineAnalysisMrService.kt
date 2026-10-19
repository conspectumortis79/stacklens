package com.stacklens.application.service.pipeline

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.analysis.GitlabConfigLookupPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.pipeline.PipelineMrSimulationPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.FindingSeverity
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.PipelineFinding
import com.stacklens.domain.pipeline.PipelineMrResult
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter

@ApplicationService
class PipelineAnalysisMrService(
    private val analysisRepo: com.stacklens.application.port.out.PipelineAnalysisPort,
    private val gitlabMrPort: GitlabMrPort,
    private val gitlabConfigPort: GitlabConfigPort,
    private val githubRepoConfigPort: GithubRepoConfigPort,
    private val githubBridge: GithubTicketBridgePort,
    private val gitlabRepoPort: GitlabConfigLookupPort,
    private val jsonCodec: JsonReaderPort,
    private val i18n: I18nPort,
    private val simulationPort: PipelineMrSimulationPort,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(PipelineAnalysisMrService::class.java)

    fun createMrOrPr(
        analysisId: Long,
        gitlabConfigId: Long? = null,
        gitlabProjectId: Long? = null,
    ): PipelineMrResult {
        val analysis =
            analysisRepo.findPipelineAnalysisById(analysisId)
                ?: throw IllegalArgumentException(i18n.t("pipeline.be_analysis_not_found", analysisId))

        val preferredGitlabConfigId = gitlabConfigId ?: analysis.gitlabConfigId
        val result =
            when {
                preferredGitlabConfigId != null -> createGitlabMr(analysisId, analysis, preferredGitlabConfigId, gitlabProjectId)
                analysis.githubRepoConfigId != null -> createGithubPr(analysisId, analysis)
                else -> simulationPort.simulatedResult(analysis, gitlabConfigPort.existsActiveGitlabConfig())
            }

        analysis.mrUrl = result.url
        analysis.mrBranch = result.branch
        analysis.mrProvider = result.provider
        analysisRepo.savePipelineAnalysis(analysis)
        return result
    }

    private fun createGitlabMr(
        analysisId: Long,
        analysis: PipelineAnalysis,
        overrideConfigId: Long,
        overrideProjectId: Long?,
    ): PipelineMrResult {
        val effectiveProjectId = overrideProjectId ?: analysis.gitlabProjectId
        if (effectiveProjectId == null) {
            throw IllegalArgumentException(i18n.t("pipeline.mr_select_hint"))
        }
        val config =
            gitlabRepoPort.findGitlabConfig(overrideConfigId)
                ?: throw IllegalArgumentException(i18n.t("pipeline.be_gitlab_config_not_found", overrideConfigId))

        val project = gitlabMrPort.fetchProjectDetail(config, effectiveProjectId)
        val defaultBranch = project.defaultBranch.ifBlank { "main" }
        val branch = "pipeline-analyse-$analysisId"

        val marker =
            """# StackLens Pipeline-Analyse #$analysisId

Diese Pipeline- / Compose-Analyse wurde mit der StackLens KI erstellt.
Der vollständige Bericht ist im MR-Text verlinkt (siehe Beschreibung).

**Repository:** ${project.pathWithNamespace.ifBlank { project.name }}
**Standard-Branch:** $defaultBranch
**Analyse-ID:** $analysisId
"""
        val markerAction =
            mapOf(
                "action" to "create",
                "file_path" to ".stacklens/pipeline-analysis-$analysisId.md",
                "content" to marker,
                "encoding" to "text",
            )
        try {
            gitlabMrPort.commitFiles(
                config,
                effectiveProjectId,
                branch,
                "pipeline-analysis: report for #$analysisId",
                listOf(markerAction),
            )
        } catch (e: Exception) {
            log.warn("[PipelineMrService] Marker commit failed ({}): {}", analysisId, e.message)
            throw e
        }

        val mrTitle = "Pipeline-Analyse: Befunde & Optimierungen (Analysis #$analysisId)"
        val mrDescription = buildMrDescription(analysis)
        val mrUrl =
            gitlabMrPort.createMergeRequest(config, effectiveProjectId, branch, defaultBranch, mrTitle, mrDescription)

        return PipelineMrResult(
            provider = "GITLAB",
            url = mrUrl,
            kind = "merge_request",
            branch = branch,
        )
    }

    private fun createGithubPr(
        analysisId: Long,
        analysis: PipelineAnalysis,
    ): PipelineMrResult {
        val configId =
            analysis.githubRepoConfigId
                ?: throw IllegalArgumentException(i18n.t("pipeline.be_no_github_token"))
        val config =
            githubRepoConfigPort.findGithubRepoConfigById(configId)
                ?: throw IllegalArgumentException(i18n.t("pipeline.be_github_config_not_found", configId))

        val now = clock.localDateTime()
        val branchName =
            "pipeline-analysis-" +
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_" +
                now.format(DateTimeFormatter.ofPattern("HH-mm-ss"))
        val marker =
            """# StackLens Pipeline-Analyse #${analysis.id}

Diese Pipeline- / Compose-Analyse wurde mit der StackLens KI erstellt.
Der vollständige Bericht ist in der PR-Beschreibung verlinkt.

**Repository:** ${config.owner}/${config.repo}
**Analyse-ID:** ${analysis.id}
"""
        val ticket =
            JiraTicketData(
                key = "PIPELINE-${analysis.id}",
                summary = "Pipeline-Analyse #$analysisId",
                description = "KI-gestützte Pipeline- / Compose-Analyse für ${config.owner}/${config.repo}",
                comments = "",
                issueType = "Task",
                status = "Open",
                priority = "Medium",
                labels = listOf("stacklens", "pipeline-analysis"),
                components = emptyList(),
                url = "",
            )
        val codeImpl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("${config.owner}/${config.repo}"),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "${config.owner}/${config.repo}",
                            path = ".stacklens/pipeline-analysis-${analysis.id}.md",
                            language = "markdown",
                            description = "StackLens Pipeline-Analyse Marker",
                            changeType = com.stacklens.domain.enums.FileChangeType.CREATE,
                            code = marker,
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "StackLens Pipeline-Analyse #$analysisId",
            )
        val prUrl = githubBridge.createPullRequest(config, ticket, codeImpl)
        return PipelineMrResult(
            provider = "GITHUB",
            url = prUrl,
            kind = "pull_request",
            branch = branchName,
        )
    }

    fun buildMrDescription(analysis: PipelineAnalysis): String =
        buildString {
            appendLine("## Pipeline-Analyse – Befunde & Optimierungen")
            appendLine()
            appendLine("*Automatisch generiert von StackLens Pipeline-Analyse*")
            appendLine()

            val summary = analysis.summaryText
            if (!summary.isNullOrBlank()) {
                appendLine("### Zusammenfassung")
                appendLine(summary.trim())
                appendLine()
            }

            val findings: List<PipelineFinding> =
                analysis.findingsJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        try {
                            jsonCodec.decodeList(it, PipelineFinding::class)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()
            if (findings.isNotEmpty()) {
                appendLine("### Befunde (${findings.size})")
                appendLine()
                findings.forEach { f ->
                    val badge = FindingSeverity.badgeFor(f.severity)
                    appendLine("**$badge ${f.type.ifBlank { "?" }}** – `${f.file}`${f.line?.let { " (Zeile $it)" } ?: ""}")
                    appendLine(f.description)
                    if (f.suggestion.isNotBlank()) appendLine("*Vorschlag:* ${f.suggestion}")
                    appendLine()
                }
            }

            val opts: List<Map<String, Any?>> =
                analysis.optimizationsJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            jsonCodec.decodeList(it, Map::class) as List<Map<String, Any?>>
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()
            if (opts.isNotEmpty()) {
                appendLine("### Optimierungsvorschläge (${opts.size})")
                appendLine()
                opts.forEach { o ->
                    val title = o["title"]?.toString().orEmpty()
                    val rationale = o["rationale"]?.toString().orEmpty()
                    if (title.isNotBlank()) appendLine("- **$title**")
                    if (rationale.isNotBlank()) appendLine("  - $rationale")
                }
                appendLine()
            }

            appendLine("---")
            appendLine("*Generated by StackLens Pipeline-Analyse*")
        }
}
