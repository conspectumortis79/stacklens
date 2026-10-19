package com.stacklens.application.service

import ch.qos.logback.classic.Level
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.config.AiConfigSecretFieldRegistry
import com.stacklens.application.service.config.AiConfigService
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger

class AiConfigServiceLoggingTest {
    private val repo: AiConfigPort = mock()

    private val gateway: AiConnectionTestGateway = mock()

    private val service = AiConfigService(repo, gateway, AiConfigSecretFieldRegistry())

    private val logger: LogbackLogger = LoggerFactory.getLogger(AiConfigService::class.java) as LogbackLogger

    private var previousLevel: Level? = null

    @BeforeEach
    fun enableDebug() {
        previousLevel = logger.level
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun restoreLevel() {
        logger.level = previousLevel
    }

    private fun stubSave() {
        whenever(repo.saveAiConfig(any())).thenAnswer { invocation -> invocation.getArgument<AiConfig>(0) }
    }

    @Test
    fun `a stored claude key is described by the debug line`() {
        stubSave()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "claudecode", claudeCodeApiKey = "sk-ant-0123456789"))

        service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "claudecode", model = "m"))
    }

    @Test
    fun `a missing claude key is described as empty by the debug line`() {
        stubSave()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "claudecode", claudeCodeApiKey = null))

        service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "claudecode", model = "m"))
    }

    @Test
    fun `a blank submitted url clears the stored one`() {
        stubSave()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, lmStudioUrl = "http://stored"))

        val saved = service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "lmstudio", lmStudioUrl = "   "))

        assertEquals(null, saved.lmStudioUrl)
    }

    @Test
    fun `a submitted url replaces the stored one`() {
        stubSave()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, lmStudioUrl = "http://stored"))

        val saved = service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "lmstudio", lmStudioUrl = "http://fresh"))

        assertEquals("http://fresh", saved.lmStudioUrl)
    }

    @Test
    fun `an unknown submitted provider falls back to the stored one and then to copilot`() {
        stubSave()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "openai"))

        assertEquals("openai", service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "quatsch")).aiProvider)

        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "   "))
        assertEquals("copilot", service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "quatsch")).aiProvider)

        whenever(repo.findAiConfig()).thenReturn(null)
        assertEquals("ollama", service.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "ollama")).aiProvider)
    }
}

class ConnectionHealthProbeBackgroundTest {
    @Test
    fun `a successful background probe reports completion`() {
        val port = RecordingProbeService()

        port.probeAllInBackground()

        assertEquals(1, port.probeRuns)
    }

    @Test
    fun `a failing background probe is reported but never thrown`() {
        val port = RecordingProbeService(throwOnProbe = true)

        port.probeAllInBackground()

        assertEquals(1, port.probeRuns)
    }

    private class RecordingProbeService(
        private val throwOnProbe: Boolean = false,
    ) : com.stacklens.application.service.health.ConnectionHealthProbeService(
            adapters = emptyList(),
            probeExecutor =
                java.util.concurrent.Executors
                    .newSingleThreadExecutor(),
            startupTasks =
                object : com.stacklens.application.port.out.async.StartupTaskExecutor {
                    override fun submit(task: () -> Unit) {
                        task()
                    }
                },
        ) {
        var probeRuns = 0

        override fun probeAll() {
            probeRuns++
            if (throwOnProbe) throw IllegalStateException("probe pool exhausted")
        }
    }
}

class AiConnectionTestSecretsTest {
    private val gateway: com.stacklens.application.port.out.ai.AiConnectionTestGateway = mock()

    private val repo: AiConfigPort = mock()

    private val service =
        com.stacklens.application.service.config.AiConnectionTestService(
            gateway,
            repo,
            com.stacklens.application.port.out.config
                .AiDefaultsPort { "http://default" },
        )

    @Test
    fun `the copilot model spec prefers the submitted model over the stored one`() {
        whenever(repo.findAiConfig()) doReturn AiConfig(id = 1L, model = "stored", aiProvider = "copilot")
        whenever(gateway.testConnection(any())).thenReturn(
            com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
                .Success(mapOf("ok" to true)),
        )

        service.testConnection(
            com.stacklens.application.port.inbound.config.AiConnectionTestTarget.COPILOT_MODEL,
            com.stacklens.application.port.inbound.config
                .AiConnectionTestRequest(model = "   "),
        )

        org.mockito.Mockito
            .verify(gateway)
            .testConnection(ProviderSpec.CopilotModel("stored", "copilot"))
    }

    @Test
    fun `a submitted blank api key falls back to the stored key of every provider`() {
        whenever(repo.findAiConfig()) doReturn AiConfig(id = 1L, openRouterApiKey = "orr", claudeCodeApiKey = "cc")
        whenever(gateway.testConnection(any())).thenReturn(
            com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
                .Success(mapOf("ok" to true)),
        )

        service.testConnection(
            com.stacklens.application.port.inbound.config.AiConnectionTestTarget.OPENROUTER,
            com.stacklens.application.port.inbound.config
                .AiConnectionTestRequest(openRouterApiKey = ""),
        )
        service.testConnection(
            com.stacklens.application.port.inbound.config.AiConnectionTestTarget.CLAUDE_CODE,
            com.stacklens.application.port.inbound.config
                .AiConnectionTestRequest(claudeCodeApiKey = ""),
        )

        org.mockito.Mockito
            .verify(gateway)
            .testConnection(ProviderSpec.OpenRouter("orr"))
        org.mockito.Mockito
            .verify(gateway)
            .testConnection(ProviderSpec.ClaudeCode("cc"))
    }
}
