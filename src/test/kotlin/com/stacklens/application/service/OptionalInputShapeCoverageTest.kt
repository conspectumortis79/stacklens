package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.service.knowledge.KnowledgeBaseService
import com.stacklens.application.service.logging.LoggingFixPlanStage
import com.stacklens.application.service.review.PptxAnalysisLog
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.testsupport.PrivateInvocation
import com.stacklens.testsupport.TestJsonCodec
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class OptionalInputShapeCoverageTest {
    @Test
    fun `an update that passes no fields leaves the stored base alone`() {
        val basePort = mock<KnowledgeBasePort>()
        whenever(basePort.findById(4L)).thenReturn(KnowledgeBase(id = 4L, name = "original", description = "kept"))
        whenever(basePort.save(any())).thenAnswer { it.getArgument(0) }
        val service =
            KnowledgeBaseService(
                basePort = basePort,
                sourcePort = mock(),
                documentPort = mock(),
                chunkPort = mock(),
                indexPort = mock(),
                vectorIndexPort = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        val updated = service.updateBase(4L, name = null, description = null, active = null, includeInGlobalChat = null)

        assertEquals("original", updated.name)
        assertEquals("kept", updated.description)
    }

    private fun orchestrator(): PptxAnalysisOrchestrator =
        PptxAnalysisOrchestrator(
            structureExtractor = mock(),
            jsonCodec = mock(),
            questionExtractor = mock(),
            answerMapper = mock(),
            renderer = mock(),
            storage = mock(),
            promptAssembler = mock(),
            analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `a whitespace process log is treated as no log at all`() {
        val analysis = ReviewAnalysis(id = 1L, jiraSprintUrl = "https://jira.example/sprint/1")
        analysis.processLogJson = "   "

        PrivateInvocation.invoke(orchestrator(), "addLog", analysis, "first step")

        assertTrue(analysis.processLogJson!!.contains("first step"), analysis.processLogJson)
        assertEquals(false, analysis.processLogJson!!.contains("older"), analysis.processLogJson)
    }

    @Test
    fun `an existing process log is decoded and extended`() {
        val analysis = ReviewAnalysis(id = 3L, jiraSprintUrl = "https://jira.example/sprint/3")
        analysis.processLogJson = """["older entry"]"""

        PrivateInvocation.invoke(orchestrator(), "addLog", analysis, "new step")

        assertTrue(analysis.processLogJson!!.contains("older entry"), analysis.processLogJson)
        assertTrue(analysis.processLogJson!!.contains("new step"), analysis.processLogJson)
    }

    @Test
    fun `oversized project files are skipped while small ones are quoted`(
        @TempDir dir: Path,
    ) {
        val stage =
            LoggingFixPlanStage(
                copilot = mock(),
                promptLoader = mock(),
                markers = catalogBackedLoggingFixMarkers(),
                fileSystem = JavaFileSystemAdapter(),
            )
        val root = dir.toFile()
        File(root, "README.md").writeText("x".repeat(70 * 1024))
        File(root, "build.gradle.kts").writeText("y".repeat(70 * 1024))

        val big = PrivateInvocation.invoke(stage, "scanRepo", root) as String

        File(root, "README.md").writeText("# small")
        File(root, "build.gradle.kts").writeText("plugins { }")

        val small = PrivateInvocation.invoke(stage, "scanRepo", root) as String

        assertFalse(big.contains("## README.md (excerpt)"), big.take(200))
        assertFalse(big.contains("## build.gradle.kts (excerpt)"), big.take(200))
        assertTrue(small.contains("# small") && small.contains("plugins { }"), small)
    }

    @Test
    fun `a repository without project files scans to nothing`(
        @TempDir dir: Path,
    ) {
        val stage =
            LoggingFixPlanStage(
                copilot = mock(),
                promptLoader = mock(),
                markers = catalogBackedLoggingFixMarkers(),
                fileSystem = JavaFileSystemAdapter(),
            )

        val scanned = PrivateInvocation.invoke(stage, "scanRepo", dir.toFile()) as String

        assertFalse(scanned.contains("## README.md (excerpt)"), scanned)
        assertFalse(scanned.contains("## Kotlin packages:"), scanned)
    }
}
