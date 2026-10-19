package com.stacklens.application.port.out

import com.stacklens.domain.pipeline.PullRequestFileDiff

interface PullRequestDiffPort {
    fun computeDiffs(request: PullRequestDiffRequest): DiffResult?

    data class DiffResult(
        val baseBranch: String,
        val diffs: List<PullRequestFileDiff>,
    )
}

class PullRequestDiffRequest(
    val repoPath: String?,
    val baseBranch: String,
    val baseSha: String,
    val preExistingUntracked: List<String>,
    val finnOutput: String,
    val tessOutput: String,
    val quinnOutput: String,
    val veraOutput: String,
)
