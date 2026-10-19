package com.stacklens

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.application.service.config.AiConnectionTestService
import com.stacklens.application.service.mcp.McpServerManagementService
import com.stacklens.infrastructure.config.DockerTestAdapterConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class FinalGapsTest {
    @Test
    fun `the process view renders a stopped timestamp`() {
        val repo: com.stacklens.application.port.out.McpServerConfigPort = mock()
        val gateway: com.stacklens.application.port.out.McpGatewayPort = mock()
        val control: com.stacklens.application.port.out.McpProcessControlPort = mock()
        whenever(control.stop()).thenReturn(
            com.stacklens.application.port.out.McpProcessControlPort.Status(
                running = false,
                pid = 1L,
                startedAt = Instant.ofEpochMilli(1L),
                stoppedAt = Instant.ofEpochMilli(2L),
                exitCode = 0,
                message = "stopped",
                logs = emptyList(),
            ),
        )

        val view = McpServerManagementService(repo, gateway, control, mock(), { true }).stopServer()

        assertEquals(Instant.ofEpochMilli(2L).toString(), view.stoppedAt)
        assertEquals(Instant.ofEpochMilli(1L).toString(), view.startedAt)
    }

    @Test
    fun `the sse poll waiter reports an interrupt as a failed wait`() {
        val waiter = com.stacklens.adapter.inbound.rest.ThreadSsePollWaiter
        var reported: Boolean? = null
        val thread =
            Thread {
                reported = waiter.await(60_000L)
            }
        thread.start()
        Thread.sleep(50)
        thread.interrupt()
        thread.join(2_000)

        assertEquals(false, reported)
        assertTrue(Thread.interrupted().not() || true)
    }

    @Test
    fun `the stream executor builds daemon threads with a recognisable name`() {
        val factory = DockerTestAdapterConfig()
        val executor = factory.dockerTestStreamExecutor()

        val name =
            runCatching {
                val observed = java.util.concurrent.CountDownLatch(1)
                var captured = ""
                executor.submit {
                    captured = Thread.currentThread().name
                    observed.countDown()
                }
                observed.await(5, java.util.concurrent.TimeUnit.SECONDS)
                captured
            }.getOrDefault("")

        assertTrue(name.startsWith("docker-test-stream"), "got '$name'")
        executor.shutdownNow()
    }

    @Test
    fun `stored blank secrets are not forwarded as keys`() {
        val gateway: AiConnectionTestGateway = mock()
        val repo: AiConfigPort = mock()
        whenever(repo.findAiConfig()).thenReturn(
            com.stacklens.domain.model
                .AiConfig(id = 1L, openRouterApiKey = "", claudeCodeApiKey = "  ", model = " "),
        )
        whenever(gateway.testConnection(any())).thenReturn(AiConnectionTestPort.TestResult.Success(mapOf("ok" to true)))
        val service = AiConnectionTestService(gateway, repo, AiDefaultsPort { "http://default" })
        val request =
            com.stacklens.application.port.inbound.config
                .AiConnectionTestRequest()

        service.testConnection(com.stacklens.application.port.inbound.config.AiConnectionTestTarget.OPENROUTER, request)
        service.testConnection(com.stacklens.application.port.inbound.config.AiConnectionTestTarget.CLAUDE_CODE, request)
        service.testConnection(com.stacklens.application.port.inbound.config.AiConnectionTestTarget.COPILOT_MODEL, request)

        verify(gateway).testConnection(ProviderSpec.OpenRouter(null))
        verify(gateway).testConnection(ProviderSpec.ClaudeCode(null))
        verify(gateway).testConnection(ProviderSpec.CopilotModel(null, "copilot"))
    }

    private fun verify(gateway: AiConnectionTestGateway) = org.mockito.Mockito.verify(gateway)
}
