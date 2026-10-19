package com.stacklens.adapter.out.compiler

import com.stacklens.application.service.FakeI18n
import com.stacklens.testsupport.RecordingProcessStub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerSubprocessTest {
    private val i18n = FakeI18n()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()
    private val jarLocator: KotlinCompilerJarLocator = mock()
    private val runtimeJarLocator: KotlinRuntimeJarLocator = mock()

    @Test
    fun `compileWithEmbeddedCompiler builds a fat jar from the subprocess output and the stdlib`(
        @TempDir tmp: Path,
    ) {
        val stub = RecordingProcessStub(tmp.toFile(), "java", "exit 0")
        whenever(jarLocator.locate()).thenReturn("/libs/kotlin-compiler-embeddable.jar")
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/kotlin-stdlib.jar", "/libs/kotlin-reflect.jar"))

        val jarFile = File(tmp.toFile(), "tool.jar")
        invoker(stub).compileWithEmbeddedCompiler(jarFile, source(tmp.toFile()))

        val arguments = stub.recordedArguments()
        assertTrue(arguments.contains("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"), "arguments were: $arguments")
        assertEquals(
            listOf("-classpath", "/libs/kotlin-stdlib.jar"),
            arguments.windowed(2).first { it.first() == "-classpath" },
        )
        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), eq(File("/libs/kotlin-stdlib.jar")))
    }

    @Test
    fun `compileWithEmbeddedCompiler passes a null stdlib when the runtime jars hold no stdlib`(
        @TempDir tmp: Path,
    ) {
        val stub = RecordingProcessStub(tmp.toFile(), "java", "exit 0")
        whenever(jarLocator.locate()).thenReturn("/libs/kotlin-compiler-embeddable.jar")
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/guice.jar"))

        val jarFile = File(tmp.toFile(), "tool.jar")
        invoker(stub).compileWithEmbeddedCompiler(jarFile, source(tmp.toFile()))

        val arguments = stub.recordedArguments()
        assertTrue(arguments.none { it == "-classpath" }, "no explicit classpath expected: $arguments")
        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), eq(null))
    }

    @Test
    fun `compileWithEmbeddedCompiler throws the timeout message when the subprocess hangs`(
        @TempDir tmp: Path,
    ) {
        val stub = RecordingProcessStub(tmp.toFile(), "java", "sleep 30")
        whenever(jarLocator.locate()).thenReturn("/libs/kotlin-compiler-embeddable.jar")
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/kotlin-stdlib.jar"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoker(stub, timeoutSeconds = 1L).compileWithEmbeddedCompiler(
                    File(tmp.toFile(), "tool.jar"),
                    source(tmp.toFile()),
                )
            }

        assertEquals(listOf("tooling.be_compile_timeout" to listOf<Any>(1L)), i18n.requests)
        assertEquals("tooling.be_compile_timeout(1)", failure.message)
    }

    @Test
    fun `compileWithEmbeddedCompiler throws the error message when the subprocess fails`(
        @TempDir tmp: Path,
    ) {
        val stub = RecordingProcessStub(tmp.toFile(), "java", "echo 'error: cannot compile'", "exit 1")
        whenever(jarLocator.locate()).thenReturn("/libs/kotlin-compiler-embeddable.jar")
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/kotlin-stdlib.jar"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoker(stub, timeoutSeconds = 20L).compileWithEmbeddedCompiler(
                    File(tmp.toFile(), "tool.jar"),
                    source(tmp.toFile()),
                )
            }

        assertEquals(listOf("tooling.be_compile_error"), i18n.requests.map { it.first })
        assertEquals(
            1,
            i18n.requests
                .single()
                .second
                .first(),
        )
        assertTrue(
            i18n.requests
                .single()
                .second[1]
                .toString()
                .contains("error: cannot compile"),
        )
    }

    private fun source(dir: File): File = File(dir, "Main.kt").apply { writeText("fun main() {}") }

    private fun invoker(
        stub: RecordingProcessStub,
        timeoutSeconds: Long = 20L,
    ): EmbeddedKotlincInvoker =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = { stub.executable.absolutePath },
            compileTimeoutSeconds = timeoutSeconds,
            jarLocator = jarLocator,
            runtimeJarLocator = runtimeJarLocator,
        )
}
