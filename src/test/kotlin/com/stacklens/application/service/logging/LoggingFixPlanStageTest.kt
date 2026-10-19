package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class LoggingFixPlanStageTest {
    @Test
    fun `execute emits skipped marker when no repo root is available`() {
        val stage = LoggingFixPlanStage(StubCopilot(""), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
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
        assertTrue(ctx.planOutput.toString().contains("Plan-Stage übersprungen"))
        assertFalse(
            ctx.diagnosisIncomplete,
            "No-repo skip path must NOT touch the diagnosisIncomplete flag",
        )
    }

    @Test
    fun `execute scans repo and sends snapshot to AI`() {
        val tmp = Files.createTempDirectory("plan-stage-test").toFile()
        try {
            File(tmp, "README.md").writeText("# checkout-service\nA Kotlin service.")
            File(tmp, "build.gradle.kts").writeText("plugins { kotlin(\"jvm\") }")
            File(tmp, "src/main/kotlin/com/x").mkdirs()
            File(tmp, "src/test/kotlin/com/x").mkdirs()
            File(tmp, "src/test/systemTest/kotlin/com/x").mkdirs()

            var capturedSnapshot = ""
            val stage =
                LoggingFixPlanStage(
                    StubCopilot("plan-output"),
                    StubPromptLoader(
                        onGet = { _: String, _: String, vars: Map<String, String> ->
                            capturedSnapshot = vars["repo_snapshot"] ?: ""
                            "p"
                        },
                    ),
                    markers = catalogBackedLoggingFixMarkers(),
                    fileSystem = JavaFileSystemAdapter(),
                )
            val ctx =
                LoggingFixStageContext(
                    logText = "log",
                    selectedCategories = listOf("error"),
                    repoPath = tmp.absolutePath,
                    repoRoot = tmp,
                    language = "de",
                    onPartial = { _, _ -> },
                )
            stage.execute(ctx)

            assertEquals("plan-output", ctx.planOutput.toString())
            assertTrue(capturedSnapshot.contains("README.md"))
            assertTrue(capturedSnapshot.contains("build.gradle.kts"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `execute handles missing systemTest directory gracefully`() {
        val tmp = Files.createTempDirectory("plan-stage-no-systest").toFile()
        try {
            var capturedSnapshot = ""
            val stage =
                LoggingFixPlanStage(
                    StubCopilot("ok"),
                    StubPromptLoader(
                        onGet = { _: String, _: String, vars: Map<String, String> ->
                            capturedSnapshot = vars["repo_snapshot"] ?: ""
                            "p"
                        },
                    ),
                    markers = catalogBackedLoggingFixMarkers(),
                    fileSystem = JavaFileSystemAdapter(),
                )
            val ctx =
                LoggingFixStageContext(
                    logText = "log",
                    selectedCategories = listOf("error"),
                    repoPath = tmp.absolutePath,
                    repoRoot = tmp,
                    language = "de",
                    onPartial = { _, _ -> },
                )
            stage.execute(ctx)
            assertEquals("ok", ctx.planOutput.toString())
            assertTrue(capturedSnapshot.contains("NICHT GEFUNDEN"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `execute skips AI call when diagnosisIncomplete is true and no repo root`() {
        val copilot = AssertingNeverCalledCopilotForPlan()
        val promptLoader = StubPromptLoader()
        val stage = LoggingFixPlanStage(copilot, promptLoader, catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())

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
        val plan = ctx.planOutput.toString()
        assertTrue(
            plan.contains("Plan") && plan.contains("Diagnose") && plan.contains("übersprungen"),
            "plan must contain a skip marker mentioning 'Plan', 'Diagnose' and 'übersprungen'. got=$plan",
        )
    }

    @Test
    fun `execute skips AI call when diagnosisIncomplete is true even with repo root available`() {
        val tmp = Files.createTempDirectory("plan-stage-dx-incomplete").toFile()
        try {
            File(tmp, "README.md").writeText("# irrelevant")
            val copilot = AssertingNeverCalledCopilotForPlan()
            val promptLoader = AssertingNeverCalledPromptLoader()
            val stage = LoggingFixPlanStage(copilot, promptLoader, catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())

            val ctx =
                LoggingFixStageContext(
                    logText = "error",
                    selectedCategories = listOf("error"),
                    repoPath = tmp.absolutePath,
                    repoRoot = tmp,
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
            assertTrue(
                ctx.planOutput.toString().contains("Plan") &&
                    ctx.planOutput.toString().contains("Diagnose") &&
                    ctx.planOutput.toString().contains("übersprungen"),
                "Plan must contain skip marker mentioning Plan/Diagnose/übersprungen",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `execute does NOT skip when repoRoot is missing but diagnosisIncomplete is false`() {
        val stage = LoggingFixPlanStage(StubCopilot(""), StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())
        val ctx =
            LoggingFixStageContext(
                logText = "log",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
                diagnosisIncomplete = false,
            )
        stage.execute(ctx)

        assertTrue(
            ctx.planOutput.toString().contains("kein Repository ausgewählt"),
            "Existing no-repo skip marker must still apply when diagnosisIncomplete is false",
        )
    }

    @Test
    fun `execute emits the skip marker exactly once via onPartial when diagnosisIncomplete is true`() {
        val copilot = AssertingNeverCalledCopilotForPlan()
        val stage = LoggingFixPlanStage(copilot, StubPromptLoader(), catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())

        val partialChunks = mutableListOf<String>()
        val ctx =
            LoggingFixStageContext(
                logText = "error",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { stageName, chunk ->
                    assertEquals("plan", stageName, "Plan-stage onPartial must use stage key 'plan'")
                    partialChunks += chunk
                },
                diagnosisIncomplete = true,
            )
        stage.execute(ctx)

        assertEquals(1, partialChunks.size, "Plan-stage skip-marker must be emitted exactly once via onPartial")
        assertEquals(ctx.planOutput.toString(), partialChunks.single())
    }
}

private class AssertingNeverCalledCopilotForPlan : com.stacklens.application.port.out.ai.CopilotStreamingPort {
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

private class AssertingNeverCalledPromptLoader : com.stacklens.application.port.out.prompt.PromptLoaderPort {
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

private fun File.deleteRecursively() {
    if (isDirectory) listFiles()?.forEach { it.deleteRecursively() }
    delete()
}
