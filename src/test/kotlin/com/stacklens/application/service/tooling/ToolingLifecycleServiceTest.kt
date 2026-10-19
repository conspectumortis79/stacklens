package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ToolingLifecycleServiceTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val asyncExecutor = InlineAsyncExecutor()
    private val generationRunner: ToolingGenerationRunner = mock()
    private val i18n: I18nPort = mock()
    private val toolJarStore: com.stacklens.application.port.out.tooling.ToolJarStore = mock()
    private val cancellations = ToolingCancellationRegistry()

    private val service =
        ToolingLifecycleService(
            toolRepo = toolRepo,
            asyncExecutor = asyncExecutor,
            generationRunner = generationRunner,
            i18n = i18n,
            toolJarStore = toolJarStore,
            cancellations = cancellations,
        )

    @Test
    fun `startGeneration creates a PENDING tool and dispatches`() {
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_starting"))).thenReturn("Starting")

        val tool = service.startGeneration(name = "MyTool", scriptContent = "println(1)")

        assertEquals("MyTool", tool.name)
        assertEquals("SCRIPT", tool.confluencePageUrl)
        assertEquals(AnalysisStatus.PENDING, tool.status)
        assertEquals("Starting", tool.currentStep)
        assertEquals("println(1)", tool.pageContentText)
        verify(generationRunner).runGeneration(any())
    }

    @Test
    fun `startGeneration schedules generation on the analysis task executor`() {
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_starting"))).thenReturn("Starting")
        val executorNames = mutableListOf<String?>()
        val executor =
            object : AsyncExecutorPort {
                override fun submit(
                    executorName: String?,
                    task: () -> Unit,
                ) {
                    executorNames += executorName
                    task()
                }
            }
        val service =
            ToolingLifecycleService(
                toolRepo = toolRepo,
                asyncExecutor = executor,
                generationRunner = generationRunner,
                i18n = i18n,
                toolJarStore = toolJarStore,
                cancellations = cancellations,
            )

        val tool = service.startGeneration(name = "T", scriptContent = null)

        assertEquals(listOf("analysisTaskExecutor"), executorNames)
        verify(generationRunner).runGeneration(tool.id)
    }

    @Test
    fun `startGeneration trims whitespace from name`() {
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_starting"))).thenReturn("Starting")

        val tool = service.startGeneration(name = "  Spaced  ", scriptContent = null)

        assertEquals("Spaced", tool.name)
    }

    @Test
    fun `startGeneration with null scriptContent stores empty string`() {
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_starting"))).thenReturn("Starting")

        val tool = service.startGeneration(name = "T", scriptContent = null)

        assertEquals("", tool.pageContentText)
    }

    @Test
    fun `cancelGeneration marks the tool as cancelled in the registry`() {
        service.cancelGeneration(42L)

        assertTrue(cancellations.consumeCancellation(42L))
    }

    @Test
    fun `cancelGeneration is idempotent for repeated calls`() {
        service.cancelGeneration(7L)
        service.cancelGeneration(7L)

        assertTrue(cancellations.consumeCancellation(7L))
        assertFalse(cancellations.consumeCancellation(7L))
    }

    @Test
    fun `deleteTool cancels and removes the tool`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                jarPath = "/tmp/nonexistent-jar-for-test.jar",
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)

        service.deleteTool(1L)

        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `deleteTool deletes the jar via store when present`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile().apply { writeBytes(byteArrayOf(1)) }
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
                jarPath = jarFile.absolutePath,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)

        service.deleteTool(1L)

        verify(toolJarStore).deleteJar(jarFile.absolutePath)
        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `deleteTool with null jarPath does not touch the jar store`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)

        service.deleteTool(1L)

        verify(toolJarStore, never()).deleteJar(any())
        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `deleteTool with non-existent tool does not call delete`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)

        service.deleteTool(1L)

        verify(toolRepo, never()).deleteConfluenceTool(any())
    }

    @Test
    fun `deleteTool marks the tool cancelled so a running generation aborts`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)

        service.deleteTool(1L)

        assertTrue(cancellations.consumeCancellation(1L))
    }

    @Test
    fun `deleteToolRecord marks tool as hidden`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service.deleteToolRecord(1L)

        assertTrue(tool.hiddenFromHistory)
        verify(toolRepo).saveConfluenceTool(tool)
    }

    @Test
    fun `deleteToolRecord with non-existent tool is a no-op`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)

        service.deleteToolRecord(1L)

        verify(toolRepo, never()).saveConfluenceTool(any())
    }
}
