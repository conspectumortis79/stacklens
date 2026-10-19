package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class AiHttpClientExecutorCoverageTest {
    private val executor = AiHttpClientExecutor()

    private fun requestTo(uri: URI): HttpRequest {
        val request = mock<HttpRequest>()
        whenever(request.uri()).thenReturn(uri)
        whenever(request.method()).thenReturn("POST")
        return request
    }

    private fun clientReturning(future: CompletableFuture<HttpResponse<String>>): HttpClient {
        val client = mock<HttpClient>()
        whenever(client.sendAsync(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(future)
        return client
    }

    private fun failedWith(throwable: Throwable): CompletableFuture<HttpResponse<String>> = CompletableFuture<HttpResponse<String>>().apply { completeExceptionally(throwable) }

    private fun send(
        client: HttpClient,
        uri: URI,
        timeoutSeconds: Long = 5L,
    ): HttpResponse<String>? =
        executor.sendWithTimeout(
            client,
            requestTo(uri),
            HttpResponse.BodyHandlers.ofString(),
            timeoutSeconds,
        )

    @Test
    fun `an opaque uri skips dns pre resolution`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(failedWith(CompletionException(IOException("no route")))), URI("mailto:nobody@example"))
            }

        assertEquals("no route", failure.message)
    }

    @Test
    fun `a bracketed ipv6 host is unwrapped before resolving`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(failedWith(CompletionException(IOException("refused")))), URI("http://[::1]:8080/v1/chat"))
            }

        assertEquals("refused", failure.message)
    }

    @Test
    fun `an unknown host is surfaced as an io failure before the request is sent`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(CompletableFuture()), URI("http://stacklens-does-not-exist.invalid/v1/chat"))
            }

        assertTrue(failure.message!!.contains("DNS pre-resolution failed"), failure.message)
    }

    @Test
    fun `an io cause is rethrown unchanged`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(failedWith(CompletionException(IOException("socket closed")))), URI("http://localhost:1/x"))
            }

        assertEquals("socket closed", failure.message)
    }

    @Test
    fun `a non io cause is wrapped into an io failure`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(failedWith(CompletionException(IllegalStateException("boom")))), URI("http://localhost:1/x"))
            }

        assertTrue(failure.message!!.contains("AI HTTP send failed"), failure.message)
        assertTrue(failure.cause is IllegalStateException)
    }

    @Test
    fun `a completion exception without a cause still reports its own type`() {
        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(failedWith(CompletionException(null))), URI("http://localhost:1/x"))
            }

        assertTrue(failure.message!!.contains("CompletionException"), failure.message)
    }

    @Test
    fun `a hard timeout is surfaced as an io failure`() {
        @Suppress("UNCHECKED_CAST")
        val timingOut = mock<CompletableFuture<HttpResponse<String>>>()
        whenever(timingOut.orTimeout(any(), any())).thenReturn(timingOut)
        whenever(timingOut.isDone).thenReturn(true)
        org.mockito.Mockito
            .doAnswer { throw java.util.concurrent.TimeoutException() }
            .`when`(timingOut)
            .join()

        val failure =
            assertThrows(IOException::class.java) {
                send(clientReturning(timingOut), URI("http://localhost:1/x"), timeoutSeconds = 2L)
            }

        assertTrue(failure.message!!.contains("timed out after 2s"), failure.message)
        verify(timingOut).orTimeout(2L, TimeUnit.SECONDS)
    }
}
