package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.AnchorClasses
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class KotlinCompilerJarLocatorTest {
    @Test
    fun `locate returns null for an empty strategy chain`() {
        assertNull(KotlinCompilerJarLocator(emptyList()).locate())
    }

    @Test
    fun `locate returns null when every strategy fails to resolve a jar`() {
        val locator =
            KotlinCompilerJarLocator(
                listOf(
                    KotlinCompilerJarStrategy { null },
                    KotlinCompilerJarStrategy { null },
                ),
            )

        assertNull(locator.locate())
    }

    @Test
    fun `locate returns the result of the first resolving strategy`() {
        val locator =
            KotlinCompilerJarLocator(
                listOf(
                    KotlinCompilerJarStrategy { null },
                    KotlinCompilerJarStrategy { "/libs/first.jar" },
                    KotlinCompilerJarStrategy { "/libs/second.jar" },
                ),
            )

        assertEquals("/libs/first.jar", locator.locate())
    }

    @Test
    fun `default resolves the compiler jar from the classpath first`() {
        val fatJarBuilder = FatJarBuilder(classPathSupplier = { "/libs/kotlin-compiler-embeddable-2.3.20.jar:/libs/x.jar" })

        assertEquals("/libs/kotlin-compiler-embeddable-2.3.20.jar", KotlinCompilerJarLocator.default(fatJarBuilder).locate())
    }

    @Test
    fun `default resolves the compiler jar from the code source when the classpath has no match`() {
        val expected =
            File(
                K2JVMCompiler::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath
        val fatJarBuilder = FatJarBuilder(classPathSupplier = { "/libs/unrelated.jar" })

        assertEquals(expected, KotlinCompilerJarLocator.default(fatJarBuilder).locate())
    }

    @Test
    fun `classpath strategy returns null when no classpath entry matches`() {
        val strategy = ClasspathJarStrategy(FatJarBuilder(classPathSupplier = { "/libs/other.jar" }))

        assertNull(strategy.locate())
    }

    @Test
    fun `classpath strategy returns null when the classpath cannot be read`() {
        val strategy = ClasspathJarStrategy(FatJarBuilder(classPathSupplier = { error("classpath unavailable") }))

        assertNull(strategy.locate())
    }

    @Test
    fun `classpath strategy prefers the first matching entry and skips documentation jars`() {
        val classpath =
            listOf(
                "/libs/kotlin-compiler-embeddable-sources.jar",
                "/libs/kotlin-compiler-embeddable-javadoc.jar",
                "/libs/kotlin-compiler-embeddable-notes.txt",
                "/libs/kotlin-compiler-embeddable-2.3.20.jar",
            ).joinToString(File.pathSeparator)

        assertEquals(
            "/libs/kotlin-compiler-embeddable-2.3.20.jar",
            ClasspathJarStrategy(FatJarBuilder(classPathSupplier = { classpath })).locate(),
        )
    }

    @Test
    fun `resource strategy returns null when the class resource lookup explodes`() {
        val anchor = AnchorClasses.definedWithFailingResourceLookup()

        assertNull(ClassloaderResourceJarStrategy(compilerClass = anchor).locate())
    }

    @Test
    fun `stdlib resource strategy returns null when the class resource lookup explodes`() {
        val anchor = AnchorClasses.definedWithFailingResourceLookup()

        assertNull(ClassloaderResourceJarStrategyFor("kotlin-stdlib", anchor).locate())
    }
}

class IsKotlinRuntimeArtifactTest {
    @Test
    fun `only versioned kotlin jars are runtime artifacts`() {
        val decisions =
            listOf(
                "kotlin-stdlib.jar",
                "kotlin-stdlib-2.3.20.jar",
                "kotlinx-coroutines-core-jvm.jar",
                "kotlin-compiler-embeddable.jar",
            ).associateWith { isKotlinRuntimeArtifact(it) }

        assertEquals(listOf(true, true, true, true), decisions.values.toList())
    }

    @Test
    fun `documentation and non jar files are no runtime artifacts`() {
        val rejected =
            listOf(
                "kotlin-stdlib-sources.jar",
                "kotlin-stdlib-javadoc.jar",
                "kotlin-notes.txt",
                "guice.jar",
                "intellij-kotlin-plugin.jar",
            ).map { isKotlinRuntimeArtifact(it) }

        assertEquals(List(5) { false }, rejected)
    }
}
