package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincEnvironment
import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KotlincResolverFindTest {
    private val incompatibleRunner = FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = ""))

    @Test
    fun `findKotlinc returns null when no candidate exists`(
        @TempDir tmp: Path,
    ) {
        val resolver =
            KotlincResolver(
                processRunner = incompatibleRunner,
                environment = FakeKotlincEnvironment(),
            )
        val originalUserHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", tmp.toAbsolutePath().toString())
            assertNull(resolver.findKotlinc())
        } finally {
            System.setProperty("user.home", originalUserHome)
        }
    }

    @Test
    fun `findKotlinc respects memoisation across calls`(
        @TempDir tmp: Path,
    ) {
        val candidate = File(tmp.toFile(), "kotlinc").apply { writeText("fake") }
        val runner =
            FakeKotlincProcessRunner(
                pathResult = candidate.absolutePath,
                versionResult = KotlincRunResult(exitCode = 0, finished = true, output = ""),
            )
        val resolver = KotlincResolver(processRunner = runner, environment = FakeKotlincEnvironment())

        val first = resolver.findKotlinc()
        val second = resolver.findKotlinc()

        assertEquals(candidate.canonicalPath, first)
        assertEquals(first, second)
        assertEquals(1, runner.versionCalls.size)
    }

    @Test
    fun `findKotlinc returns null with empty environment and no candidates`(
        @TempDir tmp: Path,
    ) {
        val resolver =
            KotlincResolver(
                processRunner = FakeKotlincProcessRunner(throwOnVersion = RuntimeException("no kotlinc")),
                environment = FakeKotlincEnvironment(mapOf("KOTLINC_PATH" to " ")),
            )
        val originalUserHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", tmp.toAbsolutePath().toString())
            assertNull(resolver.findKotlinc())
        } finally {
            System.setProperty("user.home", originalUserHome)
        }
    }

    @Test
    fun `findKotlinc checks each canonical candidate exactly once`(
        @TempDir tmp: Path,
    ) {
        val candidate =
            File(tmp.toFile(), "kotlinc").apply {
                writeText("fake")
                setExecutable(true)
            }
        val runner =
            FakeKotlincProcessRunner(
                pathResult = File(tmp.toFile(), ".").resolve("kotlinc").absolutePath,
                versionResult = KotlincRunResult(exitCode = 1, finished = true, output = ""),
            )
        val resolver =
            KotlincResolver(
                processRunner = runner,
                environment = FakeKotlincEnvironment(mapOf("KOTLINC_PATH" to candidate.absolutePath)),
            )

        assertNull(resolver.findKotlinc())
        assertEquals(
            1,
            runner.versionCalls.count { File(it).absoluteFile.canonicalPath == candidate.canonicalPath },
        )
    }

    @Test
    fun `injectJavaHome sets JAVA_HOME on the process builder`() {
        val resolver =
            KotlincResolver(
                processRunner = FakeKotlincProcessRunner(),
                environment = FakeKotlincEnvironment(),
            )
        val pb = ProcessBuilder("echo", "test")
        val prevJavaHome = System.getProperty("java.home")
        try {
            System.setProperty("java.home", "/tmp/jdk-fake")
            resolver.injectJavaHome(pb)
            assertEquals("/tmp/jdk-fake", pb.environment()["JAVA_HOME"])
        } finally {
            if (prevJavaHome != null) System.setProperty("java.home", prevJavaHome)
        }
    }

    @Test
    fun `findKotlinc returns SDKman candidate when present`(
        @TempDir tmp: Path,
    ) {
        val sdkmanBin = tmp.resolve(".sdkman/candidates/kotlin/current/bin/kotlinc").toFile()
        sdkmanBin.parentFile.mkdirs()
        sdkmanBin.writeText("fake")
        sdkmanBin.setExecutable(true)
        val runner =
            FakeKotlincProcessRunner(
                pathResult = "kotlinc-not-found",
                versionResult = KotlincRunResult(exitCode = 0, finished = true, output = ""),
            )
        val resolver =
            KotlincResolver(
                processRunner = runner,
                environment = FakeKotlincEnvironment(),
            )
        val originalUserHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", tmp.toAbsolutePath().toString())
            assertEquals(sdkmanBin.canonicalPath, resolver.findKotlinc())
        } finally {
            System.setProperty("user.home", originalUserHome)
        }
    }
}
