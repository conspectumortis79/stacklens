package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.testsupport.McpRoutingFixtures
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.function.Function

class McpRoutedGitlabPortsTest {
    private val server = MockWebServer()
    private val gateway: McpGatewayPort = mock()
    private val mcpRepo: McpServerConfigPort = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed())
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun requestThrough(builder: WebClient.Builder): String? {
        server.enqueue(MockResponse().setBody("[]"))
        return builder
            .build()
            .get()
            .uri(server.url("/api/v4/projects").toString())
            .retrieve()
            .bodyToMono<String>()
            .block()
    }

    @Test
    fun `the gitlab port factory receives a routed builder`() {
        var seen: String? = null
        val factory =
            Function<WebClient.Builder, GitlabPort> { builder ->
                seen = requestThrough(builder)
                mock<GitlabPort>()
            }

        val adapter =
            McpAwareGitlabAdapter(
                mcpRepo = mcpRepo,
                gateway = gateway,
                objectMapper = objectMapper,
                delegate = mock(),
                portFactory = factory,
                processControl = mock(),
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
            )

        assertNotNull(adapter)
        assertNotNull(seen)
    }

    @Test
    fun `the ticket bridge port factory receives a routed builder`() {
        var seen: String? = null
        val factory =
            Function<WebClient.Builder, GitlabTicketBridgePort> { builder ->
                seen = requestThrough(builder)
                mock<GitlabTicketBridgePort>()
            }

        val adapter =
            McpAwareGitlabTicketBridgeAdapter(
                mcpRepo = mcpRepo,
                gateway = gateway,
                objectMapper = objectMapper,
                delegate = mock(),
                portFactory = factory,
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
            )

        assertNotNull(adapter)
        assertNotNull(seen)
    }

    @Test
    fun `the routed builder is usable without an active mcp server`() {
        val offline = McpRoutingFixtures.repoWith(null)
        var answer: String? = null
        val factory =
            Function<WebClient.Builder, GitlabPort> { builder ->
                answer = requestThrough(builder)
                mock<GitlabPort>()
            }

        McpAwareGitlabAdapter(
            mcpRepo = offline,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = mock<GitlabPort>(),
            portFactory = factory,
            processControl = mock(),
        )

        assertNotNull(answer)
    }
}
