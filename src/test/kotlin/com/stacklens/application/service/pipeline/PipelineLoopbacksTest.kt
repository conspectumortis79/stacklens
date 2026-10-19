package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.pipeline.loopback.FayeFinnLoopback
import com.stacklens.application.service.pipeline.loopback.QuinnLoopback
import com.stacklens.application.service.pipeline.loopback.SageTessLoopback
import com.stacklens.application.service.pipeline.loopback.SydFinnLoopback
import com.stacklens.application.service.pipeline.loopback.TessFinnLoopback
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PipelineLoopbacksTest {
    private fun newContext(model: String = "test-model"): PipelineContext {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfig: AiConfigPort = mock()
        val dbConfig: DatabaseConfigPort = mock()
        val dbSchema: DatabaseSchemaPort = mock()
        val loadPrompt: (String, Map<String, String>) -> String = { _, _ -> "stubbed-prompt" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, _, _, onChunk ->
            onChunk("partial")
            "stubbed-stream"
        }
        val run = AgentPipelineRun(id = 42L, rawText = "demo", repoPath = null, featureSlug = "demo", createdAt = System.currentTimeMillis())
        return PipelineContext(
            run,
            model,
            copilot,
            copilotConfig,
            i18n,
            aiConfig,
            dbConfig,
            dbSchema,
            loadPrompt,
            writeStage,
            streamFn,
            null,
        )
    }

    @Test
    fun `FayeFinn returns false when faye output does NOT contain marker`() {
        val ctx = newContext()
        ctx.fayeOutput = "no marker here"
        val result = FayeFinnLoopback().runIfRequested(ctx)
        assertFalse(result)
    }

    @Test
    fun `FayeFinn triggers re-run when marker is present`() {
        val ctx = newContext()
        ctx.fayeOutput = "Some text 🔄 HANDOFF_TO_FINN do something"
        val result = FayeFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(ctx.fayeOutput.isNotEmpty())
    }

    @Test
    fun `FayeFinn still returns true when run is cancelled during faye re-run`() {
        val ctx = newContext()
        ctx.fayeOutput = "before 🔄 HANDOFF_TO_FINN after"
        ctx.run.cancelled = true
        val result = FayeFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(ctx.run.cancelled)
    }

    @Test
    fun `TessFinn returns false when tess output does NOT contain marker`() {
        val ctx = newContext()
        ctx.tessOutput = "no marker here"
        assertFalse(TessFinnLoopback().runIfRequested(ctx))
    }

    @Test
    fun `TessFinn triggers re-run when marker is present`() {
        val ctx = newContext()
        ctx.tessOutput = "tests done 🔄 HANDOFF_TO_FINN please fix"
        val result = TessFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(ctx.tessOutput.isNotEmpty())
    }

    @Test
    fun `TessFinn still returns true when cancelled mid-loop`() {
        val ctx = newContext()
        ctx.tessOutput = "before 🔄 HANDOFF_TO_FINN after"
        ctx.run.cancelled = true
        val result = TessFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `SydFinn returns false when syd output does NOT contain marker`() {
        val ctx = newContext()
        ctx.sydOutput = "no marker"
        assertFalse(SydFinnLoopback().runIfRequested(ctx))
    }

    @Test
    fun `SydFinn triggers full Finn+Tess+Syd re-run chain when marker is present`() {
        val ctx = newContext()
        ctx.sydOutput = "sys tests done 🔄 HANDOFF_TO_FINN fix production bug"
        val result = SydFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `SydFinn short-circuits via run cancelled flag`() {
        val ctx = newContext()
        ctx.sydOutput = "X 🔄 HANDOFF_TO_FINN Y"
        ctx.run.cancelled = true
        val result = SydFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(ctx.run.cancelled)
    }

    @Test
    fun `SydFinn short-circuits when cancelled during Tess re-run`() {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfig: AiConfigPort = mock()
        val dbConfig: DatabaseConfigPort = mock()
        val dbSchema: DatabaseSchemaPort = mock()
        val loadPrompt: (String, Map<String, String>) -> String = { agentName, _ -> "prompt for $agentName" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val run = AgentPipelineRun(id = 42L, rawText = "demo", repoPath = null, featureSlug = "demo", createdAt = System.currentTimeMillis())
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, prompt, _, onChunk ->
            if (prompt.contains("tess")) {
                run.cancelled = true
            }
            onChunk("partial")
            "stubbed-stream"
        }
        val ctx =
            PipelineContext(
                run,
                "test-model",
                copilot,
                copilotConfig,
                i18n,
                aiConfig,
                dbConfig,
                dbSchema,
                loadPrompt,
                writeStage,
                streamFn,
                null,
            )
        ctx.sydOutput = "X 🔄 HANDOFF_TO_FINN Y"
        val result = SydFinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(run.cancelled)
    }

    @Test
    fun `QuinnLoopback returns false when no markers are present`() {
        val ctx = newContext()
        ctx.quinnOutput = "all good, no follow-up needed"
        assertFalse(QuinnLoopback().runIfRequested(ctx))
    }

    @Test
    fun `QuinnLoopback triggers Tess-only re-run when only HANDOFF_TO_TESS is present`() {
        val ctx = newContext()
        ctx.quinnOutput = "review done 🔄 HANDOFF_TO_TESS add more tests"
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `QuinnLoopback triggers Syd-only re-run when only HANDOFF_TO_SYD is present`() {
        val ctx = newContext()
        ctx.quinnOutput = "review done 🔄 HANDOFF_TO_SYD expand system tests"
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `QuinnLoopback triggers both Tess and Syd when both markers are present`() {
        val ctx = newContext()
        ctx.quinnOutput = "🔄 HANDOFF_TO_TESS add tests 🔄 HANDOFF_TO_SYD expand system tests"
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `QuinnLoopback short-circuits when cancelled after Tess`() {
        val ctx = newContext()
        ctx.quinnOutput = "🔄 HANDOFF_TO_TESS more tests"
        ctx.run.cancelled = true
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `QuinnLoopback short-circuits when cancelled during Tess-redux re-Tess run`() {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfig: AiConfigPort = mock()
        val dbConfig: DatabaseConfigPort = mock()
        val dbSchema: DatabaseSchemaPort = mock()
        val loadPrompt: (String, Map<String, String>) -> String = { agentName, _ -> "prompt for $agentName" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val run = AgentPipelineRun(id = 42L, rawText = "demo", repoPath = null, featureSlug = "demo", createdAt = System.currentTimeMillis())
        val tessCallCount = intArrayOf(0)
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, prompt, _, onChunk ->
            onChunk("partial")
            if (prompt == "prompt for tess") {
                tessCallCount[0] += 1
                if (tessCallCount[0] == 2) {
                    run.cancelled = true
                }
            }
            "stubbed-stream"
        }
        val ctx =
            PipelineContext(
                run,
                "test-model",
                copilot,
                copilotConfig,
                i18n,
                aiConfig,
                dbConfig,
                dbSchema,
                loadPrompt,
                writeStage,
                streamFn,
                null,
            )
        ctx.quinnOutput = "🔄 HANDOFF_TO_TESS more tests"
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `QuinnLoopback short-circuits when cancelled during Syd re-run`() {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfig: AiConfigPort = mock()
        val dbConfig: DatabaseConfigPort = mock()
        val dbSchema: DatabaseSchemaPort = mock()
        val loadPrompt: (String, Map<String, String>) -> String = { agentName, _ -> "prompt for $agentName" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val run = AgentPipelineRun(id = 42L, rawText = "demo", repoPath = null, featureSlug = "demo", createdAt = System.currentTimeMillis())
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, prompt, _, onChunk ->
            if (prompt.contains("syd")) {
                run.cancelled = true
            }
            onChunk("partial")
            "stubbed-stream"
        }
        val ctx =
            PipelineContext(
                run,
                "test-model",
                copilot,
                copilotConfig,
                i18n,
                aiConfig,
                dbConfig,
                dbSchema,
                loadPrompt,
                writeStage,
                streamFn,
                null,
            )
        ctx.quinnOutput = "🔄 HANDOFF_TO_SYD expand system tests"
        val result = QuinnLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(run.cancelled)
    }

    @Test
    fun `SageTess returns false when sage output does NOT contain HANDOFF_TO_TESS`() {
        val ctx = newContext()
        ctx.sageOutput = "security done, no redux"
        assertFalse(SageTessLoopback().runIfRequested(ctx))
    }

    @Test
    fun `SageTess triggers full chain when HANDOFF_TO_TESS is present`() {
        val ctx = newContext()
        ctx.sageOutput = "🔄 HANDOFF_TO_TESS add security tests"
        val result = SageTessLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `SageTess short-circuits when cancelled during Tess re-run`() {
        val ctx = newContext()
        ctx.sageOutput = "🔄 HANDOFF_TO_TESS add tests"
        ctx.run.cancelled = true
        val result = SageTessLoopback().runIfRequested(ctx)
        assertTrue(result)
    }

    @Test
    fun `SageTess short-circuits when cancelled during Syd re-run`() {
        val copilot: CopilotStreamingPort = mock()
        val copilotConfig: CopilotConfigPort = mock()
        val i18n: I18nPort = mock()
        val aiConfig: AiConfigPort = mock()
        val dbConfig: DatabaseConfigPort = mock()
        val dbSchema: DatabaseSchemaPort = mock()
        val loadPrompt: (String, Map<String, String>) -> String = { agentName, _ -> "prompt for $agentName" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val run = AgentPipelineRun(id = 42L, rawText = "demo", repoPath = null, featureSlug = "demo", createdAt = System.currentTimeMillis())
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, prompt, _, onChunk ->
            if (prompt.contains("syd")) {
                run.cancelled = true
            }
            onChunk("partial")
            "stubbed-stream"
        }
        val ctx =
            PipelineContext(
                run,
                "test-model",
                copilot,
                copilotConfig,
                i18n,
                aiConfig,
                dbConfig,
                dbSchema,
                loadPrompt,
                writeStage,
                streamFn,
                null,
            )
        ctx.sageOutput = "🔄 HANDOFF_TO_TESS add tests"
        val result = SageTessLoopback().runIfRequested(ctx)
        assertTrue(result)
        assertTrue(run.cancelled)
    }
}
