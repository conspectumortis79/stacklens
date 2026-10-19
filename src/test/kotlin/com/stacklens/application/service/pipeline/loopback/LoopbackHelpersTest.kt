package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.AgentContextHelpers
import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.StageStreamFn
import com.stacklens.application.service.pipeline.fayeOutput
import com.stacklens.application.service.pipeline.finnOutput
import com.stacklens.application.service.pipeline.quinnOutput
import com.stacklens.application.service.pipeline.sydOutput
import com.stacklens.application.service.pipeline.tessOutput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.io.File

class LoopbackHelpersTest {
    private val log = LoggerFactory.getLogger(LoopbackHelpersTest::class.java)

    private fun newRun(
        repoPath: String? = null,
        mockupImages: List<MockupImage> = emptyList(),
    ): AgentPipelineRun =
        AgentPipelineRun(
            id = 7L,
            rawText = "raw",
            repoPath = repoPath,
            featureSlug = "feat",
            mockupImages = mockupImages,
            createdAt = System.currentTimeMillis(),
        )

    private fun newCtx(
        run: AgentPipelineRun,
        stream: StageStreamFn,
    ): PipelineContext {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfigRepo: AiConfigPort = mock()
        val dbConfigRepo: DatabaseConfigPort = mock()
        val dbSchemaPort: DatabaseSchemaPort = mock()
        return PipelineContext(
            run = run,
            model = "gpt-4o",
            copilot = copilot,
            copilotConfig = copilotConfig,
            i18n = i18n,
            aiConfigRepo = aiConfigRepo,
            dbConfigRepo = dbConfigRepo,
            dbSchemaPort = dbSchemaPort,
            loadAgentPrompt = { _, vars -> "PROMPT" + "\n" + vars.entries.joinToString("\n") { "${it.key}=${it.value}" } },
            writeStageOutput = { _, _, _ -> },
            stream = stream,
            repoRoot = run.repoPath?.let { File(it) },
        )
    }

    @Test
    fun `stripMarker returns the input unchanged when the marker is not present`() {
        val out = stripMarker("hello world", HANDOFF_TO_FINN, 1L, "X", log)
        assertEquals("hello world", out)
    }

    @Test
    fun `stripMarker strips the marker and logs a warning when present`() {
        val input = "partA\n$HANDOFF_TO_FINN\npartB"
        val out = stripMarker(input, HANDOFF_TO_FINN, 42L, "Loopback", log)
        assertFalse(out.contains(HANDOFF_TO_FINN))
        assertTrue(out.startsWith("partA"))
    }

    @Test
    fun `runFinnWithHandoff streams new content and updates run state`() {
        val run = newRun()
        run.finn.status = StageStatus.PENDING
        val capturedOutputs = mutableListOf<String>()
        val streamFn: StageStreamFn = { _, _, _, onChunk ->
            onChunk("first chunk")
            onChunk("second chunk")
            "full-finn-output"
        }
        val ctx = newCtx(run, streamFn)
        runFinnWithHandoff(
            ctx = ctx,
            handoffBlock = "HANDOFF",
            successLogTemplate = "[Pipeline {}] done ({} chars)",
            log = log,
        )
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals("finn", run.currentStage)
        assertTrue(run.finn.output.contains("Finn fix-pass"))
        assertTrue(ctx.finnOutput.contains("Finn fix-pass"))
        assertTrue(ctx.finnOutput.contains("full-finn-output"))
    }

    @Test
    fun `runFinnWithHandoff does not strip a stray HANDOFF_TO_FINN marker from the output (it is appended after) `() {
        val run = newRun()
        run.finn.status = StageStatus.PENDING
        val streamFn: StageStreamFn = { _, _, _, _ -> "out" }
        val ctx = newCtx(run, streamFn)
        runFinnWithHandoff(ctx, "HANDOFF", "msg {} {}", log)
        assertTrue(run.finn.output.contains("Finn fix-pass"))
    }

    @Test
    fun `runFinnWithHandoff uses a custom fixPassLabel`() {
        val run = newRun()
        run.finn.status = StageStatus.PENDING
        val streamFn: StageStreamFn = { _, _, _, _ -> "out" }
        val ctx = newCtx(run, streamFn)
        runFinnWithHandoff(
            ctx = ctx,
            handoffBlock = "X",
            successLogTemplate = "{} {}",
            log = log,
            fixPassLabel = "My custom label",
        )
        assertTrue(run.finn.output.contains("My custom label"))
    }

