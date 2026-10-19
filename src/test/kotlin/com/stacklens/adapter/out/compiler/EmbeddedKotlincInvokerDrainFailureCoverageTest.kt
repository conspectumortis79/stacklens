package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class EmbeddedKotlincInvokerDrainFailureCoverageTest {
    @Test
    fun `a stdout pipe that fails on read and close surfaces through the drain future`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(
            object : InputStream() {
                override fun read(): Int = throw IOException("stdout pipe broken")

                override fun close(): Unit = throw IOException("stdout pipe cannot be closed")
            },
        )

        val drainClass =
            Class.forName(
                "com.stacklens.adapter.out.compiler.EmbeddedKotlincInvoker\$ProcessOutputDrain",
            )
        val constructor = drainClass.getDeclaredConstructor(Process::class.java)
        constructor.isAccessible = true
        val drain = constructor.newInstance(process)
        val futureGetter = drainClass.getMethod("getFuture")
        futureGetter.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val future = futureGetter.invoke(drain) as CompletableFuture<String>

        val failure = assertThrows(ExecutionException::class.java) { future.get(10, TimeUnit.SECONDS) }
        val cause = requireNotNull(failure.cause) { "the drain must report the read failure" }
        assertTrue(
            generateSequence(cause) { it.cause }.any { it is IOException },
            "expected an IOException in the cause chain of $cause",
        )
        assertTrue(
            cause.suppressed.any { it is IOException },
            "the failing close must be attached as a suppressed exception, got ${cause.suppressed.toList()}",
        )
    }
}
