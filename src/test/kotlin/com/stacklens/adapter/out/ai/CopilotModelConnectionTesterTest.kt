package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.enums.AiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotModelConnectionTesterTest {
    private val i18n: I18nPort = mock()
    private val copilot: CopilotPort = mock()
    private lateinit var tester: CopilotModelConnectionTester

    @BeforeEach
    fun setUp() {
        tester = CopilotModelConnectionTester(copilot, i18n)
    }

    private fun mockI18n(value: String) {
        whenever(i18n.t(any<String>())).thenReturn(value)
        whenever(i18n.t(any<String>(), any())).thenReturn(value)
        whenever(i18n.t(any<String>(), any(), any())).thenReturn(value)
        whenever(i18n.t(any<String>(), any(), any(), any())).thenReturn(value)
    }

    @Test
    fun `test returns Failure when model is null`() {
        mockI18n("no-model")
        val result = tester.test(ProviderSpec.CopilotModel(model = null, savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("no-model", payload["error"])
    }

    @Test
    fun `test returns Failure when model is blank`() {
        mockI18n("no-model")
        val result = tester.test(ProviderSpec.CopilotModel(model = "   ", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure when savedProvider is not Copilot`() {
        mockI18n("save-first")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = "openai"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("save-first", payload["error"])
    }

    @Test
    fun `test returns Failure when resolveToken returns null`() {
        whenever(copilot.resolveToken()).thenReturn(null)
        mockI18n("no-token")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("no-token", payload["error"])
    }

    @Test
    fun `test returns Success when execute succeeds`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("OK")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val payload = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(true, payload["success"])
        assertEquals("gpt-4", payload["model"])
    }

    @Test
    fun `test returns quota failure on COPILOT_QUOTA error`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("[COPILOT_QUOTA] exceeded"))
        mockI18n("quota")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("quota", payload["error"])
    }

    @Test
    fun `test returns model not available on not found error`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("not found"))
        mockI18n("not-avail")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("not-avail", payload["error"])
        assertEquals("gpt-4", payload["model"])
    }

    @Test
    fun `test returns model not available on 404`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("HTTP 404"))
        mockI18n("not-avail")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns unauthorized on 401`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("401 unauthorized"))
        mockI18n("unauth")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unauth", payload["error"])
    }

    @Test
    fun `test returns unauthorized on 403 forbidden`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("403 forbidden"))
        mockI18n("unauth")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns timeout on Timeout error`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("Read Timeout"))
        mockI18n("timeout")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("timeout", payload["error"])
    }

    @Test
    fun `test returns not installed on cannot find GitHub Copilot CLI`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("Cannot find GitHub Copilot CLI in PATH"))
        mockI18n("not-installed")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("not-installed", payload["error"])
    }

    @Test
    fun `test returns generic failure on unknown error`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("something unexpected"))
        mockI18n("generic")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("generic", payload["error"])
    }

    @Test
    fun `test handles null exception message`() {
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException())
        mockI18n("generic")
        val result = tester.test(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = AiProvider.COPILOT.id))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }
}
