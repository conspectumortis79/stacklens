package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KotlinStdlibJarLocatorTest {
    @Test
    fun `locate returns the first strategy result`() {
        val locator =
            KotlinStdlibJarLocator(
                jarNamePrefix = "kotlin-stdlib",
                strategies =
                    listOf(
                        KotlinCompilerJarStrategy { null },
                        KotlinCompilerJarStrategy { "/libs/kotlin-stdlib-1.0.jar" },
                    ),
            )

        assertEquals("/libs/kotlin-stdlib-1.0.jar", locator.locate())
    }

    @Test
    fun `locate returns null when no strategy resolves the jar`() {
        val locator =
            KotlinStdlibJarLocator(
                jarNamePrefix = "kotlin-reflect",
                strategies = listOf(KotlinCompilerJarStrategy { null }),
            )

        assertNull(locator.locate())
    }

    @Test
    fun `default wires the classpath strategy for the default prefix`() {
        val fatJarBuilder = FatJarBuilder(classPathSupplier = { "/libs/kotlin-stdlib-2.3.20.jar:/libs/other.jar" })

        assertEquals("/libs/kotlin-stdlib-2.3.20.jar", KotlinStdlibJarLocator.default(fatJarBuilder).locate())
    }

    @Test
    fun `default wires the classpath strategy for an explicit prefix`() {
        val fatJarBuilder = FatJarBuilder(classPathSupplier = { "/libs/kotlin-reflect-1.9.0.jar" })

        val locator = KotlinStdlibJarLocator.default(fatJarBuilder, jarNamePrefix = "kotlin-reflect")

        assertEquals("/libs/kotlin-reflect-1.9.0.jar", locator.locate())
    }

    @Test
    fun `default falls through to the protection domain strategy when the classpath has no match`() {
        val expected =
            File(
                K2JVMCompiler::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath
        val fatJarBuilder = FatJarBuilder(classPathSupplier = { "/libs/unrelated.jar" })

        val locator = KotlinStdlibJarLocator.default(fatJarBuilder, jarNamePrefix = "kotlin-compiler-embeddable")

        assertEquals(expected, locator.locate())
    }

    @Test
    fun `classpath strategy returns null when the prefix is absent from the classpath`() {
        val strategy = ClasspathJarStrategyFor(FatJarBuilder(classPathSupplier = { "/libs/other.jar" }), "kotlin-stdlib")

        assertNull(strategy.locate())
    }

    @Test
    fun `classpath strategy returns null when the classpath lookup fails`() {
        val fatJarBuilder =
            FatJarBuilder(
                classPathSupplier = { throw IllegalStateException("classpath unavailable") },
            )

        assertNull(ClasspathJarStrategyFor(fatJarBuilder, "kotlin-stdlib").locate())
    }

    @Test
    fun `protection domain strategy matches the exact jar name`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-stdlib.jar").apply { writeText("jar-bytes") }

        assertEquals(jar.absolutePath, ProtectionDomainJarStrategyFor("kotlin-stdlib", anchorFor(jar)).locate())
    }

    @Test
    fun `protection domain strategy matches a versioned jar name`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-stdlib-2.3.20.jar").apply { writeText("jar-bytes") }

        assertEquals(jar.absolutePath, ProtectionDomainJarStrategyFor("kotlin-stdlib", anchorFor(jar)).locate())
    }

    @Test
    fun `protection domain strategy ignores a jar of a different artifact`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-reflect-2.3.20.jar").apply { writeText("jar-bytes") }

        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", anchorFor(jar)).locate())
    }

    @Test
    fun `protection domain strategy ignores a directory that matches the prefix`(
        @TempDir tmp: Path,
    ) {
        val dir = File(tmp.toFile(), "kotlin-stdlib").apply { mkdirs() }

        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", anchorFor(dir)).locate())
    }

    @Test
    fun `protection domain strategy ignores a missing file`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlin-stdlib-missing.jar")

        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", anchorFor(missing)).locate())
    }

    @Test
    fun `protection domain strategy ignores an anchor without a code source`() {
        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", String::class.java).locate())
    }

    @Test
    fun `protection domain strategy ignores an anchor whose code source has no location`() {
        val anchor = AnchorClasses.defined(codeSource = AnchorClasses.codeSourceWithoutLocation())

        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", anchor).locate())
    }

    @Test
    fun `protection domain strategy ignores a code source that is not a file uri`() {
        val anchor = anchorForUri("http://example.invalid/kotlin-stdlib.jar")

        assertNull(ProtectionDomainJarStrategyFor("kotlin-stdlib", anchor).locate())
    }

    private fun anchorFor(file: File): Class<*> = anchorForUri(AnchorClasses.uriOf(file))

    private fun anchorForUri(uri: String): Class<*> = AnchorClasses.defined(codeSource = AnchorClasses.codeSourceWith(uri))
}
