package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineExecutionMiscTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline happy path no-repo no-DB all stages reach DONE`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add login feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertNull(run.errorMessage)
        assertNull(run.currentStage)

        assertEquals(StageStatus.DONE, run.ivan.status)
        listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
            .forEach { stage -> assertEquals(StageStatus.DONE, stage.status, "Stage ${stage.name} should be DONE") }
    }

    @Test
    fun `pipeline stage outputs populated after happy path`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val counter = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            "output-${counter.incrementAndGet()}"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Build feature X", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera)
            .forEach { stage -> assertTrue(stage.output.isNotBlank(), "${stage.name} output should be set") }

        assertTrue(run.rex.output.contains("Kein Repository"), "Rex output should note no-repo")
    }

    @Test
    fun `pipeline with repo uses 5-arg overload for Finn through Vera`() {
        val tempDir = Files.createTempDirectory("pipeline-repo-test").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")

            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("no-repo-output")

            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("repo-output")

            whenever(localRepoConfigPort.findLocalRepoConfigById(1L)).thenReturn(
                LocalRepoConfig(id = 1L, name = "repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature Z", localRepoConfigId = 1L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)

            assertEquals("repo-output", run.max.output)
            assertEquals("repo-output", run.finn.output)
            assertEquals("repo-output", run.faye.output)
            assertEquals("repo-output", run.tess.output)
            assertEquals("repo-output", run.quinn.output)
            assertEquals("repo-output", run.sage.output)
            assertEquals("repo-output", run.vera.output)

            assertEquals("no-repo-output", run.nora.output)
            assertEquals("no-repo-output", run.cole.output)

            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should complete")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline no databaseConfigId - DB scan never called`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("", run.dbSchema)
        verify(dbScanPort, never()).fetchSchemaSummary(any())
    }

    @Test
    fun `pipeline InterruptedException in stage causes ERROR status`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            when (callCount.incrementAndGet()) {
                3 -> throw InterruptedException("interrupted during cole")
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals("Pipeline abgebrochen.", run.errorMessage)
        assertEquals(StageStatus.ERROR, run.cole.status)
    }

    @Test
    fun `pipeline currentStage is non-null while running and null on completion`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 2) {
                reached.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)

        reached.await(3, TimeUnit.SECONDS)
        val runDuringExec = svc.get(id)!!

        assertNotNull(runDuringExec.currentStage, "currentStage must be set while pipeline is running")
        assertEquals(PipelineStatus.RUNNING, runDuringExec.status)

        release.countDown()
        val runAfter = awaitRunDone(svc, id)

        assertNull(runAfter.currentStage, "currentStage should be null after pipeline completes")
    }

    @Test
    fun `pipeline Nora stage status is RUNNING while Nora is executing`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 2) {
                reached.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        reached.await(3, TimeUnit.SECONDS)

        val run = svc.get(id)!!
        assertEquals(StageStatus.RUNNING, run.nora.status, "Nora should be RUNNING while blocked")

        release.countDown()
        awaitRunDone(svc, id)
    }

    @Test
    fun `pipeline free-text with repo and DB - all three combined happy path`() {
        val tempDir = Files.createTempDirectory("pipeline-repo-db-combo").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            val dbCfg =
                DatabaseConfig(
                    id = 10L,
                    name = "combo-db",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "localhost",
                    port = 5432,
                    databaseName = "combodb",
                    username = "u",
                    password = "p",
                )
            whenever(dbConfigPort.findDatabaseConfigById(10L)).thenReturn(dbCfg)
            whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE combo (id INT);")
            whenever(localRepoConfigPort.findLocalRepoConfigById(51L)).thenReturn(
                LocalRepoConfig(id = 51L, name = "combo-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 51L, databaseConfigIds = listOf(10L))
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals("## Database: combo-db\n\nCREATE TABLE combo (id INT);", run.dbSchema)
            assertEquals("5arg-ok", run.finn.output)
            assertEquals("5arg-ok", run.faye.output)
            assertEquals("5arg-ok", run.vera.output)
            assertEquals("4arg-ok", run.nora.output)
            assertEquals("4arg-ok", run.cole.output)
            listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
                .forEach { stage -> assertEquals(StageStatus.DONE, stage.status, "${stage.name} should be DONE") }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline all stage outputs are non-blank after happy path`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            "output-${callCount.incrementAndGet()}"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera)
            .forEach { stage ->
                assertTrue(stage.output.isNotBlank(), "Stage ${stage.name} output should not be blank")
            }
    }

    @Test
    fun `pipeline cancel via interrupt while blocked transitions to ERROR with cancellation message`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val reachedNora = CountDownLatch(1)
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            if (callCount.incrementAndGet() == 2) {
                reachedNora.countDown()

                Thread.sleep(30_000)
            }
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", null)
        assertTrue(reachedNora.await(3, TimeUnit.SECONDS), "Pipeline must reach Nora before cancel")
        svc.cancel(id)
        val run = awaitRunDone(svc, id, timeoutMs = 5_000)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertNotNull(run.errorMessage)
        assertTrue(
            run.errorMessage!!.contains("abgebrochen"),
            "errorMessage should be the localised cancellation message, was: ${run.errorMessage}",
        )
        assertTrue(run.cancelled, "cancelled flag must be set")
        assertNull(run.currentStage, "currentStage must be cleared after cancel")
    }
}
