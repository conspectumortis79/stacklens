package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.filesystem.ShellRepoReaderFactoryAdapter
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class RepoScannerTest {
    private val processPort: ProcessPort = mock()
    private val scanner = RepoScanner(processPort, DirectRepoReader(), JavaFileSystemAdapter(), ShellRepoReaderFactoryAdapter())

    @Test
    fun `scanRepoStructure returns directory-not-found message when path missing`(
        @TempDir tmp: Path,
    ) {
        val nonExistent = tmp.resolve("does-not-exist").toString()
        val result = scanner.scanRepoStructure(nonExistent)
        assertTrue(result.contains("directory not found"))
        assertTrue(result.contains(nonExistent))
    }

    @Test
    fun `scanRepoStructure returns not-a-directory message when path is a file`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("a-file.txt").toFile().apply { writeText("hi") }
        val result = scanner.scanRepoStructure(file.absolutePath)
        assertTrue(result.contains("not a directory"))
    }

    @Test
    fun `scanRepoStructure falls back to shell when JVM access is denied`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        root.setReadable(false)
        try {
            if (!root.canRead()) {
                whenever(
                    processPort.run(
                        org.mockito.kotlin.any<List<String>>(),
                        org.mockito.kotlin.any<Long>(),
                        org.mockito.kotlin.anyOrNull(),
                    ),
                ).thenReturn(ProcessResult(exitCode = 0, stdout = "file.txt\n", stderr = ""))
                val result = scanner.scanRepoStructure(root.absolutePath)
                assertTrue(!result.contains("access denied"))
            }
        } finally {
            root.setReadable(true)
        }
    }

    @Test
    fun `scanRepoStructure returns access-denied message when both JVM and shell fail`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        root.setReadable(false)
        try {
            if (!root.canRead()) {
                whenever(processPort.run(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull()))
                    .thenReturn(ProcessResult(exitCode = 1, stdout = "", stderr = ""))
                val result = scanner.scanRepoStructure(root.absolutePath)
                assertTrue(result.contains("access denied"))
            }
        } finally {
            root.setReadable(true)
        }
    }

    @Test
    fun `scanRepoStructureDirect builds a tree of a small repo`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "src").mkdirs()
        File(root, "src/Foo.php").writeText("<?php class Foo {}")
        File(root, "Dockerfile").writeText("FROM php:8\n")
        File(root, "composer.json").writeText("""{"name":"x/y"}""")
        File(root, "Makefile").writeText("test:\n\t@phpunit\n")

        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("Repository: "))
        assertTrue(result.contains("## Directory structure"))
        assertTrue(result.contains("## composer.json"))
        assertTrue(result.contains("## Dockerfile"))
        assertTrue(result.contains("## Source tree"))
        assertTrue(result.contains("## Sample PHP files"))
        assertTrue(result.contains("## Makefile targets"))
    }

    @Test
    fun `scanRepoStructureDirect picks docker-compose over alternatives`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "docker-compose.yml").writeText("version: '3'")
        File(root, "docker").mkdirs()
        File(root, "docker/docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")

        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("## docker-compose.yml"))
        assertTrue(result.contains("version: '3'"))
    }

    @Test
    fun `scanRepoStructureDirect reads docker-compose when only docker subdirectory variant exists`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "docker").mkdirs()
        File(root, "docker/docker-compose.yml").writeText("version: '3'\nservices:\n  app:\n    image: nginx\n")

        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("## docker-compose.yml"))
        assertTrue(result.contains("services:"))
    }

    @Test
    fun `scanRepoStructureDirect walks src tree up to 50 directories`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "src").mkdirs()
        for (i in 1..60) {
            File(root, "src/dir$i").mkdirs()
        }
        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        val srcSection = result.substringAfter("## Source tree").substringBefore("## Sample PHP files")
        val dirCount = srcSection.lines().count { it.contains("/dir") }
        assertTrue(dirCount <= 50, "Expected ≤ 50 dirs, got $dirCount")
    }

    @Test
    fun `scanRepoStructureDirect picks up at most 6 sample PHP files`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        for (i in 1..10) {
            File(root, "file$i.php").writeText("<?php // $i")
        }
        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        val phpCount = result.split("### file").size - 1
        assertTrue(phpCount <= 6, "Expected ≤ 6 PHP file headers, got $phpCount")
    }

    @Test
    fun `scanRepoStructureDirect excludes vendor directories from sample files`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "vendor").mkdirs()
        File(root, "vendor/Skipped.php").writeText("<?php // in vendor")
        File(root, "Kept.php").writeText("<?php // at root")
        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("Kept.php"))
        assertTrue(!result.contains("Skipped.php"))
    }

    @Test
    fun `scanRepoStructureDirect catches IOException when reading composer json`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "composer.json").mkdirs()
        val scanner =
            RepoScanner(
                processPort = mock<ProcessPort>(),
                directReader = DirectRepoReader(),
                fileSystem = JavaFileSystemAdapter(),
                shellReaderFactory = ShellRepoReaderFactoryAdapter(),
            )
        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("(unreadable)"))
    }

    @Test
    fun `scanRepoStructureDirect handles docker-compose in src main php layout`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        File(root, "src/main/php").mkdirs()
        File(root, "src/main/php/Some.php").writeText("<?php // x")
        val result = scanner.scanRepoStructureDirect(root, root.absolutePath)
        assertTrue(result.contains("## Source tree"))
        assertTrue(result.contains("src/main/php/"))
    }

    @Test
    fun `shellRead returns null on non-zero exit code`() {
        whenever(
            processPort.run(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any<Long>(),
                org.mockito.kotlin.anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 1, stdout = "out", stderr = ""))
        assertEquals(null, scanner.shellRead("false"))
    }

    @Test
    fun `shellRead returns null when processPort throws`() {
        whenever(
            processPort.run(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any<Long>(),
                org.mockito.kotlin.anyOrNull(),
            ),
        ).thenThrow(RuntimeException("boom"))
        assertEquals(null, scanner.shellRead("anything"))
    }

    @Test
    fun `shellRead returns stdout on success`() {
        whenever(
            processPort.run(
                org.mockito.kotlin.eq(listOf("echo", "hi")),
                org.mockito.kotlin.any<Long>(),
                org.mockito.kotlin.anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 0, stdout = "hi\n", stderr = ""))
        assertEquals("hi", scanner.shellRead("echo", "hi")?.trimEnd())
    }

    @Test
    fun `shellRead falls back to stderr when stdout is blank but exit is zero`() {
        whenever(
            processPort.run(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any<Long>(),
                org.mockito.kotlin.anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 0, stdout = "", stderr = "warn-text"))
        assertEquals("warn-text", scanner.shellRead("cmd"))
    }
}
