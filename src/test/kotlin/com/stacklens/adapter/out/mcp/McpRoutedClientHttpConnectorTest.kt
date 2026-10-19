package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.http.client.reactive.ClientHttpResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.util.function.Function

class McpRoutedClientHttpConnectorTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val mapper = ObjectMapper()
    private val fallback: ClientHttpConnector = mock()

    private fun connector(
        predicate: (McpServerConfig) -> Boolean = { true },
        tool: String = "proxy",
    ) = McpRoutedClientHttpConnector(
        mcpRepo = mcpRepo,
        gateway = gateway,
        objectMapper = mapper,
        tool = tool,
        predicate = predicate,
        fallback = fallback,
    )

    private fun activeMcp(
        name: String = "mcp",
        active: Boolean = true,
    ) = McpServerConfig(
        name = name,
        baseUrl = "http://localhost:7456",
        active = active,
    )

    private fun emptyRequest(): Function<ClientHttpRequest, Mono<Void>> = Function { Mono.empty() }

    @Test
    fun `connect delegates to fallback when no MCP server is active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val resp = mock<ClientHttpResponse>()
        whenever(fallback.connect(any(), any(), any())).thenReturn(Mono.just(resp))
        val result =
            connector().connect(HttpMethod.GET, URI.create("http://x/api/v4/x"), emptyRequest())
        result.block() ?: error("Expected response")
        verify(fallback).connect(eq(HttpMethod.GET), eq(URI.create("http://x/api/v4/x")), any())
    }

    @Test
    fun `connect invokes MCP tool and returns the gateway response`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"result":"ok"}""", isError = false),
        )
        val c =
            connector(
                predicate = { cfg -> cfg.active && cfg.routeGitlab },
                tool = "gitlab_api_proxy",
            )
        val result = c.connect(HttpMethod.GET, URI.create("http://gitlab/api/v4/projects"), emptyRequest())
        val resp = result.block() ?: error("Expected response")
        val bytes = collectBody(resp)
        val body = String(bytes, Charsets.UTF_8)
        assertEquals("""{"result":"ok"}""", body)
        verify(gateway).callTool(
            any(),
            eq("gitlab_api_proxy"),
            any(),
        )
    }

    @Test
    fun `connect falls back when MCP gateway returns an error result`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "upstream 502", isError = true),
        )
        val fallbackResp = mock<ClientHttpResponse>()
        whenever(fallback.connect(any(), any(), any())).thenReturn(Mono.just(fallbackResp))
        val c = connector()
        val result = c.connect(HttpMethod.POST, URI.create("http://gitlab/api/v4/x"), emptyRequest())
        result.block() ?: error("Expected fallback response")
        verify(fallback).connect(eq(HttpMethod.POST), eq(URI.create("http://gitlab/api/v4/x")), any())
    }

    @Test
    fun `connect falls back when gateway throws`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        val fallbackResp = mock<ClientHttpResponse>()
        whenever(fallback.connect(any(), any(), any())).thenReturn(Mono.just(fallbackResp))
        val c = connector()
        val result = c.connect(HttpMethod.GET, URI.create("http://x/y"), emptyRequest())
        result.block() ?: error("Expected fallback response")
        verify(fallback).connect(eq(HttpMethod.GET), eq(URI.create("http://x/y")), any())
    }

    @Test
    fun `connect forwards JSON request body to MCP tool args as parsed tree`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val bodyJson = """{"name":"hello"}"""
        val c = connector()
        val factory = DefaultDataBufferFactory.sharedInstance
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                req.writeWith(
                    Flux.just(factory.wrap(bodyJson.toByteArray(Charsets.UTF_8))),
                )
            }
        c.connect(HttpMethod.POST, URI.create("http://x/api/test"), requestCallback).block()
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
        verify(gateway).callTool(any(), any(), captor.capture())
        val args = captor.lastValue
        assertEquals("POST", args["method"])
        assertEquals("/api/test", args["path"])
        assertNotNull(args["body"])
    }

    @Test
    fun `connect forwards non-JSON request body as raw string`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "ok", isError = false),
        )
        val bodyText = "raw text not json"
        val c = connector()
        val factory = DefaultDataBufferFactory.sharedInstance
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                req.writeWith(
                    Flux.just(factory.wrap(bodyText.toByteArray(Charsets.UTF_8))),
                )
            }
        c.connect(HttpMethod.POST, URI.create("http://x/api/x"), requestCallback).block()
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
        verify(gateway).callTool(any(), any(), captor.capture())
        val args = captor.lastValue
        assertEquals(bodyText, args["body"])
    }

    @Test
    fun `connect forwards query string in path argument`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val c = connector()
        c.connect(HttpMethod.GET, URI.create("http://x/api/test?a=1&b=2"), emptyRequest()).block()
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
        verify(gateway).callTool(any(), any(), captor.capture())
        assertEquals("/api/test?a=1&b=2", captor.lastValue["path"])
    }

    @Test
    fun `connect invokes writeAndFlushWith and setComplete on the underlying BufferingClientHttpRequest`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val factory = DefaultDataBufferFactory.sharedInstance
        val bodyJson = """{"x":1}"""
        val capturedRequest =
            java.util.concurrent.atomic
                .AtomicReference<ClientHttpRequest?>(null)
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                capturedRequest.set(req)
                req.headers.contentType = MediaType.APPLICATION_JSON
                val pub = Flux.just(factory.wrap(bodyJson.toByteArray(Charsets.UTF_8)))

                @Suppress("UNCHECKED_CAST")
                val nested = Flux.just(pub as org.reactivestreams.Publisher<out org.springframework.core.io.buffer.DataBuffer>)
                req.writeAndFlushWith(nested).then(req.setComplete())
            }
        val c = connector()
        c.connect(HttpMethod.PUT, URI.create("http://x/api/y"), requestCallback).block()
        val req = capturedRequest.get() ?: error("Request not captured")
        assertEquals(HttpMethod.PUT, req.method)
        assertEquals(URI.create("http://x/api/y"), req.uri)
    }

    @Test
    fun `BufferingClientHttpRequest getNativeRequest returns the receiver instance`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val capturedRequest =
            java.util.concurrent.atomic
                .AtomicReference<ClientHttpRequest?>(null)
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                capturedRequest.set(req)
                Mono.empty()
            }
        val c = connector()
        c.connect(HttpMethod.GET, URI.create("http://x/"), requestCallback).block()
        val req = capturedRequest.get() ?: error("Request not captured")
        val nativeRequest = req.javaClass.getDeclaredMethod("getNativeRequest").apply { isAccessible = true }
        val result = nativeRequest.invoke(req)
        assertNotNull(result)
    }

    @Test
    fun `BufferingClientHttpRequest setComplete commits an empty body`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val factory = DefaultDataBufferFactory.sharedInstance
        val capturedRequest =
            java.util.concurrent.atomic
                .AtomicReference<ClientHttpRequest?>(null)
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                capturedRequest.set(req)
                req.headers.contentType = MediaType.APPLICATION_JSON
                req.setComplete()
            }
        val c = connector()
        c.connect(HttpMethod.POST, URI.create("http://x/empty"), requestCallback).block()
        assertNotNull(capturedRequest.get())
    }

    @Test
    fun `BufferingClientHttpRequest bufferFactory is callable`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "{}", isError = false),
        )
        val capturedRequest =
            java.util.concurrent.atomic
                .AtomicReference<ClientHttpRequest?>(null)
        val requestCallback =
            Function<ClientHttpRequest, Mono<Void>> { req ->
                capturedRequest.set(req)
                Mono.empty()
            }
        val c = connector()
        c.connect(HttpMethod.GET, URI.create("http://x/"), requestCallback).block()
        val req = capturedRequest.get() ?: error("Request not captured")
        val method = req.javaClass.getDeclaredMethod("bufferFactory").apply { isAccessible = true }
        val result = method.invoke(req)
        assertNotNull(result)
    }

    @Test
    fun `connect returns response with JSON content type and matching body bytes`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"k":"v"}""", isError = false),
        )
        val c = connector()
        val resp = c.connect(HttpMethod.GET, URI.create("http://x/y"), emptyRequest()).block() ?: error("Expected response")
        assertEquals(MediaType.APPLICATION_JSON, resp.headers.contentType)
        val bytes = collectBody(resp)
        assertEquals("""{"k":"v"}""", String(bytes, Charsets.UTF_8))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `BufferingClientHttpResponse getStatusCode returns the configured status`() {
        val resp = BufferingClientHttpResponse(HttpStatus.OK, "{}".toByteArray())
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `BufferingClientHttpResponse getCookies returns the empty cookie map`() {
        val resp = BufferingClientHttpResponse(HttpStatus.OK, "{}".toByteArray())
        assertTrue(resp.cookies.isEmpty())
    }

    @Test
    fun `BufferingClientHttpResponse getId returns a non-empty unique id`() {
        val resp = BufferingClientHttpResponse(HttpStatus.OK, "{}".toByteArray())
        assertTrue(resp.id.isNotEmpty())
        val resp2 = BufferingClientHttpResponse(HttpStatus.OK, "{}".toByteArray())
        assertTrue(resp.id != resp2.id)
    }

    private fun collectBody(resp: ClientHttpResponse): ByteArray {
        val buffers = resp.body.collectList().block() ?: emptyList()
        val all = ByteArray(buffers.sumOf { it.readableByteCount() })
        var offset = 0
        for (buf in buffers) {
            val n = buf.readableByteCount()
            buf.read(all, offset, n)
            offset += n
            DataBufferUtils.release(buf)
        }
        return all
    }
}
