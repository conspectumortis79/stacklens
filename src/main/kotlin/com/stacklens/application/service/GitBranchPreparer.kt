package com.stacklens.application.service

import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.process.GitCommandPort
import com.stacklens.application.port.out.time.ClockPort
import java.io.File

internal data class GitBranchInfo(
    val branch: String,
    val log: String,
    val baseBranch: String = "",
    val baseSha: String = "",
    val preExistingUntracked: List<String> = emptyList(),
    val gitRoot: String = "",
)

internal object GitBranchPreparer {
    fun prepare(
        repoPath: String,
        slug: String,
        processPort: GitCommandPort,
        fileAttributes: FileAttributes,
        clock: ClockPort,
    ): GitBranchInfo {
        val configured = fileAttributes.canonicalFile(File(repoPath))
        if (!fileAttributes.exists(configured)) {
            return GitBranchInfo("", "(repoPath existiert nicht: $repoPath – übersprungen)")
        }

        val root =
            generateSequence(configured) { it.parentFile }
                .firstOrNull { fileAttributes.exists(File(it, ".git")) }
                ?: return GitBranchInfo(
                    "",
                    "(kein .git-Verzeichnis in $configured oder einem übergeordneten Verzeichnis gefunden – übersprungen)",
                )
        val safeSlug = sanitiseSlug(slug, clock)

        val sb = StringBuilder()
        sb.appendLine("# repo: ${root.path}")
        sb.appendLine("# requested slug: $slug → safe: $safeSlug")
        val gitBin = processPort.resolveGitBinary()
        sb.appendLine("# git binary: $gitBin")

        fun runGit(
            vararg args: String,
            allowFailure: Boolean = false,
        ): Pair<Int, String> = runGitCommand(gitBin, root, sb, args, allowFailure, processPort)

        val (_, currentBranchOut) = runGit("rev-parse", "--abbrev-ref", "HEAD")
        val baseBranch = currentBranchOut.trim()
        sb.appendLine("# baseBranch (initial HEAD): $baseBranch")
        val (_, capturedBaseSha) = runGit("rev-parse", "HEAD", allowFailure = true)
        val baseShaSnapshot = capturedBaseSha.trim().take(40)
        sb.appendLine("# baseSha (snapshot): $baseShaSnapshot")

        val (rcExists, _) = runGit("show-ref", "--verify", "--quiet", "refs/heads/$safeSlug", allowFailure = true)
        if (rcExists == 0) {
            runGit("checkout", safeSlug)
            runGit("reset", "--hard", "HEAD", allowFailure = true)
        } else {
            runGit("checkout", "-b", safeSlug)
        }
        val preExistingUntracked = captureUntracked(::runGit)
        sb.appendLine("# preExistingUntracked count: ${preExistingUntracked.size}")

        val (_, currentBranch) = runGit("rev-parse", "--abbrev-ref", "HEAD", allowFailure = true)
        sb.appendLine("# final HEAD: $currentBranch")

        return GitBranchInfo(
            branch = safeSlug,
            log = sb.toString().trimEnd(),
            baseBranch = baseBranch,
            baseSha = baseShaSnapshot,
            preExistingUntracked = preExistingUntracked,
            gitRoot = root.path,
        )
    }

    private fun captureUntracked(runGit: (Array<out String>, Boolean) -> Pair<Int, String>): List<String> {
        val (rcUntracked, untrackedOut) = runGit(arrayOf("ls-files", "--others", "--exclude-standard", "-z"), true)
        return if (rcUntracked == 0) {
            untrackedOut.split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    private fun sanitiseSlug(
        slug: String,
        clock: ClockPort,
    ): String {
        val cleaned =
            slug
                .trim()
                .replace(Regex("[^A-Za-z0-9._/-]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .replace(Regex("\\.{2,}"), ".")
                .trim('-', '/', '.')

        val valid =
            cleaned.isNotBlank() &&
                !cleaned.endsWith(".lock") &&
                cleaned.length <= 100
        return if (valid) cleaned else "stacklens-feature-${clock.epochMillis()}"
    }

    private fun runGitCommand(
        gitBin: String,
        root: File,
        log: StringBuilder,
        args: Array<out String>,
        allowFailure: Boolean,
        processPort: GitCommandPort,
    ): Pair<Int, String> {
        val result = processPort.run(listOf(gitBin) + args.toList(), timeoutSec = 30L, workDir = root)
        val out = result.stdout.trim()
        log.appendLine("$ git ${args.joinToString(" ")}  →  exit=${result.exitCode}")
        if (out.isNotBlank()) log.appendLine(out.lines().joinToString("\n") { "    $it" })
        if (result.exitCode == 124) {
            throw RuntimeException("git ${args.joinToString(" ")} timed out")
        }
        if (result.exitCode != 0 && !allowFailure) {
            throw RuntimeException("git ${args.joinToString(" ")} failed (${result.exitCode}): $out")
        }
        return Pair(result.exitCode, out)
    }
}
