package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/review-analyse")
open class ReviewAnalysisController(
    private val analysisService: ReviewAnalysisUseCase,
    private val i18n: I18nUseCase,
    private val saveHandlers: List<ReviewSaveHandler>,
    private val decoding: ReviewAnalysisDecodingUseCase,
    private val workspaceFiles: WorkspaceFileUseCase,
) {
    private val dtoMapper = ReviewAnalysisDtoMapper(decoding)

    private val documentExtensions = setOf("md", "html", "pptx", "ppt")

    internal fun browseFileEntries(
        entries: List<WorkspaceDirectoryEntry>,
        directoriesOnly: Boolean,
    ): List<Map<String, Any?>> =
        entries
            .filter {
                it.isDirectory ||
                    (!directoriesOnly && it.isFile && it.name.substringAfterLast('.', "").lowercase() in documentExtensions)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map {
                mapOf(
                    "name" to it.name,
                    "path" to it.path,
                    "type" to if (it.isDirectory) "directory" else "file",
                )
            }

    @PostMapping
    fun start(
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        val url = body["jiraSprintUrl"]?.trim().orEmpty()
        if (url.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_sprint_url_empty")))
        }
        val configId =
            body["jiraConfigId"]?.trim()?.toLongOrNull()
                ?: analysisService.firstActiveJiraConfigId()
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_invalid_jira_config_id")))

        if (!analysisService.jiraConfigExists(configId)) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_jira_config_not_found")))
        }

        val instructions = body["analysisInstructions"]?.trim().orEmpty()
        if (instructions.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_instructions_empty")))
        }

        val analysis = analysisService.startAnalysis(url, configId, instructions)
        return ResponseEntity.ok(mapOf("id" to analysis.id, "status" to analysis.status))
    }

    @GetMapping("/files/browse")
    fun browseFiles(
        @RequestParam(required = false) path: String?,
        @RequestParam(required = false, defaultValue = "true") directoriesOnly: Boolean,
    ): ResponseEntity<Map<String, Any?>> {
        val targetPath: String = if (path.isNullOrBlank()) workspaceFiles.homeDirectory() else path
        val listing =
            workspaceFiles.browseDirectory(targetPath)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_dir_not_found", targetPath)))
        return ResponseEntity.ok(
            mapOf(
                "currentPath" to listing.path,
                "parentPath" to listing.parentPath,
                "items" to browseFileEntries(listing.entries, directoriesOnly),
            ),
        )
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun startMultipart(
        @RequestPart("jiraSprintUrl") jiraSprintUrl: String,
        @RequestPart("jiraConfigId") jiraConfigId: String,
        @RequestPart("analysisInstructions") analysisInstructions: String,
        @RequestPart(value = "pptxFile", required = false) pptxFile: MultipartFile?,
        @RequestPart(value = "pptxServerPath", required = false) pptxServerPath: String?,
    ): ResponseEntity<Map<String, Any>> {
        val url = jiraSprintUrl.trim()
        if (url.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_sprint_url_empty")))
        }
        val configId =
            jiraConfigId.trim().toLongOrNull()
                ?: analysisService.firstActiveJiraConfigId()
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_invalid_jira_config_id")))
        if (!analysisService.jiraConfigExists(configId)) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_jira_config_not_found")))
        }
        val instructions = analysisInstructions.trim()
        if (instructions.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_instructions_empty")))
        }
        if (pptxFile != null && !pptxServerPath.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_pptx_ambiguous")))
        }
        val analysis =
            when {
                pptxFile != null && !pptxFile.isEmpty ->
                    analysisService.startAnalysisWithPptx(
                        url,
                        configId,
                        instructions,
                        pptxFile.originalFilename ?: "upload.pptx",
                        pptxFile.bytes,
                    )
                !pptxServerPath.isNullOrBlank() ->
                    analysisService.startAnalysisWithPptxPath(
                        url,
                        configId,
                        instructions,
                        pptxServerPath,
                    )
                else -> analysisService.startAnalysis(url, configId, instructions)
            }
        return ResponseEntity.ok(mapOf("id" to analysis.id, "status" to analysis.status))
    }

    @PostMapping("/{id}/save-to-path")
    fun saveToPath(
        @PathVariable id: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any?>> {
        val format =
            body["format"]?.lowercase()
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_invalid_format")))
        val handler =
            saveHandlers.firstOrNull { it.format == format }
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_invalid_format")))
        val dirPath =
            body["path"]
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_no_dir_path")))
        val analysis =
            analysisService.getAnalysis(id)
                ?: return ResponseEntity.notFound().build()

        val dirInfo =
            workspaceFiles.fileInfo(dirPath)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_dir_not_found", dirPath)))
        if (!dirInfo.isDirectory) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("review_analysis.be_dir_not_found", dirInfo.path)))
        }

        val sprintName = decoding.sprintName(analysis.sprintInfoJson)
        val safeSuffix =
            sprintName
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                ?.take(60)

        val ctx = ReviewSaveContext(id, analysis, dirInfo.path, sprintName, safeSuffix)
        return handler.save(ctx)
    }

    @GetMapping("/{id}/download/md")
    fun downloadMarkdown(
        @PathVariable id: Long,
        response: jakarta.servlet.http.HttpServletResponse,
    ) {
        val analysis = analysisService.getAnalysis(id)
        val rawMd = analysis?.resultMarkdown
        if (rawMd.isNullOrBlank()) {
            response.sendError(404, i18n.t("review_analysis.be_no_result"))
            return
        }
        val md = stripHtmlBlock(rawMd)
        val sprintName = decoding.sprintName(analysis.sprintInfoJson)
        val safeName =
            sprintName
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                ?.take(60)
                ?.let { "Sprint-Analyse-$it.md" } ?: "Sprint-Analyse.md"
        response.contentType = "text/plain;charset=UTF-8"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$safeName\"")
        response.writer.write(md)
    }

    @GetMapping("/{id}/download/html")
    fun downloadHtml(
        @PathVariable id: Long,
        response: jakarta.servlet.http.HttpServletResponse,
    ) {
        val analysis = analysisService.getAnalysis(id)
        val pptxHtml = analysis?.resultHtmlPath
        if (!pptxHtml.isNullOrBlank()) {
            val pptxContent = workspaceFiles.readText(pptxHtml)
            if (pptxContent != null) {
                response.contentType = "text/html;charset=UTF-8"
                val sprintName = decoding.sprintName(analysis.sprintInfoJson)
                val safeName =
                    sprintName
                        ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        ?.take(60)
                        ?.let { "PPTX-Analyse-$it.html" } ?: "PPTX-Analyse.html"
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$safeName\"")
                response.writer.write(pptxContent)
                return
            }
        }
        val md = analysis?.resultMarkdown
        if (md.isNullOrBlank()) {
            response.sendError(404, i18n.t("review_analysis.be_no_result"))
            return
        }
        val sprintName = decoding.sprintName(analysis.sprintInfoJson)
        val safeName =
            sprintName
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                ?.take(60)
                ?.let { "Sprint-Analyse-$it.html" } ?: "Sprint-Analyse.html"
        val textContent = stripHtmlBlock(md)
        val htmlBlock = extractHtmlBlock(md)
        val html: String =
            when {
                htmlBlock != null && textContent.isBlank() -> htmlBlock
                else -> buildHtmlPage(sprintName ?: "Sprint-Analyse", textContent)
            }
        response.contentType = "text/html;charset=UTF-8"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$safeName\"")
        response.writer.write(html)
    }

    @GetMapping
    fun list(): List<Map<String, Any?>> = analysisService.getAllAnalyses().map { dtoMapper.toSummaryMap(it) }

    @GetMapping("/{id}/status")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to analysis.id,
                "status" to analysis.status,
                "currentStep" to analysis.currentStep,
                "errorMessage" to analysis.errorMessage,
                "completedAt" to analysis.completedAt?.toString(),
            ),
        )
    }

    @GetMapping("/{id}")
    fun result(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dtoMapper.toDetailMap(analysis))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (analysisService.getAnalysis(id) == null) return ResponseEntity.notFound().build()
        analysisService.deleteAnalysis(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/history")
    fun deleteAll(): ResponseEntity<Map<String, Any>> {
        analysisService.deleteAllAnalyses()
        return ResponseEntity.ok(mapOf("deleted" to true))
    }
}
