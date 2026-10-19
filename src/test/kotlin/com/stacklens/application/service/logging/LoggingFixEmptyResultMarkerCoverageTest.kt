package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.domain.pipeline.LoggingInputHeuristic
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LoggingFixEmptyResultMarkerCoverageTest {
    private fun context(
        dir: File,
        language: String,
        pushed: MutableList<String>,
    ) = LoggingFixStageContext(
        logText = "2025-01-15 ERROR boom\n  at com.x.Y.z(X.java:1)",
        selectedCategories = listOf("error"),
        repoPath = dir.absolutePath,
        repoRoot = dir,
        language = language,
        onPartial = { _, chunk -> pushed += chunk },
    )

    @Test
    fun `an empty plan answer is reported with a german marker`(
        @TempDir dir: Path,
    ) {
        val pushed = mutableListOf<String>()
        val stage = LoggingFixPlanStage(StubCopilot(""), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
        val ctx = context(dir.toFile(), "de", pushed)

        stage.execute(ctx)

        assertTrue(ctx.planOutput.toString().contains("Plan-Stage ohne Ergebnis"), ctx.planOutput.toString())
        assertTrue(pushed.any { it.contains("Plan-Stage") }, pushed.toString())
    }

    @Test
    fun `an empty code answer is reported with an english marker`(
        @TempDir dir: Path,
    ) {
        val pushed = mutableListOf<String>()
        val stage = LoggingFixCodeStage(StubCopilot(""), StubPromptLoader(), catalogBackedLoggingFixMarkers())
        val ctx = context(dir.toFile(), "EN", pushed)

        stage.execute(ctx)

        assertTrue(ctx.codeOutput.toString().contains("Code stage produced no result"), ctx.codeOutput.toString())
        assertTrue(pushed.any { it.contains("Code stage produced no result") }, pushed.toString())
    }

    @Test
    fun `an empty test answer is appended to the tests buffer`(
        @TempDir dir: Path,
    ) {
        val pushed = mutableListOf<String>()
        val stage = LoggingFixTestsStage(StubCopilot(""), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
        val ctx = context(dir.toFile(), "de", pushed)

        stage.execute(ctx)

        assertTrue(ctx.testsOutput.toString().contains("Test-Stage ohne Ergebnis"), ctx.testsOutput.toString())
        assertEquals(pushed.any { it.contains("Test-Stage") }, true)
    }

    @Test
    fun `an empty category selection reaches the prompt as all`(
        @TempDir dir: Path,
    ) {
        val seen = mutableListOf<String>()
        val stage =
            LoggingFixDiagnoseStage(
                StubCopilot("diagnose-answer"),
                StubPromptLoader(
                    onGet = { _, _, vars ->
                        seen += vars["categories"].orEmpty()
                        "p"
                    },
                ),
                markers = catalogBackedLoggingFixMarkers(),
            )
        val ctx =
            LoggingFixStageContext(
                logText = "2025-01-15 ERROR boom\n  at com.x.Y.z(X.java:1)",
                selectedCategories = emptyList(),
                repoPath = dir.toFile().absolutePath,
                repoRoot = dir.toFile(),
                language = "de",
                onPartial = { _, _ -> },
            )

        stage.execute(ctx)

        assertEquals(listOf("(alle)"), seen)
        assertTrue(ctx.diagnoseOutput.toString().contains("diagnose-answer"), ctx.diagnoseOutput.toString())
    }

    @Test
    fun `a java frame matches the marker words`() {
        val verdict = LoggingInputHeuristic.classify("  at com.x.Y.z(X.java:1)")

        assertEquals(false, verdict.tooThin)
    }

    @Test
    fun `a kotlin frame with no marker word is recognised by the frame pattern`() {
        val verdict = LoggingInputHeuristic.classify("  at com.x.Y.z(Foo.kt:12)\n2025-01-15 FEHLER")

        assertTrue(verdict.hasStackTraceMarker)
        assertEquals(false, verdict.tooThin)
    }

    @Test
    fun `plain prose without any marker is too thin for a diagnosis`() {
        val verdict = LoggingInputHeuristic.classify("es lief etwas\ndann nicht mehr")

        assertTrue(verdict.tooThin)
    }

    @Test
    fun `the english marker is used for english runs`() {
        val marker = catalogBackedLoggingFixMarkers().emptyResultMarker("diagnose", "en")

        assertTrue(marker.contains("produced no result"), marker)
    }
}
