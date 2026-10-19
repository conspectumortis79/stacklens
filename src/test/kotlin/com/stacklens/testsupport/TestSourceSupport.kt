package com.stacklens.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object TestSourceSupport {
    private val projectRoots: List<Path> =
        listOf(
            Paths.get("").toAbsolutePath().normalize(),
            Paths.get("..").toAbsolutePath().normalize(),
        ).distinct()

    fun readSource(relativePath: String): String = Files.readString(resolveSource(relativePath))

    fun readSources(vararg relativePaths: String): String = relativePaths.joinToString("\n") { readSource(it) }

    fun resolveSource(relativePath: String): Path =
        projectRoots
            .asSequence()
            .map { it.resolve(relativePath).normalize() }
            .firstOrNull { Files.exists(it) }
            ?: error("Source file not found: $relativePath")

    fun functionNames(source: String): Set<String> =
        Regex("""\bfun\s+(?:[A-Za-z0-9_.<>?]+\s*\.\s*)?([A-Za-z0-9_]+)\s*\(""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

    fun containsAll(
        source: String,
        vararg tokens: String,
    ): Boolean = tokens.all { source.contains(it) }

    fun containsAny(
        source: String,
        vararg tokens: String,
    ): Boolean = tokens.any { source.contains(it) }
}
