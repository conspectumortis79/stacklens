package com.stacklens.adapter.out.compiler

import com.stacklens.application.service.FakeI18n
import com.stacklens.testsupport.RecordingProcessStub
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerProbeTest {
    private val i18n = FakeI18n()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()
    private val jarLocator: KotlinCompilerJarLocator = mock()
    private val runtimeJarLocator: KotlinRuntimeJarLocator = mock()

    @Test
    fun `findKotlinCompilerJar caches a resolved jar`() {
        whenever(jarLocator.locate()).thenReturn("/libs/kotlin-compiler-embeddable.jar")
        val invoker = invoker()

        assertEquals("/libs/kotlin-compiler-embeddable.jar", invoker.findKotlinCompilerJar())
        assertEquals("/libs/kotlin-compiler-embeddable.jar", invoker.findKotlinCompilerJar())
        verify(jarLocator).locate()
    }

    @Test
    fun `findKotlinCompilerJar caches a missing jar`() {
        whenever(jarLocator.locate()).thenReturn(null)
        val invoker = invoker()

        assertNull(invoker.findKotlinCompilerJar())
        assertNull(invoker.findKotlinCompilerJar())
        verify(jarLocator).locate()
    }

    @Test
    fun `findKotlinRuntimeJars caches the resolved jar list`() {
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/kotlin-stdlib.jar"))
        val invoker = invoker()

        assertEquals(listOf("/libs/kotlin-stdlib.jar"), invoker.findKotlinRuntimeJars())
        assertEquals(listOf("/libs/kotlin-stdlib.jar"), invoker.findKotlinRuntimeJars())
        verify(runtimeJarLocator).locateAll()
    }

    @Test
    fun `findStdlibJar returns the stdlib entry of the runtime jars`() {
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/a.jar", "/libs/kotlin-stdlib.jar"))

        assertEquals("/libs/kotlin-stdlib.jar", invoker().findKotlinRuntimeJars().firstOrNull { it.contains("kotlin-stdlib") })
    }

    @Test
    fun `findStdlibJar returns null when no stdlib is available`() {
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/a.jar"))

        assertNull(invoker().findKotlinRuntimeJars().firstOrNull { it.contains("kotlin-stdlib") })
    }

    @Test
    fun `compileWithExternalKotlinc throws the timeout message when the compiler keeps running`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = RecordingProcessStub(tmp.toFile(), "kotlinc", "echo 'partial progress'", "sleep 30")
        val jarFile = File(tmp.toFile(), "out.jar")
        val sourceFile = File(tmp.toFile(), "Main.kt").apply { writeText("fun main() {}") }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoker(timeoutSeconds = 1L).compileWithExternalKotlinc(
                    kotlinc = kotlinc.executable.absolutePath,
                    jarFile = jarFile,
                    sourceFile = sourceFile,
                    workDir = tmp.toFile(),
                )
            }

        assertEquals("tooling.be_compile_timeout(1)\npartial progress\n", failure.message)
        assertEquals(listOf("tooling.be_compile_timeout" to listOf<Any>(1L)), i18n.requests)
    }

    @Test
    fun `compileWithExternalKotlinc reports exit code and output when the compiler fails`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = RecordingProcessStub(tmp.toFile(), "kotlinc", "echo 'error: unrecoverable'", "exit 2")
        val jarFile = File(tmp.toFile(), "out.jar")
        val sourceFile = File(tmp.toFile(), "Main.kt").apply { writeText("fun main() {}") }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoker(timeoutSeconds = 20L).compileWithExternalKotlinc(
                    kotlinc = kotlinc.executable.absolutePath,
                    jarFile = jarFile,
                    sourceFile = sourceFile,
                    workDir = tmp.toFile(),
                )
            }

        assertTrue(failure.message!!.startsWith("tooling.be_compile_error"), "unexpected: ${failure.message}")
        assertEquals(
            2,
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
                .contains("error: unrecoverable"),
        )
    }

    @Test
    fun `compileWithExternalKotlinc invokes the resolver injected java home and the given sources`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = RecordingProcessStub(tmp.toFile(), "kotlinc", "exit 0")
        val jarFile = File(tmp.toFile(), "out.jar")
        val sourceFile = File(tmp.toFile(), "Main.kt").apply { writeText("fun main() {}") }

        invoker(timeoutSeconds = 20L).compileWithExternalKotlinc(
            kotlinc = kotlinc.executable.absolutePath,
            jarFile = jarFile,
            sourceFile = sourceFile,
            workDir = tmp.toFile(),
        )

        assertEquals(
            listOf("-include-runtime", "-d", jarFile.absolutePath, sourceFile.absolutePath),
            kotlinc.recordedArguments(),
        )
        verify(kotlincResolver).injectJavaHome(any())
        assertEquals(emptyList<String>(), i18n.requests.map { it.first })
    }

    @Test
    fun `invoker resolves the compiler jar through the production locator by default`() {
        val invoker =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "java" },
                compileTimeoutSeconds = 5L,
            )

        assertEquals(
            File(
                K2JVMCompiler::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath,
            invoker.findKotlinCompilerJar(),
        )
        assertTrue(invoker.findKotlinRuntimeJars().isNotEmpty())
    }

    private fun invoker(timeoutSeconds: Long = 20L): EmbeddedKotlincInvoker =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = { "java" },
            compileTimeoutSeconds = timeoutSeconds,
            jarLocator = jarLocator,
            runtimeJarLocator = runtimeJarLocator,
        )
}
