package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AgentContextHelpersSniffPathsTest {
    @Test
    fun `sniffed paths below the repository are reported relative to it`(
        @TempDir repo: Path,
    ) {
        val source = Files.createFile(repo.resolve("App.kt"))

        val paths = AgentContextHelpers.extractSniffAffectedPaths("FILE: $source (source: standard)", repo.toFile(), JavaFileSystemAdapter())

        assertEquals(listOf("App.kt"), paths)
    }

    @Test
    fun `a sniffed path that cannot be canonicalised is dropped`(
        @TempDir repo: Path,
    ) {
        val unreachable = "$repo/app\u0000kt"

        val paths = AgentContextHelpers.extractSniffAffectedPaths("FILE: $unreachable", repo.toFile(), JavaFileSystemAdapter())

        assertEquals(emptyList<String>(), paths)
    }
}
