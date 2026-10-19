package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincEnvironment
import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KotlincResolverCandidateTest {
    @Test
    fun `findKotlinc uses an executable file from KOTLINC_PATH`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = executable(tmp.toFile(), "kotlinc-from-env")

        val result = resolver(environment = mapOf("KOTLINC_PATH" to kotlinc.absolutePath)).findKotlinc()

        assertEquals(kotlinc.canonicalPath, result)
    }

    @Test
    fun `findKotlinc ignores KOTLINC_PATH when the file is not executable`(
        @TempDir tmp: Path,
    ) {
        val notExecutable = File(tmp.toFile(), "kotlinc-not-executable").apply { writeText("#!/bin/sh") }

        val result = resolver(environment = mapOf("KOTLINC_PATH" to notExecutable.absolutePath)).findKotlinc()

        assertNull(result)
    }

    @Test
    fun `findKotlinc ignores KOTLINC_PATH when the file does not exist`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "kotlinc-missing")

        val result = resolver(environment = mapOf("KOTLINC_PATH" to missing.absolutePath)).findKotlinc()

        assertNull(result)
    }

    @Test
    fun `findKotlinc skips a blank path lookup result`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = executable(tmp.toFile(), "kotlinc-from-sdkman")
        val runner = FakeKotlincProcessRunner(pathResult = "   ")

        val result =
            resolver(
                runner = runner,
                candidates = listOf(kotlinc.absolutePath),
            ).findKotlinc()

        assertEquals(kotlinc.canonicalPath, result)
        assertTrue(
            runner.versionCalls.none { it.isBlank() },
            "the blank lookup result must not be probed: ${runner.versionCalls}",
        )
    }

    @Test
    fun `findKotlinc uses the path lookup result when it is compatible`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = executable(tmp.toFile(), "kotlinc-on-path")
        val runner = FakeKotlincProcessRunner(pathResult = kotlinc.absolutePath)

        val result = resolver(runner = runner).findKotlinc()

        assertEquals(kotlinc.canonicalPath, result)
        assertEquals(listOf(kotlinc.canonicalPath), runner.versionCalls)
    }

    @Test
    fun `findKotlinc skips a path lookup result that does not exist`(
        @TempDir tmp: Path,
    ) {
        val gone = File(tmp.toFile(), "kotlinc-gone")
        val runner = FakeKotlincProcessRunner(pathResult = gone.absolutePath)

        assertNull(resolver(runner = runner).findKotlinc())
    }

    @Test
    fun `findKotlinc uses KOTLIN_HOME bin kotlinc when it is compatible`(
        @TempDir tmp: Path,
    ) {
        val home = File(tmp.toFile(), "kotlin-home")
        val kotlinc = executable(File(home, "bin").apply { mkdirs() }, "kotlinc")

        val result = resolver(environment = mapOf("KOTLIN_HOME" to home.absolutePath)).findKotlinc()

        assertEquals(kotlinc.canonicalPath, result)
    }

    @Test
    fun `findKotlinc keeps searching when the canonical path of a candidate cannot be resolved`() {
        val result = resolver(environment = mapOf("KOTLIN_HOME" to "/stacklens\u0000broken")).findKotlinc()

        assertNull(result)
    }

    @Test
    fun `findKotlinc returns the first compatible candidate and probes every path only once`(
        @TempDir tmp: Path,
    ) {
        val first = executable(tmp.toFile(), "kotlinc-first")
        val second = executable(tmp.toFile(), "kotlinc-second")
        val runner = RejectingRunner(incompatible = first.canonicalPath)

        val result =
            resolver(
                runner = runner,
                candidates = listOf(first.absolutePath, first.absolutePath, second.absolutePath),
            ).findKotlinc()

        assertEquals(second.canonicalPath, result)
        assertEquals(listOf(first.canonicalPath, second.canonicalPath), runner.probedPaths)
    }

    @Test
    fun `findKotlinc returns null when the version check rejects every existing candidate`(
        @TempDir tmp: Path,
    ) {
        val incompatible = executable(tmp.toFile(), "kotlinc-incompatible")
        val runner = FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = "incompatible"))

        val result =
            resolver(
                runner = runner,
                candidates = listOf(incompatible.absolutePath),
            ).findKotlinc()

        assertNull(result)
        assertEquals(listOf(incompatible.canonicalPath), runner.versionCalls)
    }

    @Test
    fun `findKotlinc returns null when a compatibility check throws`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = executable(tmp.toFile(), "kotlinc-throwing")
        val runner = FakeKotlincProcessRunner(throwOnVersion = IllegalStateException("boom"))

        val result = resolver(runner = runner, candidates = listOf(kotlinc.absolutePath)).findKotlinc()

        assertNull(result)
        assertEquals(listOf(kotlinc.canonicalPath), runner.versionCalls)
    }

    @Test
    fun `default candidate paths follow the configured user home`() {
        val paths = DefaultKotlincCandidatePaths(userHomeProvider = { "/home/tester" }).paths()

        assertEquals("/home/tester/.sdkman/candidates/kotlin/current/bin/kotlinc", paths.first())
        assertTrue(paths.contains("/usr/local/bin/kotlinc"))
        assertTrue(paths.contains("/home/tester/.local/share/JetBrains/Toolbox/apps/intellij-idea-community/plugins/Kotlin/kotlinc/bin/kotlinc"))
    }

    @Test
    fun `injectJavaHome skips the environment when java home is missing`() {
        val pb = ProcessBuilder("echo", "noop")
        pb.environment().remove("JAVA_HOME")

        KotlincResolver(javaHomeProvider = { null }).injectJavaHome(pb)

        assertNull(pb.environment()["JAVA_HOME"])
    }

    @Test
    fun `injectJavaHome skips the environment when java home is blank`() {
        val pb = ProcessBuilder("echo", "noop")
        pb.environment().remove("JAVA_HOME")

        KotlincResolver(javaHomeProvider = { "   " }).injectJavaHome(pb)

        assertNull(pb.environment()["JAVA_HOME"])
    }

    @Test
    fun `injectJavaHome sets the environment when java home is present`() {
        val pb = ProcessBuilder("echo", "noop")
        pb.environment().remove("JAVA_HOME")

        KotlincResolver(javaHomeProvider = { "/opt/jdk-25" }).injectJavaHome(pb)

        assertEquals("/opt/jdk-25", pb.environment()["JAVA_HOME"])
    }

    private fun resolver(
        runner: KotlincProcessRunner = FakeKotlincProcessRunner(),
        environment: Map<String, String> = emptyMap(),
        candidates: List<String> = emptyList(),
    ): KotlincResolver =
        KotlincResolver(
            processRunner = runner,
            environment = FakeKotlincEnvironment(environment),
            candidatePaths = KotlincCandidatePaths { candidates },
        )

    private fun executable(
        dir: File,
        name: String,
    ): File =
        File(dir, name).apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

    private class RejectingRunner(
        private val incompatible: String,
    ) : KotlincProcessRunner {
        val probedPaths: MutableList<String> = mutableListOf()

        override fun lookupInPath(): String? = null

        override fun runVersionCheck(kotlincPath: String): KotlincRunResult {
            probedPaths += kotlincPath
            val exitCode = if (kotlincPath == incompatible) 1 else 0
            return KotlincRunResult(exitCode = exitCode, finished = true, output = "")
        }
    }
}
