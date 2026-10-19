package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.ai.CopilotAiCaller
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor

class CopilotAiCallerCoverageTest {
    private val copilot = mock<CopilotPort>()
    private val caller = CopilotAiCaller(copilot)

    @Test
    fun `the ai caller forwards model prompt and timeout to the copilot port`() {
        whenever(copilot.execute("gpt-test", "prompt text", 42L)).thenReturn("answer")

        assertEquals("answer", caller.execute("gpt-test", "prompt text", 42L))
    }
}

class CopilotPipelineRegistryCoverageTest {
    private val registry = CopilotPipelineRegistry(Executor { })

    @Test
    fun `run ids increase and are seeded from the persisted maximum`() {
        registry.seed(41L)

        assertEquals(42L, registry.nextRunId())
    }

    @Test
    fun `clear drops every registered run`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "slug",
                createdAt = 0L,
            )
        registry.put(1L, run)

        registry.clear()

        assertTrue(registry.all().isEmpty())
    }
}

class RexStageRunnerDefaultsCoverageTest {
    @Test
    fun `the rex runner ships without additional test stages by default`() {
        assertEquals(emptyList<Any>(), RexStageRunner.DEFAULT_TEST_STAGES)
    }
}

class KiChatAttachmentPreparerMimeTypeCoverageTest {
    private val preparer = KiChatAttachmentPreparer(mock())

    @Test
    fun `only raster image mime types are supported and the check is case insensitive`() {
        assertTrue(preparer.isSupportedMimeType("IMAGE/PNG"))
        assertTrue(preparer.isSupportedMimeType("image/tiff"))
        assertFalse(preparer.isSupportedMimeType("application/pdf"))
        assertTrue(preparer.prepare(emptyList()).isEmpty())
    }
}

class LocalRepoServiceStackDetectionCoverageTest {
    private val service =
        LocalRepoService(
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock<I18nPort>(),
            PassthroughDemoConfigFilter.instance,
            JavaFileSystemAdapter(),
        )

    @Test
    fun `a directory exposes its build files as stack indicators`() {
        val dir =
            java.nio.file.Files
                .createTempDirectory("stacklens-stack")
                .toFile()
                .canonicalFile
        java.io.File(dir, "build.gradle.kts").writeText("plugins { }")
        java.io.File(dir, "package.json").writeText("{}")

        val detected = service.detectStack(dir.path)

        assertEquals(listOf("build.gradle.kts", "package.json"), detected.indicators)
        assertEquals(listOf("kotlin", "node"), detected.stacks)
        dir.delete()
    }

    @Test
    fun `a missing directory reports no stack at all`() {
        val detected = service.detectStack("/definitely/not/a/directory/here")

        assertTrue(detected.indicators.isEmpty())
        assertTrue(detected.stacks.isEmpty())
    }

    @Test
    fun `a file instead of a directory reports no stack`() {
        val file =
            java.nio.file.Files
                .createTempFile("stacklens-stack", ".txt")
                .toFile()

        val detected = service.detectStack(file.path)

        assertTrue(detected.indicators.isEmpty())
        file.delete()
    }
}
