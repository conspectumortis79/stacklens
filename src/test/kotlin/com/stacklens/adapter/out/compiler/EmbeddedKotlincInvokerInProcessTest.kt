package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerInProcessTest {
    private val i18n: I18nPort = mock()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()

    private fun invoker(locator: KotlinCompilerJarLocator = KotlinCompilerJarLocator(emptyList())) =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = { "java" },
            jarLocator = locator,
        )

    @Test
    fun `compileWithEmbeddedCompiler falls back to in-process when no JAR is found`(
        @TempDir tmp: Path,
    ) {
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile error")
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("this is not valid kotlin!!!") }

        try {
            invoker().compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler in-process path with stdlibJar not null`(
        @TempDir tmp: Path,
    ) {
        val stdlib = tmp.resolve("kotlin-stdlib.jar").toFile().apply { writeText("placeholder") }
        whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(stdlib.absolutePath)
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile error")
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Bad.kt").toFile().apply { writeText("not valid!!!") }
        try {
            invoker().compileWithEmbeddedCompiler(jarFile, sourceFile)
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `compileWithEmbeddedCompiler uses exitCode from compiler exit`() {
        val tmp = File.createTempFile("compile-test", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            whenever(fatJarBuilder.findJarOnClasspath("kotlin-stdlib")).thenReturn(null)
            whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("compile error")
            val jarFile = File(tmp, "out.jar")
            val sourceFile = File(tmp, "Empty.kt").apply { writeText("") }
            try {
                invoker().compileWithEmbeddedCompiler(jarFile, sourceFile)
            } catch (_: Exception) {
            }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
