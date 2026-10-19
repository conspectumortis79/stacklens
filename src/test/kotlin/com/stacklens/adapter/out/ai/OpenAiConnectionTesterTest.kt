package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.OpenAiModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAiConnectionTesterTest {
    private val i18n: I18nPort = mock()
    private val openAiService: OpenAiModelLookupPort = mock()
    private val tester = OpenAiConnectionTester(i18n, openAiService)

    @Test
    fun `test returns Failure when the api key is null`() {
        whenever(i18n.t(any(), any())).thenReturn("no key")
        val result = tester.test(ProviderSpec.OpenAi(apiKey = null))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure when the api key is blank`() {
        whenever(i18n.t(any(), any())).thenReturn("no key")
        val result = tester.test(ProviderSpec.OpenAi(apiKey = "   "))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Success via the OpenAiModelLookupPort without making any HTTP call`() {
        whenever(openAiService.fetchAvailableModels("sk-abcdef"))
            .thenReturn(listOf("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"))

        val result = tester.test(ProviderSpec.OpenAi(apiKey = "sk-abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(true, map["success"])
        assertEquals(3, map["modelCount"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            listOf("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"),
            map["models"] as List<String>,
        )
    }

    @Test
    fun `test returns Success with the empty model list from the lookup port`() {
        whenever(openAiService.fetchAvailableModels("sk-abcdef")).thenReturn(emptyList())

        val result = tester.test(ProviderSpec.OpenAi(apiKey = "sk-abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(0, map["modelCount"])
    }

    @Test
    fun `test returns Failure when the lookup port throws`() {
        whenever(i18n.t(any(), any())).thenReturn("openai down")
        whenever(openAiService.fetchAvailableModels(any())).thenThrow(RuntimeException("connection refused"))
        val result = tester.test(ProviderSpec.OpenAi(apiKey = "sk-abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test masks the api key in the account hint`() {
        whenever(openAiService.fetchAvailableModels(any())).thenReturn(emptyList())

        val result = tester.test(ProviderSpec.OpenAi(apiKey = "sk-abcdef-12345"))
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        val hint = map["accountHint"] as String
        assertTrue(hint != "sk-abcdef-12345", "expected the API key to be masked, got: $hint")
    }

    @Test
    fun `test returns Failure with empty error message suffix when exception message is null`() {
        whenever(openAiService.fetchAvailableModels(any())).thenThrow(RuntimeException())
        whenever(i18n.t(any(), any())).thenReturn("unreachable")
        val result = tester.test(ProviderSpec.OpenAi(apiKey = "sk-abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val map = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", map["error"])
    }
}
