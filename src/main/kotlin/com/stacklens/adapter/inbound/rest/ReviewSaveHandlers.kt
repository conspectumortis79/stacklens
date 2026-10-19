
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.ReviewAnalysis
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

data class ReviewSaveContext(
    val id: Long,
    val analysis: ReviewAnalysis,
    val dirPath: String,
    val sprintName: String?,
    val safeSuffix: String?,
)

interface ReviewSaveHandler {
    val format: String

    fun save(ctx: ReviewSaveContext): ResponseEntity<Map<String, Any?>>
}

@Component
class MarkdownReviewSaveHandler(
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) : ReviewSaveHandler {
    override val format = "md"

    override fun save(ctx: ReviewSaveContext): ResponseEntity<Map<String, Any?>> {
        val rawMd = ctx.analysis.resultMarkdown
        if (rawMd.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_no_result")))
        }
        val md = stripHtmlBlock(rawMd)
        val filename = ctx.safeSuffix?.let { "Sprint-Review-$it.md" } ?: "Sprint-Review-Analyse.md"
        val savedPath = workspaceFiles.saveOrThrow(TextFileSaveRequest(ctx.dirPath, filename, md))
        return ResponseEntity.ok(mapOf("savedPath" to savedPath))
    }
}

@Component
class HtmlReviewSaveHandler(
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) : ReviewSaveHandler {
    override val format = "html"

    override fun save(ctx: ReviewSaveContext): ResponseEntity<Map<String, Any?>> {
        val md = ctx.analysis.resultMarkdown
        if (md.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_no_result")))
        }
        val filename = ctx.safeSuffix?.let { "Sprint-Review-$it.html" } ?: "Sprint-Review-Analyse.html"
        val textContent = stripHtmlBlock(md)
        val htmlBlock = extractHtmlBlock(md)
        val fileContent: String =
            when {
                htmlBlock != null && textContent.isBlank() -> htmlBlock
                else -> buildHtmlPage(ctx.sprintName ?: "Sprint-Review-Analyse", textContent)
            }
        val savedPath = workspaceFiles.saveOrThrow(TextFileSaveRequest(ctx.dirPath, filename, fileContent))
        return ResponseEntity.ok(mapOf("savedPath" to savedPath))
    }
}

@Component
class DashboardReviewSaveHandler(
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) : ReviewSaveHandler {
    override val format = "dashboard"

    override fun save(ctx: ReviewSaveContext): ResponseEntity<Map<String, Any?>> {
        val md = ctx.analysis.resultMarkdown
        if (md.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_no_result")))
        }
        val html =
            extractHtmlBlock(md)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.dashboard_not_found")))
        val filename = ctx.safeSuffix?.let { "Sprint-Dashboard-$it.html" } ?: "Sprint-Dashboard.html"
        val savedPath = workspaceFiles.saveOrThrow(TextFileSaveRequest(ctx.dirPath, filename, html))
        return ResponseEntity.ok(mapOf("savedPath" to savedPath))
    }
}

private fun WorkspaceFileUseCase.saveOrThrow(request: TextFileSaveRequest): String =
    when (val result = saveText(request)) {
        is WorkspaceWriteResult.Saved -> result.path
        is WorkspaceWriteResult.Rejected -> throw RuntimeException("workspace write rejected: ${result.reason}")
    }
