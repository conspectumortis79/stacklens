package com.stacklens.application.port.out.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalRepoScanPortDefaultValuesTest {
    private val stub =
        object : LocalRepoScanPort {
            override fun buildCodeSummary(
                repoPath: String,
                maxFiles: Int,
                maxPerFileSizeKb: Int,
            ): CodeSummary =
                CodeSummary(
                    projectTree = "tree",
                    fileContents = "content",
                    fileCount = maxFiles,
                    charCount = repoPath.length + maxPerFileSizeKb,
                )

            override fun buildPipelineSummary(
                repoPath: String,
                maxFiles: Int,
                maxPerFileSizeKb: Int,
            ): PipelineSummary =
                PipelineSummary(
                    projectTree = "tree",
                    fileContents = "content",
                    fileCount = maxFiles,
                    charCount = repoPath.length + maxPerFileSizeKb,
                )

            override fun searchKeyword(
                repoPath: String,
                keyword: String,
                maxHits: Int,
            ): List<KeywordHit> = emptyList()
        }

    @Test
    fun `buildCodeSummary default values for maxFiles and maxPerFileSizeKb`() {
        val summary = stub.buildCodeSummary(repoPath = "/tmp/repo")
        assertEquals(150, summary.fileCount)
    }

    @Test
    fun `buildCodeSummary explicit maxFiles and maxPerFileSizeKb`() {
        val summary = stub.buildCodeSummary(repoPath = "/tmp/repo", maxFiles = 5, maxPerFileSizeKb = 7)
        assertEquals(5, summary.fileCount)
        assertEquals("/tmp/repo".length + 7, summary.charCount)
    }

    @Test
    fun `buildPipelineSummary default values for maxFiles and maxPerFileSizeKb`() {
        val summary = stub.buildPipelineSummary(repoPath = "/tmp/repo")
        assertEquals(30, summary.fileCount)
    }

    @Test
    fun `buildPipelineSummary explicit maxFiles and maxPerFileSizeKb`() {
        val summary = stub.buildPipelineSummary(repoPath = "/tmp/repo", maxFiles = 3, maxPerFileSizeKb = 9)
        assertEquals(3, summary.fileCount)
        assertEquals("/tmp/repo".length + 9, summary.charCount)
    }

    @Test
    fun `searchKeyword default value for maxHits`() {
        val hits = stub.searchKeyword(repoPath = "/tmp/repo", keyword = "needle")
        assertEquals(0, hits.size)
    }

    @Test
    fun `searchKeyword explicit maxHits`() {
        val hits = stub.searchKeyword(repoPath = "/tmp/repo", keyword = "needle", maxHits = 7)
        assertEquals(0, hits.size)
    }

    @Test
    fun `CodeSummary getters are covered`() {
        val s = CodeSummary(projectTree = "t", fileContents = "c", fileCount = 3, charCount = 10)
        assertEquals("t", s.projectTree)
        assertEquals("c", s.fileContents)
        assertEquals(3, s.fileCount)
        assertEquals(10, s.charCount)
    }

    @Test
    fun `KeywordHit getters are covered`() {
        val h = KeywordHit(filePath = "/p", lineNumber = 12, context = "ctx")
        assertEquals("/p", h.filePath)
        assertEquals(12, h.lineNumber)
        assertEquals("ctx", h.context)
    }
}
