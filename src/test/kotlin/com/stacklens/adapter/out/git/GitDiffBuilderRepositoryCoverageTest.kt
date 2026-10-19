package com.stacklens.adapter.out.git

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.PullRequestDiffRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitDiffBuilderRepositoryCoverageTest {
    private val builder = GitDiffBuilder(subprocessTimeoutSeconds = 60)
    private val root: File = Files.createTempDirectory("stacklens-git-diff").toFile().canonicalFile
    private val plainDir: File = Files.createTempDirectory("stacklens-git-plain").toFile().canonicalFile
    private val masterRepo: File = Files.createTempDirectory("stacklens-git-master").toFile().canonicalFile
    private val topicRepo: File = Files.createTempDirectory("stacklens-git-topic").toFile().canonicalFile
    private val emptyRepo: File = Files.createTempDirectory("stacklens-git-empty").toFile().canonicalFile

    @BeforeAll
    fun buildRepositories() {
        initRepo(root, "main")
        write(root, "kept.txt", "one\ntwo\nthree\n")
        write(root, "removed.txt", "a\nb\n")
        write(root, "empty.txt", "")
        git(root, "add", "kept.txt", "removed.txt", "empty.txt")
        git(root, "commit", "-q", "-m", "baseline")
        File(root, "removed.txt").delete()
        File(root, "empty.txt").delete()
        write(root, "kept.txt", "one\nTWO\nthree\nextra\n")
        write(root, "added.txt", "brand new")
        write(root, "pre-existing.txt", "was already there\n")
        write(root, "build/generated.txt", "artifact\n")
        write(root, "junit_summary.xml", "artifact\n")
        write(root, "sub/source.txt", "sub content\n")

        initRepo(masterRepo, "master")
        write(masterRepo, "file.txt", "content\n")
        git(masterRepo, "add", "file.txt")
        git(masterRepo, "commit", "-q", "-m", "first")

        initRepo(topicRepo, "trunk")
        write(topicRepo, "file.txt", "content\n")
        git(topicRepo, "add", "file.txt")
        git(topicRepo, "commit", "-q", "-m", "first")

        initRepo(emptyRepo, "main")
    }

    private fun initRepo(
        dir: File,
        branch: String,
    ) {
        git(dir, "init", "-q", "-b", branch)
        git(dir, "config", "user.email", "coverage@example.com")
        git(dir, "config", "user.name", "Coverage Test")
    }

    private fun git(
        dir: File,
        vararg args: String,
    ) = builder.runGit(dir, *args)

    private fun write(
        dir: File,
        path: String,
        content: String,
    ) {
        val file = File(dir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun context(
        gitRoot: File = root,
        baseBranch: String = "main",
        baseSha: String = "",
        prefix: String = "",
        preExisting: Set<String> = emptySet(),
        agentWritten: Set<String> = emptySet(),
    ) = GitDiffBuilder.GitContext(
        gitRoot = gitRoot,
        baseBranch = baseBranch,
        baseSha = baseSha,
        preExistingUntracked = preExisting,
        agentWrittenPaths = agentWritten,
        repoSubpathPrefix = prefix,
    )

    @Test
    fun `committed changes are collected and artifacts and foreign untracked files are dropped`() {
        val paths =
            builder.listChangedPaths(
                context(
                    preExisting = setOf("pre-existing.txt"),
                    agentWritten = setOf("pre-existing.txt"),
                ),
            )

        assertEquals(
            listOf("added.txt", "empty.txt", "kept.txt", "pre-existing.txt", "removed.txt", "sub/source.txt"),
            paths,
        )
    }

    @Test
    fun `untracked files that the agent did not write stay out of the change list`() {
        val paths = builder.listChangedPaths(context(preExisting = setOf("added.txt", "pre-existing.txt")))

        assertEquals(listOf("empty.txt", "kept.txt", "removed.txt", "sub/source.txt"), paths)
    }

    @Test
    fun `a commit sha can replace the base branch as diff source`() {
        val sha = git(root, "rev-parse", "HEAD").stdout.trim()

        val paths = builder.listChangedPaths(context(baseSha = sha))

        assertTrue("added.txt" in paths)
    }

    @Test
    fun `only files below the repository subpath are kept`() {
        val paths = builder.listChangedPaths(context(prefix = "sub/"))

        assertEquals(listOf("sub/source.txt"), paths)
    }

    @Test
    fun `a directory without a repository yields no changed paths`() {
        assertEquals(emptyList<String>(), builder.listChangedPaths(context(gitRoot = plainDir, baseBranch = "main")))
    }

    @Test
    fun `a deleted file is reported with every base line as removal`() {
        val diff = builder.buildFileDiffForPath("removed.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.DELETED, diff.status)
        assertEquals(2, diff.deletions)
        assertEquals(listOf("-", "-"), diff.lines.map { it.sign })
    }

    @Test
    fun `a deleted empty file has no removal lines`() {
        val diff = builder.buildFileDiffForPath("empty.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.DELETED, diff.status)
        assertEquals(0, diff.deletions)
    }

    @Test
    fun `a new file is reported as addition`() {
        val diff = builder.buildFileDiffForPath("added.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.ADDED, diff.status)
        assertEquals(1, diff.additions)
    }

    @Test
    fun `a modified file is reported with additions and deletions`() {
        val diff = builder.buildFileDiffForPath("kept.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.MODIFIED, diff.status)
        assertTrue(diff.additions > 0)
        assertTrue(diff.deletions > 0)
    }

    @Test
    fun `a file that exists in neither tree is unchanged`() {
        val diff = builder.buildFileDiffForPath("ghost.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, diff.status)
        assertTrue(diff.lines.isEmpty())
    }

    @Test
    fun `a path escaping the repository is treated as absent`() {
        val diff = builder.buildFileDiffForPath("../outside.txt", context())

        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, diff.status)
    }

    @Test
    fun `a path whose canonicalisation fails is treated as absent`() {
        val link = File(plainDir, "loop-link.txt").toPath()
        runCatching { Files.createSymbolicLink(link, link) }

        val diff = builder.buildFileDiffForPath("loop-link.txt", context(gitRoot = plainDir))

        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, diff.status)
    }

    @Test
    fun `the git context is resolved for a repository root without an explicit base branch`() {
        val ctx = builder.resolveGitContext(request(repoPath = root.path, baseBranch = ""))

        assertNotNull(ctx)
        assertEquals("main", ctx?.baseBranch)
        assertEquals("", ctx?.repoSubpathPrefix)
    }

    @Test
    fun `a subdirectory becomes the repository subpath of the context`() {
        val ctx = builder.resolveGitContext(request(repoPath = File(root, "sub").path, baseBranch = "main"))

        assertEquals("sub/", ctx?.repoSubpathPrefix)
    }

    @Test
    fun `the master branch is used as base when no main branch exists`() {
        val ctx = builder.resolveGitContext(request(repoPath = masterRepo.path, baseBranch = ""))

        assertEquals("master", ctx?.baseBranch)
    }

    @Test
    fun `the checked out branch becomes the base when neither main nor master exist`() {
        val ctx = builder.resolveGitContext(request(repoPath = topicRepo.path, baseBranch = ""))

        assertEquals("trunk", ctx?.baseBranch)
    }

    @Test
    fun `a repository without any commit cannot provide a base branch`() {
        assertNull(builder.resolveGitContext(request(repoPath = emptyRepo.path, baseBranch = "")))
    }

    @Test
    fun `a blank repository path is rejected`() {
        assertNull(builder.resolveGitContext(request(repoPath = "  ", baseBranch = "main")))
        assertNull(builder.resolveGitContext(request(repoPath = null, baseBranch = "main")))
    }

    @Test
    fun `a missing directory is rejected`() {
        assertNull(builder.resolveGitContext(request(repoPath = File(root, "does-not-exist").path, baseBranch = "main")))
    }

    @Test
    fun `a directory without git metadata is rejected`() {
        assertNull(builder.resolveGitContext(request(repoPath = plainDir.path, baseBranch = "main")))
    }

    @Test
    fun `diffs are computed end to end for a resolved repository`() {
        val result = builder.computeDiffs(request(repoPath = root.path, baseBranch = "main"))

        assertNotNull(result)
        assertEquals("main", result?.baseBranch)
        assertTrue(result!!.diffs.isNotEmpty())
    }

    @Test
    fun `diff computation is skipped when the repository cannot be resolved`() {
        assertNull(builder.computeDiffs(request(repoPath = plainDir.path, baseBranch = "main")))
    }

    @Test
    fun `a subprocess that outruns the reader limit is aborted`() {
        val big = File(plainDir, "huge.txt")
        big.writeText("x".repeat(9 * 1024 * 1024))

        val result = GitDiffBuilder(subprocessTimeoutSeconds = 2).runCommand("/bin/cat", plainDir, "huge.txt")

        assertNotEquals(0, result.exit)
        assertTrue(result.stdout.length < big.length())
        big.delete()
    }

    private fun run(
        repoPath: String?,
        baseBranch: String,
    ): AgentPipelineRun {
        val run =
            AgentPipelineRun(
                id = 7L,
                rawText = "raw pipeline text",
                repoPath = repoPath,
                featureSlug = "coverage",
                createdAt = 0L,
            )
        run.baseBranch = baseBranch
        return run
    }

    private fun request(
        repoPath: String?,
        baseBranch: String,
    ): PullRequestDiffRequest =
        PullRequestDiffRequest(
            repoPath = repoPath,
            baseBranch = baseBranch,
            baseSha = "",
            preExistingUntracked = emptyList(),
            finnOutput = "",
            tessOutput = "",
            quinnOutput = "",
            veraOutput = "",
        )
}
