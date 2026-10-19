package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.ShellRepoReader
import com.stacklens.application.port.out.repo.RepoFileRead
import com.stacklens.application.port.out.repo.UNBOUNDED_REPO_DEPTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RepoReaderContractTest {
    private val direct = DirectRepoReader()

    private val shell = ShellRepoReader { vararg -> runCommand(*vararg) }

    private fun runCommand(vararg cmd: String): String? {
        val process = ProcessBuilder(cmd.toList()).redirectErrorStream(false).start()
        val stdout = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy()
            return null
        }
        return if (process.exitValue() != 0 || stdout.isBlank()) null else stdout
    }

    private fun file(
        root: File,
        relative: String,
        content: String,
    ): File =
        File(root, relative).apply {
            parentFile.mkdirs()
            writeText(content)
        }

    private fun repo(root: File) {
        file(root, "src/main/App.php", "<?php class App {}")
        file(root, "vendor/lib/Lib.php", "<?php class Lib {}")
        file(root, "docker/Dockerfile.prod", "FROM alpine")
    }

    private fun relativePaths(
        root: File,
        paths: List<String>,
    ) = paths.map { File(it).relativeTo(root).path }.sorted()

    @Test
    fun `both readers locate the same first dockerfile`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        val fromDirect = direct.readFirstDockerfile(tmp.toFile(), UNBOUNDED_REPO_DEPTH, 100)
        val fromShell = shell.readFirstDockerfile(tmp.toFile(), UNBOUNDED_REPO_DEPTH, 100)

        assertEquals(fromDirect?.relativePath, fromShell?.relativePath)
        assertEquals(RepoFileRead.Available("FROM alpine"), fromShell?.content)
    }

    @Test
    fun `both readers honour the requested depth for dockerfile discovery`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        assertEquals("docker/Dockerfile.prod", direct.readFirstDockerfile(tmp.toFile(), 2, 100)?.relativePath)
        assertEquals("docker/Dockerfile.prod", shell.readFirstDockerfile(tmp.toFile(), 2, 100)?.relativePath)
        assertEquals(null, direct.readFirstDockerfile(tmp.toFile(), 1, 100))
        assertEquals(null, shell.readFirstDockerfile(tmp.toFile(), 1, 100))
    }

    @Test
    fun `both readers list the same directories down to the requested depth`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        val expected = relativePaths(tmp.toFile(), direct.listDirectories(tmp.toFile(), 2, 50))

        assertEquals(expected, relativePaths(tmp.toFile(), shell.listDirectories(tmp.toFile(), 2, 50)))
        assertTrue(".php" !in expected.joinToString())
    }

    @Test
    fun `both readers find the same source files and skip the named directory`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        val fromDirect = relativePaths(tmp.toFile(), direct.findFiles(tmp.toFile(), "php", "vendor", 10))
        val fromShell = relativePaths(tmp.toFile(), shell.findFiles(tmp.toFile(), "php", "vendor", 10))

        assertEquals(listOf("src/main/App.php"), fromDirect)
        assertEquals(fromDirect, fromShell)
    }

    @Test
    fun `both readers answer directory presence identically`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        for (candidate in listOf("src", "src/main", "vendor", "missing", "docker")) {
            assertEquals(direct.hasDirectory(tmp.toFile(), candidate), shell.hasDirectory(tmp.toFile(), candidate), candidate)
        }
        assertTrue(shell.hasDirectory(tmp.toFile(), "src/main"))
    }

    @Test
    fun `both readers read the same file content and report missing files alike`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        for (path in listOf("src/main/App.php", "nope.php")) {
            assertEquals(direct.readText(tmp.toFile(), path, 100), shell.readText(tmp.toFile(), path, 100), path)
        }
        assertEquals(RepoFileRead.Available("<?php class App {}"), direct.readText(tmp.toFile(), "src/main/App.php", 100))
    }

    @Test
    fun `both readers list the same top level entries`(
        @TempDir tmp: Path,
    ) {
        repo(tmp.toFile())

        assertEquals(direct.listTopLevel(tmp.toFile(), 10), shell.listTopLevel(tmp.toFile(), 10))
    }
}
