package com.stacklens.adapter.out.git

import com.stacklens.application.port.out.PullRequestDiffRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GitDiffBuilderTest {
    private val builder = GitDiffBuilder(subprocessTimeoutSeconds = 1L)

    @Test
    fun `parseAgentPaths extracts relative paths from output`() {
        val output = "### src/App.kt\n```kotlin\nclass App {}\n```"
        val paths = GitDiffBuilder.parseAgentPaths(output)
        assertEquals(listOf("src/App.kt"), paths)
    }

    @Test
    fun `parseAgentPaths returns empty list when no file blocks present`() {
        val paths = GitDiffBuilder.parseAgentPaths("no file blocks here")
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `parseAgentPaths rejects absolute paths`() {
        val output = "### /etc/passwd\n```\nroot\n```"
        val paths = GitDiffBuilder.parseAgentPaths(output)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `parseAgentPaths extracts multiple paths`() {
        val output =
            "### src/A.kt\n```kotlin\nclass A\n```\n" +
                "### src/B.kt\n```kotlin\nclass B\n```"
        val paths = GitDiffBuilder.parseAgentPaths(output)
        assertEquals(listOf("src/A.kt", "src/B.kt"), paths)
    }

    @Test
    fun `parseAgentPaths handles empty string`() {
        assertTrue(GitDiffBuilder.parseAgentPaths("").isEmpty())
    }

    @Test
    fun `lcsDiff returns empty list for two empty inputs`() {
        assertTrue(builder.lcsDiff(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `lcsDiff returns all additions when left is empty`() {
        val result = builder.lcsDiff(emptyList(), listOf("a", "b"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.sign == "+" })
        assertEquals(listOf("a", "b"), result.map { it.text })
    }

    @Test
    fun `lcsDiff returns all deletions when right is empty`() {
        val result = builder.lcsDiff(listOf("a", "b"), emptyList())
        assertEquals(2, result.size)
        assertTrue(result.all { it.sign == "-" })
        assertEquals(listOf("a", "b"), result.map { it.text })
    }

    @Test
    fun `lcsDiff returns all context when inputs are equal`() {
        val lines = listOf("a", "b", "c")
        val result = builder.lcsDiff(lines, lines)
        assertEquals(3, result.size)
        assertTrue(result.all { it.sign == " " })
        assertEquals(lines, result.map { it.text })
    }

    @Test
    fun `lcsDiff produces correct diff for changed middle line`() {
        val a = listOf("a", "b", "c")
        val b = listOf("a", "X", "c")
        val result = builder.lcsDiff(a, b)
        val signs = result.map { it.sign }
        assertTrue(signs.contains("-"))
        assertTrue(signs.contains("+"))
        assertTrue(signs.contains(" "))
        assertEquals("a", result.first { it.sign == " " && it.text == "a" }.text)
        assertEquals("b", result.first { it.sign == "-" }.text)
        assertEquals("X", result.first { it.sign == "+" }.text)
    }

    @Test
    fun `lcsDiff adds truncation notice when left exceeds maxLines`() {
        val a = List(5_001) { "line$it" }
        val b = listOf("line0")
        val result = builder.lcsDiff(a, b)
        assertTrue(result.last().text.contains("abgeschnitten"))
    }

    @Test
    fun `lcsDiff adds truncation notice when right exceeds maxLines`() {
        val a = listOf("line0")
        val b = List(5_001) { "line$it" }
        val result = builder.lcsDiff(a, b)
        assertTrue(result.last().text.contains("abgeschnitten"))
    }

    @Test
    fun `lcsDiff DiffLine data class stores sign and text`() {
        val d = GitDiffBuilder.DiffLine("+", "new line")
        assertEquals("+", d.sign)
        assertEquals("new line", d.text)
    }

    @Test
    fun `collapseContext returns empty list for empty input`() {
        assertTrue(builder.collapseContext(emptyList()).isEmpty())
    }

    @Test
    fun `collapseContext returns unchanged list when all lines are changes`() {
        val lines =
            listOf(
                GitDiffBuilder.DiffLine("+", "added"),
                GitDiffBuilder.DiffLine("-", "removed"),
            )
        assertEquals(lines, builder.collapseContext(lines))
    }

    @Test
    fun `collapseContext preserves small context run without sentinel`() {
        val change = GitDiffBuilder.DiffLine("+", "change")
        val context = (1..5).map { GitDiffBuilder.DiffLine(" ", "ctx$it") }
        val secondChange = GitDiffBuilder.DiffLine("+", "change2")
        val lines = listOf(change) + context + listOf(secondChange)
        val result = builder.collapseContext(lines)
        assertFalse(result.any { it.sign == "~" }, "No sentinel expected for 5-line run with context=3")
    }

    @Test
    fun `collapseContext inserts sentinel for large context run in middle`() {
        val change = GitDiffBuilder.DiffLine("+", "c1")
        val context = (1..8).map { GitDiffBuilder.DiffLine(" ", "ctx$it") }
        val change2 = GitDiffBuilder.DiffLine("+", "c2")
        val lines = listOf(change) + context + listOf(change2)
        val result = builder.collapseContext(lines)
        val sentinel = result.find { it.sign == "~" }
        assertNotNull(sentinel, "Expected a sentinel line for 8-line run")
        assertTrue(sentinel!!.text.contains("2"))
    }

    @Test
    fun `collapseContext trailing context run at end of file uses keepEnd zero`() {
        val change = GitDiffBuilder.DiffLine("+", "change")
        val trailing = (1..7).map { GitDiffBuilder.DiffLine(" ", "t$it") }
        val lines = listOf(change) + trailing
        val result = builder.collapseContext(lines)
        val sentinel = result.find { it.sign == "~" }
        assertNotNull(sentinel, "Trailing run of 7 lines should produce sentinel (skip=4)")
    }

    @Test
    fun `collapseContext leading context run before first change`() {
        val leading = (1..4).map { GitDiffBuilder.DiffLine(" ", "l$it") }
        val change = GitDiffBuilder.DiffLine("+", "change")
        val lines = leading + listOf(change)
        val result = builder.collapseContext(lines, context = 3)
        assertFalse(result.any { it.sign == "~" })
        assertTrue(result.any { it.sign == "+" })
    }

    @Test
    fun `collapseContext respects custom context parameter`() {
        val change = GitDiffBuilder.DiffLine("+", "c1")
        val context = (1..10).map { GitDiffBuilder.DiffLine(" ", "ctx$it") }
        val change2 = GitDiffBuilder.DiffLine("+", "c2")
        val lines = listOf(change) + context + listOf(change2)
        val result = builder.collapseContext(lines, context = 1)
        val sentinel = result.find { it.sign == "~" }
        assertNotNull(sentinel)
        assertTrue(sentinel!!.text.contains("8"))
    }

    @Test
    fun `collapseContext FileDiff and FileStatus data classes are accessible`() {
        val diff =
            GitDiffBuilder.FileDiff(
                path = "src/X.kt",
                status = GitDiffBuilder.FileStatus.ADDED,
                additions = 5,
                deletions = 0,
                lines = listOf(GitDiffBuilder.DiffLine("+", "new")),
            )
        assertEquals("src/X.kt", diff.path)
        assertEquals(GitDiffBuilder.FileStatus.ADDED, diff.status)
        assertEquals(5, diff.additions)
        assertEquals(0, diff.deletions)
        assertEquals(1, diff.lines.size)
    }

    @Test
    fun `FileStatus enum covers all values`() {
        val values = GitDiffBuilder.FileStatus.values()
        assertTrue(values.contains(GitDiffBuilder.FileStatus.ADDED))
        assertTrue(values.contains(GitDiffBuilder.FileStatus.MODIFIED))
        assertTrue(values.contains(GitDiffBuilder.FileStatus.DELETED))
        assertTrue(values.contains(GitDiffBuilder.FileStatus.UNCHANGED))
    }

    @Test
    fun `runGit returns error result when the binary times out`() {
        val builder = GitDiffBuilder(subprocessTimeoutSeconds = 1L)
        val result = builder.runCommand("/bin/sleep", File("."), "5")
        assertEquals(-1, result.exit)
        assertEquals("", result.stdout)
    }

    @Test
    fun `runGit catches exceptions and returns error result`() {
        val builder = GitDiffBuilder()
        val result = builder.runCommand("/this/binary/does/not/exist-${System.nanoTime()}", File("."))
        assertEquals(-1, result.exit)
        assertEquals("", result.stdout)
    }

    @Test
    fun `isArtifactPathForPrefix returns true for stacklens dot folder path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix(".stacklens/foo/bar", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for phpunit cache dot folder path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix(".phpunit.cache/data", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for phpunit dot folder path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix(".phpunit/foo", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for reports path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("reports/foo.html", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for build path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("build/output.jar", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for node_modules path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("node_modules/lodash/index.js", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for vendor path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("vendor/symfony/console", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for data path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("data/cache.json", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for test-results path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("test-results/junit.xml", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for playwright-report path`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("playwright-report/index.html", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for junit_ filename prefix`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("some/dir/junit_test.xml", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for coverage dot xml filename`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("some/dir/coverage.xml", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for coverageOutput literal filename`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("some/dir/coverageOutput", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for clover literal filename suffix`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("some/dir/coverage.clover", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns true for cov filename suffix`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("some/dir/coverage.cov", ""))
    }

    @Test
    fun `isArtifactPathForPrefix returns false for regular source file`() {
        val b = GitDiffBuilder()
        assertFalse(b.isArtifactPathForPrefix("src/main.kt", ""))
    }

    @Test
    fun `isArtifactPathForPrefix strips prefix before checking`() {
        val b = GitDiffBuilder()
        assertTrue(b.isArtifactPathForPrefix("sub/build/output.jar", "sub/"))
    }

    @Test
    fun `isArtifactPathForPrefix with empty prefix does not strip`() {
        val b = GitDiffBuilder()
        assertFalse(b.isArtifactPathForPrefix("src/main.kt", ""))
    }

    private fun git(
        dir: File,
        vararg args: String,
    ): String {
        val proc =
            ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return out.trim()
    }

    private fun initGitRepo(
        dir: File,
        branch: String = "main",
    ) {
        git(dir, "init", "-q")
        git(dir, "config", "user.email", "test@example.com")
        git(dir, "config", "user.name", "Test")
        git(dir, "checkout", "-q", "-b", branch)
    }

    @Test
    fun `resolveGitContext uses base branch captured by pipeline`(
        @org.junit.jupiter.api.io.TempDir tmpDir: java.nio.file.Path,
    ) {
        val gitDir = tmpDir.toFile()
        initGitRepo(gitDir, branch = "main")
        val request =
            PullRequestDiffRequest(
                repoPath = gitDir.path,
                baseBranch = "develop",
                baseSha = "",
                preExistingUntracked = emptyList(),
                finnOutput = "",
                tessOutput = "",
                quinnOutput = "",
                veraOutput = "",
            )

        val context = GitDiffBuilder().resolveGitContext(request)

        assertNotNull(context)
        assertEquals("develop", context!!.baseBranch)
    }

    @Test
    fun `buildFileDiffForPath returns DELETED when base exists and working tree is missing`(
        @org.junit.jupiter.api.io.TempDir tmpDir: java.nio.file.Path,
    ) {
        val gitDir = tmpDir.toFile()
        initGitRepo(gitDir, branch = "main")
        File(gitDir, "Foo.kt").writeText("original line\n")
        git(gitDir, "add", ".")
        git(gitDir, "commit", "-m", "init")
        File(gitDir, "Foo.kt").delete()
        val ctx =
            GitDiffBuilder.GitContext(
                gitRoot = gitDir,
                baseBranch = "main",
                baseSha = "",
                preExistingUntracked = emptySet(),
                agentWrittenPaths = emptySet(),
                repoSubpathPrefix = "",
            )
        val diff = GitDiffBuilder().buildFileDiffForPath("Foo.kt", ctx)
        assertEquals(GitDiffBuilder.FileStatus.DELETED, diff.status)
        assertEquals(1, diff.deletions)
    }

    @Test
    fun `buildFileDiffForPath returns UNCHANGED when neither base nor work exist`(
        @org.junit.jupiter.api.io.TempDir tmpDir: java.nio.file.Path,
    ) {
        val gitDir = tmpDir.toFile()
        initGitRepo(gitDir, branch = "main")
        val ctx =
            GitDiffBuilder.GitContext(
                gitRoot = gitDir,
                baseBranch = "main",
                baseSha = "",
                preExistingUntracked = emptySet(),
                agentWrittenPaths = emptySet(),
                repoSubpathPrefix = "",
            )
        val diff = GitDiffBuilder().buildFileDiffForPath("Ghost.kt", ctx)
        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, diff.status)
        assertEquals(0, diff.additions)
        assertEquals(0, diff.deletions)
    }

    @Test
    fun `buildFileDiffForPath returns DELETED for empty base content`(
        @org.junit.jupiter.api.io.TempDir tmpDir: java.nio.file.Path,
    ) {
        val gitDir = tmpDir.toFile()
        initGitRepo(gitDir, branch = "main")
        File(gitDir, "Empty.kt").writeText("")
        git(gitDir, "add", ".")
        git(gitDir, "commit", "-m", "init")
        File(gitDir, "Empty.kt").delete()
        val ctx =
            GitDiffBuilder.GitContext(
                gitRoot = gitDir,
                baseBranch = "main",
                baseSha = "",
                preExistingUntracked = emptySet(),
                agentWrittenPaths = emptySet(),
                repoSubpathPrefix = "",
            )
        val diff = GitDiffBuilder().buildFileDiffForPath("Empty.kt", ctx)
        assertEquals(GitDiffBuilder.FileStatus.DELETED, diff.status)
    }
}
