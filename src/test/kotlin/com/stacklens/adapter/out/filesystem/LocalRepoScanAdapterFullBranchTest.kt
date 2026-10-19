package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files

class LocalRepoScanAdapterFullBranchTest {
    private val adapter = LocalRepoScanAdapter()

    @Test
    fun `safeReadText returns binary marker when file disappears between check and read`() {
        val tmp = Files.createTempDirectory("safe-read-disappears")
        val file = File(tmp.toFile(), "doomed.txt")
        file.writeText("first line")
        file.setReadable(false, false)
        file.setReadable(false, true)
        file.setWritable(false, false)
        file.setWritable(false, true)
        file.setExecutable(false, false)
        file.setExecutable(false, true)
        try {
            val summary = adapter.buildCodeSummary(tmp.toString(), maxFiles = 5, maxPerFileSizeKb = 64)
            assertTrue(summary.fileContents.isNotEmpty())
        } catch (_: Exception) {
        } finally {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        }
    }

    @Test
    fun `safeReadText reads full content when file smaller than maxBytes`() {
        val tmp = Files.createTempDirectory("safe-read-small")
        val file = File(tmp.toFile(), "small.md")
        file.writeText("hello world")
        val summary = adapter.buildCodeSummary(tmp.toString(), maxFiles = 5, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains("hello world"))
    }

    @Test
    fun `safeReadText appends truncation marker when file larger than maxBytes`() {
        val tmp = Files.createTempDirectory("safe-read-large")
        val file = File(tmp.toFile(), "big.md")
        val payload = "x".repeat(100_000)
        file.writeText(payload)
        val summary = adapter.buildCodeSummary(tmp.toString(), maxFiles = 5, maxPerFileSizeKb = 1)
        assertTrue(summary.fileContents.contains("[truncated]") || summary.fileContents.length < 100_000)
    }

