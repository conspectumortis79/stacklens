package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class LoggingFixDiagnoseStageTest {
    private val copilot = RecordingCopilot("Some diagnosis")
    private val markers = catalogBackedLoggingFixMarkers()
    private var promptLoads = 0
    private var lastPromptKey: String? = null
    private val promptLoader =
        StubPromptLoader(onGet = { _, key, _ ->
            promptLoads++
            lastPromptKey = key
            "PROMPT"
        })
    private val stage = LoggingFixDiagnoseStage(copilot, promptLoader, markers)

    private fun context(
        logText: String,
        language: String = "de",
        emitted: StringBuilder = StringBuilder(),
        selectedCategories: List<String> = listOf("error"),
    ) = LoggingFixStageContext(
        logText = logText,
        selectedCategories = selectedCategories,
        repoPath = null,
        repoRoot = null,
        language = language,
        onPartial = { _, chunk -> emitted.append(chunk) },
        isCancelled = { false },
    )

    @Test
    fun `short-circuits on too-thin input and emits the canonical DE skip marker`() {
        val emitted = StringBuilder()
        val ctx = context(logText = "error", emitted = emitted, selectedCategories = emptyList())

        stage.execute(ctx)

        assertThat(emitted.toString()).isEqualTo(markers.skipMarker("de"))
        assertThat(ctx.diagnosisIncomplete).isTrue
        assertThat(copilot.calls).isEqualTo(0)
        assertThat(promptLoads).isEqualTo(0)
    }

    @Test
    fun `does not call the AI port for blank input`() {
        val ctx = context(logText = "", language = "en")

        stage.execute(ctx)

        assertThat(copilot.calls).isEqualTo(0)
        assertThat(promptLoads).isEqualTo(0)
        assertThat(ctx.diagnosisIncomplete).isTrue
    }

    @Test
    fun `emits the english skip marker for english runs`() {
        val emitted = StringBuilder()
        val ctx = context(logText = "error", language = "en", emitted = emitted)

        stage.execute(ctx)

        assertThat(emitted.toString()).isEqualTo(markers.skipMarker("en"))
    }

    @Test
    fun `loads the logging fix diagnose prompt and invokes the copilot for rich input`() {
        val emitted = StringBuilder()
        val ctx = context(logText = RICH_INPUT, emitted = emitted)

        stage.execute(ctx)

        assertThat(lastPromptKey).isEqualTo("logging.fix_diagnose")
        assertThat(copilot.calls).isEqualTo(1)
        assertThat(emitted.toString()).isEqualTo("Some diagnosis")
        assertThat(ctx.diagnosisIncomplete).isFalse
    }

    @Test
    fun `calls copilot for short input containing a stack-trace marker`() {
        val ctx = context(logText = "Caused by: java.lang.NullPointerException")

        stage.execute(ctx)

        assertThat(copilot.calls).isEqualTo(1)
        assertThat(ctx.diagnosisIncomplete).isFalse
    }

    @Test
    fun `isDiagnosisNotPossibleMarker recognises the canonical skip marker and the structured token`() {
        assertThat(stage.isDiagnosisNotPossibleMarker(markers.skipMarker("de"))).isTrue
        assertThat(stage.isDiagnosisNotPossibleMarker("DIAGNOSIS_NOT_POSSIBLE foo")).isTrue
        assertThat(stage.isDiagnosisNotPossibleMarker("Some normal diagnosis")).isFalse
    }

    @Test
    fun `isDiagnosisNotPossibleMarker recognises the skip marker after the model dropped its first characters`() {
        val mangledMarker = "\n\n" + markers.skipMarker("de").substring(6)

        assertThat(stage.isDiagnosisNotPossibleMarker(mangledMarker)).isTrue
    }

    @Test
    fun `isDiagnosisNotPossibleMarker recognises the english skip marker`() {
        assertThat(stage.isDiagnosisNotPossibleMarker(markers.skipMarker("en"))).isTrue
    }

    @Test
    fun `flags the diagnosis as incomplete when the model emits a mangled skip marker`() {
        copilot.response = "\n\n" + markers.skipMarker("de").substring(6)
        val ctx = context(logText = RICH_INPUT)

        stage.execute(ctx)

        assertThat(ctx.diagnosisIncomplete).isTrue
    }

    @Test
    fun `does not flag a real diagnosis that merely mentions the word uebersprungen`() {
        copilot.response = "Root cause: the retry loop skips failed requests. Nothing was übersprungen here."
        val ctx = context(logText = RICH_INPUT)

        stage.execute(ctx)

        assertThat(ctx.diagnosisIncomplete).isFalse
    }

    @Test
    fun `treats an empty AI answer as an incomplete diagnosis and reports it`() {
        copilot.response = "  \n "
        val emitted = StringBuilder()
        val ctx = context(logText = RICH_INPUT, emitted = emitted)

        stage.execute(ctx)

        assertThat(ctx.diagnosisIncomplete).isTrue
        assertThat(emitted.toString()).contains("ohne Ergebnis")
        assertThat(ctx.diagnoseOutput.toString()).contains("ohne Ergebnis")
    }

    companion object {
        private val RICH_INPUT: String =
            listOf(
                "2025-01-15 ERROR something failed",
                "WARN earlier warning",
                "INFO another line",
                "ERROR boom",
                "at com.x.Y.z(X.java:1)",
            ).joinToString("\n")
    }
}

private class RecordingCopilot(
    var response: String,
) : CopilotStreamingPort {
    var calls: Int = 0
    var lastPrompt: String = ""

    override val providerLabel: String
        get() = "recording"

    override fun resolveToken(): String? = null

    override fun resolveModel(): String = "recording-model"

    override fun resolveLanguage(): String = "de"

    override fun isReady(): Boolean = true

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String {
        calls++
        lastPrompt = prompt
        return response
    }

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        calls++
        lastPrompt = prompt
        onPartialOutput(response)
        return response
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
        calls++
        lastPrompt = prompt
        onPartialOutput(response)
        return response
    }
}