    @Test
    fun `runFayeWithUpdatedFinn strips the Finn handoff marker and updates state`() {
        val run = newRun()
        run.finn.output = "priorFinn"
        val streamFn: StageStreamFn = { _, _, _, _ -> "faye-output\n$HANDOFF_TO_FINN\nmore" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "priorFinn"
        runFayeWithUpdatedFinn(ctx, log)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals("faye", run.currentStage)
        assertFalse(ctx.fayeOutput.contains(HANDOFF_TO_FINN))
    }

    @Test
    fun `runTessWithUpdatedFinn strips the Finn handoff marker from output`() {
        val run = newRun()
        val streamFn: StageStreamFn = { _, _, _, _ -> "tess-content\n$HANDOFF_TO_FINN\njunk" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runTessWithUpdatedFinn(ctx, log)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals("tess", run.currentStage)
        assertFalse(ctx.tessOutput.contains(HANDOFF_TO_FINN))
    }

    @Test
    fun `runSydWithUpdatedFinnAndTess with repoRoot null uses repoConfigured false`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd-out"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithUpdatedFinnAndTess(ctx, log)
        assertTrue(promptText!!.contains("repoConfigured=false"))
    }

    @Test
    fun `runSydWithUpdatedFinnAndTess with repoRoot set uses repoConfigured true`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd-out"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithUpdatedFinnAndTess(ctx, log)
        assertTrue(promptText!!.contains("repoConfigured=true"))
    }

