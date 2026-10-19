package com.stacklens.application.port.out.repo

data class CodeSummary(
    val projectTree: String,
    val fileContents: String,
    val fileCount: Int,
    val charCount: Int,
)

data class PipelineSummary(
    val projectTree: String,
    val fileContents: String,
    val fileCount: Int,
    val charCount: Int,
)

data class KeywordHit(
    val filePath: String,
    val lineNumber: Int,
    val context: String,
)

interface LocalRepoScanPort {
    fun buildCodeSummary(
        repoPath: String,
        maxFiles: Int = 150,
        maxPerFileSizeKb: Int = 3,
    ): CodeSummary

    fun buildPipelineSummary(
        repoPath: String,
        maxFiles: Int = 30,
        maxPerFileSizeKb: Int = 16,
    ): PipelineSummary

    fun searchKeyword(
        repoPath: String,
        keyword: String,
        maxHits: Int = 100,
    ): List<KeywordHit>
}
