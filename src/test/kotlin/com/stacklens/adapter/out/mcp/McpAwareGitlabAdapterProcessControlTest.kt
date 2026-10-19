package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.function.Function

class McpAwareGitlabAdapterProcessControlTest {
    private fun mcpCfg(
        active: Boolean = true,
        routeGitlab: Boolean = true,
        gitlabBaseUrl: String = "https://gitlab.example.com",
    ) = McpServerConfig(
        active = active,
        routeGitlab = routeGitlab,
        gitlabBaseUrl = gitlabBaseUrl,
    )

    private fun status(running: Boolean): McpProcessControlPort.Status =
        McpProcessControlPort.Status(
            running = running,
            pid = if (running) 1234L else null,
            startedAt = if (running) Instant.now() else null,
            stoppedAt = if (running) null else Instant.now(),
            exitCode = if (running) null else 1,
            message = if (running) "running" else "stopped",
            logs = emptyList(),
        )

    @Test
    fun `resolvePort delegates when MCP is configured but process is not running`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        val portFactory: Function<WebClient.Builder, GitlabPort> = mock()
        val processControl: McpProcessControlPort = mock()
        whenever(portFactory.apply(any())).thenReturn(routed)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(processControl.status()).thenReturn(status(running = false))
        val adapter =
            McpAwareGitlabAdapter(
                mcpRepo = mcpRepo,
                gateway = gateway,
                objectMapper = ObjectMapper(),
                delegate = delegate,
                portFactory = portFactory,
                processControl = processControl,
            )
        val cfg = GitlabConfig(name = "x", baseUrl = "https://gitlab.example.com", accessToken = "t")
        whenever(delegate.listAccessibleProjects(any())).thenReturn(emptyList())
        val result = adapter.listAccessibleProjects(cfg)
        assertEquals(0, result.size)
    }
}
