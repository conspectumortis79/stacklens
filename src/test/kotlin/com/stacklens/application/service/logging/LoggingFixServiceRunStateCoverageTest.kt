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
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

class LoggingFixServiceRunStateCoverageTest {
    private val repository = mock<LoggingFixPort>()

    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()

    private fun service(): LoggingFixService =
        LoggingFixService(
            repository = repository,
            partialUpdater = mock<LoggingFixPartialUpdatePort>(),
            localRepoConfigRepo = localRepoConfigRepo,
            aiUsageContext = mock<AiUsageContextPort>(),
            diagnoseStage = mock(),
            planStage = mock(),
            codeStage = mock(),
            testsStage = mock(),
            tx = mock<TransactionPort>(),
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun analysis(
        id: Long,
        repoConfigId: Long?,
    ) = LoggingFixAnalysis(
        id = id,
        featureSlug = "slug",
        logText = "log",
        selectedCategories = "cat",
        localRepoConfigId = repoConfigId,
    )

    private fun withRun(
        service: LoggingFixService,
        id: Long,
        state: Any,
    ) {
        val field = LoggingFixService::class.java.getDeclaredField("runs")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(service) as ConcurrentHashMap<Long, Any>).put(id, state)
    }

    private fun runState(
        analysis: LoggingFixAnalysis,
        proposedFiles: List<ProposedFile>,
    ): Any {
        val type = Class.forName("com.stacklens.application.service.logging.LoggingFixService\$RunState")
        val constructor =
            type.declaredConstructors.first { it.parameterCount == 9 }
        constructor.isAccessible = true
        return constructor.newInstance(
            analysis,
            false,
            proposedFiles,
            null,
            "",
            StringBuilder(),
            StringBuilder(),
            StringBuilder(),
            StringBuilder(),
        )
    }

    @Test
    fun `a tracked run without a repository still previews its files`() {
        val service = service()
        val state = runState(analysis(11L, null), listOf(ProposedFile(path = "a/B.kt", content = "x", description = "d")))
        withRun(service, 11L, state)

        val preview = service.getWriteFilesPreview(11L)

        assertNull(preview?.repoPath)
        assertEquals("a/B.kt", preview!!.files.single().path)
    }

    @Test
    fun `a tracked run previews the repository path when the config still exists`() {
        val service = service()
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(LocalRepoConfig(name = "r", path = "/tmp/r"))
        withRun(service, 12L, runState(analysis(12L, 7L), emptyList()))

        assertEquals("/tmp/r", service.getWriteFilesPreview(12L)?.repoPath)
    }

    @Test
    fun `a tracked run whose repository vanished previews without a path`() {
        val service = service()
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(null)
        withRun(service, 13L, runState(analysis(13L, 7L), emptyList()))

        assertNull(service.getWriteFilesPreview(13L)?.repoPath)
    }

    @Test
    fun `an unknown id has no preview at all`() {
        whenever(repository.findLoggingFixAnalysisById(any())).thenReturn(null)

        assertNull(service().getWriteFilesPreview(99L))
    }

    @Test
    fun `a stored analysis is used when the run is gone`() {
        whenever(repository.findLoggingFixAnalysisById(any())).thenReturn(analysis(14L, 7L))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(LocalRepoConfig(name = "r", path = "/tmp/r"))

        val preview = service().getWriteFilesPreview(14L)

        assertEquals("/tmp/r", preview?.repoPath)
        assertTrue(preview!!.files.isEmpty())
    }

    @Test
    fun `a write target without a parent directory is skipped without crashing`() {
        val service = service()
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(LocalRepoConfig(name = "r", path = "."))
        withRun(
            service,
            15L,
            runState(analysis(15L, 7L), listOf(ProposedFile(path = "", content = "", description = null))),
        )

        val result = service.writeFiles(15L)

        assertEquals(false, result.success)
        assertTrue(result.error!!.contains("Fehler beim Schreiben"), result.error.orEmpty())
    }

    @Test
    fun `a start request without a repository reference only checks the log`() {
        val service = service()

        assertEquals(emptyList<String>(), service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = null)))
    }
}
