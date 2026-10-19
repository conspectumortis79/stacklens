package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotModelConnectionTesterErrorMappingCoverageTest {
    private val copilotPort = mock<CopilotPort>()
    private val i18n = FakeI18n()
    private val tester = CopilotModelConnectionTester(copilotPort, i18n)
    private val spec = ProviderSpec.CopilotModel(model = "gpt-test", savedProvider = AiProvider.COPILOT.id)

    private fun errorOf(throwable: Throwable): String {
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.execute(any(), any(), any(), any())).thenThrow(throwable)

        val result = tester.test(spec)

        assertTrue(result is TestResult.Failure)
        return (result as TestResult.Failure).payload.getValue("error") as String
    }

    @Test
    fun `a successful run reports the model`() {
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.execute(any(), any(), any(), any())).thenReturn("OK")

        val result = tester.test(spec)

        assertTrue(result is TestResult.Success)
        assertEquals("gpt-test", (result as TestResult.Success).payload.getValue("model"))
    }

    @Test
    fun `quota errors are translated to the quota message`() {
        assertEquals(
            "i18n:common.be_copilot_quota",
            errorOf(StackLensFailureException(StackLensFailure.CopilotQuota("exceeded"))),
        )
    }

    @Test
    fun `marker text without the typed failure is not translated as quota`() {
        assertTrue(
            errorOf(RuntimeException("[COPILOT_QUOTA] exceeded"))
                .startsWith("i18n:config.be_model_test_failed"),
        )
    }

    @Test
    fun `model unavailable variants all map to the not available message`() {
        listOf("HTTP 404", "Model not supported", "model not available", "unknown model given", "invalid model given").forEach {
            assertEquals("i18n:config.be_model_not_available|gpt-test", errorOf(RuntimeException(it)), it)
        }
    }

    @Test
    fun `authorization variants map to the unauthorized message`() {
        listOf("401 denied", "403 forbidden", "unauthorized caller", "access forbidden").forEach {
            assertEquals("i18n:config.be_model_unauthorized|gpt-test", errorOf(RuntimeException(it)), it)
        }
    }

    @Test
    fun `timeout maps to the timeout message`() {
        assertEquals("i18n:config.be_model_timeout|gpt-test", errorOf(RuntimeException("Timeout after 30s")))
    }

    @Test
    fun `installation variants map to the not installed message`() {
        listOf("nicht installiert", "not installed on this machine", "Cannot find GitHub Copilot CLI").forEach {
            assertEquals("i18n:config.be_copilot_not_installed", errorOf(RuntimeException(it)), it)
        }
    }

    @Test
    fun `an unrelated message falls back to the generic failure text`() {
        assertEquals("i18n:config.be_model_test_failed|gpt-test,boom", errorOf(RuntimeException("boom")))
    }

    @Test
    fun `a throwable without a message is handled as empty text`() {
        assertEquals("i18n:config.be_model_test_failed|gpt-test,", errorOf(RuntimeException()))
    }
}
