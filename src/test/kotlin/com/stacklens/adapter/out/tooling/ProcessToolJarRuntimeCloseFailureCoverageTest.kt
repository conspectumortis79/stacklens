package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.compiler.JavaRuntimePort
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream

class ProcessToolJarRuntimeCloseFailureCoverageTest {
    private val javaRuntime = mock<JavaRuntimePort>()
    private val process = mock<Process>()

    init {
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/java")
    }

    @Test
    fun `an output stream that fails twice keeps the close failure attached`() {
        whenever(process.inputStream).thenReturn(uncloseableBrokenStream())
        whenever(process.isAlive).thenReturn(true)

        val error =
            assertThrows(Exception::class.java) {
                ProcessToolJarRuntime(javaRuntime, processStarter = { process }).runJar("/tmp/tool.jar") { }
            }

        assertTrue(error is IOException || error.cause is IOException)
        assertTrue(error.suppressed.isNotEmpty())
        verify(process).destroyForcibly()
    }

    private fun uncloseableBrokenStream(): InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("output cannot be read")

            override fun close() = throw IOException("output cannot be closed")
        }
}
