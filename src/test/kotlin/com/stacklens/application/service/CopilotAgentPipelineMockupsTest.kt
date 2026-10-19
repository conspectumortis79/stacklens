package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class CopilotAgentPipelineMockupsTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline with PNG mockup - mockup section injected into Faye prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(pngBytes)

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add gallery feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye prompt must contain mockup section: ${fayePrompt.take(300)}")
        assertTrue(fayePrompt.contains("image/png"), "Faye prompt must contain MIME type")
        assertTrue(fayePrompt.contains(b64), "Faye prompt must embed the base64 data")
    }

    @Test
    fun `pipeline with SVG mockup - SVG source embedded in Faye prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val svgContent = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='200' height='100' fill='blue'/></svg>"
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(svgContent.toByteArray())

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add hero section", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/svg+xml")
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("svg"), "Faye prompt must contain SVG markup: ${fayePrompt.take(300)}")
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye prompt must contain mockup header")
        assertTrue(fayePrompt.contains(svgContent.take(50)), "SVG source must appear in prompt")
    }

    @Test
    fun `pipeline without mockup - Faye prompt has no mockup section`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add feature", localRepoConfigId = null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertFalse(
            fayePrompt.contains("## Frontend Mockup (Faye visual reference)\n"),
            "Faye prompt must not contain injected mockup section when no mockup provided",
        )
        assertFalse(fayePrompt.contains("{mockupSection}"), "Placeholder must be replaced (not left unresolved)")
    }

    @Test
    fun `pipeline jira-mode with mockup - mockup injected into Faye prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake-image".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "PROJ-77\nSummary: New gallery page",
                ticketKey = "PROJ-77",
                localRepoConfigId = null,
                mockupBase64 = b64,
                mockupMimeType = "image/jpeg",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye prompt must contain mockup section in jira mode")
        assertTrue(fayePrompt.contains("image/jpeg"), "MIME type must appear in prompt")
    }

    @Test
    fun `pipeline free-text no-repo no-DB with mockup - all four happy path`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-bytes".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Add dashboard feature",
                localRepoConfigId = null,
                databaseConfigIds = emptyList(),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(b64, run.mockupBase64)
        assertEquals("image/png", run.mockupMimeType)
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye prompt must embed mockup")
        listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
            .forEach { s -> assertEquals(StageStatus.DONE, s.status, "${s.name} should be DONE") }
    }

    @Test
    fun `pipeline free-text no-repo with-DB with mockup - all four happy path`() {
        val dbCfg =
            DatabaseConfig(
                id = 60L,
                name = "db60",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db60",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(60L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE t60 (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("jpg-bytes".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Add invoice feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(60L),
                mockupBase64 = b64,
                mockupMimeType = "image/jpeg",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("## Database: db60\n\nCREATE TABLE t60 (id INT);", run.dbSchema)
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye prompt must embed mockup when DB also set")
        assertTrue(fayePrompt.contains("image/jpeg"), "MIME type must appear in Faye prompt")
    }

    @Test
    fun `pipeline free-text with-repo no-DB with mockup - SVG - happy path`() {
        val tempDir = Files.createTempDirectory("pipeline-repo-svg-mockup").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(61L),
            ).thenReturn(LocalRepoConfig(id = 61L, name = "repo61", path = tempDir.absolutePath))

            val svgContent = "<svg xmlns='http://www.w3.org/2000/svg'><circle cx='50' cy='50' r='40'/></svg>"
            val b64 =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(svgContent.toByteArray())

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Add map view", localRepoConfigId = 61L, mockupBase64 = b64, mockupMimeType = "image/svg+xml")
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(b64, run.mockupBase64)
            assertEquals("image/svg+xml", run.mockupMimeType)
            assertEquals("5arg-ok", run.finn.output)
            listOf(run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera)
                .forEach { s -> assertEquals(StageStatus.DONE, s.status, "${s.name} should be DONE") }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline free-text with-repo with-DB with mockup - all four happy path`() {
        val tempDir = Files.createTempDirectory("pipeline-all-four").toFile()
        try {
            val dbCfg =
                DatabaseConfig(
                    id = 62L,
                    name = "db62",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "localhost",
                    port = 5432,
                    databaseName = "db62",
                    username = "u",
                    password = "p",
                )
            whenever(dbConfigPort.findDatabaseConfigById(62L)).thenReturn(dbCfg)
            whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE t62 (id INT);")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(62L),
            ).thenReturn(LocalRepoConfig(id = 62L, name = "repo62", path = tempDir.absolutePath))

            val b64 =
                java.util.Base64
                    .getEncoder()
                    .encodeToString("tiff-bytes".toByteArray())
            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id =
                svc.start(
                    "Full feature",
                    localRepoConfigId = 62L,
                    databaseConfigIds = listOf(62L),
                    mockupBase64 = b64,
                    mockupMimeType = "image/tiff",
                )
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals("## Database: db62\n\nCREATE TABLE t62 (id INT);", run.dbSchema)
            assertEquals(b64, run.mockupBase64)
            assertEquals("image/tiff", run.mockupMimeType)
            assertEquals("5arg-ok", run.finn.output)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline jira-mode with-repo with-DB with mockup - all five dimensions combined`() {
        val tempDir = Files.createTempDirectory("pipeline-jira-all-five").toFile()
        try {
            val dbCfg =
                DatabaseConfig(
                    id = 63L,
                    name = "db63",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "localhost",
                    port = 5432,
                    databaseName = "db63",
                    username = "u",
                    password = "p",
                )
            whenever(dbConfigPort.findDatabaseConfigById(63L)).thenReturn(dbCfg)
            whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE t63 (id INT);")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(63L),
            ).thenReturn(LocalRepoConfig(id = 63L, name = "repo63", path = tempDir.absolutePath))

            val b64 =
                java.util.Base64
                    .getEncoder()
                    .encodeToString("heic-bytes".toByteArray())
            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id =
                svc.startFromJira(
                    ticketData = "JIRA-63\nSummary: Ultimate combo",
                    ticketKey = "JIRA-63",
                    localRepoConfigId = 63L,
                    databaseConfigIds = listOf(63L),
                    mockupBase64 = b64,
                    mockupMimeType = "image/heic",
                )
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals("## Database: db63\n\nCREATE TABLE t63 (id INT);", run.dbSchema)
            assertEquals(b64, run.mockupBase64)
            assertEquals("image/heic", run.mockupMimeType)
            assertEquals("4arg-ok", run.lena.output)
            assertEquals("5arg-ok", run.finn.output)
            listOf(run.lena, run.nora, run.cole, run.max, run.finn, run.faye, run.tess, run.quinn, run.sage, run.vera, run.rex)
                .forEach { s -> assertEquals(StageStatus.DONE, s.status, "${s.name} should be DONE") }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline with HEIF mockup - mime type preserved`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("heif-bytes".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/heif")
        val run = awaitRunDone(svc, id)
        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("image/heif", run.mockupMimeType)
    }

    @Test
    fun `pipeline with TIFF mockup - mime type preserved`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("tiff-bytes".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/tiff")
        val run = awaitRunDone(svc, id)
        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals("image/tiff", run.mockupMimeType)
    }

    @Test
    fun `pipeline DB fetch fails and mockup present - pipeline continues with mockup in Faye`() {
        val dbCfg =
            DatabaseConfig(
                id = 72L,
                name = "db72",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db72",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(72L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenThrow(RuntimeException("DB unreachable"))
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument<String>(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-bytes".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(72L),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertTrue(run.dbSchema.isBlank(), "Schema must be blank when DB fetch fails")
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Mockup must still be injected even when DB fails")
    }

    @Test
    fun `pipeline DB config not found and mockup present - pipeline completes with mockup`() {
        whenever(dbConfigPort.findDatabaseConfigById(73L)).thenReturn(null)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(73L),
                mockupBase64 = b64,
                mockupMimeType = "image/jpeg",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertTrue(run.dbSchema.isBlank(), "Schema must be blank when DB config not found")
        assertEquals(b64, run.mockupBase64)
    }

    @Test
    fun `pipeline two concurrent runs with different mockups complete independently`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val b64a =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-a".toByteArray())
        val b64b =
            java.util.Base64
                .getEncoder()
                .encodeToString("svg-b".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")

        val idA = svc.start("Feature A", localRepoConfigId = null, mockupBase64 = b64a, mockupMimeType = "image/png")
        val idB = svc.start("Feature B", localRepoConfigId = null, mockupBase64 = b64b, mockupMimeType = "image/svg+xml")

        val runA = awaitRunDone(svc, idA)
        val runB = awaitRunDone(svc, idB)

        assertEquals(b64a, runA.mockupBase64)
        assertEquals("image/png", runA.mockupMimeType)
        assertEquals(b64b, runB.mockupBase64)
        assertEquals("image/svg+xml", runB.mockupMimeType)
        assertEquals(PipelineStatus.DONE, runA.status)
        assertEquals(PipelineStatus.DONE, runB.status)
    }

    @Test
    fun `pipeline run without mockup followed by run with mockup - independent state`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("jpg-data".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")

        val idNoMockup = svc.start("Feature A", localRepoConfigId = null)
        val runNoMockup = awaitRunDone(svc, idNoMockup)
        assertNull(runNoMockup.mockupBase64)
        assertNull(runNoMockup.mockupMimeType)

        val idWithMockup = svc.start("Feature B", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/jpeg")
        val runWithMockup = awaitRunDone(svc, idWithMockup)
        assertEquals(b64, runWithMockup.mockupBase64)
        assertEquals("image/jpeg", runWithMockup.mockupMimeType)
    }

    @Test
    fun `pipeline Nora prompt without DB without mockup - no extra sections injected`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add search feature", localRepoConfigId = null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""

        assertFalse(
            noraPrompt.contains("## Database schema\n"),
            "Nora prompt must not contain injected DB section when no DB configured",
        )
        assertFalse(
            noraPrompt.contains("## Frontend Mockup (Faye visual reference)\n"),
            "Nora prompt must not contain injected mockup section when no mockup provided",
        )
    }

    @Test
    fun `pipeline Nora prompt with mockup - mockup section injected into Nora prompt`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-nora".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add gallery", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""
        assertTrue(
            noraPrompt.contains("Frontend Mockup"),
            "Nora prompt must contain mockup section: ${noraPrompt.take(400)}",
        )
        assertTrue(noraPrompt.contains("image/png"), "Nora prompt must contain MIME type")
    }

    @Test
    fun `pipeline Nora prompt with both DB and mockup - both sections present`() {
        val dbCfg =
            DatabaseConfig(
                id = 202L,
                name = "nora-both-db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "noraboth",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(202L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE nora_both (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-both".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Add feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(202L),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""
        assertTrue(noraPrompt.contains("## Database schema\n"), "Nora prompt must contain injected DB section")
        assertTrue(noraPrompt.contains("CREATE TABLE nora_both"), "Nora prompt must contain DB schema content")
        assertTrue(noraPrompt.contains("Frontend Mockup"), "Nora prompt must contain mockup section")
    }

    @Test
    fun `pipeline jira-mode Nora prompt receives DB and mockup - both sections present`() {
        val dbCfg =
            DatabaseConfig(
                id = 203L,
                name = "jira-nora-db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "jiranora",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(203L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE jira_nora (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-jira-nora".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.startFromJira(
                ticketData = "PROJ-200\nSummary: Jira feature with DB and mockup",
                ticketKey = "PROJ-200",
                localRepoConfigId = null,
                databaseConfigIds = listOf(203L),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""
        assertTrue(noraPrompt.contains("## Database schema\n"), "Jira-mode Nora prompt must contain injected DB section")
        assertTrue(noraPrompt.contains("CREATE TABLE jira_nora"), "Jira-mode Nora prompt must contain schema content")
        assertTrue(noraPrompt.contains("Frontend Mockup"), "Jira-mode Nora prompt must contain mockup section")
    }

    @Test
    fun `pipeline DB and mockup injected into all receiving stages - Nora Cole Max and Faye`() {
        val dbCfg =
            DatabaseConfig(
                id = 204L,
                name = "full-check-db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "fullcheck",
                username = "u",
                password = "p",
            )
        whenever(dbConfigPort.findDatabaseConfigById(204L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("CREATE TABLE full_check (id INT);")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val capturedPrompts = mutableListOf<String>()
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            capturedPrompts.add(inv.getArgument(1))
            "ok"
        }

        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-full-check".toByteArray())
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id =
            svc.start(
                "Add feature",
                localRepoConfigId = null,
                databaseConfigIds = listOf(204L),
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)

        val noraPrompt = capturedPrompts.getOrNull(1) ?: ""
        assertTrue(noraPrompt.contains("## Database schema\n"), "Nora must receive DB schema")
        assertTrue(noraPrompt.contains("Frontend Mockup"), "Nora must receive mockup section")
        val colePrompt = capturedPrompts.getOrNull(2) ?: ""
        assertTrue(colePrompt.contains("CREATE TABLE full_check"), "Cole must receive DB schema")
        val maxPrompt = capturedPrompts.getOrNull(3) ?: ""
        assertTrue(maxPrompt.contains("CREATE TABLE full_check"), "Max must receive DB schema")
        val fayePrompt = capturedPrompts.getOrNull(5) ?: ""
        assertTrue(fayePrompt.contains("Frontend Mockup"), "Faye must receive mockup section")
    }
}
