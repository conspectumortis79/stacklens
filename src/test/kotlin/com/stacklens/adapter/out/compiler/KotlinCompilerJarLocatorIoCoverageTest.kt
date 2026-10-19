package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLClassLoader

class KotlinCompilerJarLocatorIoCoverageTest {
    @Test
    fun `temp jar writer streams the full payload into a jar file`(
        @TempDir dir: File,
    ) {
        val payload = ByteArray(4096) { (it % 251).toByte() }

        val written = TempJarWriter().write("stacklens-probe-", ByteArrayInputStream(payload))

        assertTrue(written.name.endsWith(".jar"))
        assertEquals(payload.toList(), written.readBytes().toList())
    }

    @Test
    fun `nested jar extractor keeps a stream that cannot be opened out of the way`() {
        val failing = NestedJarExtractor(source = { throw java.io.IOException("closed") })

        assertNull(failing.extract("jar:file:/nope.jar!/kotlin.zip", "stacklens-probe-"))
    }

    @Test
    fun `nested jar extractor writes through a fake source`() {
        val target = File.createTempFile("stacklens-nested-", ".jar").apply { deleteOnExit() }
        val extractor = NestedJarExtractor(source = { ByteArrayInputStream("payload".toByteArray()) }, tempJarWriter = TempJarWriter())

        val extracted = extractor.extract("jar:file:/x.jar!/nested.zip", target.name.removeSuffix(".jar"))

        assertNotNull(extracted)
        assertEquals("payload", extracted!!.readText())
    }

    @Test
    fun `classloader chain strategy resolves through the default classloader supplier`() {
        val strategy = UrlClassLoaderChainJarStrategy()

        assertNull(strategy.locate())
    }

    @Test
    fun `classloader chain strategy finds a compiler jar inside a url classloader`(
        @TempDir dir: File,
    ) {
        val jar = File(dir, "kotlin-compiler-embeddable-9.9.jar").apply { writeText("x") }
        val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), null)

        assertEquals(
            jar.absolutePath,
            UrlClassLoaderChainJarStrategy(compilerClass = String::class.java, classLoaderSupplier = { loader }).locate(),
        )
    }

    @Test
    fun `classloader chain strategy ignores a compiler jar that is gone from disk`() {
        val loader = URLClassLoader(arrayOf(File("/tmp/stacklens-absent-kotlin-compiler-embeddable.jar").toURI().toURL()), null)

        assertNull(
            UrlClassLoaderChainJarStrategy(compilerClass = String::class.java, classLoaderSupplier = { loader }).locate(),
        )
    }
}
