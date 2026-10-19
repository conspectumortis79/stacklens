package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Path

class RepoFileMarkdownExtractorPathCoverageTest {
    private val log = LoggerFactory.getLogger("repo-file-markdown-extractor-coverage")

    @Test
    fun `a file name with a space and no directory separator is skipped`(
        @TempDir repo: Path,
    ) {
        val response = "## a b.java\n```java\nclass A {}\n```\n"

        assertEquals(0, RepoFileMarkdownExtractor.extract(response, repo.toFile(), log))
    }

    @Test
    fun `a spaced path that carries a directory separator is written`(
        @TempDir repo: Path,
    ) {
        val response = "## src/a b.java\n```java\nclass A {}\n```\n"

        assertEquals(1, RepoFileMarkdownExtractor.extract(response, repo.toFile(), log))
    }

    @Test
    fun `a status marker in front of the file name is skipped`(
        @TempDir repo: Path,
    ) {
        val response = "\u2705 src/Ok.java\n```java\nclass A {}\n```\n"

        assertEquals(0, RepoFileMarkdownExtractor.extract("## " + response, repo.toFile(), log))
    }
}
