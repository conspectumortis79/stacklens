package com.stacklens.domain.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AgentPipelineModelTest {
    private fun newRun(jira: Boolean = false): AgentPipelineRun =
        AgentPipelineRun(
            id = 1L,
            rawText = "x",
            repoPath = null,
            featureSlug = "x",
            jiraTicketData = if (jira) "ticket" else null,
            createdAt = System.currentTimeMillis(),
        )

    @Test
    fun `stageByName resolves every known stage to the same instance as the named accessor`() {
        val run = newRun(jira = true)
        val expected =
            mapOf(
                "lena" to run.lena,
                "nora" to run.nora,
                "cole" to run.cole,
                "ivan" to run.ivan,
                "max" to run.max,
                "finn" to run.finn,
                "faye" to run.faye,
                "vera" to run.vera,
                "tess" to run.tess,
                "syd" to run.syd,
                "quinn" to run.quinn,
                "sage" to run.sage,
                "rex" to run.rex,
            )
        for ((name, stage) in expected) {
            assertSame(stage, run.stageByName(name), "stageByName(\"$name\")")
            assertEquals(name, stage.name)
        }
    }

    @Test
    fun `stageByName returns null for unknown name`() {
        assertNull(newRun().stageByName("zoe"))
    }

    @Test
    fun `stageByName returns null for null name`() {
        assertNull(newRun().stageByName(null))
    }

    @Test
    fun `stagesByName contains all 13 stages keyed by name`() {
        val run = newRun()
        assertEquals(13, run.stagesByName.size)
        run.stagesByName.forEach { (key, stage) -> assertEquals(key, stage.name) }
    }

    @Test
    fun `stages includes lena first for jira runs and keeps order`() {
        val run = newRun(jira = true)
        assertEquals(
            listOf("lena", "nora", "cole", "ivan", "max", "finn", "faye", "vera", "tess", "syd", "quinn", "sage", "rex"),
            run.stages.map { it.name },
        )
    }

    @Test
    fun `stages omits lena for free-text runs and keeps order`() {
        val run = newRun(jira = false)
        assertEquals(
            listOf("nora", "cole", "ivan", "max", "finn", "faye", "vera", "tess", "syd", "quinn", "sage", "rex"),
            run.stages.map { it.name },
        )
    }
}
