package com.stacklens.application.service

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.loadtest.AiAnalysisRunner
import com.stacklens.application.service.loadtest.K6ProcessController
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.LoadTestAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LoadTestAnalysisServiceTest {
    private val analysisRepo: LoadTestAnalysisPort = mock()
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val copilotPort: CopilotPort = mock()
    private val gitlabService: GitlabPort = mock()
    private val i18n: I18nPort = mock()
    private val startupHealer: StartupHealer = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val scanService: LocalRepoScanPort = mock()
    private val aiRunner: AiAnalysisRunner = mock()
    private val processRunPort: ProcessRunPort = mock()
    private val k6Controller: K6ProcessController = mock()

    private val service =
        LoadTestAnalysisService(
            LoadTestAnalysisServiceDeps(
                analysisRepo = analysisRepo,
                gitlabConfigRepo = gitlabConfigRepo,
                copilotPort = copilotPort,
                gitlabService = gitlabService,
                i18n = i18n,
                startupHealer = startupHealer,
                localRepoConfigRepo = localRepoConfigRepo,
                githubRepoConfigRepo = githubRepoConfigRepo,
                scanService = scanService,
                aiRunner = aiRunner,
                processRunPort = processRunPort,
                k6Controller = k6Controller,
            ),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun analysis(
        id: Long = 1L,
        status: String = "PENDING",
    ) = LoadTestAnalysis(
        id = id,
        status = status,
    )

    @Test
    fun `resetStaleAnalyses delegates to startupHealer`() {
        service.heal()
        verify(startupHealer).heal(
            logTag = eq("Lasttest-Analyse"),
            failedStatus = eq(AnalysisStatus.CANCELLED),
            findStale = any<() -> List<LoadTestAnalysis>>(),
            markFailed = any<(LoadTestAnalysis) -> Unit>(),
            save = any<(LoadTestAnalysis) -> Unit>(),
        )
    }

    @Test
    fun `resetStaleAnalyses findStale filter keeps only RUNNING and PENDING analyses`() {
        val findStaleCaptor = argumentCaptor<() -> List<LoadTestAnalysis>>()
        val markFailedCaptor = argumentCaptor<(LoadTestAnalysis) -> Unit>()
        val saveCaptor = argumentCaptor<(LoadTestAnalysis) -> Unit>()
        val running = analysis(1L, AnalysisStatus.RUNNING)
        val pending = analysis(2L, AnalysisStatus.PENDING)
        val completed = analysis(3L, AnalysisStatus.COMPLETED)
        val failed = analysis(4L, AnalysisStatus.FAILED)
        val analyzing = analysis(5L, AnalysisStatus.ANALYZING)
        whenever(analysisRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc())
            .thenReturn(listOf(running, pending, completed, failed, analyzing))
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        whenever(i18n.t("load_test.be_aborted_restart")).thenReturn("aborted")

        service.resetStaleAnalyses()
        verify(startupHealer).heal(
            logTag = eq("Lasttest-Analyse"),
            failedStatus = eq(AnalysisStatus.CANCELLED),
            findStale = findStaleCaptor.capture(),
            markFailed = markFailedCaptor.capture(),
            save = saveCaptor.capture(),
        )

        val staleFn = findStaleCaptor.lastValue
        val filtered = staleFn()
        assertEquals(2, filtered.size)
        assertTrue(filtered.contains(running))
        assertTrue(filtered.contains(pending))
        assertTrue(!filtered.contains(completed))
        assertTrue(!filtered.contains(failed))
        assertTrue(!filtered.contains(analyzing))

        for (s in filtered) {
            markFailedCaptor.lastValue(s)
        }
        assertEquals(AnalysisStatus.CANCELLED, running.status)
        assertEquals(AnalysisStatus.CANCELLED, pending.status)
        assertTrue(running.completedAt != null)
        assertEquals("aborted", running.errorMessage)
        assertEquals("aborted", pending.errorMessage)
    }

    @Test
    fun `getAnalysis returns the loaded analysis`() {
        val a = analysis(7L)
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(a)
        assertEquals(a, service.getAnalysis(7L))
    }

    @Test
    fun `getAnalysis returns null when not found`() {
        whenever(analysisRepo.findLoadTestAnalysisById(99L)).thenReturn(null)
        assertNull(service.getAnalysis(99L))
    }

    @Test
    fun `getLatestGitlabAnalysis returns the repo entry`() {
        val a = analysis(1L)
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(1L, 100L))
            .thenReturn(a)
        assertEquals(a, service.getLatestGitlabAnalysis(1L, 100L))
    }

    @Test
    fun `getLatestLocalAnalysis returns the repo entry`() {
        val a = analysis(2L)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(2L)).thenReturn(a)
        assertEquals(a, service.getLatestLocalAnalysis(2L))
    }

    @Test
    fun `getLatestGithubAnalysis returns the repo entry`() {
        val a = analysis(3L)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(3L)).thenReturn(a)
        assertEquals(a, service.getLatestGithubAnalysis(3L))
    }

    @Test
    fun `getAllAnalyses returns the repo listing`() {
        val list = listOf(analysis(1L), analysis(2L))
        whenever(analysisRepo.findRecentLoadTestAnalyses(200)).thenReturn(list)
        assertEquals(list, service.getAllAnalyses())
    }

    @Test
    fun `deleteAnalysis forwards the id to the repo`() {
        service.deleteAnalysis(7L)
        verify(analysisRepo).deleteLoadTestAnalysisById(7L)
    }

    @Test
    fun `cancelAnalysis marks analysis CANCELLED and interrupts the runner thread`() {
        val a = analysis(7L, "RUNNING")
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(a)
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        whenever(i18n.t(eq("load_test.be_cancelled"))).thenReturn("cancelled")

        service.cancelAnalysis(7L)

        assertEquals("CANCELLED", a.status)
        assertEquals("cancelled", a.errorMessage)
        assertNotNull(a.completedAt)
        verify(aiRunner).cancelRunningThread(7L)
    }

    @Test
    fun `cancelAnalysis is a no-op when the analysis is not found`() {
        whenever(analysisRepo.findLoadTestAnalysisById(99L)).thenReturn(null)

        service.cancelAnalysis(99L)

        verify(analysisRepo, never()).saveLoadTestAnalysis(any())
        verify(aiRunner, never()).cancelRunningThread(any())
    }

    @Test
    fun `cancelAnalysis does not flip COMPLETED analyses to CANCELLED`() {
        val a = analysis(7L, "COMPLETED")
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(a)

        service.cancelAnalysis(7L)

        assertEquals("COMPLETED", a.status)
        verify(analysisRepo, never()).saveLoadTestAnalysis(any())
        verify(aiRunner, never()).cancelRunningThread(any())
    }

    @Test
    fun `cancelAnalysis does not flip FAILED analyses to CANCELLED`() {
        val a = analysis(7L, "FAILED")
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(a)

        service.cancelAnalysis(7L)

        assertEquals("FAILED", a.status)
        verify(analysisRepo, never()).saveLoadTestAnalysis(any())
    }

    @Test
    fun `cancelAnalysis marks a PENDING analysis CANCELLED and interrupts the runner`() {
        val a = analysis(7L, "PENDING")
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(a)
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        whenever(i18n.t(eq("load_test.be_cancelled"))).thenReturn("cancelled")

        service.cancelAnalysis(7L)

        assertEquals("CANCELLED", a.status)
        verify(aiRunner).cancelRunningThread(7L)
    }

    @Test
    fun `startK6Script delegates to the K6ProcessController`() {
        service.startK6Script(99L, bearerToken = null, environmentName = null, customScript = null)
        verify(k6Controller).startK6Script(99L, null, null, null)
    }

    @Test
    fun `getK6Status delegates to the K6ProcessController`() {
        val stubController: K6ProcessController = mock()
        swapK6ControllerOnDeps(service, stubController)
        val expected =
            com.stacklens.application.port.inbound.loadtest.K6StatusResult(
                running = true,
                exitCode = null,
                output = "ok",
                error = "",
            )
        whenever(stubController.getK6Status(7L)).thenReturn(expected)

        val result = service.getK6Status(7L)
        assertEquals(expected, result)
        verify(stubController).getK6Status(7L)
    }

    @Test
    fun `stopK6Script delegates to the K6ProcessController`() {
        val stubController: K6ProcessController = mock()
        swapK6ControllerOnDeps(service, stubController)

        service.stopK6Script(7L)
        verify(stubController).stopK6Script(7L)
    }

    @Test
    fun `resolveToolBin forwards the extraCandidates to the K6ProcessController`() {
        val stubController: K6ProcessController = mock()
        swapK6ControllerOnDeps(service, stubController)
        whenever(stubController.resolveToolBin("k6", listOf("a", "b"))).thenReturn("/a/k6")

        assertEquals("/a/k6", service.resolveToolBin("k6", listOf("a", "b")))
    }

    @Test
    fun `resolveToolBin resolves via the K6ProcessController`() {
        val stubController: K6ProcessController = mock()
        swapK6ControllerOnDeps(service, stubController)
        whenever(stubController.resolveToolBin("k6")).thenReturn("/usr/bin/k6")

        assertEquals("/usr/bin/k6", service.resolveToolBin("k6"))
        verify(stubController).resolveToolBin("k6", emptyList())
    }

    @Test
    fun `resolveToolBin returns a different path when extraCandidates is provided`() {
        val stubController: K6ProcessController = mock()
        swapK6ControllerOnDeps(service, stubController)
        whenever(stubController.resolveToolBin("k6", listOf("a", "b"))).thenReturn("/a/k6")

        assertEquals("/a/k6", service.resolveToolBin("k6", listOf("a", "b")))
    }

    @Test
    fun `startGitlabAnalysis creates a PENDING analysis and triggers the runner`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }

        val result = service.startGitlabAnalysis(1L, 100L, "group/proj")

        assertEquals("PENDING", result.status)
        assertEquals(1L, result.gitlabConfigId)
        assertEquals(100L, result.gitlabProjectId)
        assertEquals("group/proj", result.gitlabProjectPath)
        assertTrue(result.modelUsed!!.contains("gpt-x"))
        verify(aiRunner).runGitlabAnalysis(result.id, 1L, 100L, "group/proj")
    }

    @Test
    fun `startLocalAnalysis creates a PENDING analysis and triggers the runner`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-y")
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }

        val result = service.startLocalAnalysis(7L)

        assertEquals("PENDING", result.status)
        assertEquals(7L, result.repoConfigId)
        verify(aiRunner).runLocalAnalysis(result.id, 7L)
    }

    @Test
    fun `startGithubAnalysis creates a PENDING analysis and triggers the runner`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-z")
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }

        val result = service.startGithubAnalysis(13L)

        assertEquals("PENDING", result.status)
        assertEquals(13L, result.githubRepoConfigId)
        verify(aiRunner).runGithubAnalysis(result.id, 13L)
    }

    private fun swapK6ControllerOnDeps(
        service: LoadTestAnalysisService,
        stubController: K6ProcessController,
    ) {
        val depsField =
            LoadTestAnalysisService::class
                .java
                .getDeclaredField("deps")
                .apply { isAccessible = true }
        val depsBundle =
            depsField.get(service) as com.stacklens.application.service.LoadTestAnalysisServiceDeps
        val k6Field =
            com.stacklens.application.service.LoadTestAnalysisServiceDeps::class
                .java
                .getDeclaredField("k6Controller")
                .apply { isAccessible = true }
        k6Field.set(depsBundle, stubController)
    }
}
