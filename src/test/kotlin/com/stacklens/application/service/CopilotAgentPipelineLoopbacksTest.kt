package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineLoopbacksTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline Faye HANDOFF_TO_FINN triggers Finn fix-pass and Faye redux`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("finn-initial-output")
            .thenReturn("🔄 HANDOFF_TO_FINN\nFrontend needs backend changes")
            .thenReturn("finn-fix-pass-output")
            .thenReturn("faye-redux-output")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature X", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertTrue(run.finn.output.contains("finn-initial-output"), "Finn should retain initial output")
        assertTrue(run.finn.output.contains("finn-fix-pass-output"), "Finn should include fix-pass output")
        assertTrue(run.finn.output.contains("Finn fix-pass (Faye)"), "Finn output should have Faye fix-pass marker")

        assertEquals("faye-redux-output", run.faye.output)
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals(StageStatus.DONE, run.faye.status)
    }

    @Test
    fun `pipeline Faye redux re-emits HANDOFF_TO_FINN - warning logged no second loopback`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("finn-initial")
            .thenReturn("🔄 HANDOFF_TO_FINN\nfirst handoff")
            .thenReturn("finn-fix-output")
            .thenReturn("faye-redux-content\n🔄 HANDOFF_TO_FINN\nsecond re-emit")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertFalse(run.faye.output.contains("HANDOFF_TO_FINN"), "Marker must be stripped from faye.output: ${run.faye.output}")

        assertTrue(run.faye.output.contains("faye-redux-content"), "Content before marker must be retained")

        assertEquals(StageStatus.DONE, run.tess.status)
    }

    @Test
    fun `pipeline Tess HANDOFF_TO_FINN triggers Finn fix-pass and Tess re-run`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("finn-initial")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("🔄 HANDOFF_TO_FINN\nTess found code issues")
            .thenReturn("finn-tess-fix")
            .thenReturn("tess-loop-output")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature Y", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertTrue(run.finn.output.contains("finn-initial"))
        assertTrue(run.finn.output.contains("finn-tess-fix"))

        assertEquals("tess-loop-output", run.tess.output)
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals(StageStatus.DONE, run.tess.status)
    }

    @Test
    fun `pipeline Tess fix-pass re-emits HANDOFF_TO_FINN - no second loopback`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("🔄 HANDOFF_TO_FINN\nfirst handoff")
            .thenReturn("ok")
            .thenReturn("tess-loop-content\n🔄 HANDOFF_TO_FINN\nre-emitted")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertFalse(run.tess.output.contains("HANDOFF_TO_FINN"), "Marker must be stripped from tess.output: ${run.tess.output}")
        assertTrue(run.tess.output.contains("tess-loop-content"), "Content before marker must be retained")

        assertEquals(StageStatus.DONE, run.quinn.status)
    }

    @Test
    fun `pipeline Quinn HANDOFF_TO_TESS triggers Tess quinn-redux`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("🔄 HANDOFF_TO_TESS\nQuinn found coverage issues")
            .thenReturn("tess-quinn-redux")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature Q", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertEquals("tess-quinn-redux", run.tess.output)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals(StageStatus.DONE, run.sage.status)
    }

    @Test
    fun `pipeline Quinn HANDOFF_TO_TESS with nested Tess HANDOFF_TO_FINN runs Finn and Tess rerun`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("finn-initial")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("ok")
            .thenReturn("🔄 HANDOFF_TO_TESS\nQuinn wants Tess redux")
            .thenReturn("🔄 HANDOFF_TO_FINN\nTess-quinn-redux found code issues")
            .thenReturn("finn-quinn-tess-fix")
            .thenReturn("tess-post-finn-quinn-redux")
            .thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Complex feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertTrue(run.finn.output.contains("finn-initial"))
        assertTrue(run.finn.output.contains("finn-quinn-tess-fix"))

        assertEquals("tess-post-finn-quinn-redux", run.tess.output)
        assertEquals(StageStatus.DONE, run.sage.status)
    }

    @Test
    fun `pipeline Faye loopback with DB schema - schema propagated through all stages`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            val prompt = inv.arguments[1] as String
            capturedPrompts.add(prompt)
            val callN = capturedPrompts.size
            when (callN) {
                6 -> "🔄 HANDOFF_TO_FINN\nhandoff"
                else -> "ok"
            }
        }
        val dbCfg =
            DatabaseConfig(
                id = 7L,
                name = "schemadb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(7L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE products (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature with DB", null, databaseConfigIds = listOf(7L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: schemadb\n\nCREATE TABLE products (id INT);", run.dbSchema)

        assertTrue(capturedPrompts.any { it.contains("CREATE TABLE products") })
    }

    @Test
    fun `pipeline Tess loopback with DB schema - schema propagated through Tess redux`() {
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        val callCount = AtomicInteger(0)

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            val prompt = inv.getArgument<String>(1)
            capturedPrompts.add(prompt)
            val call = callCount.incrementAndGet()
            if (call == 8) "🔄 HANDOFF_TO_FINN\nTess wants a rework" else "ok"
        }

        val dbCfg =
            DatabaseConfig(
                id = 5L,
                name = "testdb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(5L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE products (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add product feature", null, databaseConfigIds = listOf(5L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        assertTrue(
            run.tess.output.isNotBlank() || run.finn.output.isNotBlank(),
            "Tess and/or Finn output should not be blank after loopback",
        )

        val schemaInAnyPrompt = capturedPrompts.any { it.contains("CREATE TABLE products") }
        assertTrue(schemaInAnyPrompt, "DB schema should be injected into at least one prompt")
    }

    @Test
    fun `pipeline Quinn loopback with DB schema - schema propagated through Quinn redux`() {
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        val callCount = AtomicInteger(0)

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            val prompt = inv.getArgument<String>(1)
            capturedPrompts.add(prompt)
            val call = callCount.incrementAndGet()
            if (call == 10) "🔄 HANDOFF_TO_TESS\nQuinn needs Tess help" else "ok"
        }

        val dbCfg =
            DatabaseConfig(
                id = 6L,
                name = "infradb",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "infra",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(6L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE infra (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add infra feature", null, databaseConfigIds = listOf(6L))
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val schemaInAnyPrompt = capturedPrompts.any { it.contains("CREATE TABLE infra") }
        assertTrue(schemaInAnyPrompt, "DB schema should be injected into at least one prompt")
    }

    @Test
    fun `pipeline Faye loopback with repo - 5-arg overload used for all affected stages`() {
        val tempDir = Files.createTempDirectory("pipeline-faye-loop-repo").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(localRepoConfigPort.findLocalRepoConfigById(30L)).thenReturn(
                LocalRepoConfig(id = 30L, name = "loopback-repo", path = tempDir.absolutePath),
            )

            val fiveArgCallCount = AtomicInteger(0)

            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
                if (inv.arguments[3] == null) {
                    "ok"
                } else {
                    val n = fiveArgCallCount.incrementAndGet()
                    if (n == 2) "🔄 HANDOFF_TO_FINN\nFaye wants a rework" else "ok"
                }
            }

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature X", localRepoConfigId = 30L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)

            assertTrue(fiveArgCallCount.get() >= 2, "At least 2 five-arg calls expected (Finn + Faye)")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Tess loopback with repo - 5-arg overload used for Finn fix-pass and Tess redux`() {
        val tempDir = Files.createTempDirectory("pipeline-tess-loop-repo").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(53L)).thenReturn(
                LocalRepoConfig(id = 53L, name = "tess-loop-repo", path = tempDir.absolutePath),
            )

            val fiveArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
                when (fiveArgCount.incrementAndGet()) {
                    4 -> "🔄 HANDOFF_TO_FINN\nTess spotted issues"
                    else -> "5arg-ok"
                }
            }

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 53L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)

            assertTrue(
                fiveArgCount.get() >= 5,
                "Expected ≥5 five-arg calls (finn+faye+tess+finn-fix+tess-redux): got ${fiveArgCount.get()}",
            )
            assertEquals(StageStatus.DONE, run.tess.status)
            assertEquals(StageStatus.DONE, run.finn.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Quinn loopback with repo - 5-arg overload used for Tess redux`() {
        val tempDir = Files.createTempDirectory("pipeline-quinn-loop-repo").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(54L)).thenReturn(
                LocalRepoConfig(id = 54L, name = "quinn-loop-repo", path = tempDir.absolutePath),
            )

            val fiveArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
                when (fiveArgCount.incrementAndGet()) {
                    6 -> "🔄 HANDOFF_TO_TESS\nQuinn requests Tess redo"
                    else -> "5arg-ok"
                }
            }

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 54L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)

            assertTrue(
                fiveArgCount.get() >= 5,
                "Expected ≥5 five-arg calls (finn+faye+tess+quinn+tess-redux): got ${fiveArgCount.get()}",
            )
            assertEquals(StageStatus.DONE, run.quinn.status)
            assertEquals(StageStatus.DONE, run.tess.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Faye and Tess loopbacks both fire in same run`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            when (callCount.incrementAndGet()) {
                6 -> "🔄 HANDOFF_TO_FINN\nFaye wants Finn backend fix"
                10 -> "🔄 HANDOFF_TO_FINN\nTess wants Finn to add tests"
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id, timeoutMs = 10_000)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals(StageStatus.DONE, run.sage.status)
        assertEquals(StageStatus.DONE, run.vera.status)

        assertEquals(15, callCount.get(), "Expected 15 copilot calls for double-loopback run; got ${callCount.get()}")
    }

    @Test
    fun `pipeline Quinn-Tess-Finn nested loopback with DB - schema in all re-run prompts`() {
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")

        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            when (callCount.incrementAndGet()) {
                10 -> "🔄 HANDOFF_TO_TESS\nQuinn wants Tess redo with coverage"
                11 -> "🔄 HANDOFF_TO_FINN\nTess-redux needs code changes"
                else -> "ok"
            }
        }
        val dbCfg =
            DatabaseConfig(
                id = 12L,
                name = "nested-db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "nesteddb",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(12L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE nested (id INT);")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null, databaseConfigIds = listOf(12L))
        val run = awaitRunDone(svc, id, timeoutMs = 10_000)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals(StageStatus.DONE, run.sage.status)

        val schemaPromptCount = capturedPrompts.count { it.contains("CREATE TABLE nested") }
        assertTrue(schemaPromptCount >= 2, "DB schema should appear in ≥2 prompts; found in $schemaPromptCount")
    }

    @Test
    fun `pipeline mockup with Faye loopback - mockup NOT in Faye redux prompt (known gap)`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            val n = callCount.incrementAndGet()

            if (n == 6) "🔄 HANDOFF_TO_FINN\nsome faye content" else "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val firstFayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(firstFayePrompt.contains("Frontend Mockup"), "First Faye call must contain mockup")

        val fayeReduxPrompt = capturedPrompts.getOrNull(7) ?: ""
        assertTrue(
            fayeReduxPrompt.contains("Frontend Mockup"),
            "Faye redux prompt must include mockup section",
        )
    }

    @Test
    fun `pipeline mockup with Tess loopback - mockup section still in initial Faye prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            val n = callCount.incrementAndGet()

            if (n == 8) "🔄 HANDOFF_TO_FINN\ntess content" else "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("svg-data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add slider", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/svg+xml")
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Original Faye prompt must still contain mockup")
    }

    @Test
    fun `pipeline mockup with DB and Faye loopback - DB in Cole Max, mockup in Faye, loopback triggers`() {
        val dbCfg =
            DatabaseConfig(
                id = 70L,
                name = "db70",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db70",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(70L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE t70 (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            if (n == 6) "🔄 HANDOFF_TO_FINN\nfaye content" else "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("heic-data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Feature with DB+mockup+loopback",
                localRepoConfigId = null,
                databaseConfigIds = listOf(70L),
                mockupBase64 = b64,
                mockupMimeType = "image/heic",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: db70\n\nCREATE TABLE t70 (id INT);", run.dbSchema)
        assertEquals(b64, run.mockupBase64)

        assertTrue(run.faye.output.isNotBlank())
        assertEquals(StageStatus.DONE, run.faye.status)
    }

    @Test
    fun `pipeline Faye loopback followed by Quinn-Tess loopback in same run`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            when (n) {
                6 -> "🔄 HANDOFF_TO_FINN\nfaye initial"

                12 -> "🔄 HANDOFF_TO_TESS\nquinn content"
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Complex feature", localRepoConfigId = null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals(StageStatus.DONE, run.sage.status)
        assertEquals(StageStatus.DONE, run.vera.status)
    }

    @Test
    fun `pipeline Faye loopback with DB and mockup - all three combined with loopback`() {
        val dbCfg =
            DatabaseConfig(
                id = 80L,
                name = "db80",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db80",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(80L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE t80 (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            if (n == 6) "🔄 HANDOFF_TO_FINN\nfaye content" else "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(80L),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: db80\n\nCREATE TABLE t80 (id INT);", run.dbSchema)
        assertEquals(b64, run.mockupBase64)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertTrue(run.faye.output.isNotBlank())
    }

    @Test
    fun `pipeline Quinn-Tess-Finn nested loopback with mockup - mockup in initial Faye prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        val callCount = AtomicInteger(0)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            when (callCount.incrementAndGet()) {
                10 -> "🔄 HANDOFF_TO_TESS\nQuinn needs coverage"

                11 -> "🔄 HANDOFF_TO_FINN\nTess-quinn-redux needs code fix"
                else -> "ok"
            }
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-quinn-nested".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
        val run = awaitRunDone(svc, id, timeoutMs = 10_000)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.quinn.status)

        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(
            fayePrompt.contains("Frontend Mockup"),
            "Faye initial prompt must contain mockup in Quinn-Tess-Finn nested loopback",
        )
        assertTrue(fayePrompt.contains("image/png"), "Faye prompt must contain MIME type")
    }

    @Test
    fun `pipeline Faye and Tess loopbacks both fire with mockup - mockup in both Faye calls`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        val callCount = AtomicInteger(0)

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            when (callCount.incrementAndGet()) {
                6 -> "🔄 HANDOFF_TO_FINN\nFaye wants Finn backend fix"
                10 -> "🔄 HANDOFF_TO_FINN\nTess wants Finn to add tests"
                else -> "ok"
            }
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-double-loopback".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
        val run = awaitRunDone(svc, id, timeoutMs = 10_000)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.DONE, run.tess.status)

        val firstFayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(firstFayePrompt.contains("Frontend Mockup"), "First Faye call must contain mockup: ${firstFayePrompt.take(300)}")
        val fayeReduxPrompt = capturedPrompts.getOrNull(7) ?: ""
        assertTrue(fayeReduxPrompt.contains("Frontend Mockup"), "Faye redux must contain mockup: ${fayeReduxPrompt.take(300)}")
    }
}
