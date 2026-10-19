package com.stacklens.application.service.tooling

import com.stacklens.adapter.out.tooling.FileToolJarStore
import com.stacklens.adapter.out.tooling.ProcessToolJarRuntime
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.tooling.ToolingIconPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.FakeToolingConfigPort
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingServicesSmokeTest {
    private val toolRepo = mock<ConfluenceToolPort>()
    private val confluenceConfigRepo = mock<ConfluenceConfigPort>()
    private val copilotCliService = mock<CopilotPort>()
    private val aiUsageContext = mock<AiUsageContextPort>()
    private val i18n = mock<I18nPort>()
    private val compiler = mock<KotlinCompilerPort>()
    private val promptBuilder = mock<ToolingPromptBuilder>()
    private val iconService = mock<ToolingIconPort>()
    private val asyncExecutor = InlineAsyncExecutor()
    private val generationRunner = mock<ToolingGenerationRunner>()
    private val stackLensConfig = mock<StackLensConfigPort>()
    private val javaRuntime = mock<JavaRuntimePort>()
    private val codeService = mock<ToolingCodeService>()
    private val cancellations = ToolingCancellationRegistry()

    @Test
    fun `all tooling services can be constructed with their dependencies`() {
        val readService =
            ToolingReadService(
                toolRepo = toolRepo,
                confluenceConfigRepo = confluenceConfigRepo,
            )
        val lifecycleService =
            ToolingLifecycleService(
                toolRepo = toolRepo,
                asyncExecutor = asyncExecutor,
                generationRunner = generationRunner,
                i18n = i18n,
                toolJarStore = FileToolJarStore(stackLensConfig),
                cancellations = cancellations,
            )
        val executionService =
            ToolingExecutionService(
                toolRepo = toolRepo,
                toolJarStore = FileToolJarStore(stackLensConfig),
                toolJarRuntime = ProcessToolJarRuntime(javaRuntime),
                i18n = i18n,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val importService =
            ToolingImportService(
                toolJarStore = FileToolJarStore(stackLensConfig),
                toolRepo = toolRepo,
                i18n = i18n,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val codeEditService = ToolingCodeEditService(codeService)
        val generationEngine =
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

        assertNotNull(readService)
        assertNotNull(lifecycleService)
        assertNotNull(executionService)
        assertNotNull(importService)
        assertNotNull(codeEditService)
        assertNotNull(generationEngine)
    }

    @Test
    fun `ConfluenceTool data class works correctly`() {
        val tool =
            ConfluenceTool(
                id = 1L,
                name = "Test Tool",
                description = "A test tool",
                confluencePageUrl = "https://example.com/page",
                status = "READY",
            )
        assertEquals(1L, tool.id)
        assertEquals("Test Tool", tool.name)
        assertEquals("A test tool", tool.description)
        assertEquals("READY", tool.status)
    }

    @Test
    fun `AnalysisStatus constants have expected values`() {
        assertNotNull(AnalysisStatus.PENDING)
        assertNotNull(AnalysisStatus.RUNNING)
        assertNotNull(AnalysisStatus.READY)
        assertNotNull(AnalysisStatus.FAILED)
    }
}
