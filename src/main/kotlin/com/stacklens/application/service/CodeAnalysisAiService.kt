package com.stacklens.application.service

import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisAi
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeAnalysis
import org.slf4j.LoggerFactory

@ApplicationService
class CodeAnalysisAiService(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
    private val clock: ClockPort,
) : CodeAnalysisAi {
    private val log = LoggerFactory.getLogger(CodeAnalysisAiService::class.java)

    override fun buildAnalysisPrompt(
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        val summaryFormatHint = promptLoader.get(lang, "codeanalysis.summaryFormatHint")
        val mermaidSection = if (includeMermaid) promptLoader.get(lang, "codeanalysis.mermaidSection") else ""
        val drawioSection = if (includeDrawio) promptLoader.get(lang, "codeanalysis.drawioSection") else ""
        return promptLoader.get(
            lang,
            "codeanalysis.analysisPrompt",
            mapOf(
                "projectName" to projectName,
                "projectPath" to projectPath,
                "projectTree" to projectTree.take(3000),
                "fileContents" to fileContents.take(30_000),
                "summaryFormatHint" to summaryFormatHint,
                "mermaidSection" to mermaidSection,
                "drawioSection" to drawioSection,
            ),
        )
    }

    override fun applyAiOutput(
        analysis: CodeAnalysis,
        output: String,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ) {
        var summary = extractSection(output, "SUMMARY:", "FINDINGS:").trim()
        if (summary.isBlank()) {
            summary =
                extractSectionLoose(output, listOf("SUMMARY", "ZUSAMMENFASSUNG"), listOf("FINDINGS", "BEFUNDE", "FILE_CHANGES")).trim()
        }
        if (summary.isBlank() && output.isNotBlank()) {
            val firstFence = output.indexOf("```")
            val raw = if (firstFence > 0) output.substring(0, firstFence) else output
            summary =
                raw
                    .lineSequence()
                    .filterNot { it.trim().startsWith("```") }
                    .joinToString("\n")
                    .trim()
                    .take(4000)
        }
        analysis.summaryText = summary
        analysis.findingsJson = extractFindingsJson(output)
        analysis.fileChangesJson = extractFileChangesJson(output)
        if (includeMermaid) analysis.mermaid = extractMermaid(output)
        if (includeDrawio) analysis.drawioXml = extractDrawioXml(output)?.let { sanitizeDrawioXml(it) }
        analysis.status = AnalysisStatus.COMPLETED
        analysis.completedAt = clock.localDateTime()
        log.debug(
            "[CodeAnalysis {}] Extraktion — summary={}ch findings={} mermaid={} drawioXml={} | Output {} chars | Erste 500:\n{}",
            analysis.id,
            summary.length,
            if (analysis.findingsJson != null) "OK" else "NULL",
            if (analysis.mermaid != null) "OK(${analysis.mermaid?.length}ch)" else "NULL",
            if (analysis.drawioXml != null) "OK(${analysis.drawioXml?.length}ch)" else "NULL",
            output.length,
            output.take(500),
        )
    }

    internal fun extractSectionLoose(
        text: String,
        startWords: List<String>,
        endWords: List<String>,
    ): String = aiJsonExtractor.extractLooseSection(text, startWords, endWords)

    private val findingsMarkers =
        listOf(
            "FINDINGS:",
            "Findings:",
            "BEFUNDE:",
            "Befunde:",
            "## FINDINGS",
            "## Findings",
        )

    private val fileChangesMarkers =
        listOf(
            "FILE_CHANGES:",
            "File Changes:",
            "FILE CHANGES:",
            "File changes:",
            "DATEI-ÄNDERUNGEN:",
            "Datei-Änderungen:",
            "## FILE_CHANGES",
            "## File Changes",
        )

    internal fun findMarkerSection(
        text: String,
        markers: List<String>,
    ): String? {
        val lower = text.lowercase()
        var bestIdx = -1
        var bestLen = 0
        for (marker in markers) {
            val idx = lower.indexOf(marker.lowercase())
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx
                bestLen = marker.length
            }
        }
        if (bestIdx < 0) return null
        val cursor = bestIdx + bestLen
        return text.substring(cursor).dropWhile { it == ':' || it == '*' || it.isWhitespace() }
    }

    internal fun extractFindingsJson(output: String): String? {
        extractJsonBlock(output, "FINDINGS:", "FILE_CHANGES:")?.let { return it }
        val section = findMarkerSection(output, findingsMarkers) ?: return null
        val upper = section.uppercase()
        val endMarkers = fileChangesMarkers.map { it.uppercase() }
        val end =
            endMarkers
                .mapNotNull { m -> upper.indexOf(m).takeIf { it >= 0 } }
                .minOrNull() ?: section.length
        return extractFirstJsonArray(section.substring(0, end))
    }

    internal fun extractFileChangesJson(output: String): String? {
        extractJsonBlockAfter(output, "FILE_CHANGES:")?.let { return it }
        val section = findMarkerSection(output, fileChangesMarkers) ?: return null
        return extractFirstJsonArray(section)
    }

    fun extractMermaid(text: String): String? = MermaidSupport.extract(text)

    fun extractDrawioXml(text: String): String? = DrawioSupport.extract(text)

    fun sanitizeDrawioXml(xml: String): String = DrawioSupport.sanitize(xml)

    fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String = aiJsonExtractor.extractTrimmedSection(text, startMarker, endMarker)

    fun extractJsonBlock(
        text: String,
        sectionMarker: String,
        endMarker: String,
    ): String? {
        val si = text.indexOf(sectionMarker).takeIf { it >= 0 } ?: return null
        val section =
            run {
                val after = text.substring(si + sectionMarker.length)
                val ei = after.indexOf(endMarker).takeIf { it >= 0 } ?: return@run after
                after.substring(0, ei)
            }
        return extractFirstJsonArray(section)
    }

    fun extractJsonBlockAfter(
        text: String,
        sectionMarker: String,
    ): String? {
        val si = text.indexOf(sectionMarker).takeIf { it >= 0 } ?: return null
        val section = text.substring(si + sectionMarker.length)
        return extractFirstJsonArray(section)
    }

    fun extractFirstJsonArray(text: String): String? = aiJsonExtractor.extractJsonArray(text)
}