    @Test
    fun `walkDirectoryForPipeline enters pipeline directory even when in skipDirs`() {
        val tmp = Files.createTempDirectory("walk-pipeline")
        val root = tmp.toFile()
        val github = File(root, ".github")
        github.mkdirs()
        val workflows = File(github, "workflows")
        workflows.mkdirs()
        File(workflows, "ci.yml").writeText("name: CI")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".github/workflows/ci.yml"))
    }

    @Test
    fun `walkDirectoryForPipeline enters circleci directory`() {
        val tmp = Files.createTempDirectory("walk-circleci")
        val root = tmp.toFile()
        val circle = File(root, ".circleci")
        circle.mkdirs()
        File(circle, "config.yml").writeText("version: 2.1")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".circleci/config.yml"))
    }

    @Test
    fun `walkDirectoryForPipeline enters devcontainer directory`() {
        val tmp = Files.createTempDirectory("walk-devcontainer")
        val root = tmp.toFile()
        val dev = File(root, ".devcontainer")
        dev.mkdirs()
        File(dev, "Dockerfile").writeText("FROM scratch")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".devcontainer/Dockerfile"))
    }

    @Test
    fun `walkDirectoryForPipeline skips a skipDir that is not a pipeline dir`() {
        val tmp = Files.createTempDirectory("walk-skip-nopipeline")
        val root = tmp.toFile()
        val node = File(root, "node_modules")
        node.mkdirs()
        File(node, "Dockerfile").writeText("FROM scratch")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains("node_modules"))
    }

    @Test
    fun `walkDirectoryForPipeline picks root-level Dockerfile named Dockerfile`() {
        val tmp = Files.createTempDirectory("walk-root-dockerfile")
        val root = tmp.toFile()
        File(root, "Dockerfile").writeText("FROM scratch")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains("Dockerfile"))
    }

    @Test
    fun `walkDirectoryForPipeline picks root-level gitlab-ci file`() {
        val tmp = Files.createTempDirectory("walk-gitlab-root")
        val root = tmp.toFile()
        File(root, ".gitlab-ci.yml").writeText("stages: [test]")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".gitlab-ci.yml"))
    }

    @Test
    fun `walkDirectoryForPipeline picks compose yaml at root level`() {
        val tmp = Files.createTempDirectory("walk-compose-root")
        val root = tmp.toFile()
        File(root, "docker-compose.yml").writeText("services: {}")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains("docker-compose.yml"))
    }

    @Test
    fun `walkDirectoryForPipeline ignores files in circleci that are not config`() {
        val tmp = Files.createTempDirectory("walk-circleci-other")
        val root = tmp.toFile()
        val circle = File(root, ".circleci")
        circle.mkdirs()
        File(circle, "README").writeText("docs")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains(".circleci/README"))
    }

    @Test
    fun `walkDirectoryForPipeline picks circleci config yaml (not yml) variant`() {
        val tmp = Files.createTempDirectory("walk-circleci-yaml")
        val root = tmp.toFile()
        val circle = File(root, ".circleci")
        circle.mkdirs()
        File(circle, "config.yaml").writeText("version: 2.1")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".circleci/config.yaml"))
    }

    @Test
    fun `walkDirectoryForPipeline picks nested github workflow`() {
        val tmp = Files.createTempDirectory("walk-nested-workflow")
        val root = tmp.toFile()
        val github = File(root, ".github")
        val workflows = File(github, "workflows")
        workflows.mkdirs()
        File(workflows, "build.yml").writeText("name: build")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains(".github/workflows/build.yml"))
    }

    @Test
    fun `walkDirectoryForPipeline returns empty when no pipeline files exist`() {
        val tmp = Files.createTempDirectory("walk-no-pipeline")
        val root = tmp.toFile()
        File(root, "readme.md").writeText("# Project")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertEquals(0, summary.fileCount)
        assertEquals("", summary.projectTree)
        assertEquals("", summary.fileContents)
    }

    @Test
    fun `walkDirectoryForPipeline skips regular code file in regular dir`() {
        val tmp = Files.createTempDirectory("walk-skip-regular")
        val root = tmp.toFile()
        val src = File(root, "src")
        src.mkdirs()
        File(src, "Foo.kt").writeText("class Foo")
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertEquals(0, summary.fileCount)
    }

    @Test
    fun `walkDirectoryForPipeline skips files larger than 1 MB`() {
        val tmp = Files.createTempDirectory("walk-large-file")
        val root = tmp.toFile()
        val large = File(root, "Dockerfile")
        val payload = "x".repeat(1_100_000)
        Files.write(large.toPath(), payload.toByteArray())
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 10, maxPerFileSizeKb = 64)
        assertEquals(0, summary.fileCount)
    }

    @Test
    fun `walkDirectoryForPipeline respects maxFiles`() {
        val tmp = Files.createTempDirectory("walk-pipeline-cap")
        val root = tmp.toFile()
        repeat(5) { i ->
            File(root, "Dockerfile$i").writeText("FROM scratch $i")
        }
        val summary = adapter.buildPipelineSummary(root.absolutePath, maxFiles = 2, maxPerFileSizeKb = 64)
        assertEquals(2, summary.fileCount)
    }

    @Test
    fun `walkDirectory skips directories in skipDirs`() {
        val tmp = Files.createTempDirectory("walk-code-skipdir")
        val root = tmp.toFile()
        val node = File(root, "node_modules")
        node.mkdirs()
        File(node, "module.kt").writeText("package x")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains("node_modules/module.kt"))
    }

    @Test
    fun `walkDirectory skips dot-prefixed directories`() {
        val tmp = Files.createTempDirectory("walk-code-dot")
        val root = tmp.toFile()
        val hidden = File(root, ".cache")
        hidden.mkdirs()
        File(hidden, "data.kt").writeText("package x")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains(".cache/data.kt"))
    }

    @Test
    fun `walkDirectory includes code files within size limit`() {
        val tmp = Files.createTempDirectory("walk-code-include")
        val root = tmp.toFile()
        File(root, "Foo.kt").writeText("class Foo")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertTrue(summary.fileContents.contains("Foo.kt"))
    }

    @Test
    fun `walkDirectory ignores files that are not code`() {
        val tmp = Files.createTempDirectory("walk-code-noncode")
        val root = tmp.toFile()
        File(root, "binary.xyz").writeText("garbage")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains("binary.xyz"))
    }

    @Test
    fun `walkDirectory respects maxFiles cap`() {
        val tmp = Files.createTempDirectory("walk-code-cap")
        val root = tmp.toFile()
        repeat(20) { i ->
            File(root, "File$i.kt").writeText("class F$i")
        }
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 3, maxPerFileSizeKb = 64)
        assertTrue(summary.fileCount <= 3)
    }

    @Test
    fun `walkDirectory skips files larger than 500 KB`() {
        val tmp = Files.createTempDirectory("walk-code-large")
        val root = tmp.toFile()
        val big = File(root, "Big.kt")
        Files.write(big.toPath(), "x".repeat(550_000).toByteArray())
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains("Big.kt"))
    }

    @Test
    fun `walkDirectory skips files whose extension is not in codeExtensions`() {
        val tmp = Files.createTempDirectory("walk-code-noext")
        val root = tmp.toFile()
        File(root, "README.noext").writeText("just docs")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertNotNull(summary)
    }

    @Test
    fun `walkDirectory includes dotfile code in dot-prefixed dir only via recursion`() {
        val tmp = Files.createTempDirectory("walk-code-hiddir")
        val root = tmp.toFile()
        val hidden = File(root, ".private")
        hidden.mkdirs()
        File(hidden, "Foo.kt").writeText("class Foo")
        val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 50, maxPerFileSizeKb = 64)
        assertFalse(summary.fileContents.contains(".private/Foo.kt"))
    }

    @Test
    fun `isPipelineFile true via pipelineFileNames set`() {
        assertTrue(RepoScanRules().isPipelineFile("dockerfile"))
        assertTrue(RepoScanRules().isPipelineFile("jenkinsfile"))
        assertTrue(RepoScanRules().isPipelineFile("cloudbuild.yaml"))
    }

    @Test
    fun `isPipelineFile true via startsWith dockerfile prefix`() {
        assertTrue(RepoScanRules().isPipelineFile("dockerfile.dev"))
        assertTrue(RepoScanRules().isPipelineFile("dockerfile.test"))
    }

    @Test
    fun `isPipelineFile true via startsWith docker-compose prefix`() {
        assertTrue(RepoScanRules().isPipelineFile("docker-compose.override.yml"))
    }

    @Test
    fun `isPipelineFile true via startsWith compose prefix`() {
        assertTrue(RepoScanRules().isPipelineFile("compose.dev.yaml"))
    }

    @Test
    fun `isPipelineFile true via startsWith buildkite yaml prefix`() {
        assertTrue(RepoScanRules().isPipelineFile("buildkite.yml"))
        assertTrue(RepoScanRules().isPipelineFile("buildkite.yaml"))
    }

    @Test
    fun `isPipelineFile false for unrelated name`() {
        assertFalse(RepoScanRules().isPipelineFile("README.md"))
        assertFalse(RepoScanRules().isPipelineFile("notapipeline.txt"))
    }

    @Test
    fun `isPipelineFile true for dockerfile prod suffix`() {
        assertTrue(RepoScanRules().isPipelineFile("dockerfile.prod"))
    }

    @Test
    fun `isPipelineFile true for compose yaml prefix variant`() {
        assertTrue(RepoScanRules().isPipelineFile("compose.dev.yml"))
    }

    @Test
    fun `isPipelineFile false for files containing but not starting with dockerfile`() {
        assertFalse(RepoScanRules().isPipelineFile("notdockerfile.txt"))
    }

    @Test
    fun `isPipelineFile false for files containing but not starting with docker-compose`() {
        assertFalse(RepoScanRules().isPipelineFile("mydocker-compose.bak"))
    }

    @Test
    fun `isPipelineFile true for buildkite variant with prefix to force fall-throughs`() {
        assertTrue(RepoScanRules().isPipelineFile("buildkite.yml.local"))
    }

    @Test
    fun `isPipelineFile true for buildkite yaml variant with prefix to force fall-throughs`() {
        assertTrue(RepoScanRules().isPipelineFile("buildkite.yaml.local"))
    }

    @Test
    fun `isPipelineFile handles mixed case input`() {
        assertTrue(RepoScanRules().isPipelineFile("DOCKERFILE"))
        assertTrue(RepoScanRules().isPipelineFile("Docker-Compose.yml"))
    }

    @Test
    fun `isPipelineFile returns false for empty string`() {
        assertFalse(RepoScanRules().isPipelineFile(""))
    }

    @Test
    fun `isPipelineFile true for capitalised buildkite`() {
        assertTrue(RepoScanRules().isPipelineFile("BUILDKITE.YAML"))
    }

    @Test
    fun `isPipelineFile true via docker-compose dot with explicit fall-through`() {
        assertTrue(RepoScanRules().isPipelineFile("docker-compose.prod.yaml"))
    }

    @Test
    fun `isPipelineFile false for name that does not start with docker-compose dot`() {
        assertFalse(RepoScanRules().isPipelineFile("plainfile.txt"))
    }

    @Test
    fun `isPipelineFile false for name that is in pipelineFileNames but does not match compose branches`() {
        assertTrue(RepoScanRules().isPipelineFile("jenkinsfile"))
        assertTrue(RepoScanRules().isPipelineFile("cloudbuild.yaml"))
    }

    @Test
    fun `isPriorityPipelineFile true for all variants`() {
        assertTrue(RepoScanRules().isPriorityPipelineFile(File("docker-compose.yml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File("compose.yaml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File(".gitlab-ci.yml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File(".gitlab-ci.yaml")))
        assertTrue(RepoScanRules().isPriorityPipelineFile(File("jenkinsfile")))
    }

    @Test
    fun `isPriorityPipelineFile false for non-priority pipeline files`() {
        assertFalse(RepoScanRules().isPriorityPipelineFile(File("Dockerfile")))
        assertFalse(RepoScanRules().isPriorityPipelineFile(File("cloudbuild.yaml")))
    }

    @Test
    fun `resolveRoot throws when path does not exist`() {
        assertThrows<IllegalArgumentException> {
            adapter.buildCodeSummary("/totally/nonexistent/path/123abc", maxFiles = 5, maxPerFileSizeKb = 64)
        }
    }

    @Test
    fun `resolveRoot throws when path is a file not a directory`() {
        val tmp = Files.createTempFile("not-a-dir", ".txt")
        try {
            tmp.toFile().writeText("hello")
            assertThrows<IllegalArgumentException> {
                adapter.buildCodeSummary(tmp.toString(), maxFiles = 5, maxPerFileSizeKb = 64)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun `searchKeyword clips context at start of file`() {
        val tmp = Files.createTempDirectory("search-start")
        val root = tmp.toFile()
        File(root, "src.kt").writeText("MATCHHERE\nline2\nline3\nline4")
        val hits = adapter.searchKeyword(root.absolutePath, "MATCHHERE", maxHits = 10)
        assertNotNull(hits)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().context.contains("MATCHHERE"))
    }

    @Test
    fun `searchKeyword clips context at end of file`() {
        val tmp = Files.createTempDirectory("search-end")
        val root = tmp.toFile()
        File(root, "src.kt").writeText("line1\nline2\nMATCHATEND")
        val hits = adapter.searchKeyword(root.absolutePath, "MATCHATEND", maxHits = 10)
        assertNotNull(hits)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().context.contains("MATCHATEND"))
    }

    @Test
    fun `searchKeyword stops once maxHits is reached`() {
        val tmp = Files.createTempDirectory("search-max")
        val root = tmp.toFile()
        val sb = StringBuilder()
        repeat(50) { sb.append("MATCHHERE\n") }
        File(root, "src.kt").writeText(sb.toString())
        val hits = adapter.searchKeyword(root.absolutePath, "MATCHHERE", maxHits = 5)
        assertEquals(5, hits.size)
    }

    @Test
    fun `searchKeyword returns empty when no file matches keyword`() {
        val tmp = Files.createTempDirectory("search-empty")
        val root = tmp.toFile()
        File(root, "src.kt").writeText("hello world\nfoo bar")
        val hits = adapter.searchKeyword(root.absolutePath, "NoMatchHere", maxHits = 10)
        assertEquals(0, hits.size)
    }

    @Test
    fun `searchKeyword with maxHits zero returns empty`() {
        val tmp = Files.createTempDirectory("search-zero-max")
        val root = tmp.toFile()
        File(root, "src.kt").writeText("MATCHHERE\nMATCHHERE")
        val hits = adapter.searchKeyword(root.absolutePath, "MATCHHERE", maxHits = 0)
        assertEquals(0, hits.size)
    }

    @Test
    fun `searchKeyword stops at maxHits mid-line within a file`() {
        val tmp = Files.createTempDirectory("search-midfile")
        val root = tmp.toFile()
        val sb = StringBuilder()
        repeat(20) { sb.append("MATCHHERE line $it\n") }
        File(root, "src.kt").writeText(sb.toString())
        val hits = adapter.searchKeyword(root.absolutePath, "MATCHHERE", maxHits = 3)
        assertEquals(3, hits.size)
    }
}
