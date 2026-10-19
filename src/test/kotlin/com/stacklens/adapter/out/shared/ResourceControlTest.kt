package com.stacklens.adapter.out.shared

import com.stacklens.domain.util.closingAuto
import com.stacklens.domain.util.quietly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.io.IOException

class ResourceControlTest {
    private class Closer(
        private val closeError: Throwable?,
    ) : Closeable {
        var closes = 0

        override fun close() {
            closes++
            closeError?.let { throw it }
        }
    }

    private class AutoCloser(
        private val closeError: Throwable?,
    ) : AutoCloseable {
        var closes = 0

        override fun close() {
            closes++
            closeError?.let { throw it }
        }
    }

    @Test
    fun `quietly runs the action`() {
        var ran = false

        quietly { ran = true }

        assertTrue(ran)
    }

    @Test
    fun `quietly swallows a failing action`() {
        quietly { throw IOException("action failed") }
    }

    @Test
    fun `closingAuto hands back the block result and closes a Closeable resource`() {
        val closer = Closer(null)

        val result = closer.closingAuto { "wert" }

        assertEquals("wert", result)
        assertEquals(1, closer.closes)
    }

    @Test
    fun `closingAuto hands back the block result and closes the resource`() {
        val closer = AutoCloser(null)

        val result = closer.closingAuto { 7 }

        assertEquals(7, result)
        assertEquals(1, closer.closes)
    }

    @Test
    fun `closingAuto closes the resource when the block fails`() {
        val closer = AutoCloser(null)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                closer.closingAuto { throw IllegalStateException("block failed") }
            }

        assertEquals("block failed", failure.message)
        assertEquals(1, closer.closes)
    }

    @Test
    fun `closingAuto reports a close failure of a successful block`() {
        val closer = AutoCloser(IOException("close failed"))

        val failure =
            assertThrows(IOException::class.java) {
                closer.closingAuto { 1 }
            }

        assertEquals("close failed", failure.message)
    }

    @Test
    fun `closingAuto suppresses the close failure onto the block failure`() {
        val closer = AutoCloser(IOException("close failed"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                closer.closingAuto { throw IllegalStateException("block failed") }
            }

        assertEquals("block failed", failure.message)
        assertEquals(listOf("close failed"), failure.suppressed.map { it.message })
    }
}
