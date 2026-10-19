package com.stacklens.application.service

import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.PipelineAnalysis
import org.slf4j.LoggerFactory

@ApplicationService
class PipelineAnalysisAiService(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
) {
    private val log = LoggerFactory.getLogger(PipelineAnalysisAiService::class.java)

    fun buildAnalysisPrompt(
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
        includeMermaid: Boolean,
        userPrompt: String?,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        val ctx = promptLoader.context(lang)
        val summaryFormatHint = ctx.get("pipeline.summaryFormatHint")
        val mermaidSection = if (includeMermaid) ctx.get("pipeline.mermaidSection") else ""
        val userPromptSection =
            if (!userPrompt.isNullOrBlank()) {
                ctx.get("pipeline.userPromptSection", mapOf("userPrompt" to userPrompt.trim()))
            } else {
                ""
            }
        return ctx.get(
            "pipeline.analysisPrompt",
            mapOf(
                "projectName" to projectName,
                "projectPath" to projectPath,
                "projectTree" to projectTree.take(3000),
                "fileContents" to fileContents.take(40_000),
                "summaryFormatHint" to summaryFormatHint,
                "mermaidSection" to mermaidSection,
                "userPromptSection" to userPromptSection,
            ),
        )
    }

    fun loadRepoData(repoDataLoader: () -> com.stacklens.application.port.out.pipeline.PipelineRepoData?): com.stacklens.application.port.out.pipeline.PipelineRepoData? {
        val data = repoDataLoader() ?: return null
        if (data.tree.isBlank() || data.contents.isBlank()) return null
        return data
    }

    fun applyAiOutput(
        analysis: PipelineAnalysis,
        output: String,
        includeMermaid: Boolean,
    ) {
        analysis.summaryText = extractSummary(output)
        analysis.findingsJson = extractFirstJsonArray(output, "FINDINGS:", "FILE_DESCRIPTIONS:")
            ?: extractFirstJsonArray(output, "Findings:", "File Descriptions:")
        analysis.fileDescriptionsJson = extractFirstJsonArray(output, "FILE_DESCRIPTIONS:", "OPTIMIZATIONS:")
            ?: extractFirstJsonArray(output, "File Descriptions:", "Optimizations:")
        analysis.optimizationsJson = extractJsonBlockAfter(output, "OPTIMIZATIONS:")
            ?: extractJsonBlockAfter(output, "Optimizations:")
        if (includeMermaid) analysis.mermaid = MermaidSupport.extract(output)
        analysis.errorMessage = null
        log.debug(
            "[PipelineAnalysis {}] Parsing – summary={}ch findings={} fileDesc={} opt={} mermaid={} | Output {} chars",
            analysis.id,
            analysis.summaryText?.length ?: 0,
            if (analysis.findingsJson != null) "OK" else "NULL",
            if (analysis.fileDescriptionsJson != null) "OK" else "NULL",
            if (analysis.optimizationsJson != null) "OK" else "NULL",
            if (analysis.mermaid != null) "OK" else "NULL",
            output.length,
        )
    }

    internal fun extractSummary(output: String): String {
        val summary = extractSection(output, "SUMMARY:", "FINDINGS:").trim()
        if (summary.isNotBlank()) return summary
        val loose =
            extractSectionLoose(
                output,
                listOf("SUMMARY", "ZUSAMMENFASSUNG"),
                listOf("FINDINGS", "BEFUNDE", "FILE_DESCRIPTIONS"),
            ).trim()
        if (loose.isNotBlank()) return loose
        if (output.isBlank()) return ""
        val firstFence = output.indexOf("```")
        val raw = if (firstFence > 0) output.substring(0, firstFence) else output
        return raw
            .lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
            .take(4000)
    }

    internal fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String = aiJsonExtractor.extractTrimmedSection(text, startMarker, endMarker)

    internal fun extractSectionLoose(
        text: String,
        startWords: List<String>,
        endWords: List<String>,
    ): String = aiJsonExtractor.extractLooseSection(text, startWords, endWords)

    internal fun extractFirstJsonArray(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String? {
        val si = text.indexOf(startMarker).takeIf { it >= 0 } ?: return null
        val after = text.substring(si + startMarker.length)
        val ei = after.indexOf(endMarker).takeIf { it >= 0 } ?: after.length
        val section = after.substring(0, ei)
        return extractJsonArray(section)
    }

    internal fun extractJsonBlockAfter(
        text: String,
        marker: String,
    ): String? {
        val si = text.indexOf(marker).takeIf { it >= 0 } ?: return null
        return extractJsonArray(text.substring(si + marker.length))
    }

    internal fun extractJsonArray(text: String): String? = aiJsonExtractor.extractJsonArray(text)
}
