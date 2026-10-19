package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ProtectionDomainJarStrategyTest {
    @Test
    fun `locate returns the compiler jar when K2JVMCompiler is loaded from a jar`() {
        val expected =
            File(
                K2JVMCompiler::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath

        assertEquals(expected, ProtectionDomainJarStrategy().locate())
    }

    @Test
    fun `locate returns null when the anchor class has no code source`() {
        assertNull(ProtectionDomainJarStrategy(String::class.java).locate())
    }

    @Test
    fun `locate returns null when the code source has no location`() {
        val anchor = AnchorClasses.defined(codeSource = AnchorClasses.codeSourceWithoutLocation())

        assertNull(ProtectionDomainJarStrategy(anchor).locate())
    }

    @Test
    fun `locate returns the jar path when the code source points at an existing jar`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-compiler-embeddable-1.2.3.jar").apply { writeText("x") }
        val anchor = anchorWith(codeSourceUri = AnchorClasses.uriOf(jar))

        assertEquals(jar.absolutePath, ProtectionDomainJarStrategy(anchor).locate())
    }

    @Test
    fun `locate returns null when the code source points at a directory`(
        @TempDir tmp: Path,
    ) {
        val dir = File(tmp.toFile(), "kotlin-compiler-embeddable.jar")
        dir.mkdirs()
        val anchor = anchorWith(codeSourceUri = AnchorClasses.uriOf(dir))

        assertNull(ProtectionDomainJarStrategy(anchor).locate())
    }

    @Test
    fun `locate returns null when the code source file is not a jar`(
        @TempDir tmp: Path,
    ) {
        val txt = File(tmp.toFile(), "kotlin-compiler-embeddable.txt").apply { writeText("x") }
        val anchor = anchorWith(codeSourceUri = AnchorClasses.uriOf(txt))

        assertNull(ProtectionDomainJarStrategy(anchor).locate())
    }

    @Test
    fun `locate returns null when the code source file does not exist`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlin-compiler-embeddable-missing.jar")
        val anchor = anchorWith(codeSourceUri = AnchorClasses.uriOf(missing))

        assertNull(ProtectionDomainJarStrategy(anchor).locate())
    }

    @Test
    fun `locate returns null when the code source location is not a file uri`() {
        val anchor = anchorWith(codeSourceUri = "http://example.invalid/kotlin-compiler-embeddable.jar")

        assertNull(ProtectionDomainJarStrategy(anchor).locate())
    }

    private fun anchorWith(codeSourceUri: String): Class<*> = AnchorClasses.defined(codeSource = AnchorClasses.codeSourceWith(codeSourceUri))
}
