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

class KotlinRuntimeJarLocatorTest {
    @Test
    fun `locateAll returns the result of the first runtime strategy that finds jars`() {
        val locator =
            KotlinRuntimeJarLocator(
                listOf(
                    object :
                        KotlinCompilerJarStrategy,
                        RuntimeJarStrategy {
                        override fun locate(): String? = "/ignored.jar"

                        override fun locateAll(): List<String> = listOf("/libs/kotlin-stdlib.jar")
                    },
                ),
            )

        assertEquals(listOf("/libs/kotlin-stdlib.jar"), locator.locateAll())
    }

    @Test
    fun `locateAll falls back to the single jar result of a plain strategy`() {
        val locator =
            KotlinRuntimeJarLocator(
                listOf(
                    object :
                        KotlinCompilerJarStrategy,
                        RuntimeJarStrategy {
                        override fun locate(): String? = null

                        override fun locateAll(): List<String> = emptyList()
                    },
                    KotlinCompilerJarStrategy { "/libs/kotlin-reflect.jar" },
                ),
            )

        assertEquals(listOf("/libs/kotlin-reflect.jar"), locator.locateAll())
    }

    @Test
    fun `locateAll skips strategies that throw`() {
        val locator =
            KotlinRuntimeJarLocator(
                listOf(
                    KotlinCompilerJarStrategy { throw IllegalStateException("probe failure") },
                    object :
                        KotlinCompilerJarStrategy,
                        RuntimeJarStrategy {
                        override fun locate(): String? = null

                        override fun locateAll(): List<String> = listOf("/libs/kotlin-stdlib.jar")
                    },
                ),
            )

        assertEquals(listOf("/libs/kotlin-stdlib.jar"), locator.locateAll())
    }

    @Test
    fun `locateAll returns an empty list when no strategy resolves anything`() {
        assertEquals(emptyList<String>(), KotlinRuntimeJarLocator(listOf(KotlinCompilerJarStrategy { null })).locateAll())
    }

    @Test
    fun `default wires the runtime strategies and resolves jars that exist on disk`() {
        val resolved = KotlinRuntimeJarLocator.default().locateAll()

        assertTrue(resolved.isNotEmpty(), "expected the runtime locator to find kotlin jars on the test classpath")
        assertTrue(resolved.all { File(it).exists() }, "every resolved runtime jar must exist: $resolved")
        assertTrue(resolved.any { File(it).name.startsWith("kotlin") }, "expected kotlin runtime jars, got: $resolved")
    }

    @Test
    fun `classpath strategy keeps only kotlin artifacts of the given classpath`() {
        val classpath =
            listOf(
                "/libs/kotlin-stdlib-2.3.20.jar",
                "/libs/kotlinx-coroutines-core-jvm.jar",
                "/libs/kotlin-compiler-embeddable-sources.jar",
                "/libs/kotlin-docs-javadoc.jar",
                "/libs/kotlin-notes.txt",
                "/libs/guice.jar",
            ).joinToString(File.pathSeparator)

        val resolved = RuntimeClasspathStrategy(classPathSupplier = { classpath }).locateAll()

        assertEquals(listOf("/libs/kotlin-stdlib-2.3.20.jar", "/libs/kotlinx-coroutines-core-jvm.jar"), resolved)
    }

    @Test
    fun `classpath strategy ignores a locate call`() {
        assertNull(RuntimeClasspathStrategy(classPathSupplier = { "/libs/kotlin-stdlib.jar" }).locate())
    }

    @Test
    fun `classpath strategy returns nothing when the classpath property is unavailable`() {
        assertEquals(emptyList<String>(), RuntimeClasspathStrategy(classPathSupplier = { null }).locateAll())
    }

    @Test
    fun `nested jar strategy returns nothing when the anchor has no class loader`() {
        assertEquals(emptyList<String>(), RuntimeNestedJarStrategy(Int::class.java, NestedJarExtractor()).locateAll())
    }

    @Test
    fun `nested jar strategy returns nothing when the class resource is missing`() {
        val anchor = AnchorClasses.defined(resourceUrl = null)

        assertEquals(emptyList<String>(), RuntimeNestedJarStrategy(anchor, NestedJarExtractor()).locateAll())
    }

    @Test
    fun `nested jar strategy returns nothing when the resource url is not inside a compiler jar`() {
        val anchor = AnchorClasses.defined(resourceUrl = "file:/build/classes/org/Foo.class")

        assertEquals(emptyList<String>(), RuntimeNestedJarStrategy(anchor, NestedJarExtractor()).locateAll())
    }

