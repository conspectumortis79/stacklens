package com.stacklens.application.service.codeanalysis

import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.CodeFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CodeAnalysisMrServiceCoverageTest {
    private val analysisRepo = mock<CodeAnalysisPort>()
    private val codeRepoPort = mock<CodeAnalysisRepoApi>()
    private val gitlabMrPort = mock<GitlabMrPort>()
    private val jsonCodec = mock<JsonCodecPort>()
    private val i18n = mock<I18nPort>()
    private val service = CodeAnalysisMrService(analysisRepo, codeRepoPort, gitlabMrPort, jsonCodec, i18n)

    private fun stubFindings(vararg findings: CodeFinding) {
        whenever(jsonCodec.decodeList(any(), eq(CodeFinding::class))).thenReturn(findings.toList())
    }

    @Test
    fun `a description built without repository coordinates renders plain file labels`() {
        stubFindings(
            CodeFinding(
                id = "f1",
                file = "src/Main.kt",
                type = "CLEANUP",
                line = 12,
                description = "duplicated branch",
                suggestion = "extract helper",
            ),
        )

        val description =
            service.buildMrDescription(
                CodeAnalysis(id = 1L, summaryText = "Zwei Befunde", findingsJson = "[]"),
            )

        assertTrue(description.contains("### Zusammenfassung"))
        assertTrue(description.contains("### Befunde (1)"))
        assertTrue(description.contains("src/Main.kt (Zeile 12)"))
        assertTrue(description.contains("*Vorschlag:* extract helper"))
        assertFalse(description.contains("]("))
    }

    @Test
    fun `a description without summary and findings only carries the footer`() {
        val description = service.buildMrDescription(CodeAnalysis(id = 2L), "https://gitlab.example/g/r", "code-analyse-2")

        assertFalse(description.contains("### Zusammenfassung"))
        assertTrue(description.contains("Automatisch generiert von StackLens Code-Analyse"))
    }

    @Test
    fun `a decoding failure degrades to a description without findings`() {
        whenever(jsonCodec.decodeList(any(), eq(CodeFinding::class))).thenThrow(IllegalArgumentException("broken json"))

        val description =
            service.buildMrDescription(
                CodeAnalysis(id = 3L, findingsJson = "{not decodable"),
                "https://gitlab.example/g/r",
                "code-analyse-3",
            )

        assertFalse(description.contains("### Befunde"))
    }

    @Test
    fun `a finding without a file path renders no reference at all`() {
        assertEquals("", service.renderFindingFileReference("", 4, "https://gitlab.example/g/r", "branch"))
    }

    @Test
    fun `a finding without a line number renders the bare path label`() {
        assertEquals(
            "src/Main.kt",
            service.renderFindingFileReference("src/Main.kt", null, "", ""),
        )
    }

    @Test
    fun `a missing branch keeps the label plain even with a web url`() {
        assertEquals(
            "src/Main.kt (Zeile 9)",
            service.renderFindingFileReference("src/Main.kt", 9, "https://gitlab.example/g/r/", ""),
        )
    }

    @Test
    fun `a complete web url and branch produce a line anchored link`() {
        assertEquals(
            "[src/Main.kt (Zeile 9)](https://gitlab.example/g/r/-/blob/feature/x/src/Main.kt#L9)",
            service.renderFindingFileReference("src/Main.kt", 9, "https://gitlab.example/g/r/", "feature/x"),
        )
    }

    @Test
    fun `a link without a line number has no anchor`() {
        assertEquals(
            "[src/Main.kt](https://gitlab.example/g/r/-/blob/feature/x/src/Main.kt)",
            service.renderFindingFileReference("src/Main.kt", null, "https://gitlab.example/g/r", "feature/x"),
        )
    }
}
