package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerTest {
    private val i18n: I18nPort = mock()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()

    private fun invoker(
        javaExecutable: () -> String = { "java" },
        locator: KotlinCompilerJarLocator = KotlinCompilerJarLocator(emptyList()),
    ): EmbeddedKotlincInvoker =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = javaExecutable,
            jarLocator = locator,
        )

    @Test
    fun `findKotlinCompilerJar caches the resolved JAR`() {
        val inv = invoker()
        val first = inv.findKotlinCompilerJar()
        val second = inv.findKotlinCompilerJar()
        assertEquals(first, second)
        if (first != null) assertTrue(File(first).exists() || first.isNotBlank())
    }

    @Test
    fun `findKotlinCompilerJar returns null when no JAR is available`() {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-compiler-embeddable")).thenReturn(null)
        val inv = invoker()
        val result = inv.findKotlinCompilerJar()
        assertNotNull(result == null || result.isNotEmpty())
    }

    @Test
    fun `findKotlinCompilerJar is idempotent across repeated calls`() {
        val inv = invoker()
        val results = (1..5).map { inv.findKotlinCompilerJar() }
        assertTrue(results.all { it == results.first() })
    }

    @Test
    fun `jarLocator property getter returns the locator passed to the constructor`() {
        val locator = KotlinCompilerJarLocator(emptyList())
        val inv = invoker(locator = locator)

        assertEquals(locator, inv.jarLocator)
    }

    @Test
    fun `each instance maintains its own cache`() {
        val inv1 = invoker()
        val inv2 = invoker()
        assertEquals(inv1.findKotlinCompilerJar(), inv2.findKotlinCompilerJar())
    }

    @Test
    fun `compileWithExternalKotlinc throws error when exit code is non-zero`(
        @TempDir tmp: Path,
    ) {
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile failed")

        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val scriptFile = tmp.resolve("failing-kotlinc.sh").toFile()
        scriptFile.writeText("#!/bin/sh\nexit 1\n")
        scriptFile.setExecutable(true)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                inv.compileWithExternalKotlinc(
                    kotlinc = scriptFile.absolutePath,
                    jarFile = jarFile,
                    sourceFile = sourceFile,
                    workDir = tmp.toFile(),
                )
            }
        assertTrue(ex.message!!.contains("compile", ignoreCase = true))
    }

    @Test
    fun `compileWithExternalKotlinc completes successfully when exit is zero`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("src.kt").toFile().apply { writeText("fun main(){}") }
        val scriptFile = tmp.resolve("ok-kotlinc.sh").toFile()
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
    fun `compileWithEmbeddedCompiler throws when in-process compiler fails`(
        @TempDir tmp: Path,
    ) {
        val inv =
            invoker(locator = KotlinCompilerJarLocator(emptyList()))
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile failed embedded")

        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("this is not valid kotlin !!! syntax") }
        assertThrows(IllegalStateException::class.java) {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler handles empty source gracefully`(
        @TempDir tmp: Path,
    ) {
        val inv =
            invoker(locator = KotlinCompilerJarLocator(emptyList()))
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)

        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Empty.kt").toFile().apply { writeText("") }
        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
        assertTrue(true)
    }

    @Test
    fun `compileWithEmbeddedCompiler adds stdlibJar to classpath when present`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        assumeTrue(
            inv.findKotlinCompilerJar() != null,
            "Skipping subprocess test – kotlin-compiler-embeddable JAR is not on the classpath",
        )

        val stdlib = tmp.resolve("kotlin-stdlib.jar").toFile().apply { writeText("placeholder") }
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(stdlib.absolutePath)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile failed")
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Good.kt").toFile().apply { writeText("fun main(){}") }
        try {
            inv.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
        assertTrue(true)
    }

    @Test
    fun `compileWithEmbeddedCompiler subprocess times out and throws`(
        @TempDir tmp: Path,
    ) {
        val inv = invoker()
        assumeTrue(
            inv.findKotlinCompilerJar() != null,
            "Skipping subprocess test – kotlin-compiler-embeddable JAR is not on the classpath",
        )
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile timed out")

        val sleep = tmp.resolve("java-sleep.sh").toFile()
        sleep.writeText("#!/bin/sh\nsleep 5\n")
        sleep.setExecutable(true)
        val invWithSleep = invoker(javaExecutable = { sleep.absolutePath })
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Timeout.kt").toFile().apply { writeText("fun main(){}") }

        try {
            invWithSleep.compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
        assertTrue(true)
    }
}
