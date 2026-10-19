package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StageOutputsTest {
    private val outputs = StageOutputs()

    @Test
    fun `a recorded stage is readable again and unknown stages answer as empty`() {
        outputs.record("max", "plan")

        assertEquals("plan", outputs.of("max"))
        assertEquals("plan", outputs.optional("max"))
        assertEquals("", outputs.of("never-recorded"))
        assertNull(outputs.optional("never-recorded"))
    }

    @Test
    fun `recording without output removes the stage again`() {
        outputs.record("finn", "code")
        outputs.record("finn", null)

        assertNull(outputs.optional("finn"))
    }

    @Test
    fun `the registry keeps the stages in recording order`() {
        outputs.record("nora", "requirements")
        outputs.record("cole", "plan")
        outputs.record("max", "architecture")

        assertEquals(listOf("nora", "cole", "max"), outputs.asMap().keys.toList())
        assertEquals(listOf("requirements", "plan", "architecture"), outputs.valuesFor(listOf("nora", "cole", "max")))
        assertEquals(listOf("plan", ""), outputs.valuesFor(listOf("cole", "never-recorded")))
    }

    @Test
    fun `each stage writes and reads through the same registry slot`() {
        val context = newContext()

        context.noraOutput = "requirements"
        context.coleOutput = "plan"
        context.maxOutput = "architecture"
        context.finnOutput = "code"
        context.fayeOutput = "frontend"
        context.veraOutput = "infrastructure"
        context.tessOutput = "unit tests"
        context.sydOutput = "system tests"
        context.quinnOutput = "quality"
        context.sageOutput = "security"
        context.lenaOutput = "pre analysis"

        assertEquals("requirements", context.noraOutput)
        assertEquals("plan", context.coleOutput)
        assertEquals("architecture", context.maxOutput)
        assertEquals("code", context.finnOutput)
        assertEquals("frontend", context.fayeOutput)
        assertEquals("infrastructure", context.veraOutput)
        assertEquals("unit tests", context.tessOutput)
        assertEquals("system tests", context.sydOutput)
        assertEquals("quality", context.quinnOutput)
        assertEquals("security", context.sageOutput)
        assertEquals("pre analysis", context.lenaOutput)
        assertEquals(
            listOf("requirements", "plan", "architecture", "code", "frontend", "infrastructure", "unit tests", "system tests", "quality", "security"),
            context.outputs.valuesFor(listOf("nora", "cole", "max", "finn", "faye", "vera", "tess", "syd", "quinn", "sage")),
        )
        assertTrue(
            context.outputs
                .asMap()
                .values
                .containsAll(listOf("code", "security")),
        )
    }

    @Test
    fun `an unassigned stage stays null for the optional view and empty for the plain one`() {
        val context = newContext()

        assertNull(context.lenaOutput)
        assertEquals("", context.finnOutput)
    }

    private fun newContext(): PipelineContext =
        PipelineContext(
            run = AgentPipelineRun(id = 1L, rawText = "", repoPath = null, featureSlug = "", createdAt = 0L),
            model = "model",
            copilot = org.mockito.kotlin.mock(),
            copilotConfig = org.mockito.kotlin.mock(),
            i18n = org.mockito.kotlin.mock(),
            aiConfigRepo = org.mockito.kotlin.mock(),
            dbConfigRepo = org.mockito.kotlin.mock(),
            dbSchemaPort = org.mockito.kotlin.mock(),
            loadAgentPrompt = { _, _ -> "" },
            writeStageOutput = { _, _, _ -> },
            stream = { _, _, _, _ -> "" },
            repoRoot = null,
        )
}
