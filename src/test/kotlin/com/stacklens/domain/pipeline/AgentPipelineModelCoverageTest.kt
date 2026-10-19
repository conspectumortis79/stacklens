package com.stacklens.domain.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentPipelineModelCoverageTest {
    @Test
    fun `AgentStage defaults are sensible`() {
        val s = AgentStage(name = "n")
        assertEquals("n", s.name)
        assertEquals(StageStatus.PENDING, s.status)
        assertEquals("", s.output)
    }

    @Test
    fun `AgentStage status and output are mutable`() {
        val s = AgentStage("n")
        s.status = StageStatus.DONE
        s.output = "done"
        assertEquals(StageStatus.DONE, s.status)
        assertEquals("done", s.output)
    }

    @Test
    fun `MockupImage stores its arguments verbatim`() {
        val m = MockupImage(base64 = "AAAA", mimeType = "image/png")
        assertEquals("AAAA", m.base64)
        assertEquals("image/png", m.mimeType)
    }

    @Test
    fun `AgentPipelineRun defaults are sensible`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        assertEquals(1L, r.id)
        assertEquals("raw", r.rawText)
        assertNull(r.repoPath)
        assertEquals("", r.featureSlug)
        assertNull(r.jiraTicketData)
        assertTrue(r.createdAt > 0L)
        assertTrue(r.databaseConfigIds.isEmpty())
        assertNull(r.mockupBase64)
        assertNull(r.mockupMimeType)
        assertTrue(r.mockupImages.isEmpty())
        assertEquals(PipelineStatus.PENDING, r.status)
        assertNull(r.currentStage)
        assertNull(r.errorMessage)
        assertFalse(r.cancelled)
        assertNull(r.detectedTicketKey)
        assertEquals("", r.gitBranch)
        assertEquals("", r.gitLog)
        assertEquals("", r.gitRoot)
        assertEquals("", r.baseBranch)
        assertEquals("", r.baseSha)
        assertTrue(r.preExistingUntracked.isEmpty())
        assertEquals("", r.dbSchema)
        assertFalse(r.filesAutoWritten)
        assertTrue(r.autoWrittenFiles.isEmpty())
        assertTrue(r.autoDeletedFiles.isEmpty())
        assertTrue(r.autoWriteErrors.isEmpty())
        assertNull(r.cancellation)
    }

    @Test
    fun `AgentPipelineRun stores provided values verbatim`() {
        val mockups = listOf(MockupImage("B", "image/jpeg"))
        val r =
            AgentPipelineRun(
                id = 99L,
                rawText = "raw",
                repoPath = "/repo",
                featureSlug = "feature/x",
                jiraTicketData = "PROJ-1",
                createdAt = 1234L,
                databaseConfigIds = listOf(1L, 2L),
                mockupBase64 = "AAA",
                mockupMimeType = "image/png",
                mockupImages = mockups,
            )
        assertEquals("feature/x", r.featureSlug)
        assertEquals("/repo", r.repoPath)
        assertEquals("PROJ-1", r.jiraTicketData)
        assertEquals(1234L, r.createdAt)
        assertEquals(listOf(1L, 2L), r.databaseConfigIds)
        assertEquals("AAA", r.mockupBase64)
        assertEquals("image/png", r.mockupMimeType)
        assertEquals(mockups, r.mockupImages)
    }

    @Test
    fun `AgentPipelineRun mutates volatile fields via setters`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        r.status = PipelineStatus.DONE
        r.currentStage = "nora"
        r.errorMessage = "boom"
        r.cancelled = true
        r.detectedTicketKey = "PROJ-1"
        r.gitBranch = "feat/x"
        r.gitLog = "log"
        r.gitRoot = "/repo"
        r.baseBranch = "develop"
        r.baseSha = "deadbeef"
        r.preExistingUntracked = listOf("a.kt")
        r.dbSchema = "DDL"
        r.filesAutoWritten = true
        r.autoWrittenFiles = listOf("Foo.kt")
        r.autoDeletedFiles = listOf("Old.kt")
        r.autoWriteErrors = listOf("warn")

        assertEquals(PipelineStatus.DONE, r.status)
        assertEquals("nora", r.currentStage)
        assertEquals("boom", r.errorMessage)
        assertTrue(r.cancelled)
        assertEquals("PROJ-1", r.detectedTicketKey)
        assertEquals("feat/x", r.gitBranch)
        assertEquals("log", r.gitLog)
        assertEquals("/repo", r.gitRoot)
        assertEquals("develop", r.baseBranch)
        assertEquals("deadbeef", r.baseSha)
        assertEquals(listOf("a.kt"), r.preExistingUntracked)
        assertEquals("DDL", r.dbSchema)
        assertTrue(r.filesAutoWritten)
        assertEquals(listOf("Foo.kt"), r.autoWrittenFiles)
        assertEquals(listOf("Old.kt"), r.autoDeletedFiles)
        assertEquals(listOf("warn"), r.autoWriteErrors)
    }

    @Test
    fun `AgentPipelineRun stages exclude lena in free-text mode`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        val names = r.stages.map { it.name }
        assertFalse("lena" in names)

        assertTrue("nora" in names)
        assertTrue("cole" in names)
        assertTrue("rex" in names)
        assertEquals(12, names.size)
    }

    @Test
    fun `AgentPipelineRun stages include lena in Jira mode`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", jiraTicketData = "PROJ-1", createdAt = System.currentTimeMillis())
        val names = r.stages.map { it.name }
        assertTrue("lena" in names)
        assertEquals(13, names.size)
    }

    @Test
    fun `AgentPipelineRun stagesByName contains every stage regardless of jira mode`() {
        val freeText = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        val jira = AgentPipelineRun(id = 2L, rawText = "raw", repoPath = null, featureSlug = "", jiraTicketData = "PROJ-1", createdAt = System.currentTimeMillis())

        assertEquals(13, freeText.stagesByName.size)
        assertEquals(13, jira.stagesByName.size)

        assertEquals(13, jira.stages.size)
        assertEquals(12, freeText.stages.size)

        assertEquals(13, freeText.stagesByName.keys.count { freeText.stageByName(it) != null })
        assertEquals(13, jira.stagesByName.keys.count { jira.stageByName(it) != null })
    }

    @Test
    fun `AgentPipelineRun stageByName returns null for null and unknown names`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        assertNull(r.stageByName(null))
        assertNull(r.stageByName(""))
        assertNull(r.stageByName("does-not-exist"))
    }

    @Test
    fun `AgentPipelineRun stageByName returns the matching stage`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        assertEquals("nora", r.stageByName("nora")?.name)
    }

    @Test
    fun `AgentPipelineRun convenience getters resolve every documented stage`() {
        val r = AgentPipelineRun(id = 1L, rawText = "raw", repoPath = null, featureSlug = "", createdAt = System.currentTimeMillis())
        assertNotNull(r.nora)
        assertNotNull(r.cole)
        assertNotNull(r.ivan)
        assertNotNull(r.max)
        assertNotNull(r.finn)
        assertNotNull(r.faye)
        assertNotNull(r.vera)
        assertNotNull(r.tess)
        assertNotNull(r.syd)
        assertNotNull(r.quinn)
        assertNotNull(r.sage)
        assertNotNull(r.rex)
    }

    @Test
    fun `PipelineStatus has the four documented values`() {
        assertEquals(4, PipelineStatus.entries.size)
        assertEquals(
            setOf("PENDING", "RUNNING", "DONE", "ERROR"),
            PipelineStatus.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `StageStatus has the four documented values`() {
        assertEquals(4, StageStatus.entries.size)
        assertEquals(
            setOf("PENDING", "RUNNING", "DONE", "ERROR"),
            StageStatus.entries.map { it.name }.toSet(),
        )
    }
}
