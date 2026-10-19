package com.stacklens.application.service.logging

import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggingFixCodeStageTest {
    @Test
    fun `execute parses multiple FILE blocks and populates proposedFiles`() {
        val aiOutput =
            """
            Hier ist der Fix:

            ### FILE: src/main/kotlin/Validator.kt
            ### DESCRIPTION: Neuer Validator
            package com.example

            class Validator {
                fun check(x: String) = require(x.isNotBlank())
            }

            ### FILE: src/main/kotlin/Caller.kt
            package com.example

            class Caller {
                fun run() = Validator().check("ok")
            }
            """.trimIndent()
        val stage = LoggingFixCodeStage(StubCopilot(aiOutput), StubPromptLoader(), catalogBackedLoggingFixMarkers())
        val ctx =
            LoggingFixStageContext(
                logText = "log",
                selectedCategories = listOf("error"),
                repoPath = "/tmp/repo",
                repoRoot = java.io.File("/tmp/repo"),
                language = "de",
                onPartial = { _, _ -> },
            )
        stage.execute(ctx)

        assertEquals(2, ctx.proposedFiles.size)
        assertEquals("src/main/kotlin/Validator.kt", ctx.proposedFiles[0].path)
        assertEquals("Neuer Validator", ctx.proposedFiles[0].description)
        assertTrue(ctx.proposedFiles[0].content.contains("class Validator"))
        assertEquals("src/main/kotlin/Caller.kt", ctx.proposedFiles[1].path)
        assertNull(ctx.proposedFiles[1].description)
        assertTrue(ctx.proposedFiles[1].content.contains("class Caller"))
    }

    @Test
    fun `execute emits skipped marker when no repo root is available`() {
        val stage = LoggingFixCodeStage(StubCopilot("ignored"), StubPromptLoader(), catalogBackedLoggingFixMarkers())
        val ctx =
            LoggingFixStageContext(
                logText = "log",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
            )
        stage.execute(ctx)
        assertTrue(ctx.codeOutput.toString().contains("Code-Stage übersprungen"))
        assertTrue(ctx.proposedFiles.isEmpty())
    }

    @Test
    fun `execute skips AI call when diagnosisIncomplete is true and no repo root`() {
        val copilot = AssertingNeverCalledCopilotForCode()
        val promptLoader = AssertingNeverCalledPromptLoaderForCode()
        val stage = LoggingFixCodeStage(copilot, promptLoader, catalogBackedLoggingFixMarkers())

        val ctx =
            LoggingFixStageContext(
                logText = "error",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
                diagnosisIncomplete = true,
            )
        stage.execute(ctx)

        assertEquals(0, copilot.executeWithStreamingPerLineCalls)
        assertEquals(0, promptLoader.invocations)
        val code = ctx.codeOutput.toString()
        assertTrue(code.contains("Code") && code.contains("Diagnose") && code.contains("übersprungen"), "code=$code")
        assertTrue(
            !code.contains("kein Repository ausgewählt"),
            "diagnosisIncomplete skip must use its own marker, not the no-repo one",
        )
        assertTrue(ctx.proposedFiles.isEmpty(), "proposedFiles must remain empty on skip")
    }

    @Test
    fun `execute skips AI call when diagnosisIncomplete is true even with repo root available`() {
        val copilot = AssertingNeverCalledCopilotForCode()
        val promptLoader = AssertingNeverCalledPromptLoaderForCode()
        val stage = LoggingFixCodeStage(copilot, promptLoader, catalogBackedLoggingFixMarkers())

        val ctx =
            LoggingFixStageContext(
                logText = "error",
                selectedCategories = listOf("error"),
                repoPath = "/tmp/repo",
                repoRoot = java.io.File("/tmp/repo"),
                language = "de",
                onPartial = { _, _ -> },
                diagnosisIncomplete = true,
            )
        stage.execute(ctx)

        assertEquals(
            0,
            copilot.executeWithStreamingPerLineCalls,
            "AI must NOT be invoked when diagnose flagged the input as unparseable",
        )
        assertEquals(
            0,
            promptLoader.invocations,
            "Prompt loader must NOT be invoked when diagnose flagged the input as unparseable",
        )
        assertTrue(ctx.proposedFiles.isEmpty())
        assertTrue(
            ctx.codeOutput.toString().contains("Code") &&
                ctx.codeOutput.toString().contains("Diagnose") &&
                ctx.codeOutput.toString().contains("übersprungen"),
        )
    }

    @Test
    fun `execute emits the skip marker exactly once via onPartial when diagnosisIncomplete is true`() {
        val stage = LoggingFixCodeStage(AssertingNeverCalledCopilotForCode(), StubPromptLoader(), catalogBackedLoggingFixMarkers())
        val partialChunks = mutableListOf<String>()
        val ctx =
            LoggingFixStageContext(
                logText = "error",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { stageName, chunk ->
                    assertEquals("code", stageName, "Code-stage onPartial must use stage key 'code'")
                    partialChunks += chunk
                },
                diagnosisIncomplete = true,
            )
        stage.execute(ctx)
        assertEquals(1, partialChunks.size, "Code-stage skip-marker must be emitted exactly once via onPartial")
        assertEquals(ctx.codeOutput.toString(), partialChunks.single())
    }
}

private class AssertingNeverCalledCopilotForCode : com.stacklens.application.port.out.ai.CopilotStreamingPort {
    var executeWithStreamingPerLineCalls: Int = 0

    override fun resolveToken(): String? = null

    override fun resolveModel(): String = "stub"

    override fun resolveLanguage(): String = "de"

    override fun isReady(): Boolean = true

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<com.stacklens.application.port.out.ai.AiAttachment>,
    ): String = throw AssertionError("Copilot.execute must not be called when diagnosisIncomplete is true")

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<com.stacklens.application.port.out.ai.AiAttachment>,
    ): String = throw AssertionError("Copilot.executeWithStreaming must not be called when diagnosisIncomplete is true")

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: java.io.File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<com.stacklens.application.port.out.ai.AiAttachment>,
    ): String {
        executeWithStreamingPerLineCalls++
        throw AssertionError("Copilot.executeWithStreamingPerLine must not be called when diagnosisIncomplete is true")
    }
}

private class AssertingNeverCalledPromptLoaderForCode : com.stacklens.application.port.out.prompt.PromptLoaderPort {
    var invocations: Int = 0

    override fun get(
        lang: String,
        key: String,
        vars: Map<String, String>,
    ): String {
        invocations++
        throw AssertionError("PromptLoader.get must not be called when diagnosisIncomplete is true")
    }

    override fun context(lang: String) = throw UnsupportedOperationException()

    override fun getCustom(key: String): String? = null

    override fun getDefault(
        lang: String,
        key: String,
    ): String = ""

    override fun saveCustom(
        key: String,
        content: String,
    ) {}

    override fun deleteCustom(key: String) {}

    override fun listKeys(
        lang: String,
        prefix: String,
    ): List<String> = emptyList()

    override fun listGroups(
        lang: String,
        prefix: String,
    ): List<com.stacklens.application.port.out.prompt.PromptGroup> = emptyList()
}
