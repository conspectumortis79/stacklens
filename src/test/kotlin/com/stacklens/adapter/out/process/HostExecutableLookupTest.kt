package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakePlatformPort
import com.stacklens.testsupport.FakeShellPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class HostExecutableLookupTest {
    private class SandboxedContributor(
        private val wanted: String,
        private val directories: (ExecutableHost) -> List<String>,
    ) : ExecutablePathContributor {
        override fun supports(executable: String): Boolean = executable == wanted

        override fun directories(
            executable: String,
            host: ExecutableHost,
        ): List<String> = directories(host)
    }

    private fun executableFile(
        dir: File,
        name: String,
    ): File =
        File(dir, name).apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

    private fun lookup(
        shell: FakeShellPort = FakeShellPort(),
        home: String = "/home/tester",
        osType: OsType = OsType.LINUX,
        contributors: List<ExecutablePathContributor> = emptyList(),
    ) = HostExecutableLookup(
        shell = shell,
        hostDirectories = FakeHostDirectoryPort(home = home),
        platform = FakePlatformPort(osType),
        contributors = contributors,
        executableProbe = FileSystemExecutableProbe(),
    )

    @Test
    fun `resolve returns the path reported by the shell probe`(
        @TempDir tmp: Path,
    ) {
        val tool = executableFile(tmp.toFile(), "mytool")
        val shell = FakeShellPort(pathOfCommand = { tool.absolutePath })
        assertEquals(tool.absolutePath, lookup(shell = shell).resolve("mytool"))
        assertEquals(listOf("mytool"), shell.probedCommands)
    }

    @Test
    fun `resolve ignores a probe path that is not executable`(
        @TempDir tmp: Path,
    ) {
        val ghost = tmp.resolve("ghost-tool").toFile()
        val shell = FakeShellPort(pathOfCommand = { ghost.absolutePath })
        assertNull(lookup(shell = shell).resolve("ghost-tool"))
    }

    @Test
    fun `resolve continues when the shell probe throws`(
        @TempDir tmp: Path,
    ) {
        val shell = FakeShellPort(pathOfCommand = { throw RuntimeException("process crashed") })
        val tool = executableFile(tmp.toFile(), "k6")
        val sut =
            lookup(
                shell = shell,
                home = tmp.toString(),
                contributors = listOf(SandboxedContributor("k6") { listOf(tmp.toString()) }),
            )
        assertEquals(tool.absolutePath, sut.resolve("k6"))
    }

    @Test
    fun `resolve falls back to the home local bin when the probe finds nothing`(
        @TempDir tmp: Path,
    ) {
        val localBin = File(tmp.toFile(), ".local/bin").apply { mkdirs() }
        val tool = executableFile(localBin, "k6")
        assertEquals(tool.absolutePath, lookup(home = tmp.toString()).resolve("k6"))
    }

    @Test
    fun `resolve checks preferred paths before any candidate directory`(
        @TempDir tmp: Path,
    ) {
        val preferred = executableFile(tmp.toFile(), "custom-tool")
        val localBin = File(tmp.toFile(), ".local/bin").apply { mkdirs() }
        executableFile(localBin, "custom-tool")
        val result = lookup(home = tmp.toString()).resolve("custom-tool", listOf(preferred.absolutePath))
        assertEquals(preferred.absolutePath, result)
    }

    @Test
    fun `resolve returns null for an unknown executable`(
        @TempDir tmp: Path,
    ) {
        assertNull(lookup(home = tmp.toString()).resolve("definitely-no-such-tool-${System.nanoTime()}"))
    }

    @Test
    fun `directoriesFor puts contributor directories first and removes duplicates`() {
        val contributor =
            SandboxedContributor("docker") { host ->
                listOf("/usr/local/bin", "${host.homeDirectory}/.docker/bin")
            }
        val dirs = lookup(home = "/home/tester", contributors = listOf(contributor)).directoriesFor("docker")
        assertEquals(listOf("/usr/local/bin", "/home/tester/.docker/bin"), dirs.take(2))
        assertEquals(dirs.distinct(), dirs)
    }

    @Test
    fun `directoriesFor ignores contributors for other executables`() {
        val contributor = SandboxedContributor("docker") { listOf("/opt/docker/bin") }
        val dirs = lookup(home = "/home/tester", contributors = listOf(contributor)).directoriesFor("k6")
        assertTrue("/opt/docker/bin" !in dirs)
        assertEquals("/home/tester/.local/bin", dirs.first())
    }

    @Test
    fun `directoriesFor keeps the generic order on macOS without contributors`() {
        val dirs = lookup(home = "/Users/tester", osType = OsType.MACOS).directoriesFor("k6")
        assertEquals(
            listOf(
                "/Users/tester/.local/bin",
                "/usr/local/bin",
                "/snap/bin",
                "/opt/homebrew/bin",
                "/opt/homebrew/sbin",
                "/usr/bin",
                "/usr/sbin",
            ),
            dirs,
        )
    }
}
