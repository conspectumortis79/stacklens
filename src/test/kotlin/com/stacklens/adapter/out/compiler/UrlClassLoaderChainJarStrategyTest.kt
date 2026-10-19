package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

class UrlClassLoaderChainJarStrategyTest {
    @Test
    fun `locate returns null when there is no class loader to walk`() {
        val strategy = UrlClassLoaderChainJarStrategy(classLoaderSupplier = { null })

        assertNull(strategy.locate())
    }

    @Test
    fun `locate returns null when no loader in the chain is a url class loader`() {
        val strategy =
            UrlClassLoaderChainJarStrategy(
                compilerClass = String::class.java,
                classLoaderSupplier = { ClassLoader.getSystemClassLoader() },
            )

        assertNull(strategy.locate())
    }

    @Test
    fun `locate returns the jar of the first url class loader that carries the compiler`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-compiler-embeddable-2.0.0.jar").apply { writeText("compiler") }

        assertEquals(jar.absolutePath, strategyWith(urlClassLoaderWith(jar)).locate())
    }

    @Test
    fun `locate skips a loader whose compiler jar url points to a missing file`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlin-compiler-embeddable-gone.jar")
        val found = File(tmp.toFile(), "kotlin-compiler-embeddable-parent.jar").apply { writeText("compiler") }

        val strategy =
            UrlClassLoaderChainJarStrategy(
                classLoaderSupplier = { urlClassLoaderWith(missing, parent = urlClassLoaderWith(found)) },
            )

        assertEquals(found.absolutePath, strategy.locate())
    }

    @Test
    fun `locate returns null when the only matching url points to a missing file`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlin-compiler-embeddable-missing.jar")

        assertNull(strategyWith(urlClassLoaderWith(missing)).locate())
    }

    @Test
    fun `locate returns null when the chain has url class loaders without the compiler jar`(
        @TempDir tmp: Path,
    ) {
        val unrelated = File(tmp.toFile(), "guice.jar").apply { writeText("guice") }

        assertNull(strategyWith(urlClassLoaderWith(unrelated, parent = urlClassLoaderWith(File(tmp.toFile(), "missing.jar")))).locate())
    }

    @Test
    fun `locate returns null when the matching url is not a file url`() {
        val loader = URLClassLoader(arrayOf(AnchorClasses.urlOf("http://example.invalid/kotlin-compiler-embeddable.jar")), null)

        assertNull(strategyWith(loader).locate())
    }

    @Test
    fun `locate returns the jar of a parent loader behind a plain loader`(
        @TempDir tmp: Path,
    ) {
        val jar = File(tmp.toFile(), "kotlin-compiler-embeddable-parent.jar").apply { writeText("compiler") }
        val plain = object : ClassLoader(urlClassLoaderWith(jar)) {}

        assertEquals(jar.absolutePath, strategyWith(plain).locate())
    }

    private fun strategyWith(loader: ClassLoader): UrlClassLoaderChainJarStrategy = UrlClassLoaderChainJarStrategy(compilerClass = String::class.java, classLoaderSupplier = { loader })

    private fun urlClassLoaderWith(
        vararg jars: File,
        parent: ClassLoader? = null,
    ): URLClassLoader = URLClassLoader(jars.map { AnchorClasses.urlOf(it.toURI().toString()) }.toTypedArray(), parent)
}
