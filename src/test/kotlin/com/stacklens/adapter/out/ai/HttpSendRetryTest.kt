package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.process.SleeperPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

class HttpSendRetryTest {
    private fun retryWithTrackingSleeper(
        maxAttempts: Int = 3,
        initialBackoffMs: Long = 0L,
        backoffMultiplier: Double = HttpSendRetry.DEFAULT_BACKOFF_MULTIPLIER,
    ): Pair<HttpSendRetry, MutableList<Long>> {
        val sleeps = mutableListOf<Long>()
        val retry =
            HttpSendRetry(
                maxAttempts = maxAttempts,
                initialBackoffMs = initialBackoffMs,
                backoffMultiplier = backoffMultiplier,
                sleeper = SleeperPort { sleeps.add(it) },
            )
        return retry to sleeps
    }

    @Test
    fun `returns the block's result on the first successful attempt`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        var calls = 0
        val result =
            retry.sendWithRetry("test") {
                calls++
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(1, calls)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `retries on IOException and returns the result of the second attempt`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 100L)
        val calls = AtomicInteger(0)
        val result =
            retry.sendWithRetry("test") {
                if (calls.incrementAndGet() == 1) {
                    throw IOException("simulated chunked-encoding EOF")
                }
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, calls.get())
        assertEquals(listOf(100L), sleeps)
    }

    @Test
    fun `retries on EOFException — the exact cause from the chunked-encoding error`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        val calls = AtomicInteger(0)
        val result =
            retry.sendWithRetry("test") {
                if (calls.incrementAndGet() == 1) {
                    throw IOException("chunked transfer encoding, state: READING_LENGTH", EOFException("EOF reached while reading"))
                }
                "recovered"
            }
        assertEquals("recovered", result)
    }

    @Test
    fun `retries on SocketException (connection reset)`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        val calls = AtomicInteger(0)
        val result =
            retry.sendWithRetry("test") {
                if (calls.incrementAndGet() == 1) {
                    throw SocketException("Connection reset")
                }
                "ok"
            }
        assertEquals("ok", result)
    }

    @Test
    fun `does not retry on IllegalStateException (HTTP 4xx-5xx)`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 100L)
        var calls = 0
        val ex =
            assertThrows(IllegalStateException::class.java) {
                retry.sendWithRetry("test") {
                    calls++
                    throw IllegalStateException("HTTP 401: unauthorized")
                }
            }
        assertEquals(1, calls)
        assertTrue(ex.message!!.contains("HTTP 401"))
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `does not retry on generic RuntimeException`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        var calls = 0
        assertThrows(IllegalArgumentException::class.java) {
            retry.sendWithRetry("test") {
                calls++
                throw IllegalArgumentException("bad input")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `does not retry on InterruptedException and re-asserts the interrupt flag`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        var calls = 0
        Thread.interrupted()
        val ex =
            assertThrows(InterruptedException::class.java) {
                retry.sendWithRetry("test") {
                    calls++
                    throw InterruptedException("cancelled")
                }
            }
        assertEquals(1, calls)
        assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be re-asserted")

        Thread.interrupted()
        assertNotNull(ex)
    }

    @Test
    fun `interrupted sleeper re-asserts interrupt flag and propagates InterruptedException`() {
        val calls = AtomicInteger(0)
        val retry =
            HttpSendRetry(
                maxAttempts = 3,
                initialBackoffMs = 1_000L,
                sleeper = SleeperPort { false },
            )
        Thread.interrupted()
        val ex =
            assertThrows(InterruptedException::class.java) {
                retry.sendWithRetry("test") {
                    calls.incrementAndGet()
                    throw IOException("fail-fast so retry enters the sleep branch")
                }
            }
        assertEquals(1, calls.get())
        assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be re-asserted after interrupted sleep")
        Thread.interrupted()
        assertNotNull(ex)
    }

    @Test
    fun `after max attempts throws IllegalStateException with the last IOException as cause`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 100L)
        val calls = AtomicInteger(0)
        val lastError = IOException("final failure")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                retry.sendWithRetry("opName") {
                    calls.incrementAndGet()
                    throw lastError
                }
            }
        assertEquals(3, calls.get())
        assertTrue(ex.message!!.contains("opName"))
        assertTrue(ex.message!!.contains("3 attempt(s)"))
        assertSame(lastError, ex.cause)

        assertEquals(listOf(100L, 200L), sleeps)
    }

    @Test
    fun `error message includes the last error string for diagnostics`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 2, initialBackoffMs = 0L)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                retry.sendWithRetry("OpenRouter.chatCompletions") {
                    throw IOException("chunked transfer encoding, state: READING_LENGTH")
                }
            }
        assertTrue(ex.message!!.contains("OpenRouter.chatCompletions"))
        assertTrue(ex.message!!.contains("chunked transfer encoding"))
    }

    @Test
    fun `maxAttempts=1 means no retry, single attempt then fail`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 1, initialBackoffMs = 0L)
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            retry.sendWithRetry("test") {
                calls++
                throw IOException("fail")
            }
        }
        assertEquals(1, calls)

        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `backoff grows exponentially between attempts`() {
        val (retry, sleeps) =
            retryWithTrackingSleeper(
                maxAttempts = 4,
                initialBackoffMs = 10L,
                backoffMultiplier = 2.0,
            )
        assertThrows(IllegalStateException::class.java) {
            retry.sendWithRetry("test") {
                throw IOException("fail")
            }
        }

        assertEquals(listOf(10L, 20L, 40L), sleeps)
    }

    @Test
    fun `initialBackoffMs=0 skips the sleep entirely`() {
        val (retry, sleeps) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        assertThrows(IllegalStateException::class.java) {
            retry.sendWithRetry("test") {
                throw IOException("fail")
            }
        }

        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `backoff is capped at MAX_BACKOFF_MS`() {
        val (retry, sleeps) =
            retryWithTrackingSleeper(
                maxAttempts = 4,
                initialBackoffMs = 1_000L,
                backoffMultiplier = 10.0,
            )
        assertThrows(IllegalStateException::class.java) {
            retry.sendWithRetry("test") { throw IOException("fail") }
        }

        assertEquals(listOf(1_000L, 10_000L, 30_000L), sleeps)
    }

    @Test
    fun `constructor rejects maxAttempts less than 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpSendRetry(maxAttempts = 0)
        }
    }

    @Test
    fun `constructor rejects negative initialBackoffMs`() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpSendRetry(maxAttempts = 1, initialBackoffMs = -1L)
        }
    }

    @Test
    fun `constructor rejects backoffMultiplier less than 1_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpSendRetry(maxAttempts = 1, backoffMultiplier = 0.5)
        }
    }

    @Test
    fun `works for any return type — e_g_ HttpResponse`() {
        val (retry, _) = retryWithTrackingSleeper(maxAttempts = 3, initialBackoffMs = 0L)
        val sentinel = Any()
        val result = retry.sendWithRetry("test") { sentinel }
        assertSame(sentinel, result)
    }

    @Test
    fun `default constants match the documented values`() {
        assertEquals(3, HttpSendRetry.DEFAULT_MAX_ATTEMPTS)
        assertEquals(1_000L, HttpSendRetry.DEFAULT_INITIAL_BACKOFF_MS)
        assertEquals(2.0, HttpSendRetry.DEFAULT_BACKOFF_MULTIPLIER)
        assertEquals(30_000L, HttpSendRetry.MAX_BACKOFF_MS)
    }

    @Test
    fun `default constructor uses 3 attempts and 1s initial backoff`() {
        val sleeps = mutableListOf<Long>()
        val retry = HttpSendRetry(sleeper = SleeperPort { sleeps.add(it) })

        val twoFailures = AtomicInteger(0)
        val r1 =
            retry.sendWithRetry("test") {
                if (twoFailures.incrementAndGet() <= 2) throw IOException("fail")
                "ok"
            }
        assertEquals("ok", r1)

        assertEquals(listOf(1_000L, 2_000L), sleeps)
        sleeps.clear()

        val threeFailures = AtomicInteger(0)
        assertThrows(IllegalStateException::class.java) {
            retry.sendWithRetry("test") {
                threeFailures.incrementAndGet()
                throw IOException("fail")
            }
        }
        assertEquals(3, threeFailures.get())
    }
}

