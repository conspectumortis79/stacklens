package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoggingFixServiceStageStatusCoverageTest {
    private val repository: LoggingFixPort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()

    private val service =
        LoggingFixService(
            repository,
            mock<LoggingFixPartialUpdatePort>(),
            localRepoConfigRepo,
            mock<AiUsageContextPort>(),
            mock<LoggingFixStage>(),
            mock<LoggingFixStage>(),
            mock<LoggingFixStage>(),
            mock<LoggingFixStage>(),
            catalogBackedLoggingFixMarkers(),
            JavaFileSystemAdapter(),
            mock<TransactionPort>(),
            FixedClockPort(0L),
            com.stacklens.adapter.out.process.SystemWorkerStarterPort,
        )

    @Test
    fun `a finished run with blank buffers still reports every stage as pending`() {
        whenever(repository.findLoggingFixAnalysisById(4L)).thenReturn(
            LoggingFixAnalysis(
                id = 4L,
                featureSlug = "slug",
                status = LoggingFixAnalysis.STATUS_DONE,
                logText = "text",
                selectedCategories = "",
                stageDiagnose = "",
            ),
        )

        val outputs = service.getStageOutputs(4L)!!

        assertEquals("PENDING", outputs.stages.getValue("diagnose").status)
        assertNull(service.getWriteFilesPreview(4L)?.repoPath)
    }

    @Test
    fun `a cancelled run reports its stages as cancelled`() {
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(
            LoggingFixAnalysis(
                id = 5L,
                featureSlug = "slug",
                status = LoggingFixAnalysis.STATUS_CANCELLED,
                logText = "text",
                selectedCategories = "",
                stagePlan = null,
            ),
        )

        val outputs = service.getStageOutputs(5L)!!

        assertEquals("PENDING", outputs.stages.getValue("plan").status)
    }
}
