package com.stacklens.application.service

import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class LoadTestAnalysisServiceStartupHealCoverageTest {
    private val analysisRepo = mock<LoadTestAnalysisPort>()
    private val i18n = mock<I18nPort>()
    private val service =
        LoadTestAnalysisService(
            LoadTestAnalysisServiceDeps(
                analysisRepo = analysisRepo,
                gitlabConfigRepo = mock(),
                copilotPort = mock(),
                gitlabService = mock(),
                i18n = i18n,
                startupHealer = StartupHealer(),
                localRepoConfigRepo = mock(),
                githubRepoConfigRepo = mock(),
                scanService = mock(),
                aiRunner = mock(),
                processRunPort = mock(),
                k6Controller = mock(),
            ),
            FixedClockPort(java.time.Instant.parse("2026-09-03T07:30:00Z")),
        )

    @Test
    fun `a stale analysis is cancelled, timestamped and persisted on startup`() {
        val running = LoadTestAnalysis(id = 1L, status = AnalysisStatus.RUNNING)
        val pending = LoadTestAnalysis(id = 2L, status = AnalysisStatus.PENDING)
        val done = LoadTestAnalysis(id = 3L, status = AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(running, pending, done))
        whenever(i18n.t("load_test.be_aborted_restart")).thenReturn("aborted by restart")

        service.resetStaleAnalyses()

        assertEquals(AnalysisStatus.CANCELLED, running.status)
        assertEquals("aborted by restart", running.errorMessage)
        assertEquals(LocalDateTime.of(2026, 9, 3, 7, 30), running.completedAt)
        verify(analysisRepo).saveLoadTestAnalysis(running)
        verify(analysisRepo).saveLoadTestAnalysis(pending)
        verify(analysisRepo, never()).saveLoadTestAnalysis(done)
    }
}