class HttpSendRetryMockitoTest {
    private fun OngoingStubbing<String>.thenThrowChecked(t: Throwable): OngoingStubbing<String> = this.thenAnswer { throw t }

    @Test
    fun `block is invoked exactly 2 times when the first call throws IOException`() {
        val op: () -> String = mock()
        whenever(op.invoke())
            .thenThrowChecked(IOException("first call fails"))
            .thenReturn("second-call-result")

        val retry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L)
        val result = retry.sendWithRetry("test", op)
        assertEquals("second-call-result", result)
        verify(op, times(2)).invoke()
    }

    @Test
    fun `block is invoked exactly 1 time when the call succeeds`() {
        val op: () -> String = mock()
        whenever(op.invoke()).thenReturn("ok")
        val retry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L)
        assertEquals("ok", retry.sendWithRetry("test", op))
        verify(op, times(1)).invoke()
    }

    @Test
    fun `block is invoked exactly maxAttempts times on persistent failure`() {
        val op: () -> String = mock()
        whenever(op.invoke()).thenThrowChecked(IOException("always fails"))
        val retry = HttpSendRetry(maxAttempts = 4, initialBackoffMs = 0L)
        assertThrows(IllegalStateException::class.java) { retry.sendWithRetry("test", op) }
        verify(op, times(4)).invoke()
    }

    @Test
    fun `block is invoked exactly 1 time when the call throws IllegalStateException`() {
        val op: () -> String = mock()
        whenever(op.invoke()).thenThrow(IllegalStateException("HTTP 500"))
        val retry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L)
        assertThrows(IllegalStateException::class.java) { retry.sendWithRetry("test", op) }
        verify(op, times(1)).invoke()
    }

    @Test
    fun `sendWithRetry succeeds on last attempt after IOException failures`() {
        val op = mock<java.util.concurrent.Callable<String>>()
        whenever(op.call())
            .thenThrow(java.io.IOException("transient"))
            .thenReturn("ok")
        val noSleepSleeper = SleeperPort { true }
        val retry =
            HttpSendRetry(maxAttempts = 2, initialBackoffMs = 0, sleeper = noSleepSleeper)
        val result = retry.sendWithRetry("op") { op.call() }
        assertEquals("ok", result)
        verify(op, times(2)).call()
    }

    @Test
    fun `sendWithRetry throws IllegalStateException when all attempts fail`() {
        val op = mock<java.util.concurrent.Callable<String>>()
        whenever(op.call()).thenThrow(java.io.IOException("boom"))
        val noSleepSleeper = SleeperPort { true }
        val retry =
            HttpSendRetry(maxAttempts = 2, initialBackoffMs = 0, sleeper = noSleepSleeper)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                retry.sendWithRetry("op") { op.call() }
            }
        assertTrue(ex.message!!.contains("op"))
    }
}
