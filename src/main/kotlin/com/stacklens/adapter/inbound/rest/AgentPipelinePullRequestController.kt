package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.pipeline.PullRequestFileDiff
import com.stacklens.domain.pipeline.PullRequestFileStatus
import com.stacklens.domain.text.AgentTextUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@RestController
@RequestMapping("/api/agent-pipeline")
class AgentPipelinePullRequestController(
    private val pipelineService: CopilotAgentPipelineUseCase,
    private val i18n: I18nUseCase,
    private val templateEngine: TemplateEngine,
) {
    companion object {
        fun parseAgentPaths(output: String): List<String> = AgentTextUtils.parseAgentOutput(output).map { it.path }
    }

    @GetMapping("/{id}/pull-request", produces = [MediaType.TEXT_HTML_VALUE])
    fun pullRequestView(
        @PathVariable id: Long,
    ): ResponseEntity<String> {
        val prData =
            pipelineService.buildPullRequestData(id) ?: return ResponseEntity
                .status(404)
                .contentType(MediaType.TEXT_HTML)
                .body(htmlError("Pipeline #$id not found."))

        val diffs = prData.diffs
        val added = diffs.count { it.status == PullRequestFileStatus.ADDED }
        val modified = diffs.count { it.status == PullRequestFileStatus.MODIFIED }
        val deleted = diffs.count { it.status == PullRequestFileStatus.DELETED }
        val gitAvailable = prData.baseBranch.isNotBlank()

        val title = "Pipeline #${prData.runId}: ${prData.featureSlug}"
        return ResponseEntity
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .body(
                renderPage(
                    title = title,
                    branch = prData.gitBranch,
                    baseBranch = prData.baseBranch.ifBlank { null },
                    repoPath = prData.repoPath,
                    gitAvailable = gitAvailable,
                    added = added,
                    modified = modified,
                    deleted = deleted,
                    diffs = diffs,
                ),
            )
    }

    private fun renderPage(
        title: String,
        branch: String,
        baseBranch: String?,
        repoPath: String?,
        gitAvailable: Boolean,
        added: Int,
        modified: Int,
        deleted: Int,
        diffs: List<PullRequestFileDiff>,
    ): String {
        val noChangesMsg =
            when {
                diffs.isNotEmpty() -> ""
                gitAvailable -> i18n.t("ticket_implementation.be_no_changes_base_branch")
                else -> i18n.t("ticket_implementation.be_no_git_repo_diff")
            }
        val ctx = Context()
        ctx.setVariable("title", title)
        ctx.setVariable("branch", branch)
        ctx.setVariable("baseBranch", baseBranch)
        ctx.setVariable("repoPath", repoPath?.ifBlank { null })
        ctx.setVariable("baseLabel", baseBranch ?: "-")
        ctx.setVariable("repoPathLabel", repoPath?.ifBlank { null } ?: "-")
        ctx.setVariable("gitAvailable", gitAvailable)
        ctx.setVariable("added", added)
        ctx.setVariable("modified", modified)
        ctx.setVariable("deleted", deleted)
        ctx.setVariable("diffs", diffs.map { toFileDiffView(it) })
        ctx.setVariable("noChangesMsg", noChangesMsg)
        return templateEngine.process("pull-request", ctx)
    }

    private fun toFileDiffView(d: PullRequestFileDiff): FileDiffView {
        val (pillClass, pillLabelShort, pillLabelLong) =
            when (d.status) {
                PullRequestFileStatus.ADDED -> Triple("pill-add", "new", "New")
                PullRequestFileStatus.MODIFIED -> Triple("pill-mod", "~", "Modified")
                PullRequestFileStatus.DELETED -> Triple("pill-del", "−", "Deleted")
                PullRequestFileStatus.UNCHANGED -> Triple("pill-unchanged", "=", "Unchanged")
            }
        var oldLn = 0
        var newLn = 0
        val lines =
            d.lines.map { line ->
                when (line.sign) {
                    "+" -> DiffLineView(hunk = false, rowClass = "line-add", lineNo = (++newLn).toString(), sign = "+", text = line.text)
                    "-" -> DiffLineView(hunk = false, rowClass = "line-del", lineNo = (++oldLn).toString(), sign = "−", text = line.text)
                    "~" -> DiffLineView(hunk = true, rowClass = "line-hunk", lineNo = "", sign = "", text = line.text)
                    else -> {
                        ++oldLn
                        DiffLineView(
                            hunk = false,
                            rowClass = "line-ctx",
                            lineNo = (++newLn).toString(),
                            sign = " ",
                            text = line.text,
                        )
                    }
                }
            }
        return FileDiffView(
            path = d.path,
            pillClass = pillClass,
            pillLabelShort = pillLabelShort,
            pillLabelLong = pillLabelLong,
            additions = d.additions,
            deletions = d.deletions,
            unchanged = d.status == PullRequestFileStatus.UNCHANGED || d.lines.isEmpty(),
            lines = lines,
        )
    }

    private fun htmlError(msg: String): String {
        val ctx = Context()
        ctx.setVariable("message", msg)
        return templateEngine.process("pull-request-error", ctx)
    }
}

private data class FileDiffView(
    val path: String,
    val pillClass: String,
    val pillLabelShort: String,
    val pillLabelLong: String,
    val additions: Int,
    val deletions: Int,
    val unchanged: Boolean,
    val lines: List<DiffLineView>,
)

private data class DiffLineView(
    val hunk: Boolean,
    val rowClass: String,
    val lineNo: String,
    val sign: String,
    val text: String,
)