    @Test
    fun `nested jar strategy extracts every candidate that is available next to the compiler jar`() {
        val base = "jar:file:/stacklens/libs/"
        val payloads =
            mapOf(
                "${base}kotlin-stdlib-1.8.1.jar!/" to byteArrayOf(1),
                "${base}kotlin-reflect-2.3.20.jar!/" to ByteArray(0),
                "${base}kotlin-reflect-2.1.0.jar!/" to byteArrayOf(2),
            )
        val source = FakeNestedJarSource(payloads = payloads)
        val anchor = AnchorClasses.defined(resourceUrl = "${base}kotlin-compiler-embeddable-2.3.20.jar!/$COMPILER_RESOURCE")

        val resolved = RuntimeNestedJarStrategy(anchor, NestedJarExtractor(source)).locateAll()

        assertEquals(
            listOf(byteArrayOf(1).toList(), byteArrayOf(2).toList()),
            resolved.map { File(it).readBytes().toList() },
        )
        assertEquals(
            listOf(
                "${base}kotlin-stdlib-1.8.1.jar!/",
                "${base}kotlin-reflect-1.8.1.jar!/",
                "${base}kotlin-reflect-2.3.20.jar!/",
                "${base}kotlin-reflect-2.1.0.jar!/",
                "${base}kotlin-script-runtime-1.8.1.jar!/",
            ),
            source.requestedUrls.take(5),
        )
    }

    @Test
    fun `nested jar strategy returns nothing when the class loader lookup explodes`() {
        val anchor = AnchorClasses.definedWithFailingResourceLookup()

        assertEquals(emptyList<String>(), RuntimeNestedJarStrategy(anchor, NestedJarExtractor()).locateAll())
    }

    @Test
    fun `url chain strategy returns nothing when the anchor has no class loader`() {
        assertEquals(emptyList<String>(), RuntimeUrlClassLoaderChainStrategy(Int::class.java).locateAll())
    }

    @Test
    fun `url chain strategy keeps only existing kotlin jars of the loader chain`(
        @TempDir tmp: Path,
    ) {
        val stdlib = File(tmp.toFile(), "kotlin-stdlib-2.3.20.jar").apply { writeText("stdlib") }
        val coroutines = File(tmp.toFile(), "kotlinx-coroutines-core-jvm.jar").apply { writeText("coroutines") }
        val sources = File(tmp.toFile(), "kotlin-stdlib-sources.jar").apply { writeText("sources") }
        val javadoc = File(tmp.toFile(), "kotlin-api-javadoc.jar").apply { writeText("javadoc") }
        val plain = File(tmp.toFile(), "guice.jar").apply { writeText("guice") }
        val directory = File(tmp.toFile(), "kotlin-classes").apply { mkdirs() }
        val missing = File(tmp.toFile(), "kotlin-reflect-missing.jar")
        val anchor =
            AnchorClasses.definedFromUrls(
                listOf(stdlib, stdlib, coroutines, sources, javadoc, plain, directory, missing).map { urlOf(it) },
            )

        val resolved = RuntimeUrlClassLoaderChainStrategy(anchor).locateAll()

        assertEquals(listOf(stdlib.absolutePath, coroutines.absolutePath), resolved)
    }

    @Test
    fun `url chain strategy walks up to parent loaders`(
        @TempDir tmp: Path,
    ) {
        val parentJar = File(tmp.toFile(), "kotlin-reflect-1.0.jar").apply { writeText("reflect") }
        val childJar = File(tmp.toFile(), "unrelated.jar").apply { writeText("unrelated") }
        val parent = URLClassLoader(arrayOf(urlOf(parentJar)), null)
        val anchor = AnchorClasses.definedFromUrls(listOf(urlOf(childJar)), parent = parent)

        assertEquals(listOf(parentJar.absolutePath), RuntimeUrlClassLoaderChainStrategy(anchor).locateAll())
    }

    @Test
    fun `url chain strategy returns nothing for a non file url of a kotlin jar`() {
        val anchor = AnchorClasses.definedFromUrls(listOf(AnchorClasses.urlOf("http://example.invalid/kotlin-stdlib.jar")))

        assertEquals(emptyList<String>(), RuntimeUrlClassLoaderChainStrategy(anchor).locateAll())
    }

    private fun urlOf(file: File): java.net.URL = AnchorClasses.urlOf(file.toURI().toString())

    private companion object {
        const val COMPILER_RESOURCE = "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class"
    }
}
