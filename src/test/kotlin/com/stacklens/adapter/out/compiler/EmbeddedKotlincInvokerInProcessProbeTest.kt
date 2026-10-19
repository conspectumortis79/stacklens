package com.stacklens.adapter.out.compiler

import com.stacklens.application.service.FakeI18n
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class EmbeddedKotlincInvokerInProcessProbeTest {
    private val i18n = FakeI18n()
    private val kotlincResolver: KotlincResolver = mock()
    private val fatJarBuilder: FatJarBuilder = mock()
    private val runtimeJarLocator: KotlinRuntimeJarLocator = mock()

    @Test
    fun `in process fallback recognises the stdlib by its exact jar name`(
        @TempDir tmp: Path,
    ) {
        val stdlib = File(tmp.toFile(), "kotlin-stdlib.jar").apply { realStdlib().copyTo(this, overwrite = true) }
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/guice.jar", stdlib.absolutePath))
        val jarFile = File(tmp.toFile(), "tool.jar")

        invoker(anchor = K2JVMCompiler::class.java).compileWithEmbeddedCompiler(jarFile, emptySource(tmp.toFile()))

        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), eq(File(stdlib.absolutePath)))
    }

    @Test
    fun `in process fallback recognises the stdlib by its containing directory`(
        @TempDir tmp: Path,
    ) {
        val nested =
            File(tmp.toFile(), "libs/kotlin-stdlib/2.3.20/kotlin-stdlib-2.3.20.jar").apply {
                parentFile.mkdirs()
                realStdlib().copyTo(this, overwrite = true)
            }
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf(nested.absolutePath))
        val jarFile = File(tmp.toFile(), "tool.jar")

        invoker(anchor = K2JVMCompiler::class.java).compileWithEmbeddedCompiler(jarFile, emptySource(tmp.toFile()))

        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), eq(File(nested.absolutePath)))
    }

    @Test
    fun `in process fallback builds the fat jar without a stdlib when none is available`(
        @TempDir tmp: Path,
    ) {
        whenever(runtimeJarLocator.locateAll()).thenReturn(listOf("/libs/guice.jar"))
        val jarFile = File(tmp.toFile(), "tool.jar")

        invoker().compileWithEmbeddedCompiler(jarFile, emptySource(tmp.toFile()))

        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), isNull())
    }

    @Test
    fun `in process fallback uses the system class loader when the compiler anchor has none`(
        @TempDir tmp: Path,
    ) {
        whenever(runtimeJarLocator.locateAll()).thenReturn(emptyList())
        val jarFile = File(tmp.toFile(), "tool.jar")

        invoker(anchor = Int::class.java).compileWithEmbeddedCompiler(jarFile, emptySource(tmp.toFile()))

        verify(fatJarBuilder).buildFatJar(eq(jarFile), any(), isNull())
    }

    @Test
    fun `in process fallback reports the embedded compiler error when the source is broken`(
        @TempDir tmp: Path,
    ) {
        whenever(runtimeJarLocator.locateAll()).thenReturn(emptyList())
        val broken = File(tmp.toFile(), "Broken.kt").apply { writeText("invalid !!! kotlin syntax") }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoker().compileWithEmbeddedCompiler(File(tmp.toFile(), "tool.jar"), broken)
            }

        assertEquals(listOf("tooling.be_compile_error_embedded"), i18n.requests.map { it.first })
        assertEquals(
            "COMPILATION_ERROR",
            i18n.requests
                .single()
                .second
                .first()
                .toString(),
        )
    }

    private fun emptySource(dir: File): File = File(dir, "Empty.kt").apply { writeText("") }

    private fun realStdlib(): File =
        KotlinRuntimeJarLocator
            .default()
            .locateAll()
            .map(::File)
            .first { it.name.startsWith("kotlin-stdlib") && it.isFile }

    private fun invoker(anchor: Class<*> = K2JVMCompiler::class.java): EmbeddedKotlincInvoker =
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = { "java" },
            compileTimeoutSeconds = 120L,
            jarLocator = KotlinCompilerJarLocator(listOf(KotlinCompilerJarStrategy { null })),
            runtimeJarLocator = runtimeJarLocator,
            compilerAnchorClass = anchor,
        )
}
