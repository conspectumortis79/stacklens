package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import com.stacklens.testsupport.FakeSleeper
import com.stacklens.testsupport.RecordingLmStudioLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConnectionAdaptersProbeTest {
    @Test
    fun `the default config resolver maps every stored value and tolerates a missing config`() {
        val repo = mock<AiConfigPort>()
        val resolver = DefaultAiConfigResolver(repo)

        whenever(repo.findAiConfig())
            .thenReturn(AiConfig(ollamaUrl = "http://ollama:11434", ollamaNumCtx = 2048, model = "llama3", language = "de"))
        assertEquals("http://ollama:11434", resolver.findOllamaUrl())
        assertEquals(2048, resolver.findOllamaNumCtx())
        assertEquals("llama3", resolver.findModel())
        assertEquals("de", resolver.findLanguage())

        whenever(repo.findAiConfig()).thenReturn(null)
        assertEquals(null, resolver.findOllamaUrl())
        assertEquals(0, resolver.findOllamaNumCtx())
        assertEquals(null, resolver.findModel())
        assertEquals(null, resolver.findLanguage())
    }

    @Test
    fun `a compatible endpoint uses the first usable environment key`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "Provider",
                fallbackBaseUrl = "https://api.example.com/v1",
                defaultModel = "model-x",
                envVarNames = listOf("BLANK_FIRST", "REAL_SECOND"),
                envReader = { name -> mapOf("BLANK_FIRST" to "  ", "REAL_SECOND" to "schluessel")[name] },
            )

        assertEquals("schluessel", endpoint.resolveApiKey(null))
        assertEquals("https://api.example.com/v1", endpoint.resolveBaseUrl(null))
    }

    @Test
    fun `a compatible endpoint without environment keys falls back to the stored values`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "Provider",
                fallbackBaseUrl = "https://fallback.example/v1",
                defaultModel = "m",
                apiKeyOf = { config -> config?.openAiApiKey },
                baseUrlOf = { config -> config?.compatibleBaseUrl },
                envReader = { null },
            )

        assertEquals(null, endpoint.resolveApiKey(AiConfig(openAiApiKey = "   ")))
        assertEquals("configured", endpoint.resolveApiKey(AiConfig(openAiApiKey = "configured")))
        assertEquals("https://andere.example/v1", endpoint.resolveBaseUrl(AiConfig(compatibleBaseUrl = "https://andere.example/v1")))
    }

    @Test
    fun `the connection test adapter dispatches by provider spec and rejects unknown specs`() {
        val ollamaTester =
            object : AiProviderTester<ProviderSpec.Ollama> {
                override val specType = ProviderSpec.Ollama::class

                override fun test(spec: ProviderSpec.Ollama): AiConnectionTestPort.TestResult = AiConnectionTestPort.TestResult.Success(mapOf("base" to spec.baseUrl))
            }
        val adapter =
            AiConnectionTestAdapter(
                listOf(ollamaTester),
                RecordingLmStudioLookup { emptyList() },
                FakeContainerRuntimeProbePort(),
            )

        val result = adapter.testConnection(ProviderSpec.Ollama("http://x"))

        assertEquals(
            mapOf("base" to "http://x"),
            (result as AiConnectionTestPort.TestResult.Success).payload,
        )
        assertThrows(IllegalStateException::class.java) { adapter.testConnection(ProviderSpec.OpenAi("k")) }
    }

    @Test
    fun `resolveLmStudioModel prefers the request then the stored model then the server list`() {
        val lmStudio = RecordingLmStudioLookup { listOf("geladenes-modell") }
        val adapter =
            AiConnectionTestAdapter(
                emptyList(),
                lmStudio,
                FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
            )

        assertEquals("gefragt", adapter.resolveLmStudioModel("gefragt", null, AiConfig()))
        assertEquals(
            "gespeichert",
            adapter.resolveLmStudioModel(null, null, AiConfig(aiProvider = AiProvider.LMSTUDIO.id, model = "gespeichert")),
        )
        assertEquals(
            "geladenes-modell",
            adapter.resolveLmStudioModel("   ", "http://localhost:1234", AiConfig(aiProvider = "anderer", model = "  ")),
        )
        assertEquals(listOf("http://localhost:1234"), lmStudio.requested)
    }

    @Test
    fun `resolveLmStudioModel falls back to the default model when the server cannot help`() {
        val failing = RecordingLmStudioLookup { throw IllegalStateException("offline") }
        val offlineAdapter =
            AiConnectionTestAdapter(
                emptyList(),
                failing,
                FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
            )
        val empty = RecordingLmStudioLookup { listOf("  ") }
        val dockerAdapter =
            AiConnectionTestAdapter(
                emptyList(),
                empty,
                FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER),
            )

        assertEquals(AiProvider.LMSTUDIO.defaultModel, offlineAdapter.resolveLmStudioModel(null, null, AiConfig()))
        assertEquals(AiProvider.LMSTUDIO.defaultModel, dockerAdapter.resolveLmStudioModel(null, "http://localhost:1234", AiConfig()))
        assertEquals(listOf("http://host.docker.internal:1234"), empty.requested)
    }

    @Test
    fun `http retries give up after the configured attempts and keep the last error`() {
        var calls = 0
        val sleeper = FakeSleeper()
        val retry = HttpSendRetry(maxAttempts = 2, initialBackoffMs = 5L, sleeper = sleeper)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                retry.sendWithRetry("op") {
                    calls++
                    throw java.io.IOException("netz weg")
                }
            }

        assertEquals(2, calls)
        assertEquals(listOf(5L), sleeper.requests)
        assertTrue(failure.message!!.contains("failed after 2 attempt(s)"), "unexpected: ${failure.message}")
        assertTrue(failure.cause is java.io.IOException, "the last error must be kept as the cause")
    }

    @Test
    fun `http retries stop immediately when the sleeper reports an interruption`() {
        val retry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 5L, sleeper = FakeSleeper(awake = false))

        val failure =
            assertThrows(InterruptedException::class.java) {
                retry.sendWithRetry("op") { throw java.io.IOException("netz weg") }
            }

        assertEquals("HTTP retry 'op' interrupted after attempt 1", failure.message)
        assertTrue(Thread.interrupted(), "the interrupt flag must be restored")
    }

    @Test
    fun `http retries pass a successful response through`() {
        val retry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L, sleeper = FakeSleeper())

        assertEquals("antwort", retry.sendWithRetry("op") { "antwort" })
    }

    @Test
    fun `an interrupted request keeps the interrupt flag and the original exception`() {
        val retry = HttpSendRetry(sleeper = FakeSleeper())

        assertThrows(InterruptedException::class.java) {
            retry.sendWithRetry("op") { throw InterruptedException("abgebrochen") }
        }
        assertTrue(Thread.interrupted())
    }

    @Test
    fun `the retry guard rejects unusable configurations`() {
        assertThrows(IllegalArgumentException::class.java) { HttpSendRetry(maxAttempts = 0) }
        assertThrows(IllegalArgumentException::class.java) { HttpSendRetry(initialBackoffMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { HttpSendRetry(backoffMultiplier = 0.5) }
    }
}
