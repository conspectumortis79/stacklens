package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeAnalysisFullTest {
    @Test
    fun `CodeAnalysis default values are correct`() {
        val a = CodeAnalysis()
        assertEquals(0L, a.id)
        assertNull(a.repoConfigId)
        assertNull(a.gitlabConfigId)
        assertNull(a.gitlabProjectId)
        assertNull(a.gitlabProjectPath)
        assertNull(a.githubRepoConfigId)
        assertEquals("PENDING", a.status)
        assertNull(a.summaryText)
        assertNull(a.findingsJson)
        assertNull(a.fileChangesJson)
        assertNull(a.errorMessage)
        assertNull(a.modelUsed)
        assertNull(a.mrUrl)
        assertNull(a.mrBranch)
        assertNull(a.mermaid)
        assertNull(a.drawioXml)
        assertNull(a.monorepoSubPath)
        assertNull(a.monorepoAppName)
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
    }

    @Test
    fun `CodeAnalysis supports mutating all var fields`() {
        val a = CodeAnalysis()
        a.gitlabConfigId = 1L
        a.gitlabProjectId = 2L
        a.gitlabProjectPath = "g/p"
        a.githubRepoConfigId = 4L
        a.status = "RUNNING"
        a.summaryText = "s"
        a.findingsJson = "[]"
        a.fileChangesJson = "[]"
        a.errorMessage = "e"
        a.modelUsed = "m"
        a.mrUrl = "https://gl/mr/1"
        a.mrBranch = "feature/x"
        a.mermaid = "graph TD"
        a.drawioXml = "<mxfile/>"
        a.monorepoSubPath = "services/backend"
        a.monorepoAppName = "backend"
        a.completedAt = java.time.LocalDateTime.now()
        assertEquals(1L, a.gitlabConfigId)
        assertEquals(2L, a.gitlabProjectId)
        assertEquals("g/p", a.gitlabProjectPath)
        assertEquals(4L, a.githubRepoConfigId)
        assertEquals("RUNNING", a.status)
        assertEquals("s", a.summaryText)
        assertEquals("[]", a.findingsJson)
        assertEquals("[]", a.fileChangesJson)
        assertEquals("e", a.errorMessage)
        assertEquals("m", a.modelUsed)
        assertEquals("https://gl/mr/1", a.mrUrl)
        assertEquals("feature/x", a.mrBranch)
        assertEquals("graph TD", a.mermaid)
        assertEquals("<mxfile/>", a.drawioXml)
        assertEquals("services/backend", a.monorepoSubPath)
        assertEquals("backend", a.monorepoAppName)
        assertNotNull(a.completedAt)
    }
}
