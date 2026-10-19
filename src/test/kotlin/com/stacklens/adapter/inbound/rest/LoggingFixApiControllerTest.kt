package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.logging.CancelOutcome
import com.stacklens.application.port.inbound.logging.DeleteOutcome
import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.inbound.logging.LoggingFixUseCase
import com.stacklens.application.port.inbound.logging.StageOutputs
import com.stacklens.application.port.inbound.logging.WriteFilesPreview
import com.stacklens.application.port.inbound.logging.WriteFilesResult
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.LoggingFixAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class LoggingFixApiControllerTest {
    private val recordingUseCase = RecordingUseCase()
    private val recordingI18n = RecordingI18n()
    private val controller = LoggingFixApiController(recordingUseCase, recordingUseCase, recordingUseCase, recordingUseCase, recordingI18n)

    @BeforeEach
    fun reset() {
        recordingUseCase.reset()
        recordingI18n.reset()
    }

    @Test
    fun `start returns 200 for a normal multi-line log text`() {
        recordingUseCase.startFixReturnValue = 7L

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "2025-01-15 ERROR NullPointerException at com.x.Y.z(X.java:1)",
                    selectedCategories = listOf("error"),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(7L, body["id"])
        assertEquals("PENDING", body["status"])
        assertEquals(1, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start returns 400 when logText is blank`() {
        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "   ",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("Log-Text darf nicht leer sein.", body["error"])
        assertEquals(0, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start returns 400 when logText is empty`() {
        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals(0, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start returns 400 when logText exceeds the maximum length`() {
        val huge = "x".repeat(60_000)
        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = huge,
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("Log-Text ist zu lang (max. 50.000 Zeichen).", body["error"])
        assertEquals(0, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start returns 422 when validateStartRequest signals unparseable input`() {
        recordingUseCase.nextReasons = listOf("Eingabe enthält keine auswertbaren Log-Zeilen, Stacktraces oder Fehlermeldungen.")

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "error",
                    selectedCategories = listOf("error"),
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(
            "Eingabe enthält keine auswertbaren Log-Zeilen, Stacktraces oder Fehlermeldungen.",
            body["error"],
        )
        assertNotNull(body["reason"], "422 response must include a non-null 'reason' field")
        assertTrue(
            (body["reason"] as String).isNotBlank(),
            "422 reason field must contain a non-blank explanation",
        )
        assertEquals(0, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start accepts one-word inputs and runs the pipeline (legacy too-thin pre-check removed)`() {
        listOf("foo", "bar", "INFO", "WARN", "DEBUG", "error").forEach { oneWord ->
            recordingUseCase.reset()
            recordingUseCase.startFixReturnValue = 7L
            val resp =
                controller.start(
                    LoggingFixApiController.StartRequest(
                        logText = oneWord,
                        selectedCategories = emptyList(),
                    ),
                )
            assertEquals(
                HttpStatus.OK,
                resp.statusCode,
                "Expected 200 for one-word input '$oneWord' after removing the legacy too-thin pre-check, got ${resp.statusCode}",
            )
            assertEquals(1, recordingUseCase.startFixInvocations.size)
        }
    }

    @Test
    fun `start returns 422 when validateStartRequest reports a repository-level error`() {
        recordingUseCase.nextReasons = listOf("repository path does not exist: /missing/path")
        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "2025-01-15 ERROR boom",
                    selectedCategories = listOf("error"),
                    localRepoConfigId = 99L,
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
        assertEquals(0, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start accepts inputs with stack-trace markers even when short`() {
        recordingUseCase.startFixReturnValue = 11L

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "Caused by: java.lang.NullPointerException",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(1, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start accepts inputs with ISO timestamps even when short`() {
        recordingUseCase.startFixReturnValue = 12L

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "2025-01-15 boom",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(1, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start accepts inputs with five or more non-blank lines`() {
        recordingUseCase.startFixReturnValue = 13L

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "line1\nline2\nline3\nline4\nline5",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(1, recordingUseCase.startFixInvocations.size)
    }

    @Test
    fun `start uses the default language when an empty language is provided`() {
        recordingUseCase.startFixReturnValue = 14L

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "2025-01-15 ERROR foo\n  at com.x.Y.z(X.java:1)",
                    selectedCategories = emptyList(),
                    language = "",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        val inv = recordingUseCase.startFixInvocations.single()
        assertEquals("de", inv.language, "Controller must default an empty language to 'de'")
    }

    @Test
    fun `start 422 path does NOT use the empty-log 400 message`() {
        recordingUseCase.nextReasons = listOf("input is unparseable")

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "error",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertTrue(
            body["error"] != "Log-Text darf nicht leer sein.",
            "422 path must NOT return the be_log_text_empty message",
        )
        assertTrue(recordingI18n.requests.contains("logging_fix.be_log_text_unparseable"))
        assertTrue(!recordingI18n.requests.contains("logging_fix.be_log_text_empty"))
    }

    @Test
    fun `start 422 path surfaces the first non-blank validateStartRequest reason as the reason field`() {
        recordingUseCase.nextReasons = listOf("", "  ", "less than 5 lines and no stack-trace marker")

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "error",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("less than 5 lines and no stack-trace marker", body["reason"])
    }

    @Test
    fun `start 422 path falls back to a generic reason when validateStartRequest returns only blank strings`() {
        recordingUseCase.nextReasons = listOf("", "  ", "")

        val resp =
            controller.start(
                LoggingFixApiController.StartRequest(
                    logText = "error",
                    selectedCategories = emptyList(),
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["reason"])
        assertTrue((body["reason"] as String).isNotBlank())
    }

    private class RecordingUseCase : LoggingFixUseCase {
        var nextReasons: List<String> = emptyList()
        var startFixReturnValue: Long = 0L
        val startFixInvocations: MutableList<StartFixInvocation> = mutableListOf()

        fun reset() {
            nextReasons = emptyList()
            startFixReturnValue = 0L
            startFixInvocations.clear()
        }

        override fun startFix(
            logText: String,
            selectedCategories: List<String>,
            localRepoConfigId: Long?,
            language: String,
        ): Long {
            startFixInvocations += StartFixInvocation(logText, selectedCategories, localRepoConfigId, language)
            return startFixReturnValue
        }

        override fun validateStartRequest(request: LoggingFixStartRequest): List<String> = nextReasons

        override fun getAnalysis(id: Long): LoggingFixAnalysis? = null

        override fun listRecent(limit: Int): List<LoggingFixAnalysis> = emptyList()

        override fun cancel(id: Long): CancelOutcome = CancelOutcome.NotFound

        override fun delete(id: Long): DeleteOutcome = DeleteOutcome.NotFound

        override fun deleteAllFinished(): Int = 0

        override fun getStageOutputs(id: Long): StageOutputs? = null

        override fun getWriteFilesPreview(id: Long): WriteFilesPreview? = null

        override fun writeFiles(id: Long): WriteFilesResult = WriteFilesResult(success = false, error = "not used")
    }

    private data class StartFixInvocation(
        val logText: String,
        val selectedCategories: List<String>,
        val localRepoConfigId: Long?,
        val language: String,
    )

    private class RecordingI18n :
        I18nUseCase,
        I18nPort {
        val requests: MutableList<String> = mutableListOf()

        fun reset() {
            requests.clear()
        }

        override fun t(
            key: String,
            vararg args: Any,
        ): String {
            requests += key
            return localize(key)
        }

        override fun tFor(
            lang: String,
            key: String,
            vararg args: Any,
        ): String {
            requests += key
            return localize(key)
        }

        override fun translateError(e: Exception): String = e.message ?: ""

        override fun resolveLanguage(): String = "de"

        private fun localize(key: String): String =
            when (key) {
                "logging_fix.be_log_text_empty" -> "Log-Text darf nicht leer sein."
                "logging_fix.be_log_text_too_long" -> "Log-Text ist zu lang (max. 50.000 Zeichen)."
                "logging_fix.be_log_text_unparseable" ->
                    "Eingabe enthält keine auswertbaren Log-Zeilen, Stacktraces oder Fehlermeldungen."
                else -> key
            }
    }
}
