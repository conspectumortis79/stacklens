package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiConnectionTestServiceClaudeCodeTest {
    @Mock private lateinit var aiConfigPort: AiConfigPort

    @Mock private lateinit var gateway: AiConnectionTestGateway

    private val successPayload: Map<String, Any> = mapOf("success" to true)

    private fun service() = AiConnectionTestService(gateway, aiConfigPort, AiDefaultsPort { "https://compatible.test/v1" })

    private fun probe(request: AiConnectionTestRequest): Map<String, Any> = service().testConnection(AiConnectionTestTarget.CLAUDE_CODE, request)

    @Test
    fun `a freshly typed token and model are probed together`() {
        val spec = ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-typed", model = "claude-opus-4-5")
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(successPayload))

        assertEquals(successPayload, probe(AiConnectionTestRequest(claudeCodeApiKey = "sk-ant-oat01-typed", model = "claude-opus-4-5")))
        verify(gateway).testConnection(spec)
    }

    @Test
    fun `the stored model is used when claude code is the stored provider`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "claudecode", claudeCodeApiKey = "stored", model = "claude-haiku-4-5"))
        val spec = ProviderSpec.ClaudeCode(apiKey = "stored", model = "claude-haiku-4-5")
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(successPayload))

        assertEquals(successPayload, probe(AiConnectionTestRequest()))
        verify(gateway).testConnection(spec)
    }

    @Test
    fun `a model stored for another provider is not probed against claude code`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "compatible", claudeCodeApiKey = "stored", model = "qwen3.8-flash"))
        val spec = ProviderSpec.ClaudeCode(apiKey = "stored", model = null)
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(successPayload))

        assertEquals(successPayload, probe(AiConnectionTestRequest()))
        verify(gateway).testConnection(spec)
    }

    @Test
    fun `the mask placeholder keeps the stored token`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "claudecode", claudeCodeApiKey = "stored", model = null))
        val spec = ProviderSpec.ClaudeCode(apiKey = "stored", model = null)
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(successPayload))

        assertEquals(successPayload, probe(AiConnectionTestRequest(claudeCodeApiKey = "••••••••")))
        verify(gateway).testConnection(spec)
    }

    @Test
    fun `the compatible endpoint keeps resolving its own stored model`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "compatible", compatibleApiKey = "ckey", compatibleBaseUrl = "https://saved.test/v1", model = "qwen3.8-max"))
        val spec = ProviderSpec.Compatible("ckey", "https://saved.test/v1", "qwen3.8-max")
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(successPayload))

        assertEquals(successPayload, service().testConnection(AiConnectionTestTarget.COMPATIBLE, AiConnectionTestRequest()))
        verify(gateway).testConnection(spec)
    }
}
