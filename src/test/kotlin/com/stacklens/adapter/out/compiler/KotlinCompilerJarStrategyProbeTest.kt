package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import com.stacklens.testsupport.PlainTestClassLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KotlinCompilerJarStrategyProbeTest {
    @Test
    fun `stdlib chain strategy walks past a parent loader that is not a url class loader`(
        @TempDir tmp: Path,
    ) {
        val classesDir = File(tmp.toFile(), "kotlin-stdlib").apply { mkdirs() }
        val anchor =
            AnchorClasses.definedFromUrls(
                listOf(AnchorClasses.urlOf(classesDir.toURI().toString())),
                parent = PlainTestClassLoader(null),
            )

        assertNull(UrlClassLoaderChainJarStrategyFor("kotlin-stdlib", anchor).locate())
    }

    @Test
    fun `chain strategy ignores a class loader that is not a url class loader`() {
        val strategy =
            UrlClassLoaderChainJarStrategy(
                compilerClass = String::class.java,
                classLoaderSupplier = { PlainTestClassLoader(null) },
            )

        assertNull(strategy.locate())
    }

    @Test
    fun `nested runtime strategy exposes no single jar result`() {
        assertNull(RuntimeNestedJarStrategy().locate())
    }

    @Test
    fun `url chain runtime strategy exposes no single jar result`() {
        assertNull(RuntimeUrlClassLoaderChainStrategy().locate())
    }

    @Test
    fun `classpath runtime strategy returns nothing when the classpath property is missing`() {
        assertEquals(emptyList<String>(), RuntimeClasspathStrategy(classPathSupplier = { null }).locateAll())
    }

    @Test
    fun `url chain runtime strategy skips a class path entry that is a directory`(
        @TempDir tmp: Path,
    ) {
        val classesDir = File(tmp.toFile(), "kotlin-classes").apply { mkdirs() }
        val anchor =
            AnchorClasses.definedFromUrls(
                listOf(AnchorClasses.urlOf(classesDir.toURI().toString())),
                parent = PlainTestClassLoader(null),
            )

        assertEquals(emptyList<String>(), RuntimeUrlClassLoaderChainStrategy(anchor).locateAll())
    }

    @Test
    fun `url chain runtime strategy returns nothing when a kotlin jar url is not a local file`() {
        val anchor =
            AnchorClasses.definedFromUrls(
                listOf(AnchorClasses.urlOf("http://example.invalid/kotlin-stdlib.jar")),
            )

        assertEquals(emptyList<String>(), RuntimeUrlClassLoaderChainStrategy(anchor).locateAll())
    }
}
