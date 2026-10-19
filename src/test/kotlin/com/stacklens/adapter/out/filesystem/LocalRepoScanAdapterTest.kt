package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LocalRepoScanAdapterTest {
    private val adapter = LocalRepoScanAdapter()

    @TempDir
    lateinit var tempDir: Path

    private fun root(): File = tempDir.toFile()

    @Test
    fun `isPipelineFile matches common pipeline filenames`() {
        assertTrue(RepoScanRules().isPipelineFile("Dockerfile"))
        assertTrue(RepoScanRules().isPipelineFile("docker-compose.yml"))
        assertTrue(RepoScanRules().isPipelineFile(".gitlab-ci.yml"))
        assertTrue(RepoScanRules().isPipelineFile("Jenkinsfile"))
        assertTrue(RepoScanRules().isPipelineFile("bitbucket-pipelines.yml"))
    }

    @Test
    fun `isPipelineFile matches dockerfile and docker-compose prefixes`() {
        assertTrue(RepoScanRules().isPipelineFile("Dockerfile.dev"))
        assertTrue(RepoScanRules().isPipelineFile("docker-compose.override.yml"))
        assertTrue(RepoScanRules().isPipelineFile("compose.yaml"))
    }

    @Test
    fun `isPipelineFile returns false for unrelated names`() {
        assertFalse(RepoScanRules().isPipelineFile("README.md"))
        assertFalse(RepoScanRules().isPipelineFile("Foo.kt"))
    }

    @Test
    fun `isPipelineDirectory matches github circleci devcontainer`() {
        assertTrue(RepoScanRules().isPipelineDirectory(".github"))
        assertTrue(RepoScanRules().isPipelineDirectory(".circleci"))
        assertTrue(RepoScanRules().isPipelineDirectory(".devcontainer"))
        assertTrue(RepoScanRules().isPipelineDirectory(".GITHUB"))
        assertFalse(RepoScanRules().isPipelineDirectory("node_modules"))
    }

    @Test
    fun `isPriorityPipelineFile matches common root-level pipeline files`() {
        assertTrue(RepoScanRules().isPriorityPipelineFile(File("docker-compose.yml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File(".gitlab-ci.yml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File("Jenkinsfile")))
        assertFalse(RepoScanRules().isPriorityPipelineFile(File("Dockerfile")))
    }

    @Test
    fun `listFilesOrEmpty returns the children of a directory`() {
        root().resolve("a.txt").writeText("x")
        root().resolve("b.txt").writeText("y")
        val files = adapter.listFilesOrEmptyForTest(root())
        assertEquals(2, files.size)
    }

    @Test
    fun `listFilesOrEmpty returns an empty array for a missing directory`() {
        val files = adapter.listFilesOrEmptyForTest(File("/does/not/exist/${System.nanoTime()}"))
        assertEquals(0, files.size)
    }

    @Test
    fun `buildCodeSummary throws when the path is not a directory`() {
        assertThrows<IllegalArgumentException> {
            adapter.buildCodeSummary("/does/not/exist/${System.nanoTime()}", 10, 50)
        }
    }

    @Test
    fun `buildCodeSummary returns a CodeSummary for a tiny repo`() {
        val r = root()
        r.resolve("Foo.kt").writeText("class Foo")
        r.resolve("README.md").writeText("hi")
        val summary = adapter.buildCodeSummary(r.path, maxFiles = 10, maxPerFileSizeKb = 50)
        assertTrue(summary.projectTree.contains("Foo.kt"))
        assertTrue(summary.fileContents.contains("class Foo"))
        assertEquals(2, summary.fileCount)
    }

    @Test
    fun `buildPipelineSummary returns empty when no pipeline files exist`() {
        val r = root()
        r.resolve("Foo.kt").writeText("class Foo")
        val summary = adapter.buildPipelineSummary(r.path, maxFiles = 10, maxPerFileSizeKb = 50)
        assertEquals(0, summary.fileCount)
        assertEquals("", summary.fileContents)
    }

    @Test
    fun `buildPipelineSummary returns content for a docker-compose file`() {
        val r = root()
        r.resolve("docker-compose.yml").writeText("services:\n  web:\n    image: nginx")
        val summary = adapter.buildPipelineSummary(r.path, maxFiles = 10, maxPerFileSizeKb = 50)
        assertTrue(summary.fileCount >= 1)
        assertTrue(summary.fileContents.contains("nginx"))
    }

    @Test
    fun `buildPipelineSummary walks into dot-directories like github workflows`() {
        val r = root()
        val wf = r.resolve(".github/workflows/ci.yml")
        wf.parentFile.mkdirs()
        wf.writeText("name: CI")
        val summary = adapter.buildPipelineSummary(r.path, maxFiles = 10, maxPerFileSizeKb = 50)
        assertTrue(summary.fileCount >= 1)
        assertTrue(summary.fileContents.contains("name: CI"))
    }

    @Test
    fun `buildPipelineSummary walks into dot-circleci`() {
        val r = root()
        val cfg = r.resolve(".circleci/config.yml")
        cfg.parentFile.mkdirs()
        cfg.writeText("version: 2.1")
        val summary = adapter.buildPipelineSummary(r.path, maxFiles = 10, maxPerFileSizeKb = 50)
        assertTrue(summary.fileCount >= 1)
        assertTrue(summary.fileContents.contains("version: 2.1"))
    }

    @Test
    fun `buildPipelineSummary throws when the path is not a directory`() {
        assertThrows<IllegalArgumentException> {
            adapter.buildPipelineSummary("/does/not/exist/${System.nanoTime()}", 10, 50)
        }
    }

    @Test
    fun `searchKeyword finds matching lines with context`() {
        val r = root()
        r.resolve("Foo.kt").writeText("line1\nline2 foo\nline3\nline4")
        val hits = adapter.searchKeyword(r.path, "foo", maxHits = 10)
        assertEquals(1, hits.size)
        assertEquals("Foo.kt", hits[0].filePath)
        assertEquals(2, hits[0].lineNumber)
        assertTrue(hits[0].context.contains("line1"))
    }

    @Test
    fun `searchKeyword is case-insensitive`() {
        val r = root()
        r.resolve("Foo.kt").writeText("FOO")
        val hits = adapter.searchKeyword(r.path, "foo", maxHits = 10)
        assertEquals(1, hits.size)
    }

    @Test
    fun `searchKeyword returns up to maxHits hits`() {
        val r = root()
        r.resolve("Foo.kt").writeText("foo\nfoo\nfoo\nfoo\nfoo")
        val hits = adapter.searchKeyword(r.path, "foo", maxHits = 2)
        assertEquals(2, hits.size)
    }

    @Test
    fun `searchKeyword returns an empty list when no file contains the keyword`() {
        val r = root()
        r.resolve("Foo.kt").writeText("nothing here")
        val hits = adapter.searchKeyword(r.path, "bar", maxHits = 10)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `searchKeyword skips binary files (file with a read error)`() {
        val r = root()
        val f = r.resolve("Foo.kt")
        f.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
        val hits = adapter.searchKeyword(r.path, "foo", maxHits = 10)
        assertTrue(hits.isEmpty())
    }
}
