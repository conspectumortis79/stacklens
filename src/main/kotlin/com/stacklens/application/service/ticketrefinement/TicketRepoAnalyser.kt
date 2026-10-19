package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.CodeHostingPlatform
import com.stacklens.domain.model.RepoAnalysis
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ApplicationService
class TicketRepoAnalyser(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val localRepoScan: LocalRepoScanPort,
    private val gitlab: GitlabPort,
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val i18n: I18nPort,
    private val jsonCodec: JsonCodecPort,
    private val fieldFormatter: TicketFieldFormatter,
    private val parser: TicketRefinementJsonParser,
) {
    private val log: Logger = LoggerFactory.getLogger(TicketRepoAnalyser::class.java)

    fun analyzeRepositories(
        parent: TicketRefinementTicket,
        child: TicketRefinementTicket,
        selections: List<RepoSelection>,
    ): List<RepoAnalysis> {
        val out = mutableListOf<RepoAnalysis>()
        for (sel in selections) {
            try {
                when (CodeHostingPlatform.fromWire(sel.type)) {
                    is CodeHostingPlatform.Gitlab -> out.addAll(analyzeGitlabRepo(parent, child, sel))
                    is CodeHostingPlatform.Github, is CodeHostingPlatform.Unknown ->
                        analyzeLocalRepo(parent, child, sel)?.let(out::add)
                }
            } catch (e: Exception) {
                log.warn("Repo analysis failed for {}:{} — {}", sel.type, sel.id, e.message)
                out.add(
                    RepoAnalysis(
                        repoRef = "${sel.type}:${sel.id}",
                        repoName = "${sel.type}:${sel.id}",
                        repoType = sel.type,
                        relevant = false,
                        summary = "",
                        state = "FAILED",
                        errorMessage = i18n.translateError(e),
                    ),
                )
            }
        }
        return out
    }

    fun parseRepoSelections(json: String?): List<RepoSelection> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            jsonCodec.decodeList(json, RepoSelection::class)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun parseRepoAnalysisResponse(
        raw: String,
        repoRef: String,
        repoName: String,
        repoType: String,
    ): RepoAnalysis {
        val jsonText = parser.extractJsonBlock(raw.trim())
        return try {
            val map: Map<String, Any?> = jsonCodec.decodeMap(jsonText)
            val relevant = (map["relevant"] as? Boolean) ?: false
            val summary = (map["summary"] as? String).orEmpty().trim()
            val changes =
                (map["suggestedChanges"] as? List<Any?>)
                    .orEmpty()
                    .mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            val deps =
                (map["dependencies"] as? List<Any?>)
                    .orEmpty()
                    .mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            val hiddenDeps =
                (map["hiddenDependencies"] as? List<Any?>)
                    .orEmpty()
                    .mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            val rawComplexity = (map["complexity"] as? String).orEmpty().trim().uppercase()
            val complexity = if (relevant && rawComplexity in setOf("S", "M", "X")) rawComplexity else ""
            RepoAnalysis(
                repoRef = repoRef,
                repoName = repoName,
                repoType = repoType,
                relevant = relevant,
                summary = summary,
                suggestedChanges = if (relevant) changes else emptyList(),
                dependencies = if (relevant) deps else emptyList(),
                hiddenDependencies = if (relevant) hiddenDeps else emptyList(),
                complexity = complexity,
                state = if (relevant) "OK" else "SKIPPED",
            )
        } catch (_: Exception) {
            RepoAnalysis(
                repoRef = repoRef,
                repoName = repoName,
                repoType = repoType,
                relevant = false,
                summary = raw.take(500),
                state = "FAILED",
                errorMessage = "AI response could not be parsed",
            )
        }
    }

    private fun analyzeLocalRepo(
        parent: TicketRefinementTicket,
        child: TicketRefinementTicket,
        sel: RepoSelection,
    ): RepoAnalysis? {
        val cfg = localRepoConfigRepo.findLocalRepoConfigById(sel.id) ?: return null
        val summary = localRepoScan.buildCodeSummary(cfg.path, maxFiles = 80, maxPerFileSizeKb = 2)
        return runRepoAnalysis(
            parent = parent,
            child = child,
            repoRef = "local:${cfg.id}",
            repoName = cfg.name,
            repoType = "local",
            projectTree = summary.projectTree,
            fileContents = summary.fileContents,
        )
    }

    private fun analyzeGitlabRepo(
        parent: TicketRefinementTicket,
        child: TicketRefinementTicket,
        sel: RepoSelection,
    ): List<RepoAnalysis> {
        val cfg = gitlabConfigRepo.findGitlabConfigById(sel.id) ?: return emptyList()
        val projectIds =
            sel.projectId?.let { listOf(it) }
                ?: cfg.projectIdList().ifEmpty {
                    runCatching { gitlab.listAccessibleProjects(cfg).take(5).map { it.id } }
                        .getOrElse { emptyList() }
                }
        if (projectIds.isEmpty()) return emptyList()
        val accessible = runCatching { gitlab.listAccessibleProjects(cfg) }.getOrElse { emptyList() }
        return projectIds.mapNotNull { pid ->
            try {
                val info = accessible.firstOrNull { it.id == pid }
                val name = info?.pathWithNamespace ?: "${cfg.name}#$pid"
                val files = gitlab.fetchProjectFiles(cfg, pid, maxFiles = 40, maxPerFileSizeBytes = 2048)
                val tree = files.joinToString("\n") { it.first }
                val contents =
                    files.joinToString("\n\n") { (path, content) ->
                        "── $path ──\n${content.take(2048)}"
                    }
                runRepoAnalysis(
                    parent = parent,
                    child = child,
                    repoRef = "gitlab:${cfg.id}:$pid",
                    repoName = name,
                    repoType = "gitlab",
                    projectTree = tree,
                    fileContents = contents,
                )
            } catch (e: Exception) {
                log.warn("GitLab project {} analysis failed: {}", pid, e.message)
                RepoAnalysis(
                    repoRef = "gitlab:${cfg.id}:$pid",
                    repoName = "${cfg.name}#$pid",
                    repoType = "gitlab",
                    relevant = false,
                    summary = "",
                    state = "FAILED",
                    errorMessage = i18n.translateError(e),
                )
            }
        }
    }

    private fun runRepoAnalysis(
        parent: TicketRefinementTicket,
        child: TicketRefinementTicket,
        repoRef: String,
        repoName: String,
        repoType: String,
        projectTree: String,
        fileContents: String,
    ): RepoAnalysis {
        val template =
            promptLoader.get(
                copilot.resolveLanguage(),
                "ticketrefinement.repo_analysis",
                mapOf("outputLanguage" to outputLanguage()),
            )
        val prompt =
            template
                .replace("{parentKey}", parent.key)
                .replace("{parentSummary}", parent.summary)
                .replace("{parentDescription}", parent.description)
                .replace("{ticketKey}", child.key)
                .replace("{ticketSummary}", child.summary)
                .replace("{ticketDescription}", child.description)
                .replace("{ticketLabels}", fieldFormatter.formatLabels(child))
                .replace("{ticketSubtasks}", fieldFormatter.formatSubtasks(child))
                .replace("{referencedTickets}", fieldFormatter.formatReferencedTickets(child))
                .replace("{repoName}", repoName)
                .replace("{repoType}", repoType)
                .replace("{projectTree}", projectTree.take(8000))
                .replace("{fileContents}", fileContents.take(20000))
        val raw = copilot.execute(copilot.resolveModel(), prompt)
        return parseRepoAnalysisResponse(raw, repoRef, repoName, repoType)
    }

    internal fun outputLanguage(): String =
        when (copilot.resolveLanguage()) {
            "de" -> "German"
            else -> "English"
        }
}
