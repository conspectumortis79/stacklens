package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineDbSchemaTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline DB schema config found - schema fetched and stored in run`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val dbCfg =
            DatabaseConfig(
                id = 1L,
                name = "testdb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(1L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE users (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add user feature", null, databaseConfigIds = listOf(1L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: testdb\n\nCREATE TABLE users (id INT);", run.dbSchema)
    }

    @Test
    fun `pipeline DB schema config not found - pipeline continues with empty schema`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        whenever(dbConfigPort.findDatabaseConfigById(99L)).thenReturn(null)

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", null, databaseConfigIds = listOf(99L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("", run.dbSchema)
    }

    @Test
    fun `pipeline DB schema already non-blank - fetch skipped`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 1) {
                reached.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
            "ok"
        }
        whenever(dbConfigPort.findDatabaseConfigById(5L)).thenReturn(
            DatabaseConfig(
                id = 5L,
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db",
                username = "u",
                password = "p",
            ),
        )

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null, databaseConfigIds = listOf(5L))
        reached.await(3, TimeUnit.SECONDS)

        svc.get(id)!!.dbSchema = "pre-existing schema"
        release.countDown()

        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("pre-existing schema", run.dbSchema)
        verify(dbScanPort, never()).fetchSchemaSummary(any())
    }

    @Test
    fun `pipeline DB schema passed through to Cole and Max prompts`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.arguments[1] as String)
            "ok"
        }
        val dbCfg =
            DatabaseConfig(
                id = 3L,
                name = "testdb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(3L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE orders (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add order feature", null, databaseConfigIds = listOf(3L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val schemaInAnyPrompt = capturedPrompts.any { it.contains("CREATE TABLE orders") }
        assertTrue(schemaInAnyPrompt, "DB schema should be injected into at least one agent prompt")
    }

    @Test
    fun `pipeline Nora prompt with DB schema - DB section injected into Nora prompt`() {
        val dbCfg =
            DatabaseConfig(
                id = 201L,
                name = "nora-db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "noratest",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(201L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE nora_test (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add search feature", localRepoConfigId = null, databaseConfigIds = listOf(201L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""

        assertTrue(
            noraPrompt.contains("## Database schema\n"),
            "Nora prompt must contain injected DB schema section: ${noraPrompt.take(400)}",
        )
        assertTrue(
            noraPrompt.contains("CREATE TABLE nora_test"),
            "Nora prompt must contain DB schema content",
        )
    }
}
