package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PipelineAnalysisCoverageTest {
    @Test
    fun `PipelineAnalysis defaults are sensible`() {
        val p = PipelineAnalysis()
        assertEquals(0L, p.id)
        assertNull(p.repoConfigId)
        assertNull(p.gitlabConfigId)
        assertNull(p.gitlabProjectId)
        assertNull(p.gitlabProjectPath)
        assertNull(p.githubRepoConfigId)
        assertEquals("PENDING", p.status)
        assertNull(p.summaryText)
        assertNull(p.findingsJson)
        assertNull(p.fileDescriptionsJson)
        assertNull(p.optimizationsJson)
        assertNull(p.mermaid)
        assertNull(p.errorMessage)
        assertNull(p.modelUsed)
        assertNotNull(p.createdAt)
        assertNull(p.completedAt)
        assertNull(p.userPromptAdditions)
        assertNull(p.mrUrl)
        assertNull(p.mrBranch)
        assertNull(p.mrProvider)
    }

    @Test
    fun `PipelineAnalysis stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val done = LocalDateTime.of(2025, 1, 1, 1, 0)
        val p =
            PipelineAnalysis(
                id = 7L,
                repoConfigId = 11L,
                gitlabConfigId = 22L,
                gitlabProjectId = 33L,
                gitlabProjectPath = "group/proj",
                githubRepoConfigId = 44L,
                status = "COMPLETED",
                summaryText = "summary",
                findingsJson = "[{\"id\":\"f1\"}]",
                fileDescriptionsJson = "[{\"path\":\"x\"}]",
                optimizationsJson = "[{\"title\":\"t\"}]",
                mermaid = "graph TD; A-->B",
                errorMessage = "err",
                modelUsed = "gpt-4",
                createdAt = now,
                completedAt = done,
                userPromptAdditions = "be brief",
                mrUrl = "https://example/mr/1",
                mrBranch = "feat/x",
                mrProvider = "GITLAB",
            )
        assertEquals(7L, p.id)
        assertEquals(11L, p.repoConfigId)
        assertEquals(22L, p.gitlabConfigId)
        assertEquals(33L, p.gitlabProjectId)
        assertEquals("group/proj", p.gitlabProjectPath)
        assertEquals(44L, p.githubRepoConfigId)
        assertEquals("COMPLETED", p.status)
        assertEquals("summary", p.summaryText)
        assertEquals("[{\"id\":\"f1\"}]", p.findingsJson)
        assertEquals("[{\"path\":\"x\"}]", p.fileDescriptionsJson)
        assertEquals("[{\"title\":\"t\"}]", p.optimizationsJson)
        assertEquals("graph TD; A-->B", p.mermaid)
        assertEquals("err", p.errorMessage)
        assertEquals("gpt-4", p.modelUsed)
        assertEquals(now, p.createdAt)
        assertEquals(done, p.completedAt)
        assertEquals("be brief", p.userPromptAdditions)
        assertEquals("https://example/mr/1", p.mrUrl)
        assertEquals("feat/x", p.mrBranch)
        assertEquals("GITLAB", p.mrProvider)
    }

    @Test
    fun `createdAt defaults to roughly now and can be overridden`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val p = PipelineAnalysis(createdAt = now)
        assertEquals(now, p.createdAt)
        val fresh = PipelineAnalysis()
        assertFalse(fresh.createdAt == now)
    }
}
