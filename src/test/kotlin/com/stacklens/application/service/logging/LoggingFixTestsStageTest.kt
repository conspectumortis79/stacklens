package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggingFixTestsStageTest {
    @Test
    fun `execute parses multiple FILE blocks and appends them to proposedFiles`() {
        val aiOutput =
            """
            ### FILE: src/test/kotlin/ValidatorTest.kt
            ### DESCRIPTION: Unit-Tests für Validator
            package com.example

            class ValidatorTest {
                @Test fun ok() { assertTrue(true) }
            }

            ### FILE: src/test/systemTest/kotlin/ValidatorE2ETest.kt
            class ValidatorE2ETest
            """.trimIndent()
        val stage = LoggingFixTestsStage(StubCopilot(aiOutput), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
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
        assertEquals("src/test/kotlin/ValidatorTest.kt", ctx.proposedFiles[0].path)
        assertEquals("Unit-Tests für Validator", ctx.proposedFiles[0].description)
        assertTrue(ctx.proposedFiles[0].content.contains("class ValidatorTest"))
        assertEquals("src/test/systemTest/kotlin/ValidatorE2ETest.kt", ctx.proposedFiles[1].path)
        assertNull(ctx.proposedFiles[1].description)
        assertTrue(ctx.proposedFiles[1].content.contains("ValidatorE2ETest"))
    }

    @Test
    fun `execute emits the skip marker when no repo root is available`() {
        val stage = LoggingFixTestsStage(StubCopilot("ignored"), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
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
        assertTrue(ctx.testsOutput.toString().contains("Test-Stage übersprungen"))
        assertTrue(ctx.proposedFiles.isEmpty())
    }

    @Test
    fun `execute emits the diagnosisIncomplete skip marker when ctx diagnosisIncomplete is true`() {
        val stage = LoggingFixTestsStage(StubCopilot("ignored"), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
        val ctx =
            LoggingFixStageContext(
                logText = "log",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
                diagnosisIncomplete = true,
            )
        stage.execute(ctx)
        val out = ctx.testsOutput.toString()
        assertTrue(out.contains("Test") && out.contains("Diagnose") && out.contains("übersprungen"))
        assertTrue(
            !out.contains("kein Repository ausgewählt"),
            "diagnosisIncomplete skip must use its own marker, not the no-repo one",
        )
        assertTrue(ctx.proposedFiles.isEmpty())
    }

    @Test
    fun `execute honours diagnosisIncomplete even when a repo root is available`() {
        val stage =
            LoggingFixTestsStage(
                AssertingNeverCalledCopilotForTests(),
                AssertingNeverCalledPromptLoaderForTests(),
                catalogBackedLoggingFixMarkers(),
                JavaFileSystemAdapter(),
            )
        val tmp =
            java.nio.file.Files
                .createTempDirectory("tests-stage-noop")
                .toFile()
        try {
            val ctx =
                LoggingFixStageContext(
                    logText = "log",
                    selectedCategories = listOf("error"),
                    repoPath = tmp.absolutePath,
                    repoRoot = tmp,
                    language = "de",
                    onPartial = { _, _ -> },
                    diagnosisIncomplete = true,
                )
            stage.execute(ctx)
            assertTrue(ctx.proposedFiles.isEmpty())
            assertTrue(ctx.testsOutput.toString().contains("Diagnose"))
        } finally {
            tmp.deleteRecursively()
        }
    }
}

private fun java.io.File.deleteRecursively() {
    if (isDirectory) listFiles()?.forEach { it.deleteRecursively() }
    delete()
}

private class AssertingNeverCalledCopilotForTests : com.stacklens.application.port.out.ai.CopilotStreamingPort {
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

private class AssertingNeverCalledPromptLoaderForTests : com.stacklens.application.port.out.prompt.PromptLoaderPort {
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
