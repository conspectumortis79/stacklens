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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ToolingGenerationEngineRunTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val copilotCliService: CopilotPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val i18n: I18nPort = mock()
    private val compiler: KotlinCompilerPort = mock()
    private val promptBuilder: ToolingPromptBuilder = mock()
    private val iconService: ToolingIconPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val cancellations = ToolingCancellationRegistry()

    private lateinit var engine: ToolingGenerationEngine

    @BeforeEach
    fun setUp() {
        engine =
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
                toolingConfig = FakeToolingConfigPort(),
                parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
    }

    private fun pendingTool(id: Long = 1L): ConfluenceTool =
        ConfluenceTool(
            id = id,
            name = "T",
            confluencePageUrl = "u",
            pageContentText = "println(1)",
        )

    @Test
    fun `runGeneration returns silently when tool id not found`() {
        whenever(toolRepo.findConfluenceToolById(42L)).thenReturn(null)

        engine.runGeneration(42L)

        verify(toolRepo, never()).saveConfluenceTool(any())
    }

    @Test
    fun `runGeneration completes successfully and produces ready tool`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())

        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1")).thenReturn("fun main() = 1" to jarFile.absolutePath)
        whenever(iconService.determineIcon(any(), any())).thenReturn("bi-tools")
        whenever(iconService.generateIconPng(any())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.READY, tool.status)
        assertEquals(jarFile.absolutePath, tool.jarPath)
        assertEquals("bi-tools", tool.icon)
        assertNotNull(tool.completedAt)
    }

    @Test
    fun `runGeneration aborts when cancelled after AI step`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")

        cancellations.markCancelled(1L)

        engine.runGeneration(1L)

        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `runGeneration aborts when cancelled after compile step`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())

        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(iconService.determineIcon(any(), any())).thenReturn(null)
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")

        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1")).thenAnswer {
            cancellations.markCancelled(1L)
            "fun main() = 1" to jarFile.absolutePath
        }

        engine.runGeneration(1L)

        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `runGeneration marks tool as FAILED when AI step throws`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(RuntimeException("AI exploded"))
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.translateError(any())).thenReturn("AI exploded")

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.FAILED, tool.status)
        assertEquals("AI exploded", tool.errorMessage)
        assertNotNull(tool.completedAt)
    }

    @Test
    fun `runGeneration marks tool as FAILED when compile step throws`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1"))
            .thenThrow(IllegalStateException("compile failed"))
        whenever(iconService.determineIcon(any(), any())).thenReturn(null)
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")
        whenever(i18n.translateError(any())).thenReturn("compile failed")

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.FAILED, tool.status)
        assertEquals("compile failed", tool.errorMessage)
    }

    @Test
    fun `runGeneration swallows EntityConcurrentlyDeletedException`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any()))
            .thenThrow(EntityConcurrentlyDeletedException("gone"))
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")

        engine.runGeneration(1L)

        verify(toolRepo, never()).saveConfluenceTool(argThat { confTool -> confTool.status == AnalysisStatus.FAILED })
    }

    @Test
    fun `runGeneration ignores concurrent delete while persisting FAILED state`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenThrow(IllegalStateException("ai failed"))
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.translateError(any())).thenReturn("ai failed")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv ->
            val saved = inv.arguments[0] as ConfluenceTool
            if (saved.status == AnalysisStatus.FAILED) {
                throw EntityConcurrentlyDeletedException("gone")
            }
            saved
        }

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.FAILED, tool.status)
        assertEquals("ai failed", tool.errorMessage)
    }

    @Test
    fun `runGeneration catches icon processing exceptions and continues`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())

        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1")).thenReturn("fun main() = 1" to jarFile.absolutePath)
        whenever(iconService.determineIcon(any(), any())).thenThrow(RuntimeException("icon AI down"))
        whenever(iconService.generateIconPng(any())).thenThrow(RuntimeException("png gen down"))
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.READY, tool.status)
    }

    @Test
    fun `runGeneration persists finalCode when it differs from generatedCode`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())

        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1"))
            .thenReturn("fun main() = 99" to jarFile.absolutePath)
        whenever(iconService.determineIcon(any(), any())).thenReturn(null)
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")

        engine.runGeneration(1L)

        assertEquals("fun main() = 99", tool.generatedCode)
        assertEquals(AnalysisStatus.READY, tool.status)
    }

    @Test
    fun `runGeneration uses empty string when pageContentText is null`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.minimalJar())

        val tool = pendingTool().apply { pageContentText = null }
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(compiler.compileWithRetry(1L, "T", "fun main() = 1")).thenReturn("fun main() = 1" to jarFile.absolutePath)
        whenever(iconService.determineIcon(any(), any())).thenReturn(null)
        whenever(iconService.generateIconPng(any())).thenReturn(null)
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")
        whenever(i18n.t(eq("tooling.be_step_compiling"))).thenReturn("compiling")

        engine.runGeneration(1L)

        assertEquals(AnalysisStatus.READY, tool.status)

        verify(promptBuilder).buildCodeGenerationPrompt("T", "")
    }

    @Test
    fun `runGeneration handles cancel after AI step when jar path is null`() {
        val tool = pendingTool()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { inv -> inv.arguments[0] as ConfluenceTool }
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(promptBuilder.buildCodeGenerationPrompt(any(), any())).thenReturn("prompt")
        whenever(compiler.executeWithCodeRetry(any(), any())).thenReturn("fun main() = 1")
        whenever(i18n.t(eq("tooling.be_step_ai_generating"))).thenReturn("generating")

        cancellations.markCancelled(1L)

        engine.runGeneration(1L)

        verify(toolRepo).deleteConfluenceTool(tool)
    }

    @Test
    fun `checkCancelled consumes the cancellation exactly once`() {
        val tool = pendingTool()
        cancellations.markCancelled(1L)

        assertTrue(engine.checkCancelled(1L, tool))
        assertEquals(false, engine.checkCancelled(1L, tool))
    }
}
