package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.File

class StageExecutorTest {
    private fun pipelineStage(stageName: String): PipelineStage =
        when (stageName) {
            "nora" -> NoraStage()
            "cole" -> ColeStage()
            else -> VeraStage()
        }

    private val copilot = RecordingCopilot()

    private val written = mutableListOf<Triple<Long, String, String>>()

    private val prompts = mutableListOf<Pair<String, Map<String, String>>>()

    private val stage = AgentStage(name = "nora")

    private val run =
        AgentPipelineRun(
            id = 7L,
            rawText = "raw requirement",
            repoPath = null,
            featureSlug = "demo",
            createdAt = 1L,
        )

    private val context =
        PipelineContext(
            run = run,
            model = "gpt-test",
            copilot = copilot,
            copilotConfig =
                object : CopilotConfigPort {
                    override val pipelineTimeoutSeconds: Long = 42L

                    override val loadTestAnalysisTimeoutSeconds: Long = 1L

                    override val searchAnalysisTimeoutSeconds: Long = 1L

                    override val reviewAnalysisTimeoutSeconds: Long = 1L
                },
            i18n = mock<I18nPort>(),
            aiConfigRepo = mock<AiConfigPort>(),
            dbConfigRepo = mock<DatabaseConfigPort>(),
            dbSchemaPort = mock<DatabaseSchemaPort>(),
            loadAgentPrompt = { key, inputs ->
                prompts += key to inputs
                "prompt for $key"
            },
            writeStageOutput = { target, stageName, output ->
                written += Triple(target.id, stageName, output)
            },
            stream = { model, prompt, repoRoot, onChunk ->
                copilot.streamCalls += StreamingCall(model, prompt, repoRoot)
                listOf("streamed ", "result").forEach(onChunk)
                "streamed result"
            },
            repoRoot = File("/repo"),
        )

    @Test
    fun `execute streams the stage output line by line through the copilot port`() {
        val target = pipelineStage("nora")

        val output =
            StageExecutor.execute(
                stage = target,
                ctx = context,
                slot = { stage },
                promptInputs = mapOf("slug" to "demo"),
                send = StageExecutor.viaCopilot(context),
            )

        assertEquals("stage output", output)
        assertEquals(StageStatus.DONE, stage.status)
        assertEquals(listOf("nora" to mapOf("slug" to "demo")), prompts)
        assertEquals(listOf(Triple(7L, "nora", "stage output")), written)
        assertEquals(
            listOf(CopilotCall(model = "gpt-test", timeout = 42L, prompt = "prompt for nora")),
            copilot.perLineCalls,
        )
        assertEquals("nora", run.currentStage)
    }

    @Test
    fun `execute can transform the raw answer before it is stored`() {
        val target = pipelineStage("cole")

        val output =
            StageExecutor.execute(
                stage = target,
                ctx = context,
                slot = { stage },
                promptInputs = emptyMap(),
                send = StageExecutor.viaCopilot(context),
                transform = { raw -> "[$raw]" },
            )

        assertEquals("[stage output]", output)
        assertEquals("[stage output]", stage.output)
        assertEquals(listOf(Triple(7L, "cole", "[stage output]")), written)
    }

    @Test
    fun `execute streams through the injected stream function when one is supplied`() {
        val target = pipelineStage("vera")

        val output =
            StageExecutor.execute(
                stage = target,
                ctx = context,
                slot = { stage },
                promptInputs = emptyMap(),
                send = StageExecutor.viaStream(context),
            )

        assertEquals("streamed result", output)
        assertEquals("streamed result", stage.output)
        assertEquals(StageStatus.DONE, stage.status)
        assertEquals("vera", run.currentStage)
        assertEquals(listOf("vera" to emptyMap<String, String>()), prompts)
        assertEquals(listOf(Triple(7L, "vera", "streamed result")), written)
        assertEquals(
            listOf(StreamingCall("gpt-test", "prompt for vera", File("/repo"))),
            copilot.streamCalls,
        )
    }

    private class RecordingCopilot : CopilotStreamingPort {
        val perLineCalls: MutableList<CopilotCall> = mutableListOf()

        val streamCalls: MutableList<StreamingCall> = mutableListOf()

        override fun resolveToken(): String? = null

        override fun resolveModel(): String = "gpt-test"

        override fun resolveLanguage(): String = "de"

        override fun execute(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            attachments: List<AiAttachment>,
        ): String = "plain output"

        override fun executeWithStreaming(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            onPartialOutput(prompt)
            return "streamed output"
        }

        override fun executeWithStreamingPerLine(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            repoDir: File?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            perLineCalls += CopilotCall(model, timeoutOverride, prompt)
            onPartialOutput("partial ")
            return "stage output"
        }
    }

    private data class CopilotCall(
        val model: String,
        val timeout: Long?,
        val prompt: String,
    )

    private data class StreamingCall(
        val model: String,
        val prompt: String,
        val repoRoot: File?,
    )
}
