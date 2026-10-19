package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class LoggingFixCodeStageStreamingTest {
    private val copilot: CopilotStreamingPort = mock()
    private val promptLoader: PromptLoaderPort = mock()
    private val stage = LoggingFixCodeStage(copilot, promptLoader, catalogBackedLoggingFixMarkers())

    private fun context(
        repoRoot: File?,
        chunks: MutableList<Pair<String, String>>,
        diagnosisIncomplete: Boolean = false,
    ) = LoggingFixStageContext(
        logText = "NullPointerException at Foo",
        selectedCategories = listOf("logging"),
        repoPath = repoRoot?.absolutePath,
        repoRoot = repoRoot,
        language = "de",
        onPartial = { stage, chunk -> chunks += stage to chunk },
        diagnosisIncomplete = diagnosisIncomplete,
    )

    private fun stubStream(answer: String) {
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("PROMPT")
        whenever(
            copilot.executeWithStreamingPerLine(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(5).invoke("### FILE: a/B.kt\nbody")
            answer
        }
        whenever(copilot.resolveModel()).thenReturn("model-x")
    }

    @Test
    fun `the code stage streams partial chunks into the run context`(
        @TempDir dir: Path,
    ) {
        stubStream("### FILE: a/B.kt\nplain body\n")
        val chunks = mutableListOf<Pair<String, String>>()

        stage.execute(context(dir.toFile(), chunks))

        assertEquals(listOf("code"), chunks.map { it.first })
        assertTrue(chunks.single().second.contains("FILE"))
    }

    @Test
    fun `the code stage proposes files from the streamed answer`(
        @TempDir dir: Path,
    ) {
        stubStream("intro\n### FILE: src/Main.kt\nfun main() {}\n")
        val chunks = mutableListOf<Pair<String, String>>()
        val ctx = context(dir.toFile(), chunks)

        stage.execute(ctx)

        assertEquals("src/Main.kt", ctx.proposedFiles.single().path)
    }

    @Test
    fun `an incomplete diagnosis skips the code stage`() {
        val chunks = mutableListOf<Pair<String, String>>()
        val ctx = context(null, chunks, diagnosisIncomplete = true)

        stage.execute(ctx)

        assertTrue(ctx.codeOutput.toString().contains("Diagnose nicht m"))
        assertEquals(0, ctx.proposedFiles.size)
    }

    @Test
    fun `a missing repository skips the code stage`() {
        val chunks = mutableListOf<Pair<String, String>>()
        val ctx = context(null, chunks)

        stage.execute(ctx)

        assertTrue(ctx.codeOutput.toString().contains("kein Repository"))
    }
}
