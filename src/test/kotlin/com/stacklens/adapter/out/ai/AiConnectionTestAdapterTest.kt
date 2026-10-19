@file:Suppress("CONSTANT_NAME")

package com.stacklens.adapter.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import com.stacklens.testsupport.FakeLmStudioModelLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConnectionTestAdapterTest {
    private val i18n: I18nPort = mock()

    private val lmStudioService: LmStudioModelLookupPort = mock()

    private fun testerFor(
        spec: ProviderSpec,
        result: TestResult,
    ): AiProviderTester<ProviderSpec> {
        val tester =
            object : AiProviderTester<ProviderSpec> {
                @Suppress("UNCHECKED_CAST")
                override val specType: kotlin.reflect.KClass<ProviderSpec> = spec::class as kotlin.reflect.KClass<ProviderSpec>

                override fun test(spec: ProviderSpec): TestResult = result
            }
        return tester
    }

    @Test
    fun `TestResult Success and Failure expose their payload`() {
        val success = TestResult.Success(mapOf("success" to true))
        val failure = TestResult.Failure(ConnectionFailureKind.OTHER, mapOf("success" to false))
        assertEquals(mapOf<String, Any>("success" to true), success.payload)
        assertEquals(mapOf<String, Any>("success" to false), failure.payload)
        assertEquals(ConnectionFailureKind.OTHER, failure.kind)
        assertEquals(success, TestResult.Success(mapOf("success" to true)))
        assertNotEquals(success as Any, failure as Any)
        assertTrue(success.toString().contains("Success"))
        assertTrue(failure.toString().contains("Failure"))
    }

    @Test
    fun `testConnection dispatches to the matching tester for Ollama`() {
        val expected = TestResult.Success(mapOf("success" to true, "models" to listOf("llama3")))
        val tester = testerFor(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.Ollama("http://localhost:11434"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for OpenAi`() {
        val expected = TestResult.Failure(ConnectionFailureKind.OTHER, mapOf("success" to false, "error" to "x"))
        val tester = testerFor(ProviderSpec.OpenAi(apiKey = "k"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.OpenAi("k"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for ClaudeCode`() {
        val expected = TestResult.Success(mapOf("success" to true))
        val tester = testerFor(ProviderSpec.ClaudeCode(apiKey = "k"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.ClaudeCode("k"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for GithubToken`() {
        val expected = TestResult.Success(mapOf("success" to true, "githubUser" to "u"))
        val tester = testerFor(ProviderSpec.GithubToken(token = "t"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.GithubToken("t"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for CopilotModels`() {
        val expected = TestResult.Success(mapOf("success" to true))
        val tester = testerFor(ProviderSpec.CopilotModels(token = "t"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.CopilotModels("t"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for CopilotModel`() {
        val expected = TestResult.Success(mapOf("success" to true, "model" to "gpt-4"))
        val tester = testerFor(ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = "copilot"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.CopilotModel("gpt-4", "copilot"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for LmStudio`() {
        val expected = TestResult.Success(mapOf("success" to true))
        val tester = testerFor(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.LmStudio("http://localhost:1234"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection dispatches to the matching tester for OpenRouter`() {
        val expected = TestResult.Success(mapOf("success" to true))
        val tester = testerFor(ProviderSpec.OpenRouter(apiKey = "k"), expected)
        val adapter = AiConnectionTestAdapter(testers = listOf(tester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.testConnection(ProviderSpec.OpenRouter("k"))
        assertSame(expected, result)
    }

    @Test
    fun `testConnection picks the right tester when multiple are registered`() {
        val ollamaExpected = TestResult.Success(mapOf("success" to true, "models" to listOf("llama3")))
        val openAiExpected = TestResult.Failure(ConnectionFailureKind.OTHER, mapOf("success" to false, "error" to "openai"))
        val ollamaTester = testerFor(ProviderSpec.Ollama(baseUrl = "x"), ollamaExpected)
        val openAiTester = testerFor(ProviderSpec.OpenAi(apiKey = "k"), openAiExpected)
        val adapter = AiConnectionTestAdapter(testers = listOf(ollamaTester, openAiTester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        assertSame(ollamaExpected, adapter.testConnection(ProviderSpec.Ollama("x")))
        assertSame(openAiExpected, adapter.testConnection(ProviderSpec.OpenAi("k")))
    }

    @Test
    fun `testSpec rejects a spec that does not match the tester spec type`() {
        val tester =
            object : AiProviderTester<ProviderSpec.Ollama> {
                override val specType: kotlin.reflect.KClass<ProviderSpec.Ollama> = ProviderSpec.Ollama::class

                override fun test(spec: ProviderSpec.Ollama): TestResult = TestResult.Success(mapOf("success" to true))
            }
        assertThrows(IllegalArgumentException::class.java) {
            tester.testSpec(ProviderSpec.OpenAi("k"))
        }
    }

    @Test
    fun `testConnection throws when no tester is registered for the spec type`() {
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        assertThrows(IllegalStateException::class.java) {
            adapter.testConnection(ProviderSpec.GithubToken("x"))
        }
    }

    @Test
    fun `testConnection throws when only unrelated testers are registered`() {
        val ollamaTester = testerFor(ProviderSpec.Ollama(baseUrl = "x"), TestResult.Success(mapOf("success" to true)))
        val adapter = AiConnectionTestAdapter(testers = listOf(ollamaTester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        assertThrows(IllegalStateException::class.java) {
            adapter.testConnection(ProviderSpec.OpenAi("k"))
        }
    }

    @Test
    fun `testConnection throws when no testers are given at all`() {
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        assertThrows(IllegalStateException::class.java) {
            adapter.testConnection(ProviderSpec.CopilotModel("gpt-4", "copilot"))
        }
    }

    @Test
    fun `resolveLmStudioModel returns requested model when non-blank`() {
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("requested", null, AiConfig(id = 1L))
        assertEquals("requested", result)
    }

    @Test
    fun `resolveLmStudioModel returns existing model for LMSTUDIO provider`() {
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val existing = AiConfig(id = 1L, aiProvider = "lmstudio", model = "existing-model")
        val result = adapter.resolveLmStudioModel("", null, existing)
        assertEquals("existing-model", result)
    }

    @Test
    fun `resolveLmStudioModel discovers model from lmStudioService when requested and existing are blank`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("discovered", "other"))
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("", null, AiConfig(id = 1L, aiProvider = "openai"))
        assertEquals("discovered", result)
    }

    @Test
    fun `resolveLmStudioModel falls back to provider default when discovery fails`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenThrow(RuntimeException("oops"))
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("", null, AiConfig(id = 1L))
        assertEquals(com.stacklens.domain.enums.AiProvider.LMSTUDIO.defaultModel, result)
    }

    @Test
    fun `resolveLmStudioModel falls back to provider default when discovery returns empty`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(emptyList())
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("", null, AiConfig(id = 1L))
        assertEquals(com.stacklens.domain.enums.AiProvider.LMSTUDIO.defaultModel, result)
    }

    @Test
    fun `resolveLmStudioModel prefers requestedLmStudioUrl over existing lmStudioUrl`() {
        whenever(lmStudioService.fetchAvailableModels("http://requested:9999")).thenReturn(listOf("from-requested"))
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val existing = AiConfig(id = 1L, lmStudioUrl = "http://existing:1234")
        val result = adapter.resolveLmStudioModel(null, "http://requested:9999", existing)
        assertEquals("from-requested", result)
    }

    @Test
    fun `resolveLmStudioModel uses existing lmStudioUrl when requested is blank`() {
        whenever(lmStudioService.fetchAvailableModels("http://existing:1234")).thenReturn(listOf("from-existing"))
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val existing = AiConfig(id = 1L, lmStudioUrl = "http://existing:1234")
        val result = adapter.resolveLmStudioModel(null, "  ", existing)
        assertEquals("from-existing", result)
    }

    @Test
    fun `resolveLmStudioModel falls back to localhost default when no url is configured`() {
        whenever(lmStudioService.fetchAvailableModels("http://localhost:1234")).thenReturn(listOf("local"))
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = lmStudioService, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("", "", AiConfig(id = 1L))
        assertEquals("local", result)
    }

    @Test
    fun `resolveLmStudioModel returns null-test-model when lmStudioService returns null and existing is default`() {
        val nullLmStudio: LmStudioModelLookupPort = mock()
        whenever(nullLmStudio.fetchAvailableModels(any())).thenReturn(null)
        val adapter = AiConnectionTestAdapter(testers = emptyList(), lmStudioService = nullLmStudio, containerRuntimeProbe = FakeContainerRuntimeProbePort())
        val result = adapter.resolveLmStudioModel("", null, AiConfig(id = 1L))
        assertEquals(com.stacklens.domain.enums.AiProvider.LMSTUDIO.defaultModel, result)
    }

    @Test
    fun `resolveLmStudioModel falls back to the default model when the lookup fails`() {
        val adapter =
            AiConnectionTestAdapter(
                testers = emptyList(),
                lmStudioService = FakeLmStudioModelLookupPort { throw RuntimeException("lm studio unreachable") },
                containerRuntimeProbe = FakeContainerRuntimeProbePort(),
            )
        val result = adapter.resolveLmStudioModel("my-model", null, AiConfig(id = 1L))
        assertEquals("my-model", result)
        val result2 = adapter.resolveLmStudioModel("", null, AiConfig(id = 1L))
        assertNull(result2.let { if (it == com.stacklens.domain.enums.AiProvider.LMSTUDIO.defaultModel) null else it })
    }
}
