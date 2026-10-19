package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File

class CopilotAgentPipelineJiraStartTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `startFromJira creates a run with jiraTicketData`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "Ticket: PROJ-1\nSummary: Do something",
                ticketKey = "PROJ-1",
                localRepoConfigId = null,
            )
        assertTrue(id > 0)

        assertTrue(svc.allRuns().any { it.id == id })
    }

    @Test
    fun `startFromJira sets detectedTicketKey from ticketKey`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "Ticket: PROJ-42\nSummary: Feature",
                ticketKey = "PROJ-42",
                localRepoConfigId = null,
            )
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals("PROJ-42", run!!.detectedTicketKey)
        assertEquals("PROJ-42", run.featureSlug)
    }

    @Test
    fun `startFromJira with blank ticketKey uses generated slug from ticketData`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "Add payment gateway integration",
                ticketKey = "",
                localRepoConfigId = null,
            )

        val run = svc.get(id)
        assertNotNull(run)

        assertTrue(run!!.featureSlug.isNotBlank(), "Slug should be generated from ticketData")
    }

    @Test
    fun `startFromJira with DB schema config passed through`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val dbCfg =
            DatabaseConfig(
                id = 8L,
                name = "jiradb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "jiradb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(8L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE tickets (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "PROJ-50\nSummary: Jira feature with DB",
                ticketKey = "PROJ-50",
                localRepoConfigId = null,
                databaseConfigIds = listOf(8L),
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: jiradb\n\nCREATE TABLE tickets (id INT);", run.dbSchema)
        assertEquals(StageStatus.DONE, run.lena.status)
    }

    @Test
    fun `startFromJira with blank ticketKey uses generated feature slug`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira("ticket description", "", null)
        val run = svc.get(id)
        assertNotNull(run)

        assertNull(run!!.detectedTicketKey)
    }

    @Test
    fun `startFromJira with non-blank ticketKey sets detectedTicketKey`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira("ticket description", "PROJ-42", null)
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals("PROJ-42", run!!.detectedTicketKey)
        assertEquals("PROJ-42", run.featureSlug)
    }

    @Test
    fun `startFromJira with non-blank mockupBase64 adds to mockupImages`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira("desc", "PROJ-1", null, emptyList(), "b64data", "image/jpeg")
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
    }

    @Test
    fun `startFromJira with non-null localRepoConfigId where config not found results in null repoPath`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        whenever(localRepoConfigPort.findLocalRepoConfigById(99L)).thenReturn(null)
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira("desc", "PROJ-1", localRepoConfigId = 99L)
        val run = svc.get(id)
        assertNotNull(run)
        assertNull(run!!.repoPath)
    }

    @Test
    fun `startFromJira with non-empty mockupImages uses them directly`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val images =
            listOf(
                MockupImage("existingB64", "image/webp"),
            )
        val id = svc.startFromJira("desc", "PROJ-1", null, emptyList(), "anotherB64", "image/png", images)
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
        assertEquals("existingB64", run.mockupImages[0].base64)
    }

    @Test
    fun `startFromJira with non-blank mockupBase64 and null mockupMimeType uses empty string as mimeType`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira("desc", "PROJ-1", null, emptyList(), "base64data", null)
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
        assertEquals("", run.mockupImages[0].mimeType)
    }

    @Test
    fun `startFromJira with non-empty mockupImages skips ifEmpty lambda`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val images =
            listOf(
                MockupImage("b64", "image/png"),
            )
        val id = svc.startFromJira("desc", "PROJ-2", null, emptyList(), null, null, images)
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
        assertEquals("b64", run.mockupImages[0].base64)
    }

    @Test
    fun `startFromJira with whitespace-only mockupBase64 results in empty mockupImages`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")

        val id = svc.startFromJira("desc", "PROJ-3", null, emptyList(), " ", "image/png", emptyList())
        val run = svc.get(id)
        assertNotNull(run)
        assertTrue(run!!.mockupImages.isEmpty())
    }
}
