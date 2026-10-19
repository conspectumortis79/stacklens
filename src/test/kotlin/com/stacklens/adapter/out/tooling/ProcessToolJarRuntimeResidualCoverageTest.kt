package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.compiler.JavaRuntimePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream

class ProcessToolJarRuntimeResidualCoverageTest {
    private val javaRuntime = mock<JavaRuntimePort>()
    private val process = mock<Process>()

    init {
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/java")
    }

    @Test
    fun `a failing close after a successful read surfaces the close error`() {
        whenever(process.inputStream).thenReturn(readableButUncloseableStream())
        whenever(process.isAlive).thenReturn(true)

        val error =
            assertThrows(Exception::class.java) {
                ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { }
            }

        assertTrue(error is IOException || error.cause is IOException)
        assertTrue(error.suppressed.isEmpty())
        verify(process).destroyForcibly()
    }

    @Test
    fun `lines are streamed to the callback while the process output is read`() {
        val seen = mutableListOf<String>()
        whenever(process.inputStream).thenReturn(twoLineStream())

        val output =
            ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { seen += it }

        assertEquals(listOf("first", "second"), seen)
        assertEquals("first\nsecond\n", output)
    }

    private fun readableButUncloseableStream(): InputStream =
        object : InputStream() {
            private val data = "ok\n".toByteArray()
            private var pos = 0

            override fun read(): Int =
                if (pos < data.size) {
                    data[pos++].toInt() and 0xFF
                } else {
                    -1
                }

            override fun close() = throw IOException("close failed after clean read")
        }

    private fun twoLineStream(): InputStream =
        object : InputStream() {
            private val data = "first\nsecond\n".toByteArray()
            private var pos = 0

            override fun read(): Int =
                if (pos < data.size) {
                    data[pos++].toInt() and 0xFF
                } else {
                    -1
                }
        }
}
