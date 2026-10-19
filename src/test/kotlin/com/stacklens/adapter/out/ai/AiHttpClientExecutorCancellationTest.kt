package com.stacklens.adapter.out.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AiHttpClientExecutorCancellationTest {
    private lateinit var server: HttpServer
    private var port: Int = 0
    private lateinit var client: HttpClient
    private val served = AtomicInteger(0)
    private val interrupts = Executors.newSingleThreadScheduledExecutor()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/hang") { exchange ->
            served.incrementAndGet()
        }
        server.start()
        port = server.address.port
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @AfterEach
    fun tearDown() {
        interrupts.shutdownNow()
        server.stop(0)
        client.close()
    }

    private fun hangingRequest(): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI.create("http://${InetAddress.getLoopbackAddress().hostAddress}:$port/hang"))
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()

    @Test
    fun `already requested cancellation aborts the send long before the timeout`() {
        val executor = AiHttpClientExecutor(pollIntervalMillis = 5L)
        val startMs = System.currentTimeMillis()

        val failure =
            assertThrows(InterruptedException::class.java) {
                executor.sendWithTimeout(client, hangingRequest(), HttpResponse.BodyHandlers.ofString(), 60L) { true }
            }

        assertTrue(System.currentTimeMillis() - startMs < 5_000L, "cancellation must not wait for the 60 s timeout")
        assertTrue(failure.message!!.contains("cancelled"), "message was ${failure.message}")
        assertTrue(served.get() <= 1, "a cancelled request must not be replayed, saw ${served.get()}")
    }

    @Test
    fun `cancellation that arrives mid-flight aborts the pending request`() {
        val executor = AiHttpClientExecutor(pollIntervalMillis = 5L)
        val polls = AtomicInteger(0)

        assertThrows(InterruptedException::class.java) {
            executor.sendWithTimeout(
                client,
                hangingRequest(),
                HttpResponse.BodyHandlers.ofString(),
                60L,
            ) { polls.incrementAndGet() >= 3 }
        }

        assertTrue(polls.get() >= 3, "cancellation must be polled, not assumed, was ${polls.get()}")
    }

    @Test
    fun `an interrupt aborts a pending send even without a cancellation predicate`() {
        val executor = AiHttpClientExecutor(pollIntervalMillis = 5L)
        val failures = ConcurrentLinkedQueue<Throwable?>()
        var interruptFlagAfter = false

        val worker =
            Thread {
                failures.add(
                    runCatching {
                        executor.sendWithTimeout(client, hangingRequest(), HttpResponse.BodyHandlers.ofString(), 60L)
                    }.exceptionOrNull(),
                )
                interruptFlagAfter = Thread.currentThread().isInterrupted
            }.also { it.isDaemon = true }

        worker.start()
        interrupts.schedule({ if (worker.isAlive) worker.interrupt() }, 200L, TimeUnit.MILLISECONDS)
        worker.join(20_000L)

        assertFalse(worker.isAlive, "an interrupt must break the wait, `join` alone does not")
        assertTrue(failures.first() is InterruptedException, "expected InterruptedException, got ${failures.first()}")
        assertTrue(interruptFlagAfter, "the interrupt flag must stay set for the caller to observe")
    }

    @Test
    fun `without a cancellation predicate the plain send still returns the response`() {
        server.createContext("/quick") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://${InetAddress.getLoopbackAddress().hostAddress}:$port/quick"))
                .GET()
                .build()

        val response = AiHttpClientExecutor().sendWithTimeout(client, request, HttpResponse.BodyHandlers.ofString(), 10L)

        assertEquals(200, response.statusCode())
        assertEquals("ok", response.body())
    }

    @Test
    fun `a non-positive poll interval is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AiHttpClientExecutor(pollIntervalMillis = 0L) }
    }
}
