package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class LoggingFixServiceStateTest {
    private val repository = mock<LoggingFixPort>()

    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()

    private val service =
        LoggingFixService(
            repository = repository,
            partialUpdater = mock<LoggingFixPartialUpdatePort>(),
            localRepoConfigRepo = localRepoConfigRepo,
            aiUsageContext = mock<AiUsageContextPort>(),
            diagnoseStage = mock(),
            planStage = mock(),
            codeStage = mock(),
            testsStage = mock(),
            tx =
                object : TransactionPort {
                    override fun <T> inTransaction(block: () -> T): T = block()

                    override fun afterCommit(block: () -> Unit) {
                        block()
                    }
                },
            clock = FixedClockPort(Instant.parse("2026-08-24T10:00:00Z")),
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
        )

    private fun analysis(
        id: Long,
        status: String,
        repoConfigId: Long? = null,
    ) = LoggingFixAnalysis(
        id = id,
        featureSlug = "slug",
        logText = "ERROR something",
        selectedCategories = "error",
        localRepoConfigId = repoConfigId,
        status = status,
    )

    @Test
    fun `a finished analysis without stage output reports a pending stage`() {
        whenever(repository.findLoggingFixAnalysisById(30L))
            .thenReturn(analysis(30L, LoggingFixAnalysis.STATUS_DONE))

        val outputs = service.getStageOutputs(30L)

        assertEquals(LoggingFixAnalysis.STATUS_DONE, outputs!!.status)
        assertEquals(
            "PENDING",
            outputs.stages.values
                .first()
                .status,
        )
        assertTrue(outputs.stages.values.all { it.output == "" })
    }

    @Test
    fun `a running analysis reports the running stage state`() {
        whenever(repository.findLoggingFixAnalysisById(31L))
            .thenReturn(analysis(31L, LoggingFixAnalysis.STATUS_RUNNING))

        assertEquals(
            "RUNNING",
            service
                .getStageOutputs(31L)!!
                .stages.values
                .first()
                .status,
        )
    }

    @Test
    fun `an unknown analysis has no stage outputs`() {
        whenever(repository.findLoggingFixAnalysisById(32L)).thenReturn(null)

        assertNull(service.getStageOutputs(32L))
    }

    @Test
    fun `the preview of a stored analysis without a run keeps an empty file list`() {
        whenever(repository.findLoggingFixAnalysisById(33L))
            .thenReturn(analysis(33L, LoggingFixAnalysis.STATUS_DONE, repoConfigId = 5L))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(5L)).thenReturn(null)

        val preview = service.getWriteFilesPreview(33L)

        assertNull(preview?.repoPath)
        assertTrue(preview!!.files.isEmpty())
    }

    @Test
    fun `the preview of an unknown analysis stays empty`() {
        whenever(repository.findLoggingFixAnalysisById(34L)).thenReturn(null)

        assertNull(service.getWriteFilesPreview(34L))
    }

    @Test
    fun `a repository path that is no directory refuses the write`() {
        val file = Files.createTempFile("loggingfix", "blocker")
        whenever(repository.findLoggingFixAnalysisById(35L))
            .thenReturn(analysis(35L, LoggingFixAnalysis.STATUS_DONE, repoConfigId = 6L))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(6L))
            .thenReturn(LocalRepoConfig(id = 6L, name = "pfad", path = file.toString()))

        val result = service.writeFiles(35L)

        assertTrue(!result.success)
        assertTrue(result.error!!.startsWith("Repository-Pfad existiert nicht"), result.error)
        Files.deleteIfExists(file)
    }

    @Test
    fun `a stored analysis without proposed files reports nothing to write`() {
        whenever(repository.findLoggingFixAnalysisById(36L))
            .thenReturn(analysis(36L, LoggingFixAnalysis.STATUS_DONE, repoConfigId = 7L))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L))
            .thenReturn(LocalRepoConfig(id = 7L, name = "pfad", path = System.getProperty("java.io.tmpdir")))

        val result = service.writeFiles(36L)

        assertEquals("Keine Dateien zum Schreiben erkannt", result.error)
    }

    @Test
    fun `starting without a repository configuration keeps the path unset`() {
        whenever(repository.saveLoggingFixAnalysis(any())).thenAnswer { invocation -> invocation.arguments[0] as LoggingFixAnalysis }

        val id = service.startFix("ERROR boot", listOf("error"), null, "de")

        assertEquals(0L, id)
        assertNull(service.getStageOutputs(id)?.repoPath)
    }

    @Test
    fun `validation rejects a repository path below an existing file`(
        @TempDir dir: Path,
    ) {
        val blocker = dir.resolve("blocker.txt").also { Files.writeString(it, "x") }
        whenever(localRepoConfigRepo.findLocalRepoConfigById(8L))
            .thenReturn(LocalRepoConfig(id = 8L, name = "pfad", path = blocker.toString()))

        val problems =
            service.validateStartRequest(
                LoggingFixStartRequest(logText = "log", localRepoConfigId = 8L),
            )

        assertEquals(listOf("repository path does not exist: $blocker"), problems)
    }
}
