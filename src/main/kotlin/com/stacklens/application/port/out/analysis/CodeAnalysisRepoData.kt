package com.stacklens.application.port.out.analysis

data class CodeAnalysisRepoData(
    val appName: String,
    val repoPath: String,
    val tree: String,
    val contents: String,
)
