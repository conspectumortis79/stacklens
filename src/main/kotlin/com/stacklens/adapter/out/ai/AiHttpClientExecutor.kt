package com.stacklens.adapter.out.ai

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AiHttpClientExecutor(
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) {
    private val log: Logger = LoggerFactory.getLogger(AiHttpClientExecutor::class.java)

    init {
        require(pollIntervalMillis > 0) {
            "pollIntervalMillis must be > 0, was $pollIntervalMillis"
        }
    }

    fun <T> sendWithTimeout(
        client: HttpClient,
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>,
        totalTimeoutSeconds: Long,
    ): HttpResponse<T> = sendWithTimeout(client, request, bodyHandler, totalTimeoutSeconds, null)

    fun <T> sendWithTimeout(
        client: HttpClient,
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>,
        totalTimeoutSeconds: Long,
        isCancelled: (() -> Boolean)?,
    ): HttpResponse<T> {
        require(totalTimeoutSeconds > 0) {
            "totalTimeoutSeconds must be > 0, was $totalTimeoutSeconds"
        }
        BoundedCalls.runWithin(totalTimeoutSeconds, DNS_PRIME_EXECUTOR, "DNS pre-resolution for ${request.uri().host}") { primeDnsCache(request.uri()) }
        val future: CompletableFuture<HttpResponse<T>> =
            client.sendAsync(request, bodyHandler)
        val deadline = future.orTimeout(totalTimeoutSeconds, TimeUnit.SECONDS)
        awaitCompletion(deadline, request, isCancelled)
        return surfaceCompletion(deadline, totalTimeoutSeconds)
    }

    private fun awaitCompletion(
        future: CompletableFuture<*>,
        request: HttpRequest,
        isCancelled: (() -> Boolean)?,
    ) {
        while (!future.isDone) {
            if (isCancelled?.invoke() == true) {
                future.cancel(true)
                throw InterruptedException("AI HTTP send cancelled: ${request.method()} ${request.uri()}")
            }
            try {
                future.get(pollIntervalMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                continue
            } catch (_: ExecutionException) {
                return
            } catch (e: InterruptedException) {
                future.cancel(true)
                Thread.currentThread().interrupt()
                val interrupted = InterruptedException("AI HTTP send interrupted: ${request.method()} ${request.uri()}")
                interrupted.initCause(e)
                throw interrupted
            }
        }
    }

    private fun <T> surfaceCompletion(
        future: CompletableFuture<HttpResponse<T>>,
        totalTimeoutSeconds: Long,
    ): HttpResponse<T> =
        try {
            future.join()
        } catch (e: CompletionException) {
            val cause = e.cause ?: e
            when (cause) {
                is IOException -> throw cause
                else -> throw IOException("AI HTTP send failed: ${cause.javaClass.simpleName}: ${cause.message}", cause)
            }
        } catch (e: java.util.concurrent.TimeoutException) {
            log.warn(
                "AI HTTP send exceeded hard timeout of {}s — surfacing as IOException",
                totalTimeoutSeconds,
            )
            throw IOException("AI HTTP send timed out after ${totalTimeoutSeconds}s", e)
        }

    private fun primeDnsCache(uri: URI) {
        val rawHost = uri.host ?: return
        val host =
            if (rawHost.length >= 2 && rawHost.startsWith('[') && rawHost.endsWith(']')) {
                rawHost.substring(1, rawHost.length - 1)
            } else {
                rawHost
            }
        if (host.isBlank()) return
        try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            throw IOException(
                "DNS pre-resolution failed for $host: ${e.message ?: "unknown host"}",
                e,
            )
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS: Long = 250L

        internal val DNS_PRIME_EXECUTOR: ExecutorService =
            Executors.newFixedThreadPool(DNS_PRIME_THREADS) { runnable ->
                Thread(runnable, "ai-dns-prime").apply { isDaemon = true }
            }

        private const val DNS_PRIME_THREADS: Int = 4
    }
}
