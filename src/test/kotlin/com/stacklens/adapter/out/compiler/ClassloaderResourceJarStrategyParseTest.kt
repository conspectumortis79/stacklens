package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import com.stacklens.testsupport.FakeNestedJarSource
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClassloaderResourceJarStrategyParseTest {
    @Test
    fun `locate returns the compiler jar when K2JVMCompiler is loaded from a jar`() {
        val expected =
            File(
                K2JVMCompiler::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath

        assertEquals(expected, ClassloaderResourceJarStrategy().locate())
    }

    @Test
    fun `locate returns null when the anchor class has no class loader`() {
        assertNull(ClassloaderResourceJarStrategy(compilerClass = Int::class.java).locate())
    }

    @Test
    fun `locate returns null when the class resource cannot be found`() {
        val anchor = AnchorClasses.defined(resourceUrl = null)

        assertNull(ClassloaderResourceJarStrategy(compilerClass = anchor).locate())
    }

    @Test
    fun `locate resolves the jar through parseJarUrl for a defined anchor class`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-compiler-embeddable-9.9.9.jar").apply { writeText("x") }
        val anchor = AnchorClasses.defined(resourceUrl = "jar:${jar.toURI()}!/$COMPILER_RESOURCE")

        assertEquals(jar.absolutePath, ClassloaderResourceJarStrategy(compilerClass = anchor).locate())
    }

    @Test
    fun `parseJarUrl returns null when the url is not a jar url`() {
        assertNull(parse("file:/tmp/not-a-jar/Foo.class"))
    }

    @Test
    fun `parseJarUrl returns the absolute path of an existing jar file url`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "compiler.jar").apply { writeText("x") }

        assertEquals(jar.absolutePath, parse("jar:${jar.toURI()}!/org/Foo.class"))
    }

    @Test
    fun `parseJarUrl returns null for a jar file url pointing at a directory`(
        @TempDir tmp: Path,
    ) {
        val dir = File(tmp.toFile(), "directory.jar").apply { mkdirs() }

        assertNull(parse("jar:${dir.toURI()}!/org/Foo.class"))
    }

    @Test
    fun `parseJarUrl returns null for a jar file url pointing at a missing file`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "missing.jar")

        assertNull(parse("jar:${missing.toURI()}!/org/Foo.class"))
    }

    @Test
    fun `parseJarUrl appends the trailing separator for a non file jar url`() {
        val source = FakeNestedJarSource(payloads = mapOf("jar:http://host/a.jar!/" to byteArrayOf(7)))
        val strategy = ClassloaderResourceJarStrategy(nestedJarExtractor = NestedJarExtractor(source))

        val extracted = strategy.parseJarUrl(AnchorClasses.urlOf("jar:http://host/a.jar!/org/Foo.class"))

        assertEquals(listOf<Byte>(7), extracted?.let(::File)?.readBytes()?.toList())
        assertEquals(listOf("jar:http://host/a.jar!/"), source.requestedUrls)
    }

    @Test
    fun `parseJarUrl keeps a nested jar url that already ends with the separator`() {
        val nested = "jar:http://host/libs/kotlin-compiler-embeddable.jar!/"
        val source = FakeNestedJarSource(payloads = mapOf(nested to byteArrayOf(1, 2)))
        val strategy = ClassloaderResourceJarStrategy(nestedJarExtractor = NestedJarExtractor(source))

        val result = strategy.parseJarUrl(AnchorClasses.urlOf("$nested!/org/Foo.class"))

        assertNotNull(result)
        assertEquals(listOf(nested), source.requestedUrls)
    }

    @Test
    fun `parseJarUrl returns null when the nested jar cannot be extracted`() {
        val source = FakeNestedJarSource(payloads = emptyMap())
        val strategy = ClassloaderResourceJarStrategy(nestedJarExtractor = NestedJarExtractor(source))

        assertNull(strategy.parseJarUrl(AnchorClasses.urlOf("jar:http://host/a.jar!/org/Foo.class")))
        assertEquals(listOf("jar:http://host/a.jar!/"), source.requestedUrls)
    }

    private fun parse(spec: String): String? = ClassloaderResourceJarStrategy().parseJarUrl(AnchorClasses.urlOf(spec))

    private companion object {
        const val COMPILER_RESOURCE = "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class"
    }
}
