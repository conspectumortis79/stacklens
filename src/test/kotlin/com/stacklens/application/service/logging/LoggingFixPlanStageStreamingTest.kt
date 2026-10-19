package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class LoggingFixPlanStageStreamingTest {
    private val copilot = mock<CopilotStreamingPort>()
    private val promptLoader = mock<PromptLoaderPort>()
    private val stage = LoggingFixPlanStage(copilot, promptLoader, catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())

    private fun context(repoRoot: File) =
        LoggingFixStageContext(
            logText = "log",
            selectedCategories = listOf("logging"),
            repoPath = repoRoot.path,
            repoRoot = repoRoot,
            language = "de",
            diagnoseOutput = StringBuilder("diagnose"),
            onPartial = { _, chunk -> streamed += chunk },
        )

    private val streamed = mutableListOf<String>()

    private fun streamChunks(invocation: InvocationOnMock): String {
        for (argument in invocation.arguments) {
            if (argument is Function1<*, *>) {
                @Suppress("UNCHECKED_CAST")
                (argument as (String) -> Unit)("partial line")
            }
        }
        return "plan output"
    }

    @Test
    fun `the plan stage forwards every streamed chunk of the copilot answer`(
        @TempDir repo: Path,
    ) {
        whenever(promptLoader.get(any(), any(), any())).thenReturn("PROMPT")
        whenever(copilot.resolveModel()).thenReturn("model")
        whenever(
            copilot.executeWithStreamingPerLine(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
            ),
        ).thenAnswer { invocation -> streamChunks(invocation) }

        val ctx = context(repo.toFile())
        stage.execute(ctx)

        assertEquals("plan output", ctx.planOutput.toString())
        assertEquals(listOf("partial line"), streamed)
    }
}
