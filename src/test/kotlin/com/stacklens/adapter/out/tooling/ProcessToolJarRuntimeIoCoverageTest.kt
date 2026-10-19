package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.compiler.JavaRuntimePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream

class ProcessToolJarRuntimeIoCoverageTest {
    private val javaRuntime = mock<JavaRuntimePort>()
    private val process = mock<Process>()

    init {
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/java")
    }

    @Test
    fun `every output line of the jar is forwarded and collected`() {
        whenever(process.inputStream).thenReturn("first\nsecond".byteInputStream())
        whenever(process.waitFor(any(), any())).thenReturn(true)
        val lines = mutableListOf<String>()

        val output = ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { lines += it }

        assertEquals(listOf("first", "second"), lines)
        assertTrue(output.startsWith("first"))
    }

    @Test
    fun `a broken output stream aborts the run and kills the process`() {
        whenever(process.inputStream).thenReturn(brokenStream())
        whenever(process.isAlive).thenReturn(true)

        val error =
            assertThrows(Exception::class.java) {
                ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { }
            }

        assertTrue(error is IOException || error.cause is IOException)
        verify(process).destroyForcibly()
    }

    @Test
    fun `a jar that does not finish in time is killed forcibly`() {
        whenever(process.inputStream).thenReturn("done".byteInputStream())
        whenever(process.waitFor(any(), any())).thenReturn(false)

        val output = ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { }

        assertEquals("done\n", output)
        verify(process).destroyForcibly()
    }

    private fun brokenStream(): InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("output stream broken")
        }
}
