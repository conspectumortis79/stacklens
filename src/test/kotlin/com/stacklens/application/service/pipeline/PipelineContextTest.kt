package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineContextTest {
    private fun newContext(): PipelineContext {
        val copilot: CopilotStreamingPort = org.mockito.kotlin.mock()
        val copilotConfig: CopilotConfigPort = org.mockito.kotlin.mock()
        val i18n: I18nPort = org.mockito.kotlin.mock()
        val aiConfig: AiConfigPort = org.mockito.kotlin.mock()
        val dbConfig: DatabaseConfigPort = org.mockito.kotlin.mock()
        val dbSchema: DatabaseSchemaPort = org.mockito.kotlin.mock()
        val loadPrompt: (String, Map<String, String>) -> String = { _, _ -> "" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val streamFn: StageStreamFn = { _, _, _, _ -> "" }
        val run = AgentPipelineRun(id = 1L, rawText = "", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        return PipelineContext(
            run,
            "m",
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
    fun `default property values are set correctly`() {
        val ctx = newContext()
        assertEquals("(no repository selected)", ctx.repoStructure)
        assertEquals("", ctx.noraOutput)
        assertEquals("", ctx.coleOutput)
        assertEquals("", ctx.maxOutput)
        assertNull(ctx.lenaOutput)
        assertNull(ctx.repoRoot)
    }

    @Test
    fun `mutable properties can be updated`() {
        val ctx = newContext()
        ctx.repoStructure = "/repo/path"
        ctx.noraOutput = "nora result"
        ctx.coleOutput = "cole result"
        ctx.maxOutput = "max result"
        ctx.lenaOutput = "lena result"
        ctx.repoRoot = java.io.File("/some/path")
        assertEquals("/repo/path", ctx.repoStructure)
        assertEquals("nora result", ctx.noraOutput)
        assertEquals("cole result", ctx.coleOutput)
        assertEquals("max result", ctx.maxOutput)
        assertEquals("lena result", ctx.lenaOutput)
        assertEquals(java.io.File("/some/path"), ctx.repoRoot)
    }

    @Test
    fun `constructor stores all positional arguments`() {
        val copilot: CopilotStreamingPort = org.mockito.kotlin.mock()
        val copilotConfig: CopilotConfigPort = org.mockito.kotlin.mock()
        val i18n: I18nPort = org.mockito.kotlin.mock()
        val aiConfig: AiConfigPort = org.mockito.kotlin.mock()
        val dbConfig: DatabaseConfigPort = org.mockito.kotlin.mock()
        val dbSchema: DatabaseSchemaPort = org.mockito.kotlin.mock()
        val loadPrompt: (String, Map<String, String>) -> String = { _, _ -> "prompt" }
        val writeStage: (AgentPipelineRun, String, String) -> Unit = { _, _, _ -> }
        val streamFn: StageStreamFn = { _, _, _, _ -> "" }
        val run = AgentPipelineRun(id = 99L, rawText = "", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        val ctx =
            PipelineContext(
                run,
                "gpt-test",
                copilot,
                copilotConfig,
                i18n,
                aiConfig,
                dbConfig,
                dbSchema,
                loadPrompt,
                writeStage,
                streamFn,
                java.io.File("/foo"),
            )
        assertEquals(run, ctx.run)
        assertEquals("gpt-test", ctx.model)
        assertEquals(java.io.File("/foo"), ctx.repoRoot)
        assertEquals(copilot, ctx.copilot)
        assertEquals(copilotConfig, ctx.copilotConfig)
        assertEquals(i18n, ctx.i18n)
        assertEquals(aiConfig, ctx.aiConfigRepo)
        assertEquals(dbConfig, ctx.dbConfigRepo)
        assertEquals(dbSchema, ctx.dbSchemaPort)
        assertEquals(loadPrompt, ctx.loadAgentPrompt)
        assertEquals(writeStage, ctx.writeStageOutput)
        assertEquals(streamFn, ctx.stream)
    }
}
