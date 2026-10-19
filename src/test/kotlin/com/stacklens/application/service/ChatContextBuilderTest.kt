package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatContextBuilderTest {
    private fun stage(
        name: String,
        output: String,
    ): AgentStage =
        AgentStage(name = name).apply {
            this.output = output
        }

    private fun run(
        rawText: String = "Free-text requirement",
        jiraTicketData: String? = null,
        setStageOutputs: Map<String, String> = emptyMap(),
    ): AgentPipelineRun {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = rawText,
                repoPath = null,
                featureSlug = "test",
                jiraTicketData = jiraTicketData,
                createdAt = System.currentTimeMillis(),
            )
        setStageOutputs.forEach { (stageName, output) ->
            run.stageByName(stageName)?.output = output
        }
        return run
    }

    @Test
    fun `build includes original requirement when rawText is non-blank`() {
        val output = ChatContextBuilder.build(run(rawText = "Please add login"))
        assertTrue(output.contains("## Original Requirement / Ticket"))
        assertTrue(output.contains("Please add login"))
    }

    @Test
    fun `build uses jiraTicketData when rawText is blank`() {
        val output =
            ChatContextBuilder.build(
                run(rawText = "", jiraTicketData = "Full Jira ticket body"),
            )
        assertTrue(output.contains("Full Jira ticket body"))
        assertFalse(output.contains("Please add login"))
    }

    @Test
    fun `build uses jiraTicketData when present and non-blank`() {
        val output =
            ChatContextBuilder.build(
                run(rawText = "PROJ-123", jiraTicketData = "Full Jira ticket body"),
            )
        assertTrue(output.contains("Full Jira ticket body"))
    }

    @Test
    fun `build uses rawText when jiraTicketData is blank`() {
        val output =
            ChatContextBuilder.build(
                run(rawText = "Free-text requirement", jiraTicketData = "   "),
            )
        assertTrue(output.contains("Free-text requirement"))
    }

    @Test
    fun `build omits ticket section when both rawText and jiraTicketData are blank`() {
        val output = ChatContextBuilder.build(run(rawText = "", jiraTicketData = null))
        assertFalse(output.contains("## Original Requirement / Ticket"))
    }

    @Test
    fun `build truncates long ticket content to 8000 chars`() {
        val longText = "x".repeat(10_000)
        val output = ChatContextBuilder.build(run(rawText = longText))
        val xCount = output.count { it == 'x' }
        assertTrue(xCount <= 8_000, "Expected ≤8000 x's, got $xCount")
        assertTrue(xCount >= 7_000, "Expected ≥7000 x's, got $xCount")
    }

    @Test
    fun `build includes stage outputs`() {
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to "finn output")),
            )
        assertTrue(output.contains("## finn Output"))
        assertTrue(output.contains("finn output"))
    }

    @Test
    fun `build skips empty stage outputs`() {
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to "")),
            )
        assertFalse(output.contains("finn output"))
    }

    @Test
    fun `build skips whitespace-only stage outputs`() {
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to "   \n\t  ")),
            )
        assertFalse(output.contains("finn output"))
    }

    @Test
    fun `build truncates long stage outputs to 20000 chars`() {
        val longOutput = "y".repeat(25_000)
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to longOutput)),
            )
        val yCount = output.count { it == 'y' }
        assertTrue(yCount <= 20_000, "Expected ≤20000 y's, got $yCount")
    }

    @Test
    fun `build includes multiple stages in order`() {
        val output =
            ChatContextBuilder.build(
                run(
                    setStageOutputs =
                        mapOf(
                            "nora" to "nora content",
                            "finn" to "finn content",
                            "faye" to "faye content",
                        ),
                ),
            )
        assertTrue(output.contains("nora content"))
        assertTrue(output.contains("finn content"))
        assertTrue(output.contains("faye content"))
    }

    @Test
    fun `build returns empty string for empty run`() {
        val output = ChatContextBuilder.build(run(rawText = "", jiraTicketData = null))
        assertTrue(output.isBlank())
    }

    @Test
    fun `build includes both ticket and stage context`() {
        val output =
            ChatContextBuilder.build(
                run(
                    rawText = "Original requirement",
                    setStageOutputs = mapOf("finn" to "finn output"),
                ),
            )
        assertTrue(output.contains("## Original Requirement / Ticket"))
        assertTrue(output.contains("Original requirement"))
        assertTrue(output.contains("finn output"))
    }

    @Test
    fun `build preserves newlines in stage output`() {
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to "line1\nline2\nline3")),
            )
        assertTrue(output.contains("line1\nline2\nline3"))
    }

    @Test
    fun `build preserves unicode in stage output`() {
        val output =
            ChatContextBuilder.build(
                run(setStageOutputs = mapOf("finn" to "Héllo wörld 🚀")),
            )
        assertTrue(output.contains("Héllo wörld 🚀"))
    }
}
