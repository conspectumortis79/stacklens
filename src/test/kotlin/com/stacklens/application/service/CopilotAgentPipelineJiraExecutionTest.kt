package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class CopilotAgentPipelineJiraExecutionTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline jira-mode no-repo Lena through Rex all DONE`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "PROJ-100\nSummary: Add login\nDescription: Implement login",
                ticketKey = "PROJ-100",
                localRepoConfigId = null,
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertNull(run.errorMessage)
        assertEquals(StageStatus.DONE, run.lena.status)
        listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
            .forEach { stage -> assertEquals(StageStatus.DONE, stage.status, "Stage ${stage.name} should be DONE") }
    }

    @Test
    fun `pipeline jira-mode with repo Lena uses 4-arg overload`() {
        val tempDir = Files.createTempDirectory("pipeline-jira-repo").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-output")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-output")
            whenever(localRepoConfigPort.findLocalRepoConfigById(2L)).thenReturn(
                LocalRepoConfig(id = 2L, name = "repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id =
                svc.startFromJira(
                    ticketData = "PROJ-10\nSummary: Jira feature",
                    ticketKey = "PROJ-10",
                    localRepoConfigId = 2L,
                )
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)

            assertEquals("4arg-output", run.lena.output)

            assertEquals("5arg-output", run.finn.output)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline jira-mode with DB schema - schema injected after Lena`() {
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val dbCfg =
            DatabaseConfig(
                id = 7L,
                name = "jiradb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "jira",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(7L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE sprints (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "JIRA-99\nSummary: Sprint planning feature",
                ticketKey = "JIRA-99",
                localRepoConfigId = null,
                databaseConfigIds = listOf(7L),
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        val schemaInAnyPrompt = capturedPrompts.any { it.contains("CREATE TABLE sprints") }
        assertTrue(schemaInAnyPrompt, "DB schema should be injected into at least one prompt in jira-mode")
    }

    @Test
    fun `pipeline jira-mode with repo and DB - all four dimensions combined happy path`() {
        val tempDir = Files.createTempDirectory("pipeline-jira-repo-db").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            val dbCfg =
                DatabaseConfig(
                    id = 11L,
                    name = "jira-combo-db",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "localhost",
                    port = 5432,
                    databaseName = "jiracombodb",
                    username = "u",
                    password = "p",
                )
            whenever(dbConfigPort.findDatabaseConfigById(11L)).thenReturn(dbCfg)
            whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE jira_combo (id INT);")
            whenever(localRepoConfigPort.findLocalRepoConfigById(52L)).thenReturn(
                LocalRepoConfig(id = 52L, name = "jira-combo-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id =
                svc.startFromJira(
                    ticketData = "PROJ-200\nSummary: Full combo test",
                    ticketKey = "PROJ-200",
                    localRepoConfigId = 52L,
                    databaseConfigIds = listOf(11L),
                )
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals("## Database: jira-combo-db\n\nCREATE TABLE jira_combo (id INT);", run.dbSchema)
            assertEquals("4arg-ok", run.lena.output)
            assertEquals("5arg-ok", run.finn.output)
            listOf(run.lena, run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
                .forEach { stage -> assertEquals(StageStatus.DONE, stage.status, "${stage.name} should be DONE") }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