    @Test
    fun `runTessWithQuinnHandoff with repoRoot null uses repoConfigured false`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        runTessWithQuinnHandoff(ctx, "Q", log)
        assertTrue(promptText!!.contains("repoConfigured=false"))
    }

    @Test
    fun `runTessWithQuinnHandoff with repoRoot set uses repoConfigured true`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        runTessWithQuinnHandoff(ctx, "Q", log)
        assertTrue(promptText!!.contains("repoConfigured=true"))
    }

    @Test
    fun `runSydWithQuinnHandoff with repoRoot null uses repoConfigured false`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithQuinnHandoff(ctx, "Q", log)
        assertTrue(promptText!!.contains("repoConfigured=false"))
    }

    @Test
    fun `runSydWithQuinnHandoff with repoRoot set uses repoConfigured true`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithQuinnHandoff(ctx, "Q", log)
        assertTrue(promptText!!.contains("repoConfigured=true"))
    }

    @Test
    fun `runTessWithSageHandoff with repoRoot null uses repoConfigured false`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        runTessWithSageHandoff(ctx, "S", log)
        assertTrue(promptText!!.contains("repoConfigured=false"))
    }

    @Test
    fun `runTessWithSageHandoff with repoRoot set uses repoConfigured true`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        runTessWithSageHandoff(ctx, "S", log)
        assertTrue(promptText!!.contains("repoConfigured=true"))
    }

    @Test
    fun `runSydWithSageHandoff with repoRoot null uses repoConfigured false`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithSageHandoff(ctx, "S", log)
        assertTrue(promptText!!.contains("repoConfigured=false"))
    }

    @Test
    fun `runSydWithSageHandoff with repoRoot set uses repoConfigured true`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "syd"
        }
        val ctx = newCtx(run, streamFn)
        runSydWithSageHandoff(ctx, "S", log)
        assertTrue(promptText!!.contains("repoConfigured=true"))
    }

    @Test
    fun `runTessWithQuinnHandoff runs tess with the given quinn block`() {
        val run = newRun()
        val streamFn: StageStreamFn = { _, _, _, _ -> "tessOutput" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runTessWithQuinnHandoff(ctx, "QUINN-BLOCK", log)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals("tess", run.currentStage)
        assertEquals("tessOutput", ctx.tessOutput)
    }

    @Test
    fun `runSydWithQuinnHandoff runs syd with the given quinn block`() {
        val run = newRun()
        val streamFn: StageStreamFn = { _, _, _, _ -> "sydOutput" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        ctx.tessOutput = "tess"
        runSydWithQuinnHandoff(ctx, "QUINN-SYD-BLOCK", log)
        assertEquals(StageStatus.DONE, run.syd.status)
        assertEquals("syd", run.currentStage)
        assertEquals("sydOutput", ctx.sydOutput)
    }

    @Test
    fun `runQuinnWithUpdatedTessAndSyd concatenates tess plus syd into the prompt body`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "quinn-output"
        }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        ctx.tessOutput = "tess-out"
        ctx.sydOutput = "syd-out"
        runQuinnWithUpdatedTessAndSyd(ctx, log)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals("quinn", run.currentStage)
        assertEquals("quinn-output", ctx.quinnOutput)
        val capturedPrompt = promptText
        assertTrue(capturedPrompt != null && capturedPrompt.contains("tess-out"))
        assertTrue(capturedPrompt != null && capturedPrompt.contains("syd-out"))
    }

    @Test
    fun `runTessWithSageHandoff runs tess with the given sage block`() {
        val run = newRun()
        val streamFn: StageStreamFn = { _, _, _, _ -> "tessAfterSage" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runTessWithSageHandoff(ctx, "SAGE-BLOCK", log)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals("tess", run.currentStage)
        assertEquals("tessAfterSage", ctx.tessOutput)
    }

    @Test
    fun `runSydWithSageHandoff runs syd with the given sage block`() {
        val run = newRun()
        val streamFn: StageStreamFn = { _, _, _, _ -> "sydAfterSage" }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        ctx.tessOutput = "tess"
        runSydWithSageHandoff(ctx, "SAGE-BLOCK", log)
        assertEquals(StageStatus.DONE, run.syd.status)
        assertEquals("syd", run.currentStage)
        assertEquals("sydAfterSage", ctx.sydOutput)
    }

    @Test
    fun `runFayeWithUpdatedFinn builds a mockup section when run has mockups`() {
        val mockup = MockupImage(base64 = "AAA", mimeType = "image/png")
        val run = newRun(mockupImages = listOf(mockup))
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "faye-out"
        }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runFayeWithUpdatedFinn(ctx, log)
        val section = AgentContextHelpers.buildMockupSection(listOf(mockup))
        assertTrue(section.contains("image/png"))
        assertTrue(promptText!!.contains(section))
    }

    @Test
    fun `runTessWithUpdatedFinn uses repoConfigured true when repoRoot is set`() {
        val run = newRun(repoPath = "/tmp/repo")
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runTessWithUpdatedFinn(ctx, log)
        assertTrue(promptText!!.contains("true"))
    }

    @Test
    fun `runTessWithUpdatedFinn uses repoConfigured false when repoRoot is null`() {
        val run = newRun()
        var promptText: String? = null
        val streamFn: StageStreamFn = { _, prompt, _, _ ->
            promptText = prompt
            "tess"
        }
        val ctx = newCtx(run, streamFn)
        ctx.finnOutput = "fin"
        runTessWithUpdatedFinn(ctx, log)
        assertTrue(promptText!!.contains("false"))
    }

    @Test
    fun `AgentContextHelpers buildMockupSection returns empty string for empty list`() {
        assertEquals("", AgentContextHelpers.buildMockupSection(emptyList()))
    }

    @Test
    fun `AgentContextHelpers buildMockupSection includes the base64 of each image`() {
        val mocks =
            listOf(
                MockupImage(base64 = "ABCD", mimeType = "image/png"),
                MockupImage(base64 = "WXYZ", mimeType = "image/jpeg"),
            )
        val section = AgentContextHelpers.buildMockupSection(mocks)
        assertTrue(section.contains("ABCD"))
        assertTrue(section.contains("WXYZ"))
    }

    @Test
    fun `AgentContextHelpers buildMockupSection includes a real svg source for the svg mime type`() {
        val mocks = listOf(MockupImage(base64 = "PHN2Zy8+", mimeType = "image/svg+xml"))
        val section = AgentContextHelpers.buildMockupSection(mocks)
        assertTrue(section.contains("Mockup"))
    }

    @Test
    fun `AgentContextHelpers buildMockupSection skips null and blank base64 entries`() {
        val mocks =
            listOf(
                MockupImage(base64 = "ok", mimeType = "image/png"),
                MockupImage(base64 = "", mimeType = "image/png"),
            )
        val section = AgentContextHelpers.buildMockupSection(mocks)
        assertTrue(section.contains("ok"))
    }
}
