package com.stacklens.adapter.out.git

import com.stacklens.application.port.out.PullRequestDiffPort
import com.stacklens.application.port.out.PullRequestDiffRequest
import com.stacklens.domain.pipeline.PullRequestDiffLine
import com.stacklens.domain.pipeline.PullRequestFileDiff
import com.stacklens.domain.pipeline.PullRequestFileStatus
import com.stacklens.domain.text.AgentTextUtils
import com.stacklens.domain.util.closingAuto
import com.stacklens.shared.ProcessUtils
import com.stacklens.shared.utf8Reader
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
class GitDiffBuilder(
    private val subprocessTimeoutSeconds: Long = 15L,
) : PullRequestDiffPort {
    companion object {
        internal fun parseAgentPaths(output: String): List<String> = AgentTextUtils.parseAgentOutput(output).map { it.path }
    }

    internal enum class FileStatus { ADDED, MODIFIED, DELETED, UNCHANGED }

    internal data class DiffLine(
        val sign: String,
        val text: String,
    )

    internal data class FileDiff(
        val path: String,
        val status: FileStatus,
        val additions: Int,
        val deletions: Int,
        val lines: List<DiffLine>,
    )

    internal data class GitContext(
        val gitRoot: File,
        val baseBranch: String,
        val baseSha: String,
        val preExistingUntracked: Set<String>,
        val agentWrittenPaths: Set<String>,
        val repoSubpathPrefix: String,
    )

    data class GitResult(
        val exit: Int,
        val stdout: String,
    )

    internal fun resolveGitContext(request: PullRequestDiffRequest): GitContext? {
        val repoPath = request.repoPath
        if (repoPath.isNullOrBlank()) return null
        val configured =
            try {
                File(repoPath).canonicalFile
            } catch (_: Exception) {
                return null
            }
        if (!configured.exists()) return null
        val gitRoot =
            generateSequence(configured) { it.parentFile }
                .firstOrNull { File(it, ".git").exists() }
                ?: return null
        val base = request.baseBranch.takeIf { it.isNotBlank() } ?: detectBaseBranch(gitRoot) ?: return null
        val subpathPrefix =
            if (configured == gitRoot) {
                ""
            } else {
                val rel =
                    gitRoot
                        .toPath()
                        .relativize(configured.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                "$rel/"
            }
        val agentWrittenPaths =
            listOf(request.finnOutput, request.tessOutput, request.quinnOutput, request.veraOutput)
                .flatMap { parseAgentPaths(it) }
                .map { if (subpathPrefix.isNotEmpty()) "$subpathPrefix$it" else it }
                .toSet()
        return GitContext(
            gitRoot = gitRoot,
            baseBranch = base,
            baseSha = request.baseSha,
            preExistingUntracked = request.preExistingUntracked.toSet(),
            agentWrittenPaths = agentWrittenPaths,
            repoSubpathPrefix = subpathPrefix,
        )
    }

    private fun detectBaseBranch(gitRoot: File): String? {
        if (runGit(gitRoot, "show-ref", "--verify", "--quiet", "refs/heads/main").exit == 0) return "main"
        if (runGit(gitRoot, "show-ref", "--verify", "--quiet", "refs/heads/master").exit == 0) return "master"
        val head = runGit(gitRoot, "rev-parse", "--abbrev-ref", "HEAD")
        return head.stdout.trim().takeIf { it.isNotBlank() && head.exit == 0 }
    }

    private fun resolveGitBinary(): String = ProcessUtils.resolveGitBinary()

    internal fun runGit(
        cwd: File,
        vararg args: String,
    ): GitResult {
        val cmd = listOf(resolveGitBinary()) + args.toList()
        return runCommand(cmd.first(), cwd, *args)
    }

    internal fun runCommand(
        binary: String,
        cwd: File,
        vararg args: String,
    ): GitResult {
        val cmd = listOf(binary) + args.toList()
        return try {
            val proc =
                ProcessBuilder(cmd)
                    .directory(cwd)
                    .redirectErrorStream(false)
                    .start()
            val outBuf = StringBuilder()
            val drain =
                Thread(
                    object : Runnable {
                        override fun run() {
                            proc.inputStream.utf8Reader().closingAuto { r ->
                                val buf = CharArray(8 * 1024)
                                while (true) {
                                    val n = r.read(buf)
                                    if (n < 0) break
                                    outBuf.append(buf, 0, n)
                                    if (outBuf.length > 8 * 1024 * 1024) break
                                }
                            }
                        }
                    },
                ).apply {
                    isDaemon = true
                    start()
                }
            Thread(
                object : Runnable {
                    override fun run() {
                        proc.errorStream.closingAuto { it.readBytes() }
                    }
                },
            ).apply {
                isDaemon = true
                start()
            }
            val finished = proc.waitFor(subprocessTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                proc.waitFor(2, TimeUnit.SECONDS)
                return GitResult(-1, "")
            }
            drain.join(2_000)
            GitResult(proc.exitValue(), outBuf.toString())
        } catch (_: Exception) {
            GitResult(-1, "")
        }
    }

    internal fun listChangedPaths(ctx: GitContext): List<String> {
        val collected = LinkedHashSet<String>()
        val prefix = ctx.repoSubpathPrefix

        fun isArtifactPath(gitPath: String): Boolean = isArtifactPathForPrefix(gitPath, prefix)

        fun consume(out: String) {
            for (part in out.split('\u0000')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() && !isArtifactPath(trimmed)) collected.add(trimmed)
            }
        }

        val baseRef = ctx.baseSha.ifBlank { ctx.baseBranch }
        val committed = runGit(ctx.gitRoot, "diff", "--name-only", "-z", "$baseRef..HEAD")
        if (committed.exit == 0) consume(committed.stdout)

        val workingTree = runGit(ctx.gitRoot, "diff", "--name-only", "-z", "HEAD")
        if (workingTree.exit == 0) consume(workingTree.stdout)

        val untracked = runGit(ctx.gitRoot, "ls-files", "--others", "--exclude-standard", "-z")
        if (untracked.exit == 0) {
            for (part in untracked.stdout.split('\u0000')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() &&
                    !isArtifactPath(trimmed) &&
                    (trimmed !in ctx.preExistingUntracked || trimmed in ctx.agentWrittenPaths)
                ) {
                    collected.add(trimmed)
                }
            }
        }

        val scoped =
            if (prefix.isEmpty()) {
                collected
            } else {
                collected.filterTo(LinkedHashSet()) { it.startsWith(prefix) }
            }

        return scoped.sorted()
    }

    internal fun isArtifactPathForPrefix(
        gitPath: String,
        prefix: String,
    ): Boolean {
        val rel =
            if (prefix.isNotEmpty() && gitPath.startsWith(prefix)) {
                gitPath.removePrefix(prefix)
            } else {
                gitPath
            }
        val fileName = rel.substringAfterLast('/')
        return rel.startsWith(".stacklens/") ||
            rel.startsWith(".phpunit.cache/") ||
            rel.startsWith(".phpunit/") ||
            rel.startsWith("reports/") ||
            rel.startsWith("build/") ||
            rel.startsWith("node_modules/") ||
            rel.startsWith("vendor/") ||
            rel.startsWith("data/") ||
            rel.startsWith("test-results/") ||
            rel.startsWith("playwright-report/") ||
            fileName.startsWith("junit_") ||
            fileName == "coverage.xml" ||
            fileName == "coverageOutput" ||
            fileName.endsWith(".clover") ||
            fileName.endsWith(".cov")
    }

    internal fun buildFileDiffForPath(
        path: String,
        ctx: GitContext,
    ): FileDiff {
        val baseRef = ctx.baseSha.ifBlank { ctx.baseBranch }
        val baseShow = runGit(ctx.gitRoot, "show", "$baseRef:$path")
        val baseExists = baseShow.exit == 0
        val baseContent = if (baseExists) baseShow.stdout.removeSuffix("\n") else ""

        val target =
            try {
                File(ctx.gitRoot, path).canonicalFile
            } catch (_: Exception) {
                null
            }
        val rootPrefix = ctx.gitRoot.path + File.separator
        val safeTarget = target?.takeIf { it.path.startsWith(rootPrefix) }
        val workExists = safeTarget != null && safeTarget.exists() && safeTarget.isFile
        val workContent = if (workExists) safeTarget.readText(Charsets.UTF_8) else ""

        if (!baseExists) {
            if (!workExists) {
                return FileDiff(path, FileStatus.UNCHANGED, 0, 0, emptyList())
            }
            val newLines = if (workContent.isEmpty()) emptyList() else workContent.split("\n")
            return FileDiff(
                path = path,
                status = FileStatus.ADDED,
                additions = newLines.size,
                deletions = 0,
                lines = newLines.map { DiffLine("+", it) },
            )
        }
        if (!workExists) {
            val oldLines = if (baseContent.isEmpty()) emptyList() else baseContent.split("\n")
            return FileDiff(
                path = path,
                status = FileStatus.DELETED,
                additions = 0,
                deletions = oldLines.size,
                lines = oldLines.map { DiffLine("-", it) },
            )
        }

        val oldLines = baseContent.split("\n")
        val newLines = workContent.split("\n")
        if (oldLines == newLines) return FileDiff(path, FileStatus.UNCHANGED, 0, 0, emptyList())
        val rawDiff = lcsDiff(oldLines, newLines)
        val adds = rawDiff.count { it.sign == "+" }
        val dels = rawDiff.count { it.sign == "-" }
        return FileDiff(path, FileStatus.MODIFIED, adds, dels, collapseContext(rawDiff))
    }

    internal fun lcsDiff(
        a: List<String>,
        b: List<String>,
    ): List<DiffLine> = LineDiffCalculator.lcsDiff(a, b)

    internal fun collapseContext(
        lines: List<DiffLine>,
        context: Int = 3,
    ): List<DiffLine> = LineDiffCalculator.collapseContext(lines, context)

    override fun computeDiffs(request: PullRequestDiffRequest): PullRequestDiffPort.DiffResult? {
        val ctx = resolveGitContext(request) ?: return null
        val paths = listChangedPaths(ctx)
        val diffs = paths.map { path -> buildFileDiffForPath(path, ctx).toPortType() }
        return PullRequestDiffPort.DiffResult(baseBranch = ctx.baseBranch, diffs = diffs)
    }

    private fun FileDiff.toPortType(): PullRequestFileDiff =
        PullRequestFileDiff(
            path = path,
            status = status.toPortStatus(),
            additions = additions,
            deletions = deletions,
            lines = lines.map { PullRequestDiffLine(it.sign, it.text) },
        )

    private fun FileStatus.toPortStatus(): PullRequestFileStatus =
        when (this) {
            FileStatus.ADDED -> PullRequestFileStatus.ADDED
            FileStatus.MODIFIED -> PullRequestFileStatus.MODIFIED
            FileStatus.DELETED -> PullRequestFileStatus.DELETED
            FileStatus.UNCHANGED -> PullRequestFileStatus.UNCHANGED
        }
}
