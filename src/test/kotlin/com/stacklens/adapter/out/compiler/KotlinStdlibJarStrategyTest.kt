package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import com.stacklens.testsupport.FakeNestedJarSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

class KotlinStdlibJarStrategyTest {
    @Test
    fun `resource strategy returns null when the anchor has no class loader`() {
        val strategy = ClassloaderResourceJarStrategyFor("kotlin-stdlib", Int::class.java, NestedJarExtractor())

        assertNull(strategy.locate())
    }

    @Test
    fun `resource strategy returns null when the class resource is missing`() {
        val anchor = AnchorClasses.defined(resourceUrl = null)

        assertNull(ClassloaderResourceJarStrategyFor("kotlin-stdlib", anchor, NestedJarExtractor()).locate())
    }

    @Test
    fun `resource strategy returns null when the resource url has no compiler marker`() {
        val strategy = resourceStrategy("jar:file:/libs/kotlin-reflect-2.3.20.jar!/$COMPILER_RESOURCE")

        assertNull(strategy.locate())
    }

    @Test
    fun `resource strategy returns null when the compiler jar has no version suffix`() {
        val strategy = resourceStrategy("jar:file:/libs/kotlin-compiler-embeddable!/$COMPILER_RESOURCE")

        assertNull(strategy.locate())
    }

    @Test
    fun `resource strategy derives the versioned stdlib url from the compiler resource url`() {
        val nestedUrl = "jar:file:/libs/kotlin-stdlib-2.3.20.jar!/"
        val source = FakeNestedJarSource(payloads = mapOf(nestedUrl to byteArrayOf(5, 6, 7)))
        val strategy = resourceStrategy("jar:file:/libs/kotlin-compiler-embeddable-2.3.20.jar!/$COMPILER_RESOURCE", source)

        val result = strategy.locate()

        assertEquals(listOf(nestedUrl), source.requestedUrls)
        assertEquals(listOf(5.toByte(), 6.toByte(), 7.toByte()), File(result!!).readBytes().toList())
    }

    @Test
    fun `resource strategy accepts a compiler version without a leading dash`() {
        val nestedUrl = "jar:file:/libs/kotlin-stdlib-2.3.20.jar!/"
        val source = FakeNestedJarSource(payloads = mapOf(nestedUrl to byteArrayOf(5)))
        val strategy = resourceStrategy("jar:file:/libs/kotlin-compiler-embeddable2.3.20.jar!/$COMPILER_RESOURCE", source)

        val result = strategy.locate()

        assertEquals(listOf(nestedUrl), source.requestedUrls)
        assertTrue(File(result!!).exists())
    }

    @Test
    fun `resource strategy returns null when the extracted jar is empty`() {
        val source = FakeNestedJarSource(payloads = mapOf("jar:file:/libs/kotlin-stdlib-2.3.20.jar!/" to ByteArray(0)))
        val strategy = resourceStrategy("jar:file:/libs/kotlin-compiler-embeddable-2.3.20.jar!/$COMPILER_RESOURCE", source)

        assertNull(strategy.locate())
    }

    @Test
    fun `resource strategy returns null when the nested jar cannot be opened`() {
        val strategy = resourceStrategy("jar:file:/libs/kotlin-compiler-embeddable-2.3.20.jar!/$COMPILER_RESOURCE")

        assertNull(strategy.locate())
    }

    @Test
    fun `url chain strategy returns null when the anchor has no class loader`() {
        assertNull(UrlClassLoaderChainJarStrategyFor("kotlin-stdlib", Int::class.java).locate())
    }

    @Test
    fun `url chain strategy returns the exact match of the chain`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-stdlib.jar").apply { writeText("stdlib") }

        assertEquals(jar.absolutePath, chainStrategy("kotlin-stdlib", jar).locate())
    }

    @Test
    fun `url chain strategy returns a versioned match of the chain`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-stdlib-2.3.20.jar").apply { writeText("stdlib") }

        assertEquals(jar.absolutePath, chainStrategy("kotlin-stdlib", jar).locate())
    }

    @Test
    fun `url chain strategy returns null when no url of the chain matches the prefix`(
        @TempDir tmp: Path,
    ) {
        val other = File(tmp.toFile(), "kotlin-reflect-2.3.20.jar").apply { writeText("reflect") }

        assertNull(chainStrategy("kotlin-stdlib", other).locate())
    }

    @Test
    fun `url chain strategy skips a matching url whose file is gone`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlin-stdlib.jar")

        assertNull(chainStrategy("kotlin-stdlib", missing).locate())
    }

    @Test
    fun `url chain strategy continues walking the parent loaders until a match is found`(
        @TempDir tmp: Path,
    ) {
        val parentJar = File(tmp.toFile(), "kotlin-stdlib-parent.jar").apply { writeText("parent") }
        val unmatched = File(tmp.toFile(), "unrelated.jar").apply { writeText("unrelated") }
        val parent = urlClassLoaderWith(parentJar)
        val anchor = AnchorClasses.definedFromUrls(listOf(urlOf(unmatched)), parent = parent)

        assertEquals(parentJar.absolutePath, UrlClassLoaderChainJarStrategyFor("kotlin-stdlib", anchor).locate())
    }

    @Test
    fun `url chain strategy returns null when the matching url is not a file url`() {
        val anchor = AnchorClasses.definedFromUrls(listOf(AnchorClasses.urlOf("http://example.invalid/kotlin-stdlib.jar")))

        assertNull(UrlClassLoaderChainJarStrategyFor("kotlin-stdlib", anchor).locate())
    }

    private fun resourceStrategy(
        resourceUrl: String,
        source: FakeNestedJarSource = FakeNestedJarSource(),
    ): KotlinCompilerJarStrategy {
        val anchor = AnchorClasses.defined(resourceUrl = resourceUrl)
        return ClassloaderResourceJarStrategyFor("kotlin-stdlib", anchor, NestedJarExtractor(source))
    }

    private fun chainStrategy(
        prefix: String,
        jar: File,
    ): KotlinCompilerJarStrategy = UrlClassLoaderChainJarStrategyFor(prefix, AnchorClasses.definedFromUrls(listOf(urlOf(jar))))

    private fun urlOf(file: File) = AnchorClasses.urlOf(file.toURI().toString())

    private fun urlClassLoaderWith(vararg jars: File): ClassLoader = URLClassLoader(jars.map { urlOf(it) }.toTypedArray(), null)

    private companion object {
        const val COMPILER_RESOURCE = "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class"
    }
}
