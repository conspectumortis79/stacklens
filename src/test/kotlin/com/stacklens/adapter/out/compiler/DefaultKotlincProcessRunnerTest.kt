package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class DefaultKotlincProcessRunnerTest {
    @Test
    fun `lookupInPath returns the resolved path when the file exists`(
        @TempDir tmp: Path,
    ) {
        val kotlinc = File(tmp.toFile(), "kotlinc").apply { writeText("#!/bin/sh") }

        assertEquals(kotlinc.absolutePath, runner(commandLookup = kotlinc.absolutePath).lookupInPath())
    }

    @Test
    fun `lookupInPath returns null when the shell finds nothing`() {
        assertNull(runner(commandLookup = "  ").lookupInPath())
    }

    @Test
    fun `lookupInPath returns null when the resolved path does not exist`(
        @TempDir tmp: Path,
    ) {
        val gone = File(tmp.toFile(), "removed-kotlinc")

        assertNull(runner(commandLookup = gone.absolutePath).lookupInPath())
    }

    @Test
    fun `lookupInPath returns null when the shell lookup fails`() {
        val lookup = KotlincCommandLookup { throw IllegalStateException("no shell available") }

        assertNull(DefaultKotlincProcessRunner(commandLookup = lookup).lookupInPath())
    }

    @Test
    fun `shell lookup resolves an installed command and leaves unknown ones blank`() {
        val lookup = ShellKotlincCommandLookup()

        assertEquals(File("/bin/sh").canonicalPath, File(lookup.lookup("sh")).canonicalPath)
        assertEquals("", lookup.lookup("stacklens-command-that-does-not-exist"))
    }

    @Test
    fun `runVersionCheck exports JAVA_HOME to the checked process`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "printf 'JAVA_HOME=%s\\n' \"\$JAVA_HOME\"")

        val result = runner(javaHome = "/opt/jdk-25").runVersionCheck(script.absolutePath)

        assertTrue(result.finished)
        assertEquals(0, result.exitCode)
        assertEquals("JAVA_HOME=/opt/jdk-25", result.output.trim())
    }

    @Test
    fun `runVersionCheck leaves the inherited JAVA_HOME untouched when the provider value is blank`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "printf 'JAVA_HOME=%s\\n' \"\$JAVA_HOME\"")
        val inherited = System.getenv("JAVA_HOME").orEmpty()

        val result = runner(javaHome = "").runVersionCheck(script.absolutePath)

        assertEquals("JAVA_HOME=$inherited", result.output.trim())
    }

    @Test
    fun `runVersionCheck reports the exit code and merged output of a failing process`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "echo broken >&2\nexit 42")

        val result = runner(javaHome = "/opt/jdk-25").runVersionCheck(script.absolutePath)

        assertTrue(result.finished)
        assertEquals(42, result.exitCode)
        assertEquals("broken", result.output.trim())
    }

    @Test
    fun `runVersionCheck reports an unfinished result when the process exceeds the timeout`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "sleep 30")

        val result =
            DefaultKotlincProcessRunner(versionCheckTimeoutSeconds = 1L, javaHomeProvider = { "/opt/jdk-25" })
                .runVersionCheck(script.absolutePath)

        assertFalse(result.finished)
        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `runVersionCheck leaves JAVA_HOME untouched when the java home property is missing`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "printf 'JAVA_HOME=[%s]\n' \"\$JAVA_HOME\"")
        val inherited = System.getenv("JAVA_HOME").orEmpty()

        val result = runner(javaHome = null).runVersionCheck(script.absolutePath)

        assertEquals("JAVA_HOME=[$inherited]", result.output.trim())
    }

    @Test
    fun `the built in runner exports the java home of this vm into the checked process`(
        @TempDir tmp: Path,
    ) {
        val script = executableScript(tmp.toFile(), "printf 'JAVA_HOME=%s\\n' \"\$JAVA_HOME\"")

        val result = DefaultKotlincProcessRunner().runVersionCheck(script.absolutePath)

        assertTrue(result.finished)
        assertEquals("JAVA_HOME=${System.getProperty("java.home")}", result.output.trim())
    }

    @Test
    fun `output reader returns the value of a completed future`() {
        val future = CompletableFuture.completedFuture("kotlinc version 2.3.20")

        assertEquals("kotlinc version 2.3.20", ProcessOutputReader().read(future))
    }

    @Test
    fun `output reader falls back to an empty string when the future never completes`() {
        assertEquals("", ProcessOutputReader(awaitTimeoutSeconds = 1L).read(CompletableFuture()))
    }

    private fun runner(
        commandLookup: String = "",
        javaHome: String? = "/opt/jdk-25",
    ): DefaultKotlincProcessRunner =
        DefaultKotlincProcessRunner(
            javaHomeProvider = { javaHome },
            commandLookup = KotlincCommandLookup { commandLookup },
        )

    private fun executableScript(
        dir: File,
        body: String,
    ): File {
        val script = File(dir, "kotlinc")
        script.writeText("#!/bin/sh\n$body\n")
        script.setExecutable(true)
        return script
    }
}
