package com.stacklens.domain.pipeline

data class PullRequestData(
    val runId: Long,
    val featureSlug: String,
    val gitBranch: String,
    val baseBranch: String,
    val repoPath: String?,
    val diffs: List<PullRequestFileDiff>,
)

enum class PullRequestFileStatus { ADDED, MODIFIED, DELETED, UNCHANGED }

data class PullRequestFileDiff(
    val path: String,
    val status: PullRequestFileStatus,
    val additions: Int,
    val deletions: Int,
    val lines: List<PullRequestDiffLine>,
)

data class PullRequestDiffLine(
    val sign: String,
    val text: String,
)
