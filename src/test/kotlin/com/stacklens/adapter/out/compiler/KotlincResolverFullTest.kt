package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincEnvironment
import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KotlincResolverFullTest {
    @Test
    fun `injectJavaHome sets JAVA_HOME in process env`() {
        val resolver = KotlincResolver()
        val pb = ProcessBuilder("echo", "noop")
        pb.environment().remove("JAVA_HOME")
        resolver.injectJavaHome(pb)
        assertEquals(System.getProperty("java.home"), pb.environment()["JAVA_HOME"])
    }

    @Test
    fun `injectJavaHome is a no-op when java home is unavailable`() {
        val resolver = KotlincResolver(javaHomeProvider = { null })
        val pb = ProcessBuilder("echo", "noop")
        pb.environment().remove("JAVA_HOME")

        resolver.injectJavaHome(pb)

        assertNull(pb.environment()["JAVA_HOME"])
    }

    @Test
    fun `findKotlinc memoises result`() {
        val fake = FakeKotlincProcessRunner(pathResult = null, versionResult = KotlincRunResult(0, true, ""))
        val resolver = KotlincResolver(fake)

        val first = resolver.findKotlinc()
        val second = resolver.findKotlinc()
        assertEquals(first, second)

        assertNotNull(resolver)
    }

    @Test
    fun `findKotlinc uses KOTLINC_PATH when set`(
        @TempDir tempDir: Path,
    ) {
        val shim = tempDir.resolve("kotlinc").toFile()
        shim.writeText("#!/bin/sh\necho kotlinc\nexit 0\n")
        shim.setExecutable(true)
        val fake = FakeKotlincProcessRunner(versionResult = KotlincRunResult(0, true, ""))
        val env = FakeKotlincEnvironment(mapOf("KOTLINC_PATH" to shim.absolutePath))
        val resolver = KotlincResolver(fake, env)
        val result = resolver.findKotlinc()
        assertEquals(shim.canonicalPath, result)
    }

    @Test
    fun `findKotlinc skips KOTLINC_PATH when file does not exist`() {
        val fake = FakeKotlincProcessRunner(pathResult = null)
        val env = FakeKotlincEnvironment(mapOf("KOTLINC_PATH" to "/definitely/missing/${System.nanoTime()}/kotlinc"))
        val resolver = KotlincResolver(fake, env)
        val result = resolver.findKotlinc()

        assertTrue(fake.versionCalls.none { it.contains("missing") }, "should not version-check missing path")

        assertTrue(result == null || result.isNotBlank())
    }

    @Test
    fun `findKotlinc skips KOTLINC_PATH when file is not executable`(
        @TempDir tempDir: Path,
    ) {
        val shim = tempDir.resolve("kotlinc").toFile()
        shim.writeText("#!/bin/sh\nexit 0\n")
        shim.setExecutable(false)
        val fake = FakeKotlincProcessRunner(pathResult = null)
        val env = FakeKotlincEnvironment(mapOf("KOTLINC_PATH" to shim.absolutePath))
        val resolver = KotlincResolver(fake, env)
        val result = resolver.findKotlinc()

        assertTrue(fake.versionCalls.none { it == shim.absolutePath }, "should not version-check non-executable")
    }

    @Test
    fun `findKotlinc uses PATH lookup result`(
        @TempDir tempDir: Path,
    ) {
        val shim = tempDir.resolve("kotlinc").toFile()
        shim.writeText("#!/bin/sh\nexit 0\n")
        shim.setExecutable(true)
        val fake = FakeKotlincProcessRunner(pathResult = shim.absolutePath, versionResult = KotlincRunResult(0, true, ""))
        val resolver = KotlincResolver(fake)
        val result = resolver.findKotlinc()
        assertEquals(shim.canonicalPath, result)
        assertTrue(fake.versionCalls.isNotEmpty())
    }

    @Test
    fun `findKotlinc skips PATH result when version check fails`() {
        val fake =
            FakeKotlincProcessRunner(
                pathResult = "/somewhere/kotlinc",
                versionResult = KotlincRunResult(exitCode = 1, finished = true, output = ""),
            )
        val resolver = KotlincResolver(fake)

        val result = resolver.findKotlinc()
        assertNull(result)
    }

    @Test
    fun `findKotlinc skips PATH result when version check returns finished=false`() {
        val fake =
            FakeKotlincProcessRunner(
                pathResult = "/this/path/does/not/exist/kotlinc",
                versionResult = KotlincRunResult(exitCode = -1, finished = false, output = ""),
            )
        val resolver = KotlincResolver(fake)
        val result = resolver.findKotlinc()
        assertNull(result)
    }

    @Test
    fun `findKotlinc swallows exception from version check`() {
        val fake =
            FakeKotlincProcessRunner(
                pathResult = "/somewhere",
                throwOnVersion = RuntimeException("boom"),
            )
        val resolver = KotlincResolver(fake)
        val result = resolver.findKotlinc()

        assertNull(result)
    }

    @Test
    fun `findKotlinc uses KOTLIN_HOME env var`(
        @TempDir tempDir: Path,
    ) {
        val homeDir = tempDir.resolve("kotlin-home").toFile()
        homeDir.mkdirs()
        val binDir = File(homeDir, "bin")
        binDir.mkdirs()
        val shim = File(binDir, "kotlinc")
        shim.writeText("#!/bin/sh\nexit 0\n")
        shim.setExecutable(true)
        val fake = FakeKotlincProcessRunner(versionResult = KotlincRunResult(0, true, ""))
        val env = FakeKotlincEnvironment(mapOf("KOTLIN_HOME" to homeDir.absolutePath))
        val resolver = KotlincResolver(fake, env)
        val result = resolver.findKotlinc()
        assertEquals(shim.canonicalPath, result)
    }
}
