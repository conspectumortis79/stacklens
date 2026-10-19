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
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineFailuresTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline DB schema fetch throws - pipeline continues with empty schema`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val dbCfg =
            DatabaseConfig(
                id = 2L,
                name = "faildb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "faildb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(2L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenThrow(RuntimeException("connection refused"))

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature Q", null, databaseConfigIds = listOf(2L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("", run.dbSchema)
    }

    @Test
    fun `pipeline RuntimeException in stage causes ERROR with message`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            when (callCount.incrementAndGet()) {
                4 -> throw RuntimeException("max stage failed: model unavailable")
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertTrue(run.errorMessage!!.contains("max stage failed"), "Error message should contain exception text")
        assertEquals(StageStatus.ERROR, run.max.status)
    }

    @Test
    fun `pipeline Rex with sniff that fails 3 times - exhaustion message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-sniff-fail").toFile()
        try {
            File(tempDir, "Makefile").writeText("sniff:\n\t@exit 1\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")

            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn(
                "I analysed the issue but cannot produce a fix.",
            )
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(22L)).thenReturn(
                LocalRepoConfig(id = 22L, name = "sniff-fail-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Fix lint", localRepoConfigId = 22L)
            val run = awaitRunDone(svc, id, timeoutMs = 15_000)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)

            assertTrue(
                run.rex.output.contains("3 Versuchen"),
                "Rex output should report exhaustion after 3 attempts: ${run.rex.output}",
            )
            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should still complete: ${run.rex.output}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline exception during Cole marks Cole as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 3) throw RuntimeException("Cole exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertTrue(run.errorMessage?.contains("Cole exploded") == true, "Error message should include exception text")
        assertEquals(StageStatus.ERROR, run.cole.status, "Cole should be ERROR after exception")
    }

    @Test
    fun `pipeline exception during Sage marks Sage as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 11) throw RuntimeException("Sage exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.sage.status, "Sage should be ERROR after exception")

        assertEquals(StageStatus.DONE, run.nora.status)
        assertEquals(StageStatus.DONE, run.cole.status)
        assertEquals(StageStatus.DONE, run.finn.status)
    }

    @Test
    fun `pipeline Rex unit-test fails 3 times - exhaustion message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-unit-fail").toFile()
        try {
            File(tempDir, "Makefile").writeText("test:\n\t@echo 'FAIL: assertion error'\n\t@exit 1\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")

            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn(
                "I found the issue but cannot produce a fix.",
            )
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(41L)).thenReturn(
                LocalRepoConfig(id = 41L, name = "unit-fail-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Fix tests", localRepoConfigId = 41L)
            val run = awaitRunDone(svc, id, timeoutMs = 20_000)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("3 Versuchen"),
                "Rex output should report unit-test exhaustion: ${run.rex.output}",
            )
            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should still complete: ${run.rex.output}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex beautify fails then sniff AI fix loop runs`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-beautify-fail").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "sniff:\n\t@echo 'violations'; exit 1\n" +
                    "beautify:\n\t@echo 'beautify failed'; exit 1\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("no fix available")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(49L)).thenReturn(
                LocalRepoConfig(id = 49L, name = "beautify-fail-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 49L)
            val run = awaitRunDone(svc, id, timeoutMs = 20_000)

            assertEquals(PipelineStatus.DONE, run.status)
            assertTrue(
                run.rex.output.contains("beautify"),
                "Rex output should mention beautify attempt: ${run.rex.output}",
            )
            assertTrue(
                run.rex.output.contains("KI-Fix"),
                "Rex output should show AI fix loop after beautify failure: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline exception during Nora marks Nora as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 2) throw RuntimeException("Nora exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.nora.status)
        assertTrue(run.errorMessage?.contains("Nora exploded") == true)
        assertEquals(StageStatus.PENDING, run.cole.status)
        assertEquals(StageStatus.PENDING, run.max.status)
    }

    @Test
    fun `pipeline exception during Finn marks Finn as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 5) throw RuntimeException("Finn exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.finn.status)
        assertEquals(StageStatus.DONE, run.nora.status)
        assertEquals(StageStatus.DONE, run.cole.status)
        assertEquals(StageStatus.DONE, run.max.status)
        assertEquals(StageStatus.PENDING, run.faye.status)
    }

    @Test
    fun `pipeline exception during Faye marks Faye as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 6) throw RuntimeException("Faye exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.faye.status)
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals(StageStatus.PENDING, run.tess.status)
    }

    @Test
    fun `pipeline exception during Tess marks Tess as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 8) throw RuntimeException("Tess exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.tess.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.PENDING, run.quinn.status)
    }

    @Test
    fun `pipeline exception during Quinn marks Quinn as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 10) throw RuntimeException("Quinn exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.quinn.status)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals(StageStatus.PENDING, run.sage.status)
    }

    @Test
    fun `pipeline exception during Vera marks Vera as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 7) throw RuntimeException("Vera exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.vera.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.PENDING, run.tess.status)
    }

    @Test
    fun `pipeline exception during Lena marks Lena as ERROR in jira-mode`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == 1) throw RuntimeException("Lena exploded")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "PROJ-999\nSummary: Exception in Lena",
                ticketKey = "PROJ-999",
                localRepoConfigId = null,
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.lena.status)
        assertTrue(run.errorMessage?.contains("Lena exploded") == true)
        assertEquals(StageStatus.PENDING, run.nora.status)
    }

    @Test
    fun `pipeline exception during Max marks Max as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->

            if (callCount.incrementAndGet() == 4) throw RuntimeException("Max crashed")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.max.status)
        assertTrue(run.errorMessage?.contains("Max crashed") == true)
        assertEquals(StageStatus.PENDING, run.finn.status)
    }

    @Test
    fun `pipeline exception during Rex marks Rex as ERROR`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-exception").toFile()
        try {
            File(tempDir, "Makefile").writeText("sniff:\n\t@echo 'violations'; exit 1\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            val fourArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->

                if (fourArgCount.incrementAndGet() == 5) throw RuntimeException("Rex AI exploded")
                "4arg-ok"
            }
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(71L),
            ).thenReturn(LocalRepoConfig(id = 71L, name = "rex-exception-repo", path = tempDir.absolutePath))

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 71L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.ERROR, run.status)
            assertEquals(StageStatus.ERROR, run.rex.status)
            assertTrue(run.errorMessage?.contains("Rex AI exploded") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline jira-mode exception during Cole marks Cole as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->

            if (callCount.incrementAndGet() == 3) throw RuntimeException("Jira-Cole crashed")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira(ticketData = "JIRA-2\nSummary: test", ticketKey = "JIRA-2", localRepoConfigId = null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.lena.status)
        assertEquals(StageStatus.DONE, run.nora.status)
        assertEquals(StageStatus.ERROR, run.cole.status)
        assertTrue(run.errorMessage?.contains("Jira-Cole crashed") == true)
        assertEquals(StageStatus.PENDING, run.max.status)
    }

    @Test
    fun `pipeline jira-mode exception during Finn marks Finn as ERROR`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->

            if (callCount.incrementAndGet() == 5) throw RuntimeException("Jira-Finn crashed")
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira(ticketData = "JIRA-3\nSummary: test", ticketKey = "JIRA-3", localRepoConfigId = null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.finn.status)
        assertEquals(StageStatus.PENDING, run.faye.status)
    }

    @Test
    fun `pipeline copilot exception during Nora marks run as ERROR with error message`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            if (callCount.incrementAndGet() == 2) {
                throw RuntimeException("simulated copilot crash in nora")
            }
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add login feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertNotNull(run.errorMessage)
        assertTrue(
            run.errorMessage!!.contains("simulated copilot crash"),
            "errorMessage should contain underlying cause, was: ${run.errorMessage}",
        )
        assertEquals(StageStatus.ERROR, run.nora.status, "Nora stage must be marked ERROR")
        assertNull(run.currentStage, "currentStage must be cleared after error handling")
    }

    @Test
    fun `pipeline persists run to disk after exception in finally block`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            if (callCount.incrementAndGet() == 2) {
                throw RuntimeException("crash for persistence test")
            }
            "ok"
        }

        val dataDir = "build/tests/data-test-${System.currentTimeMillis()}"
        val svc = makeService(dataDir = dataDir)
        val id = svc.start("Add feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        val persisted = File("$dataDir/agent-pipeline/runs/$id.json")

        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            if (persisted.isFile && persisted.length() > 0) break
            Thread.sleep(20)
        }
        assertTrue(persisted.isFile, "Run must be persisted to disk even after exception (finally block)")
        assertTrue(persisted.length() > 0, "Persisted file must not be empty")
        val content = persisted.readText()
        assertTrue(
            Regex("\"status\"\\s*:\\s*\"ERROR\"").containsMatchIn(content),
            "Persisted JSON must reflect ERROR status, got: ${content.take(300)}",
        )
    }
}
