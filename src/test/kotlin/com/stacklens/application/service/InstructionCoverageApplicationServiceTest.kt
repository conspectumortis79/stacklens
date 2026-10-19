package com.stacklens.application.service
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class InstructionCoverageApplicationServiceTest {
    private fun writingFilePort(): FileSystemPort = JavaFileSystemAdapter()

    private val ai =
        PipelineAnalysisAiService(
            mock<CopilotPort>(),
            mock<PromptReaderPort>(),
            DefaultAiResponseJsonExtractor(),
        )

    @Test
    fun `the earliest of several start words opens the section`() {
        val text = "Vorrede\n## BEFUNDE\nzu viel last\n## OPTIMIERUNGEN\nmehr"

        val section = ai.extractSectionLoose(text, listOf("optimierungen", "befunde"), listOf("## optimierungen"))

        assertTrue(section.contains("zu viel"), section)
    }

    @Test
    fun `a section without an end word reaches the end of the text`() {
        val section = ai.extractSectionLoose("Befunde: drei Punkte", listOf("befunde"), listOf("fehit"))

        assertEquals("drei Punkte", section)
    }

    @Test
    fun `the registry constructor seeds the registry from the identifiers on disk`(
        @TempDir dir: Path,
    ) {
        val registry = mock<CopilotPipelineRegistry>()
        val runs = ConcurrentHashMap<Long, AgentPipelineRun>()
        whenever(registry.rawMap()).thenReturn(runs)
        val persistence =
            AgentRunPersistence(
                dataDir = dir.toFile().absolutePath,
                registry = registry,
                jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().findAndRegisterModules()),
                filePort = writingFilePort(),
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        val stored =
            AgentPipelineRun(
                id = 4L,
                rawText = "raw",
                repoPath = "/tmp/repo",
                featureSlug = "feat",
                createdAt = 1L,
            )
        persistence.persist(stored)
        runs.clear()

        persistence.loadFromDisk()

        assertEquals(4L, runs[4L]?.id)
        verify(registry).seed(4L)
    }

    @Test
    fun `a persistence without a registry keeps the seeded identifiers to itself`(
        @TempDir dir: Path,
    ) {
        val runs = ConcurrentHashMap<Long, AgentPipelineRun>()
        val persistence =
            AgentRunPersistence(
                dataDir = dir.toFile().absolutePath,
                runs = runs,
                jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().findAndRegisterModules()),
                filePort = writingFilePort(),
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        val stored =
            AgentPipelineRun(
                id = 9L,
                rawText = "raw",
                repoPath = "/tmp/repo",
                featureSlug = "feat",
                createdAt = 1L,
            )
        persistence.persist(stored)
        runs.clear()

        persistence.loadFromDisk()

        assertEquals(9L, runs[9L]?.id)
    }

    @Test
    fun `the default test stage list is reachable through the companion as well`() {
        assertEquals(0, RexStageRunner.DEFAULT_TEST_STAGES.size)
        assertEquals(0, RexStageRunner.Companion.DEFAULT_TEST_STAGES.size)
    }
}
