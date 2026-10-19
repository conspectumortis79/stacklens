
package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.client.reactive.AbstractClientHttpRequest
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.http.client.reactive.ClientHttpResponse
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID
import java.util.function.Function

class McpRoutedClientHttpConnector(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    private val tool: String,
    private val predicate: (McpServerConfig) -> Boolean,
    private val fallback: ClientHttpConnector,
) : ClientHttpConnector {
    private val log = LoggerFactory.getLogger(McpRoutedClientHttpConnector::class.java)

    override fun connect(
        method: HttpMethod,
        uri: URI,
        requestCallback: Function<in ClientHttpRequest, Mono<Void>>,
    ): Mono<ClientHttpResponse> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo, predicate)
                ?: return fallback.connect(method, uri, requestCallback)
        val request = BufferingClientHttpRequest(method, uri)
        return requestCallback.apply(request).then(
            Mono
                .fromCallable<ClientHttpResponse> {
                    invokeMcp(mcp, method, uri, request)
                }.onErrorResume { err ->
                    log.warn("[MCP] {} routing failed for {} {}, falling back: {}", tool, method, uri, err.message)
                    fallback.connect(method, uri, requestCallback)
                },
        )
    }

    private fun invokeMcp(
        mcp: McpServerConfig,
        method: HttpMethod,
        uri: URI,
        request: BufferingClientHttpRequest,
    ): ClientHttpResponse {
        val pathArg = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        val args =
            mutableMapOf<String, Any?>(
                "method" to method.name(),
                "path" to pathArg,
            )
        val bodyBytes = request.bodyAsBytes()
        if (bodyBytes.isNotEmpty()) {
            try {
                args["body"] = objectMapper.readTree(bodyBytes)
            } catch (e: Exception) {
                args["body"] = String(bodyBytes, Charsets.UTF_8)
            }
        }
        val res = gateway.callTool(mcp, tool, args)
        if (res.isError) {
            throw RuntimeException("MCP $tool error: ${res.text}")
        }
        val responseBody = res.text.toByteArray(Charsets.UTF_8)
        return BufferingClientHttpResponse(HttpStatus.OK, responseBody)
    }
}

private class BufferingClientHttpRequest(
    private val httpMethod: HttpMethod,
    private val uri: URI,
) : AbstractClientHttpRequest() {
    private val buffer = ByteArrayOutputStream()
    private val factory: DataBufferFactory = DefaultDataBufferFactory.sharedInstance

    fun bodyAsBytes(): ByteArray = buffer.toByteArray()

    override fun getMethod(): HttpMethod = httpMethod

    override fun getURI(): URI = uri

    override fun bufferFactory(): DataBufferFactory = factory

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getNativeRequest(): T = this as T

    override fun applyHeaders() {
    }

    override fun applyCookies() {
    }

    override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> = doCommit { writeWithInternal(body) }

    override fun writeAndFlushWith(body: Publisher<out Publisher<out DataBuffer>>): Mono<Void> = doCommit { writeAndFlushWithInternal(body) }

    override fun setComplete(): Mono<Void> = doCommit()

    private fun writeWithInternal(body: Publisher<out DataBuffer>): Mono<Void> =
        Flux
            .from(body)
            .doOnNext { db ->
                try {
                    val bytes = ByteArray(db.readableByteCount())
                    db.read(bytes)
                    buffer.write(bytes)
                } finally {
                    DataBufferUtils.release(db)
                }
            }.then()

    private fun writeAndFlushWithInternal(body: Publisher<out Publisher<out DataBuffer>>): Mono<Void> = Flux.from(body).flatMap { writeWithInternal(it) }.then()
}

internal class BufferingClientHttpResponse(
    private val status: HttpStatusCode,
    private val bytes: ByteArray,
) : ClientHttpResponse {
    private val factory: DataBufferFactory = DefaultDataBufferFactory.sharedInstance
    private val cookies: MultiValueMap<String, ResponseCookie> = LinkedMultiValueMap()
    private val httpHeaders: HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            contentLength = bytes.size.toLong()
        }
    private val id = UUID.randomUUID().toString()

    override fun getStatusCode(): HttpStatusCode = status

    override fun getHeaders(): HttpHeaders = httpHeaders

    override fun getCookies(): MultiValueMap<String, ResponseCookie> = cookies

    override fun getBody(): Flux<DataBuffer> = Flux.just(factory.wrap(bytes))

    override fun getId(): String = id
}
