package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NoraStageBranchCoverageTest {
    private val prompts = mutableListOf<Map<String, String>>()
    private val written = mutableListOf<Triple<String, String, Int>>()

    private fun context(
        run: AgentPipelineRun,
        aiAnswer: String,
    ): PipelineContext =
        PipelineContext(
            run,
            "model-x",
            mock<CopilotStreamingPort>(),
            mock<CopilotConfigPort>(),
            mock<I18nPort>(),
            mock<AiConfigPort>(),
            mock<DatabaseConfigPort>(),
            mock<DatabaseSchemaPort>(),
            { _, vars ->
                prompts += vars
                "prompt-body"
            },
            { _, stage, content -> written += Triple(stage, content, content.length) },
            { _, _, _, _ -> aiAnswer },
            null,
        )

    private fun run(text: String) =
        AgentPipelineRun(
            id = 7L,
            rawText = text,
            repoPath = null,
            featureSlug = "fallback-slug",
            createdAt = 1L,
        )

    @Test
    fun `falls back to the raw text and keeps the detected ticket key`() {
        val run = run("raw requirement")
        run.detectedTicketKey = "PROJ-1"
        val ctx = context(run, "**Summary**: JWT auth rollout")

        NoraStage().execute(ctx)

        assertEquals("raw requirement", prompts.first()["rawText"])
        assertEquals("PROJ-1", run.featureSlug)
        assertEquals(listOf("nora"), written.map { it.first })
    }

    @Test
    fun `trims the completion banner of both stages and derives the slug from the summary`() {
        val run = run("raw requirement")
        run.detectedTicketKey = "   "
        run.dbSchema = "CREATE TABLE users"
        val ctx = context(run, "Some analysis\n**Summary**: JWT auth rollout\n> ✅ Nora complete\n\n")
        ctx.lenaOutput = "plan\n> ✅ Lena complete\n\n"

        NoraStage().execute(ctx)

        assertEquals("plan", prompts.first()["rawText"])
        assertEquals("Some analysis\n**Summary**: JWT auth rollout", run.nora.output)
        assertTrue(run.featureSlug.isNotBlank())
        assertEquals("jwt-auth-rollout", run.featureSlug)
        assertEquals(listOf("lena", "nora"), written.map { it.first })
        assertTrue(prompts.first()["dbSchema"]!!.contains("CREATE TABLE users"))
    }

    @Test
    fun `keeps the previous slug when the answer has no summary`() {
        val run = run("raw requirement")
        val ctx = context(run, "prose without a summary section")

        NoraStage().execute(ctx)

        assertEquals("fallback-slug", run.featureSlug)
        assertEquals("prose without a summary section", ctx.noraOutput)
    }
}
