package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.domain.model.McpServerConfig
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient

class McpHttpClientTransportFailureTest {
    private lateinit var server: MockWebServer
    private val databaseConfigs = mock<DatabaseConfigPort>()

    @BeforeEach
    fun startAndStopServer() {
        server = MockWebServer()
        server.start()
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun deadConfig() =
        McpServerConfig(
            id = 987_654L,
            name = "unreachable",
            baseUrl = server.url("/").toString().trimEnd('/'),
            authToken = "",
        )

    @Test
    fun `a tool call whose transport breaks is reported as an error result`() {
        server.shutdown()
        val client = McpHttpClient(WebClient.builder(), ObjectMapper(), databaseConfigs, McpCircuitBreaker())

        val result = client.callTool(deadConfig(), "stacklens_transport_probe", emptyMap())

        assertTrue(result.isError)
        assertTrue(result.text.contains("stacklens_transport_probe"), result.text)
    }
}
