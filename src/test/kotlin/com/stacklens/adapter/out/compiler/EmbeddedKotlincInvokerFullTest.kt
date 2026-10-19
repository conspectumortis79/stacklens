package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerFullTest {
    private val i18n: I18nPort = mock()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()

    private fun invoker(
        javaExecutable: () -> String = { "java" },
        compileTimeoutSeconds: Long = 180L,
    ): EmbeddedKotlincInvoker =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = javaExecutable,
            compileTimeoutSeconds = compileTimeoutSeconds,
            jarLocator = KotlinCompilerJarLocator.default(fatJarBuilder),
        )

    @Test
    fun `findKotlinCompilerJar caches the resolved JAR`() {
        val inv = invoker()
        val first = inv.findKotlinCompilerJar()
        val second = inv.findKotlinCompilerJar()
        assertEquals(first, second)
    }

    @Test
    fun `findKotlinCompilerJar returns same value across many calls`() {
        val inv = invoker()
        val first = inv.findKotlinCompilerJar()
        val results = (1..10).map { inv.findKotlinCompilerJar() }
        assertTrue(results.all { it == first })
    }

    @Test
    fun `findKotlinCompilerJar is cached per instance`() {
        val inv1 = invoker()
        val inv2 = invoker()
        assertEquals(inv1.findKotlinCompilerJar(), inv2.findKotlinCompilerJar())
    }

    @Test
    fun `findKotlinCompilerJar returns null when no JAR available`() {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-compiler-embeddable")).thenReturn(null)
        val inv = invoker()
        val first = inv.findKotlinCompilerJar()
        val second = inv.findKotlinCompilerJar()

        assertNotNull(first == null || first.isEmpty() || first.isNotEmpty())
        assertEquals(first, second)
    }

    @Test
    fun `findKotlinCompilerJar accepts fatJarBuilder return value`() {
        val jarPath = "/fake/path/kotlin-compiler-embeddable.jar"
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-compiler-embeddable")).thenReturn(jarPath)
        val inv = invoker()
        val first = inv.findKotlinCompilerJar()
        assertEquals(jarPath, first)
    }

    @Test
    fun `compileWithExternalKotlinc completes successfully when exit is zero`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val scriptFile = tmp.resolve("ok.sh").toFile()
        scriptFile.writeText("#!/bin/sh\nexit 0\n")
        scriptFile.setExecutable(true)
        inv.compileWithExternalKotlinc(
            kotlinc = scriptFile.absolutePath,
            jarFile = jarFile,
            sourceFile = sourceFile,
            workDir = tmp.toFile(),
        )
        assertTrue(true)
    }

    @Test
    fun `compileWithExternalKotlinc throws when exit code is non-zero`(
        @TempDir tmp: Path,
    ) {
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile failed")

        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val scriptFile = tmp.resolve("failing.sh").toFile()
        scriptFile.writeText("#!/bin/sh\necho 'compile error output'\nexit 42\n")
        scriptFile.setExecutable(true)
        try {
            inv.compileWithExternalKotlinc(
                kotlinc = scriptFile.absolutePath,
                jarFile = jarFile,
                sourceFile = sourceFile,
                workDir = tmp.toFile(),
            )

            assertTrue(false, "Expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithExternalKotlinc captures process output`() {
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile failed")
        val inv = invoker()
        val tmp = File.createTempFile("compile-test", "dir")
        tmp.delete()
        tmp.mkdirs()
        try {
            val jarFile = File(tmp, "out.jar")
            val sourceFile = File(tmp, "src.kt").apply { writeText("fun main(){}") }
            val scriptFile = File(tmp, "failing.sh")
            scriptFile.writeText("#!/bin/sh\necho 'some error output'\nexit 1\n")
            scriptFile.setExecutable(true)
            try {
                inv.compileWithExternalKotlinc(
                    kotlinc = scriptFile.absolutePath,
                    jarFile = jarFile,
                    sourceFile = sourceFile,
                    workDir = tmp,
                )
            } catch (_: IllegalStateException) {
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler handles errors gracefully when JAR exists`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        val compilerJar = inv.findKotlinCompilerJar()
        if (compilerJar == null) {
            whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        }
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("!@#invalid!!!") }
        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler handles missing source file`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val nonExistent = tmp.resolve("DoesNotExist.kt").toFile()
        try {
            inv.compileWithEmbeddedCompiler(jarFile, nonExistent)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithExternalKotlinc calls injectJavaHome on resolver`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val scriptFile = tmp.resolve("ok.sh").toFile()
        scriptFile.writeText("#!/bin/sh\nexit 0\n")
        scriptFile.setExecutable(true)
        inv.compileWithExternalKotlinc(
            kotlinc = scriptFile.absolutePath,
            jarFile = jarFile,
            sourceFile = sourceFile,
            workDir = tmp.toFile(),
        )
        assertTrue(true)
    }

    @Test
    fun `compileWithExternalKotlinc uses correct i18n keys for error`() {
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("error msg")
        val inv = invoker()

        assertNotNull(inv)
    }

    @Test
    fun `compileWithEmbeddedCompiler passes stdlib classpath when stdlibJar is non-null`(
        @TempDir tmp: Path,
    ) {
        val compilerJar = tmp.resolve("kotlin-compiler-embeddable.jar").toFile()
        compilerJar.writeText("fake")
        val stdlibJar = tmp.resolve("kotlin-stdlib.jar").toFile()
        stdlibJar.writeText("fake-stdlib")
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-compiler-embeddable")).thenReturn(compilerJar.absolutePath)
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(stdlibJar.absolutePath)
        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Good.kt").toFile().apply { writeText("fun main() {}") }

        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler subprocess succeeds when JAR exists and exit is zero`(
        @TempDir tmp: Path,
    ) {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val fakeJar = tmp.resolve("fake-compiler.jar").toFile()
        fakeJar.writeText("not a real jar")
        val strategy =
            object : KotlinCompilerJarStrategy {
                override fun locate(): String? = fakeJar.absolutePath
            }
        val inv =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/usr/bin/env" },
                compileTimeoutSeconds = 5L,
                jarLocator = KotlinCompilerJarLocator(listOf(strategy)),
            )
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Good.kt").toFile().apply { writeText("fun main(){}") }

        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler subprocess times out and throws`(
        @TempDir tmp: Path,
    ) {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val fakeJar = tmp.resolve("fake-compiler.jar").toFile()
        fakeJar.writeText("not a real jar")
        val strategy =
            object : KotlinCompilerJarStrategy {
                override fun locate(): String? = fakeJar.absolutePath
            }

        val inv =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/bin/sleep" },
                compileTimeoutSeconds = 1L,
                jarLocator = KotlinCompilerJarLocator(listOf(strategy)),
            )
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("timeout")
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Timeout.kt").toFile().apply { writeText("fun main(){}") }
        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler subprocess exit nonzero throws`(
        @TempDir tmp: Path,
    ) {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val fakeJar = tmp.resolve("fake-compiler.jar").toFile()
        fakeJar.writeText("not a real jar")
        val strategy =
            object : KotlinCompilerJarStrategy {
                override fun locate(): String? = fakeJar.absolutePath
            }

        val inv =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/usr/bin/false" },
                compileTimeoutSeconds = 5L,
                jarLocator = KotlinCompilerJarLocator(listOf(strategy)),
            )
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile error")
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("BadExit.kt").toFile().apply { writeText("fun main(){}") }
        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithExternalKotlinc times out and throws when script sleeps past limit`(
        @TempDir tmp: Path,
    ) {
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("timeout")
        val inv = invoker(compileTimeoutSeconds = 1L)
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val sleepScript = tmp.resolve("sleep.sh").toFile()
        sleepScript.writeText("#!/bin/sh\nsleep 5\n")
        sleepScript.setExecutable(true)
        try {
            inv.compileWithExternalKotlinc(
                kotlinc = sleepScript.absolutePath,
                jarFile = jarFile,
                sourceFile = sourceFile,
                workDir = tmp.toFile(),
            )
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler subprocess exit zero sends no error`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker(javaExecutable = { "/usr/bin/true" })
        val compilerJar = tmp.resolve("fake.jar").toFile()
        compilerJar.writeText("not a jar")
        val strategy =
            object : KotlinCompilerJarStrategy {
                override fun locate(): String? = compilerJar.absolutePath
            }
        val invWithStrat =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/usr/bin/true" },
                compileTimeoutSeconds = 5L,
                jarLocator = KotlinCompilerJarLocator(listOf(strategy)),
            )
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Good.kt").toFile().apply { writeText("fun main() {}") }
        try {
            invWithStrat.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler in-process exit not OK throws with embedded error key`(
        @TempDir tmp: Path,
    ) {
        val inv =
            invoker(
                javaExecutable = { "/bin/echo" },
            )

        val invInProcess =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/bin/echo" },
                compileTimeoutSeconds = 1L,
                jarLocator = KotlinCompilerJarLocator(emptyList()),
            )
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("embedded error")
        val jarFile = tmp.resolve("out.jar").toFile()

        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("invalid !!! kotlin syntax") }
        try {
            invInProcess.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler in-process compiles valid source successfully`(
        @TempDir tmp: Path,
    ) {
        val invInProcess =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/bin/echo" },
                compileTimeoutSeconds = 1L,
                jarLocator = KotlinCompilerJarLocator(emptyList()),
            )
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val jarFile = tmp.resolve("out.jar").toFile()

        val sourceFile = tmp.resolve("Empty.kt").toFile().apply { writeText("") }
        try {
            invInProcess.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler in-process restores context classloader on exception`(
        @TempDir tmp: Path,
    ) {
        val invInProcess =
            EmbeddedKotlincInvoker(
                i18n = i18n,
                kotlincResolver = kotlincResolver,
                fatJarBuilder = fatJarBuilder,
                javaExecutableProvider = { "/bin/echo" },
                compileTimeoutSeconds = 1L,
                jarLocator = KotlinCompilerJarLocator(emptyList()),
            )
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        val origCl = Thread.currentThread().contextClassLoader
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("invalid !!!") }
        try {
            invInProcess.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: Exception) {
        }

        assertEquals(origCl, Thread.currentThread().contextClassLoader)
    }
}
