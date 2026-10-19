package com.stacklens.application.service.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.adapter.out.pipeline.PipelineMrSimulationAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.PipelineFinding
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PipelineAnalysisMrServiceDescriptionTest {
    private val jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().registerModule(KotlinModule.Builder().build()))
    private val service =
        PipelineAnalysisMrService(
            analysisRepo = mock<PipelineAnalysisPort>(),
            gitlabMrPort = mock<GitlabMrPort>(),
            gitlabConfigPort = mock<GitlabConfigPort>(),
            githubRepoConfigPort = mock<GithubRepoConfigPort>(),
            githubBridge = mock<GithubTicketBridgePort>(),
            gitlabRepoPort = mock<CodeAnalysisRepoApi>(),
            jsonCodec = jsonCodec,
            i18n = mock<I18nPort>(),
            simulationPort = PipelineMrSimulationAdapter(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun makeAnalysis(
        summary: String? = null,
        findings: List<PipelineFinding> = emptyList(),
        optimizations: List<Map<String, Any?>> = emptyList(),
    ): PipelineAnalysis {
        val a =
            PipelineAnalysis(
                id = 1L,
                repoConfigId = 1L,
                status = "COMPLETED",
                summaryText = summary,
            )
        if (findings.isNotEmpty()) {
            a.findingsJson = jsonCodec.encode(findings)
        }
        if (optimizations.isNotEmpty()) {
            a.optimizationsJson = jsonCodec.encode(optimizations)
        }
        return a
    }

    @Test
    fun `buildMrDescription includes summary when present`() {
        val out = service.buildMrDescription(makeAnalysis(summary = "The pipeline has 3 issues."))
        assertTrue(out.contains("### Zusammenfassung"))
        assertTrue(out.contains("The pipeline has 3 issues."))
    }

    @Test
    fun `buildMrDescription omits summary when null`() {
        val out = service.buildMrDescription(makeAnalysis(summary = null))
        assertFalse(out.contains("### Zusammenfassung"))
    }

    @Test
    fun `buildMrDescription omits summary when blank`() {
        val out = service.buildMrDescription(makeAnalysis(summary = "   "))
        assertFalse(out.contains("### Zusammenfassung"))
    }

    @Test
    fun `buildMrDescription omits Befunde section when findings is empty`() {
        val out = service.buildMrDescription(makeAnalysis())
        assertFalse(out.contains("### Befunde"))
    }

    @Test
    fun `buildMrDescription includes Befunde section with HIGH severity`() {
        val finding =
            PipelineFinding(
                id = "f1",
                type = "SQL_INJECTION",
                severity = "HIGH",
                file = "src/Foo.kt",
                line = 42,
                description = "Dangerous concatenation.",
                suggestion = "Use parameterized queries.",
            )
        val out = service.buildMrDescription(makeAnalysis(findings = listOf(finding)))
        assertTrue(out.contains("🔴"))
        assertTrue(out.contains("SQL_INJECTION"))
        assertTrue(out.contains("src/Foo.kt"))
        assertTrue(out.contains("(Zeile 42)"))
        assertTrue(out.contains("Dangerous concatenation."))
        assertTrue(out.contains("Use parameterized queries."))
    }

    @Test
    fun `buildMrDescription includes Befunde section with MEDIUM severity`() {
        val finding =
            PipelineFinding(
                id = "f1",
                type = "CODE_SMELL",
                severity = "MEDIUM",
                file = "src/Bar.kt",
                description = "Long method.",
                suggestion = "Refactor.",
            )
        val out = service.buildMrDescription(makeAnalysis(findings = listOf(finding)))
        assertTrue(out.contains("🟡"))
    }

    @Test
    fun `buildMrDescription includes Befunde section with LOW severity`() {
        val finding =
            PipelineFinding(
                id = "f1",
                type = "STYLE",
                severity = "LOW",
                file = "src/Baz.kt",
                description = "Indentation.",
                suggestion = "",
            )
        val out = service.buildMrDescription(makeAnalysis(findings = listOf(finding)))
        assertTrue(out.contains("🟢"))
        assertFalse(out.contains("Vorschlag:"))
    }

    @Test
    fun `buildMrDescription finding without line omits the line number`() {
        val finding =
            PipelineFinding(
                id = "f1",
                type = "X",
                severity = "LOW",
                file = "src/F.kt",
                line = null,
                description = "d",
                suggestion = "",
            )
        val out = service.buildMrDescription(makeAnalysis(findings = listOf(finding)))
        assertFalse(out.contains("(Zeile"))
    }

    @Test
    fun `buildMrDescription finding with empty type uses 'QUESTION_MARK'`() {
        val finding =
            PipelineFinding(
                id = "f1",
                type = "",
                severity = "LOW",
                file = "src/F.kt",
                description = "d",
                suggestion = "",
            )
        val out = service.buildMrDescription(makeAnalysis(findings = listOf(finding)))
        assertTrue(out.contains("?"))
    }

    @Test
    fun `buildMrDescription omits Optimierungen section when list is empty`() {
        val out = service.buildMrDescription(makeAnalysis())
        assertFalse(out.contains("### Optimierungsvorschläge"))
    }

    @Test
    fun `buildMrDescription includes Optimierungen section with title and rationale`() {
        val opts =
            listOf(
                mapOf(
                    "title" to "Cache result",
                    "rationale" to "Avoid repeated computation",
                ),
            )
        val out = service.buildMrDescription(makeAnalysis(optimizations = opts))
        assertTrue(out.contains("### Optimierungsvorschläge"))
        assertTrue(out.contains("Cache result"))
        assertTrue(out.contains("Avoid repeated computation"))
    }

    @Test
    fun `buildMrDescription skips optimization with empty title`() {
        val opts =
            listOf(
                mapOf("title" to "", "rationale" to "rationale only"),
            )
        val out = service.buildMrDescription(makeAnalysis(optimizations = opts))
        assertFalse(out.contains("- ****"))
    }

    @Test
    fun `buildMrDescription skips rationale line when empty`() {
        val opts =
            listOf(
                mapOf("title" to "Title only", "rationale" to ""),
            )
        val out = service.buildMrDescription(makeAnalysis(optimizations = opts))
        assertTrue(out.contains("Title only"))
        assertFalse(out.contains("  - "))
    }

    @Test
    fun `buildMrDescription ignores unparseable findings JSON`() {
        val analysis = makeAnalysis()
        analysis.findingsJson = "not valid json at all {"
        val out = service.buildMrDescription(analysis)
        assertFalse(out.contains("### Befunde"))
    }

    @Test
    fun `buildMrDescription ignores unparseable optimizations JSON`() {
        val analysis = makeAnalysis()
        analysis.optimizationsJson = "not valid json at all {"
        val out = service.buildMrDescription(analysis)
        assertFalse(out.contains("Optimierungsvorschläge"))
    }

    @Test
    fun `buildMrDescription treats blank findingsJson as empty`() {
        val analysis = makeAnalysis()
        analysis.findingsJson = "   "
        val out = service.buildMrDescription(analysis)
        assertFalse(out.contains("### Befunde"))
    }

    @Test
    fun `buildMrDescription treats blank optimizationsJson as empty`() {
        val analysis = makeAnalysis()
        analysis.optimizationsJson = "   "
        val out = service.buildMrDescription(analysis)
        assertFalse(out.contains("Optimierungsvorschläge"))
    }

    @Test
    fun `buildMrDescription includes 'Automatisch generiert' header`() {
        val out = service.buildMrDescription(makeAnalysis())
        assertTrue(out.contains("Automatisch generiert"))
    }
}
