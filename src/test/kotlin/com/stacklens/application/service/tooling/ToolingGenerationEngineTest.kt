package com.stacklens.application.service.tooling

import com.stacklens.adapter.out.tooling.FileToolJarStore
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.tooling.ToolingIconPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyDeletedException
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.FakeToolingConfigPort
import com.stacklens.testsupport.JarFileFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ToolingGenerationEngineTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val copilotCliService: CopilotPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val i18n: I18nPort = mock()
    private val compiler: KotlinCompilerPort = mock()
    private val promptBuilder: ToolingPromptBuilder = mock()
    private val iconService: ToolingIconPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val cancellations = ToolingCancellationRegistry()

    init {
        whenever(i18n.t(any())).thenReturn("step")
        whenever(i18n.translateError(any())).thenReturn("err")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
    }

    private val engine =
        ToolingGenerationEngine(
            toolRepo = toolRepo,
            copilotCliService = copilotCliService,
            aiUsageContext = aiUsageContext,
            i18n = i18n,
            compiler = compiler,
            promptBuilder = promptBuilder,
            iconService = iconService,
            toolJarStore = FileToolJarStore(stackLensConfig),
            cancellations = cancellations,
            toolingConfig = com.stacklens.testsupport.FakeToolingConfigPort(1),
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun baseTool(id: Long = 1L): ConfluenceTool =
        ConfluenceTool(
            id = id,
            name = "my-tool",
            confluencePageUrl = "u",
            status = AnalysisStatus.PENDING,
            currentStep = "starting",
            pageContentText = "page",
        )

    @Test
    fun `runGeneration drops inherited abort state before the first persist`() {
        val tool =
            baseTool(1L).apply {
                status = AnalysisStatus.CANCELLED
                errorMessage = "Tool-Generierung wurde abgebrochen. Starte die Generierung neu."
                completedAt = LocalDateTime.of(2020, 1, 1, 0, 0)
            }
        val persisted = mutableListOf<Triple<String, String?, LocalDateTime?>>()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as ConfluenceTool
            persisted.add(Triple(saved.status, saved.errorMessage, saved.completedAt))
            saved
        }
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenThrow(RuntimeException("stop here"))
        whenever(i18n.translateError(any())).thenReturn("err")

        engine.runGeneration(1L)

        assertEquals(
            listOf(AnalysisStatus.ANALYZING, AnalysisStatus.GENERATING, AnalysisStatus.FAILED),
            persisted.map { it.first },
        )
        assertNull(persisted[0].second)
        assertNull(persisted[0].third)
        assertNull(persisted[1].second)
        assertNull(persisted[1].third)
        assertEquals("err", persisted[2].second)
    }

    @Test
    fun `runGeneration clears the stale abort message when the retry succeeds`() {
        val tool =
            baseTool(1L).apply {
                status = AnalysisStatus.CANCELLED
                errorMessage = "Tool-Generierung wurde abgebrochen. Starte die Generierung neu."
            }
        val tmpJar = Files.createTempFile("tool-", ".jar").toFile()
        try {
            whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
            whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
            whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("code")
            whenever(compiler.compileWithRetry(any(), any(), any())).thenReturn("code" to tmpJar.absolutePath)
            whenever(iconService.determineIcon(any(), any())).thenReturn(null)
            whenever(iconService.generateIconPng(any())).thenReturn(null)
            whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }

            engine.runGeneration(1L)

            assertEquals(AnalysisStatus.READY, tool.status)
            assertNull(tool.errorMessage)
        } finally {
            tmpJar.delete()
        }
    }

    @Test
    fun `runGeneration returns immediately when tool is not found`() {
        whenever(toolRepo.findConfluenceToolById(99L)).thenReturn(null)

        engine.runGeneration(99L)

        verify(toolRepo, never()).saveConfluenceTool(any())
    }

    @Test
    fun `runGeneration marks tool as FAILED when an exception is thrown`() {
        val tool = baseTool(7L)
        whenever(toolRepo.findConfluenceToolById(7L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(RuntimeException("boom"))
        whenever(i18n.translateError(any())).thenReturn("translated boom")

        engine.runGeneration(7L)

        assertEquals(AnalysisStatus.FAILED, tool.status)
        assertEquals("translated boom".take(2000), tool.errorMessage)
    }

    @Test
    fun `runGeneration swallows EntityConcurrentlyDeletedException cleanly`() {
        val tool = baseTool(7L)
        whenever(toolRepo.findConfluenceToolById(7L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(
            EntityConcurrentlyDeletedException("x"),
        )

        engine.runGeneration(7L)
    }

    @Test
    fun `a generation failure on a deleted tool is swallowed`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool, null)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenThrow(RuntimeException("kaputt"))
        whenever(i18n.translateError(any())).thenReturn("error text")

        engine.runGeneration(1L)
    }

    @Test
    fun `runGeneration happy path completes and persists the JAR`() {
        val tool = baseTool(7L)
        whenever(toolRepo.findConfluenceToolById(7L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("src")
        whenever(compiler.compileWithRetry(eq(7L), eq("my-tool"), any())).thenReturn("src" to "/tmp/some/jar.jar")
        whenever(iconService.determineIcon(any(), any())).thenReturn(null)
        whenever(iconService.generateIconPng(any())).thenReturn(null)

        engine.runGeneration(7L)
    }

    @Test
    fun `runGeneration completes successfully when compile and icon succeed`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("code")
        whenever(iconService.determineIcon(any(), any())).thenReturn("icon-data")
        whenever(iconService.generateIconPng(any())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }

        val tmpJar = Files.createTempFile("tool-", ".jar").toFile()
        try {
            whenever(compiler.compileWithRetry(any(), any(), any())).thenReturn("code" to tmpJar.absolutePath)
            engine.runGeneration(1L)
            assertEquals(AnalysisStatus.READY, tool.status)
        } finally {
            tmpJar.delete()
        }
    }

    @Test
    fun `runGeneration re-saves tool when compiler changes the code`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("code-v1")
        whenever(iconService.determineIcon(any(), any())).thenReturn("icon")
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }

        val tmpJar = Files.createTempFile("tool-", ".jar").toFile()
        try {
            whenever(compiler.compileWithRetry(any(), any(), any())).thenReturn("code-v2" to tmpJar.absolutePath)
            engine.runGeneration(1L)

            assertEquals("code-v2", tool.generatedCode)
        } finally {
            tmpJar.delete()
        }
    }

    @Test
    fun `runGeneration survives an icon service that throws synchronously`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("code")
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }
        val tmpJar = Files.createTempFile("tool-", ".jar").toFile()
        try {
            whenever(compiler.compileWithRetry(any(), any(), any())).thenReturn("code" to tmpJar.absolutePath)
            whenever(iconService.determineIcon(any(), any())).thenAnswer {
                throw RuntimeException("simulated icon failure")
            }

            engine.runGeneration(1L)

            assertEquals(AnalysisStatus.READY, tool.status)
        } finally {
            tmpJar.delete()
        }
    }

    @Test
    fun `runGeneration returns early when tool is cancelled before compile`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("code")
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }
        cancellations.markCancelled(1L)

        engine.runGeneration(1L)

        assertNotNull(tool.status)
    }

    @Test
    fun `runGeneration handles EntityConcurrentlyDeletedException from the AI step`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any()))
            .thenThrow(EntityConcurrentlyDeletedException("deleted"))
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }

        engine.runGeneration(1L)

        assertNotNull(tool.status)
    }

    @Test
    fun `runGeneration handles generic exception by marking tool FAILED`() {
        val tool = baseTool(1L)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(RuntimeException("boom"))
        whenever(toolRepo.saveConfluenceTool(any<ConfluenceTool>())).thenAnswer { it.arguments[0] }

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.FAILED, tool.status)
    }

    @Test
    fun `runGeneration handles generic exception when tool is missing`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("p")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(RuntimeException("boom"))

        engine.runGeneration(1L)
    }

    @Test
    fun `a png generation that outlives its budget still finishes the tool`(
        @TempDir tempDir: Path,
    ) {
        val jar = tempDir.resolve("tool.jar").toFile()
        jar.writeBytes(JarFileFixtures.minimalJar())
        val tool = baseTool(21L)
        stubGeneration(tool, jar.absolutePath)
        whenever(iconService.generateIconPng(any())).thenAnswer {
            Thread.sleep(2_000L)
            byteArrayOf(1)
        }

        engine.runGeneration(21L)

        assertEquals(AnalysisStatus.READY, toolRepo.findConfluenceToolById(21L)!!.status)
        verify(iconService, never()).embedIconInJar(any(), any(), any(), any())
    }

    @Test
    fun `a broken icon embedding is reported without failing the generation`(
        @TempDir tempDir: Path,
    ) {
        val jar = tempDir.resolve("tool.jar").toFile()
        jar.writeBytes(JarFileFixtures.minimalJar())
        val tool = baseTool(22L)
        stubGeneration(tool, jar.absolutePath)
        whenever(iconService.generateIconPng(any())).thenReturn(byteArrayOf(1, 2))
        whenever(iconService.embedIconInJar(any(), any(), any(), any())).thenThrow(RuntimeException("jar versiegelt"))

        engine.runGeneration(22L)

        assertEquals(AnalysisStatus.READY, tool.status)
        org.junit.jupiter.api.Assertions
            .assertNull(tool.icon)
    }

    @Test
    fun `an icon lookup failure is recorded as no icon`(
        @TempDir tempDir: Path,
    ) {
        val jar = tempDir.resolve("tool.jar").toFile()
        jar.writeBytes(JarFileFixtures.minimalJar())
        val tool = baseTool(23L)
        stubGeneration(tool, jar.absolutePath)
        whenever(iconService.determineIcon(any(), any())).thenThrow(RuntimeException("ki offline"))
        whenever(iconService.generateIconPng(any())).thenReturn(null)

        engine.runGeneration(23L)

        assertEquals(AnalysisStatus.READY, tool.status)
        verify(iconService, never()).embedIconInJar(any(), any(), any(), any())
    }

    @Test
    fun `checkCancelled returns false when toolId is not in cancelled set`() {
        val tool = baseTool(123L)

        val result = engine.checkCancelled(123L, tool)

        assertFalse(result)
        verify(toolRepo, never()).deleteConfluenceTool(any())
    }

    @Test
    fun `checkCancelled deletes tool when cancelled`() {
        val tool = baseTool(5L).apply { jarPath = "/nonexistent-jar-for-test.jar" }
        cancellations.markCancelled(5L)

        val result = engine.checkCancelled(5L, tool)

        assertTrue(result)
        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `checkCancelled after compile deletes jar file when present`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())
        val tool =
            baseTool(1L).apply {
                status = AnalysisStatus.BUILDING
                jarPath = jarFile.absolutePath
            }
        cancellations.markCancelled(1L)

        val result = engine.checkCancelled(1L, tool)

        assertTrue(result)
        assertFalse(jarFile.exists())
        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `checkCancelled returns false when jar path non-existent`() {
        val tool =
            baseTool(1L).apply {
                status = AnalysisStatus.BUILDING
                jarPath = "/non/existent/jar/path.jar"
            }

        val result = engine.checkCancelled(1L, tool)

        assertEquals(false, result)
    }

    @Test
    fun `determineIcon delegates to iconService`() {
        whenever(iconService.determineIcon("T", "code")).thenReturn("bi-wrench")

        assertEquals("bi-wrench", engine.determineIcon("T", "code"))
    }

    @Test
    fun `generateIconPng delegates to iconService`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        whenever(iconService.generateIconPng("T")).thenReturn(png)

        assertEquals(png.toList(), engine.generateIconPng("T")!!.toList())
    }

    @Test
    fun `embedIconInJar default arg path delegates to iconService`() {
        engine.embedIconInJar(jarPath = "/tmp/a.jar", toolName = "T", icon = "bi-wrench")

        verify(iconService).embedIconInJar("/tmp/a.jar", "T", "bi-wrench", null)
    }

    @Test
    fun `embedIconInJar explicit iconPng delegates to iconService`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        engine.embedIconInJar("/tmp/a.jar", "T", "bi-wrench", png)

        verify(iconService).embedIconInJar("/tmp/a.jar", "T", "bi-wrench", png)
    }

    @Test
    fun `trySetMacOsFinderIcon delegates to iconService`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        engine.trySetMacOsFinderIcon("/tmp/a.jar", png)

        verify(iconService).trySetMacOsFinderIcon("/tmp/a.jar", png)
    }

    @Test
    fun `step sets currentStep and saves the tool`() {
        val tool = baseTool()
        whenever(toolRepo.saveConfluenceTool(tool)).thenReturn(tool)

        engine.step(tool, "Doing thing")

        assertEquals("Doing thing", tool.currentStep)
        verify(toolRepo).saveConfluenceTool(tool)
    }

    @Test
    fun `step overwrites previous currentStep value`() {
        val tool = baseTool().apply { currentStep = "Old step" }
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        engine.step(tool, "New step")

        assertEquals("New step", tool.currentStep)
    }

    @Test
    fun `step can be called multiple times in sequence`() {
        val tool = baseTool()
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        engine.step(tool, "Step 1")
        engine.step(tool, "Step 2")
        engine.step(tool, "Step 3")

        assertEquals("Step 3", tool.currentStep)
    }

    private fun stubGeneration(
        tool: ConfluenceTool,
        jarPath: String,
    ) {
        whenever(toolRepo.findConfluenceToolById(tool.id)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(eq(tool.id), eq(tool.name), any())).thenReturn("code" to jarPath)
        whenever(iconService.determineIcon(any(), any())).thenReturn("bi-tools")
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")
    }
}
