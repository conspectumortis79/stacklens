package com.stacklens.systemtest

import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.service.logging.LoggingFixDiagnoseStage
import com.stacklens.application.service.logging.LoggingFixStageContext
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LoggingFixPromptPipelineSystemTest {
    @Test
    fun `logging md prompt file is shipped with the expected fix-stage entries`() {
        for (lang in listOf("de", "en")) {
            val prompt = loadPromptFromDisk(lang)
            assertTrue(
                prompt.contains("logging.fix_diagnose"),
                "Prompt-Datei für Sprache '$lang' enthält keinen Marker 'logging.fix_diagnose'",
            )
            assertTrue(
                prompt.contains("logging.fix_plan"),
                "Prompt-Datei für Sprache '$lang' enthält keinen Marker 'logging.fix_plan'",
            )
            assertTrue(
                prompt.contains("logging.fix_code"),
                "Prompt-Datei für Sprache '$lang' enthält keinen Marker 'logging.fix_code'",
            )
            assertTrue(
                prompt.contains("logging.fix_test"),
                "Prompt-Datei für Sprache '$lang' enthält keinen Marker 'logging.fix_test'",
            )
        }
    }

    @Test
    fun `diagnose prompt template embeds categories and log_text placeholders`() {
        val dePrompt = loadPromptFromDisk("de")
        val enPrompt = loadPromptFromDisk("en")

        assertTrue(dePrompt.contains("{categories}"), "de prompt must declare {categories} placeholder")
        assertTrue(dePrompt.contains("{log_text}"), "de prompt must declare {log_text} placeholder")
        assertTrue(enPrompt.contains("{categories}"), "en prompt must declare {categories} placeholder")
        assertTrue(enPrompt.contains("{log_text}"), "en prompt must declare {log_text} placeholder")
    }

    @Test
    fun `diagnose prompt renders the categories and log_text vars when invoked through the stage`() {
        var capturedPrompt = ""
        val stage =
            LoggingFixDiagnoseStage(
                CapturingCopilot(capturedPromptReceiver = { capturedPrompt = it }),
                TemplatingPromptLoader,
                catalogBackedLoggingFixMarkers(),
            )

        val ctx =
            LoggingFixStageContext(
                logText = "2025-01-15 ERROR NullPointerException\n  at com.x.Y.z(X.java:1)",
                selectedCategories = listOf("error", "exception"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
            )
        stage.execute(ctx)

        val rendered = TemplatingPromptLoader.lastRenderedPrompt
        assertTrue(
            rendered.contains("error, exception"),
            "rendered diagnose prompt must contain the chosen categories, got=\n$rendered",
        )
        assertTrue(
            rendered.contains("NullPointerException"),
            "rendered diagnose prompt must contain the log input, got=\n$rendered",
        )
        assertTrue(capturedPrompt.isNotEmpty(), "AI must receive the rendered prompt")
        assertEquals(capturedPrompt, rendered, "Copilot must receive exactly the rendered prompt")
    }

    @Test
    fun `diagnose stage short-circuits too-thin input without invoking the AI`() {
        val copilot = AssertingNeverCalledCopilotForPipeline()
        val stage = LoggingFixDiagnoseStage(copilot, TemplatingPromptLoader, catalogBackedLoggingFixMarkers())
        val emitted = StringBuilder()

        val ctx =
            LoggingFixStageContext(
                logText = "error",
                selectedCategories = listOf("error"),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, chunk -> emitted.append(chunk) },
            )
        stage.execute(ctx)

        assertEquals(0, copilot.executeWithStreamingPerLineCalls, "AI must not be called for too-thin input")
        assertTrue(emitted.startsWith("[Diagnose übersprungen"), "skip marker expected, got=$emitted")
        assertTrue(ctx.diagnosisIncomplete)
    }

    @Test
    fun `diagnose stage invokes the AI when the log contains an ISO-timestamp marker`() {
        val stage = LoggingFixDiagnoseStage(StubCopilotReturningRootCause(), TemplatingPromptLoader, catalogBackedLoggingFixMarkers())
        val ctx =
            LoggingFixStageContext(
                logText = "2025-01-15 ERROR boom\n  at com.x.Y.z(X.java:1)\n  at com.a.B.c(C.java:2)\n",
                selectedCategories = emptyList(),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
            )
        stage.execute(ctx)

        assertTrue(
            ctx.diagnoseOutput.toString().contains("Root cause"),
            "AI response must be streamed into the context",
        )
        assertTrue(
            TemplatingPromptLoader.lastCalledKey == "logging.fix_diagnose",
            "Stage must call PromptLoader with key 'logging.fix_diagnose', got=${TemplatingPromptLoader.lastCalledKey}",
        )
        assertFalse(ctx.diagnosisIncomplete, "Plain log line must NOT set diagnosisIncomplete")
    }

    private fun loadPromptFromDisk(lang: String): String {
        val classLoader = LoggingFixPromptPipelineSystemTest::class.java.classLoader
        val res =
            classLoader.getResourceAsStream("prompts/$lang/logging.md")
                ?: error("Resource prompts/$lang/logging.md not found on classpath")
        return res.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun loadPromptFromDiskInternal(lang: String): String {
        val classLoader = LoggingFixPromptPipelineSystemTest::class.java.classLoader
        val res =
            classLoader.getResourceAsStream("prompts/$lang/logging.md")
                ?: error("Resource prompts/$lang/logging.md not found on classpath")
        return res.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private object TemplatingPromptLoader : com.stacklens.application.port.out.prompt.PromptLoaderPort {
        var lastCalledKey: String = ""
        var lastCalledLang: String = ""
        var lastRenderedPrompt: String = ""

        override fun get(
            lang: String,
            key: String,
            vars: Map<String, String>,
        ): String {
            lastCalledLang = lang
            lastCalledKey = key
            val raw = loadFromDisk(lang)
            val rendered =
                vars.entries.fold(raw) { acc, (k, v) ->
                    acc.replace("{$k}", v)
                }
            lastRenderedPrompt = rendered
            return rendered
        }

        override fun context(lang: String) = throw UnsupportedOperationException("not used in pipeline tests")

        override fun getCustom(key: String): String? = null

        override fun getDefault(
            lang: String,
            key: String,
        ): String = loadFromDisk(lang)

        override fun saveCustom(
            key: String,
            content: String,
        ) {}

        override fun deleteCustom(key: String) {}

        override fun listKeys(
            lang: String,
            prefix: String,
        ): List<String> {
            val prompt = loadFromDisk(lang)
            return prompt
                .lineSequence()
                .filter { it.startsWith("## ") }
                .map { it.removePrefix("## ").trim() }
                .filter { it.startsWith(prefix) }
                .toList()
        }

        override fun listGroups(
            lang: String,
            prefix: String,
        ): List<com.stacklens.application.port.out.prompt.PromptGroup> = emptyList()

        private fun loadFromDisk(lang: String): String {
            val classLoader = TemplatingPromptLoader::class.java.classLoader
            val res =
                classLoader.getResourceAsStream("prompts/$lang/logging.md")
                    ?: error("Resource prompts/$lang/logging.md not found on classpath")
            return res.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    private class CapturingCopilot(
        private val capturedPromptReceiver: (String) -> Unit,
    ) : CopilotStreamingPort {
        override fun resolveToken(): String? = null

        override fun resolveModel(): String = "stub"

        override fun resolveLanguage(): String = "de"

        override fun isReady(): Boolean = true

        override fun execute(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            attachments: List<AiAttachment>,
        ): String = prompt

        override fun executeWithStreaming(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            capturedPromptReceiver(prompt)
            onPartialOutput("Root cause: pipeline injected")
            return "Root cause: pipeline injected"
        }

        override fun executeWithStreamingPerLine(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            repoDir: File?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            capturedPromptReceiver(prompt)
            onPartialOutput("Root cause: pipeline injected")
            return "Root cause: pipeline injected"
        }
    }

    private class StubCopilotReturningRootCause : CopilotStreamingPort {
        var executeWithStreamingPerLineCalls: Int = 0

        override fun resolveToken(): String? = null

        override fun resolveModel(): String = "stub"

        override fun resolveLanguage(): String = "de"

        override fun isReady(): Boolean = true

        override fun execute(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            attachments: List<AiAttachment>,
        ): String = "Root cause"

        override fun executeWithStreaming(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            onPartialOutput("Root cause")
            return "Root cause"
        }

        override fun executeWithStreamingPerLine(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            repoDir: File?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            executeWithStreamingPerLineCalls++
            onPartialOutput("Root cause")
            return "Root cause"
        }
    }

    private class AssertingNeverCalledCopilotForPipeline : CopilotStreamingPort {
        var executeWithStreamingPerLineCalls: Int = 0

        override fun resolveToken(): String? = null

        override fun resolveModel(): String = "stub"

        override fun resolveLanguage(): String = "de"

        override fun isReady(): Boolean = true

        override fun execute(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            attachments: List<AiAttachment>,
        ): String = throw AssertionError("Copilot.execute must not be called for too-thin input")

        override fun executeWithStreaming(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String = throw AssertionError("Copilot.executeWithStreaming must not be called for too-thin input")

        override fun executeWithStreamingPerLine(
            model: String,
            prompt: String,
            timeoutOverride: Long?,
            repoDir: File?,
            isCancelled: (() -> Boolean)?,
            onPartialOutput: (String) -> Unit,
            attachments: List<AiAttachment>,
        ): String {
            executeWithStreamingPerLineCalls++
            throw AssertionError("Copilot.executeWithStreamingPerLine must not be called for too-thin input")
        }
    }
}
