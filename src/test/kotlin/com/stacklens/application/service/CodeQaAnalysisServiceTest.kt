package com.stacklens.application.service

import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.mr.CodeQaIntegrationTestPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.ticket.TicketTextFetchPort
import com.stacklens.application.service.codeqa.CodeQaStreamingRegistry
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class CodeQaAnalysisServiceTest {
    private val codeQaRepo: CodeQaAnalysisPort = mock()
    private val mrFetchPort: MrFetchPort = mock()
    private val mrSuggestionPort: MrSuggestionPort = mock()
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val githubConfigRepo: GithubRepoConfigPort = mock()
    private val jiraService: TicketTextFetchPort = mock()
    private val copilotPort: CopilotStreamingPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nService = mock()
    private val startupHealer: StartupHealer = StartupHealer()
    private val integrationTestService: CodeQaIntegrationTestPort = mock()
    private val self: CodeQaAnalysisService = mock()

    private val service =
        spy(
            CodeQaAnalysisService(
                CodeQaAnalysisServiceDeps(
                    codeQaRepo,
                    mrFetchPort,
                    mrSuggestionPort,
                    gitlabConfigRepo,
                    githubConfigRepo,
                    jiraService,
                    copilotPort,
                    aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                    i18n,
                    startupHealer,
                    integrationTestService,
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    streamingRegistry = CodeQaStreamingRegistry(),
                    promptAssembler = CodeQaPromptAssembler(i18n, copilotPort, promptLoader),
                ),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            ),
        )

    private fun analysis(
        id: Long = 1L,
        status: String = "PENDING",
    ) = CodeQaAnalysis(id = id, mrUrl = "https://gitlab.example.com/g/r/-/merge_requests/1", status = status)

    private fun mrData(
        title: String = "Fix bug",
        description: String = "desc",
        sourceBranch: String = "feature/fix",
        changedFiles: List<String> = emptyList(),
        headSha: String = "",
        baseSha: String = "",
        startSha: String = "",
    ) = MrFetchPort.MrData(
        mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/42",
        platform = "gitlab",
        title = title,
        description = description,
        sourceBranch = sourceBranch,
        targetBranch = "main",
        authorName = "dev",
        state = "opened",
        diffText = "diff text",
        changedFiles = changedFiles,
        diffHeadSha = headSha,
        diffBaseSha = baseSha,
        diffStartSha = startSha,
    )

    private fun gitlabConfig(id: Long = 1L) = GitlabConfig(id = id, name = "GL", baseUrl = "https://gitlab.example.com", accessToken = "tok")

    private fun githubConfig(id: Long = 2L) = GithubRepoConfig(id = id, name = "GH", owner = "acme", repo = "svc")

    @Test
    fun `getAnalysis returns null when not found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(99L)).thenReturn(null)
        assertNull(service.getAnalysis(99L))
    }

    @Test
    fun `getAnalysis returns analysis when found`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        assertEquals(a, service.getAnalysis(1L))
    }

    @Test
    fun `getAllAnalyses delegates to repo`() {
        val list = listOf(analysis(1L), analysis(2L))
        whenever(codeQaRepo.findRecent(200)).thenReturn(list)
        assertEquals(list, service.getAllAnalyses())
    }

    @Test
    fun `deleteAnalysis delegates to repo`() {
        service.deleteAnalysis(5L)
        verify(codeQaRepo).deleteCodeQaAnalysisById(5L)
    }

    @Test
    fun `deleteAllAnalyses delegates to repo`() {
        service.deleteAllAnalyses()
        verify(codeQaRepo).deleteAllCodeQaAnalyses()
    }

    @Test
    fun `cancelAnalysis does nothing when analysis not found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(99L)).thenReturn(null)
        service.cancelAnalysis(99L)
        verify(codeQaRepo, never()).saveCodeQaAnalysis(any())
    }

    @Test
    fun `cancelAnalysis marks RUNNING analysis as FAILED`() {
        val a = analysis(1L, "RUNNING")
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(i18n.t("mr_analysis.be_cancelled")).thenReturn("cancelled")
        service.cancelAnalysis(1L)
        assertEquals("CANCELLED", a.status)
        assertEquals("cancelled", a.errorMessage)
        assertNotNull(a.completedAt)
        verify(codeQaRepo).saveCodeQaAnalysis(a)
    }

    @Test
    fun `cancelAnalysis marks PENDING analysis as FAILED`() {
        val a = analysis(1L, "PENDING")
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(i18n.t("mr_analysis.be_cancelled")).thenReturn("cancelled")
        service.cancelAnalysis(1L)
        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `cancelAnalysis does not modify COMPLETED analysis`() {
        val a = analysis(1L, "COMPLETED")
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        service.cancelAnalysis(1L)
        assertEquals("COMPLETED", a.status)
        verify(codeQaRepo, never()).saveCodeQaAnalysis(any())
    }

    @Test
    fun `cancelAnalysis does not modify FAILED analysis`() {
        val a = analysis(1L, "FAILED")
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        service.cancelAnalysis(1L)
        assertEquals("FAILED", a.status)
        verify(codeQaRepo, never()).saveCodeQaAnalysis(any())
    }

    @Test
    fun `startAnalysis trims mrUrl and strips blank jiraTicketKey`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        val saved = analysis(10L)
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(saved)
        service.startAnalysis("  https://gitlab.example.com/g/r/-/merge_requests/1  ", "  ", null)
        val entity = captor.firstValue
        assertEquals("https://gitlab.example.com/g/r/-/merge_requests/1", entity.mrUrl)
        assertNull(entity.jiraTicketKey)
        verify(service).runAnalysis(10L)
    }

    @Test
    fun `startAnalysis stores valid gitPlatform`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        val saved = analysis(10L)
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(saved)
        service.startAnalysis("https://gitlab.example.com/g/r/-/merge_requests/1", null, null, 3L, "GITLAB")
        assertEquals("gitlab", captor.firstValue.gitPlatform)
    }

    @Test
    fun `startAnalysis rejects unknown gitPlatform`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        val saved = analysis(10L)
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(saved)
        service.startAnalysis("https://gitlab.example.com/g/r/-/merge_requests/1", null, null, null, "bitbucket")
        assertNull(captor.firstValue.gitPlatform)
    }

    private fun setupBuildPromptI18n() {
        whenever(i18n.tFor(any(), any())).thenAnswer { inv -> inv.getArgument<String>(1) }
        whenever(i18n.t(any(), any())).thenAnswer { inv -> inv.getArgument<String>(1) }
        whenever(i18n.tFor(any(), any(), any())).thenAnswer { inv -> inv.getArgument<String>(1) }
    }

    @Test
    fun `buildPrompt sets no-jira part headers when jiraTicketText is null`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        val result = service.buildPrompt(mrData(), null)
        assertEquals("rendered", result)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val vars = captor.firstValue
        assertEquals("mr_analysis.prompt_part1_desc_header", vars["part1Header"])
        assertEquals("", vars["jiraSection"])
    }

    @Test
    fun `buildPrompt sets jira part headers when jiraTicketText is provided`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(), "Ticket: PROJ-1\nSummary: Fix it")
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val vars = captor.firstValue
        assertEquals("mr_analysis.prompt_part1_jira_header", vars["part1Header"])
        assert(vars["jiraSection"]!!.contains("mr_analysis.prompt_jira_section_header"))
    }

    @Test
    fun `buildPrompt lists files when changedFiles not empty`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        val files = (1..55).map { "src/File$it.kt" }
        service.buildPrompt(mrData(changedFiles = files), null)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val filesList = captor.firstValue["changedFilesList"]!!
        assert(filesList.contains("mr_analysis.be_files_more")) { "Should indicate more files" }
    }

    @Test
    fun `buildPrompt shows no-info when changedFiles is empty`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(changedFiles = emptyList()), null)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val filesList = captor.firstValue["changedFilesList"]!!
        assert(filesList.contains("mr_analysis.be_no_file_info")) { "Should show no-info message" }
    }

    @Test
    fun `buildPrompt uses blank description fallback`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(description = "   "), null)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        assertEquals("mr_analysis.be_no_description", captor.firstValue["description"])
    }

    @Test
    fun `runAnalysis does nothing when analysis not found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(99L)).thenReturn(null)
        service.runAnalysis(99L)
        verify(codeQaRepo, never()).saveCodeQaAnalysis(any())
    }

    @Test
    fun `runAnalysis marks FAILED when mrFetchPort returns null`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Failure("not found"))
        whenever(i18n.translateError(any())).thenReturn("error")
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis completes successfully without jira`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("analysis result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
        assertEquals("analysis result", a.resultMarkdown)
    }

    @Test
    fun `runAnalysis uses only matching gitlab config when gitPlatform is gitlab`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "gitlab"
                it.gitConfigId = 10L
            }
        val cfg10 = gitlabConfig(10L)
        val cfg11 = gitlabConfig(11L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(cfg10, cfg11))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(githubConfig()))
        val mrDataCaptor = argumentCaptor<List<GitlabConfig>>()
        whenever(mrFetchPort.fetchMrData(any(), mrDataCaptor.capture(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(listOf(cfg10), mrDataCaptor.firstValue)
    }

    @Test
    fun `runAnalysis uses only matching github config when gitPlatform is github`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "github"
                it.gitConfigId = 2L
            }
        val gh2 = githubConfig(2L)
        val gh3 = githubConfig(3L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlabConfig()))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(gh2, gh3))
        val githubCaptor = argumentCaptor<List<GithubRepoConfig>>()
        whenever(mrFetchPort.fetchMrData(any(), any(), githubCaptor.capture())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(listOf(gh2), githubCaptor.firstValue)
    }

    @Test
    fun `runAnalysis auto-detects jira key from MR title when jiraConfigId set`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = null
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any()))
            .thenReturn(MrFetchPort.MrFetchResult.Success(mrData(title = "PROJ-42: Fix the thing", sourceBranch = "feature/fix")))
        whenever(jiraService.fetchTicketAsText("PROJ-42", 5L)).thenReturn("Ticket text")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("PROJ-42", a.jiraTicketKey)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis does not auto-detect jira when no jiraConfigId`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = null
                it.jiraTicketKey = null
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any()))
            .thenReturn(MrFetchPort.MrFetchResult.Success(mrData(title = "PROJ-42: some fix")))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertNull(a.jiraTicketKey)
    }

    @Test
    fun `runAnalysis continues without jira when jira fetch fails`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = "PROJ-1"
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(jiraService.fetchTicketAsText(any(), any())).thenThrow(RuntimeException("Jira down"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis marks FAILED when exception thrown after fresh reload`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenThrow(RuntimeException("DB error"))
        whenever(i18n.translateError(any())).thenReturn("error")
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `getStreamingOutput returns null when id not in buffer`() {
        assertNull(service.getStreamingOutput(999L))
    }

    @Test
    fun `runAnalysis calls streaming callback and getStreamingOutput returns partial`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        var capturedStreaming: String? = "not-set"
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            val callback = inv.getArgument<(String) -> Unit>(5)
            callback("partial result")
            capturedStreaming = service.getStreamingOutput(1L)
            "final result"
        }
        service.runAnalysis(1L)
        assertEquals("partial result", capturedStreaming)
        assertNull(service.getStreamingOutput(1L))
    }

    @Test
    fun `resetStaleAnalyses marks RUNNING and PENDING analyses as FAILED`() {
        val running = analysis(1L, "RUNNING")
        val pending = analysis(2L, "PENDING")
        val completed = analysis(3L, "COMPLETED")
        whenever(codeQaRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running, pending, completed))
        whenever(i18n.t("mr_analysis.be_aborted_restart")).thenReturn("aborted")
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(running)
        service.heal()
        assertEquals("CANCELLED", running.status)
        assertEquals("aborted", running.errorMessage)
        assertNotNull(running.completedAt)
        assertEquals("CANCELLED", pending.status)
        assertEquals("COMPLETED", completed.status)
        verify(codeQaRepo, never()).saveCodeQaAnalysis(completed)
    }

    @Test
    fun `startAnalysis stores github platform lowercase`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        val saved = analysis(10L)
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(saved)
        service.startAnalysis("https://github.com/acme/repo/pull/1", null, null, null, "GITHUB")
        assertEquals("github", captor.firstValue.gitPlatform)
    }

    @Test
    fun `startAnalysis stores non-blank jiraTicketKey`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        val saved = analysis(10L)
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(saved)
        service.startAnalysis("https://gitlab.example.com/g/r/-/merge_requests/1", "PROJ-1", null, null, null)
        assertEquals("PROJ-1", captor.firstValue.jiraTicketKey)
    }

    @Test
    fun `runAnalysis uses all gitlab configs when gitPlatform is gitlab but gitConfigId is null`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "gitlab"
                it.gitConfigId = null
            }
        val cfg1 = gitlabConfig(1L)
        val cfg2 = gitlabConfig(2L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(cfg1, cfg2))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(githubConfig()))
        val glCaptor = argumentCaptor<List<GitlabConfig>>()
        val ghCaptor = argumentCaptor<List<GithubRepoConfig>>()
        whenever(
            mrFetchPort.fetchMrData(any(), glCaptor.capture(), ghCaptor.capture()),
        ).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(listOf(cfg1, cfg2), glCaptor.firstValue)
        assertEquals(emptyList<GithubRepoConfig>(), ghCaptor.firstValue)
    }

    @Test
    fun `runAnalysis uses empty github configs when gitPlatform is gitlab`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "gitlab"
                it.gitConfigId = 1L
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlabConfig(1L)))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(githubConfig()))
        val ghCaptor = argumentCaptor<List<GithubRepoConfig>>()
        whenever(mrFetchPort.fetchMrData(any(), any(), ghCaptor.capture())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(emptyList<GithubRepoConfig>(), ghCaptor.firstValue)
    }

    @Test
    fun `runAnalysis uses all github configs when gitPlatform is github but gitConfigId is null`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "github"
                it.gitConfigId = null
            }
        val gh1 = githubConfig(1L)
        val gh2 = githubConfig(2L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlabConfig()))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(gh1, gh2))
        val glCaptor = argumentCaptor<List<GitlabConfig>>()
        val ghCaptor = argumentCaptor<List<GithubRepoConfig>>()
        whenever(
            mrFetchPort.fetchMrData(any(), glCaptor.capture(), ghCaptor.capture()),
        ).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(emptyList<GitlabConfig>(), glCaptor.firstValue)
        assertEquals(listOf(gh1, gh2), ghCaptor.firstValue)
    }

    @Test
    fun `runAnalysis gitlab filter returns empty when no config matches gitConfigId`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "gitlab"
                it.gitConfigId = 99L
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlabConfig(1L)))
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val glCaptor = argumentCaptor<List<GitlabConfig>>()
        whenever(mrFetchPort.fetchMrData(any(), glCaptor.capture(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(emptyList<GitlabConfig>(), glCaptor.firstValue)
    }

    @Test
    fun `runAnalysis github filter returns empty when no config matches gitConfigId`() {
        val a =
            analysis(1L).also {
                it.gitPlatform = "github"
                it.gitConfigId = 99L
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(githubConfig(1L)))
        val ghCaptor = argumentCaptor<List<GithubRepoConfig>>()
        whenever(mrFetchPort.fetchMrData(any(), any(), ghCaptor.capture())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals(emptyList<GithubRepoConfig>(), ghCaptor.firstValue)
    }

    @Test
    fun `runAnalysis does not enter auto-detect when jiraTicketKey is blank`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = ""
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any()))
            .thenReturn(MrFetchPort.MrFetchResult.Success(mrData(title = "Some fix without ticket")))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
        assertEquals("", a.jiraTicketKey)
    }

    @Test
    fun `runAnalysis detects jira key from branch name when not in title`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = null
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any()))
            .thenReturn(MrFetchPort.MrFetchResult.Success(mrData(title = "Fix some thing", sourceBranch = "feature/PROJ-77-new-stuff")))
        whenever(jiraService.fetchTicketAsText("PROJ-77", 5L)).thenReturn("Ticket text")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("PROJ-77", a.jiraTicketKey)
    }

    @Test
    fun `runAnalysis does not detect jira when no key found in any field`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = null
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any()))
            .thenReturn(
                MrFetchPort.MrFetchResult.Success(
                    mrData(title = "Fix the thing", sourceBranch = "feature/fix", description = "simple desc"),
                ),
            )
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertNull(a.jiraTicketKey)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis does not fetch jira when ticketKey set but configId is null`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = null
                it.jiraTicketKey = "PROJ-1"
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        verify(jiraService, never()).fetchTicketAsText(any(), any())
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis handles jira exception with null message`() {
        val a =
            analysis(1L).also {
                it.jiraConfigId = 5L
                it.jiraTicketKey = "PROJ-1"
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        val nullMsgException = object : RuntimeException(null as String?) {}
        whenever(jiraService.fetchTicketAsText(any(), any())).thenThrow(nullMsgException)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis returns early when fresh reload returns null in catch block`() {
        val a = analysis(1L)
        var callCount = 0
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenAnswer { if (callCount++ == 0) a else null }
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenThrow(RuntimeException("DB error"))
        whenever(i18n.translateError(any())).thenReturn("error")
        service.runAnalysis(1L)
        assertNotEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis handles exception with null message in catch block`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        val nullMsgException = object : RuntimeException(null as String?) {}
        whenever(gitlabConfigRepo.findByActiveTrue()).thenThrow(nullMsgException)
        whenever(i18n.translateError(any())).thenReturn("error")
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `buildPrompt with blank jiraTicketText treats as no-jira`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(), "   ")
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        assertEquals("mr_analysis.prompt_part1_desc_header", captor.firstValue["part1Header"])
        assertEquals("", captor.firstValue["jiraSection"])
    }

    @Test
    fun `buildPrompt with exactly 50 changed files does not append mehr`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        val files = (1..50).map { "src/File$it.kt" }
        service.buildPrompt(mrData(changedFiles = files), null)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val filesList = captor.firstValue["changedFilesList"]!!
        assert(!filesList.contains("mr_analysis.be_files_more")) { "Should not indicate more files for exactly 50" }
    }

    @Test
    fun `buildPrompt includes mrUrl in prompt vars`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(), null)
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        assertEquals("https://gitlab.example.com/group/repo/-/merge_requests/42", captor.firstValue["mrUrl"])
    }

    private fun setupRunAnalysis(
        analysisEntity: CodeQaAnalysis,
        mrDataToReturn: MrFetchPort.MrData,
    ) {
        whenever(codeQaRepo.findCodeQaAnalysisById(analysisEntity.id)).thenReturn(analysisEntity)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(analysisEntity)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrDataToReturn))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("result")
    }

    @Test
    fun `runAnalysis stores non-blank SHAs from mrData`() {
        val a = analysis(1L)
        setupRunAnalysis(a, mrData(headSha = "headSha", baseSha = "baseSha", startSha = "startSha"))
        service.runAnalysis(1L)
        assertEquals("headSha", a.diffHeadSha)
        assertEquals("baseSha", a.diffBaseSha)
        assertEquals("startSha", a.diffStartSha)
    }

    @Test
    fun `runAnalysis stores null for blank base and start SHAs when headSha is non-blank`() {
        val a = analysis(1L)
        setupRunAnalysis(a, mrData(headSha = "headSha", baseSha = "", startSha = ""))
        service.runAnalysis(1L)
        assertEquals("headSha", a.diffHeadSha)
        assertNull(a.diffBaseSha)
        assertNull(a.diffStartSha)
    }

    @Test
    fun `postSuggestion throws when analysis not found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(99L)).thenReturn(null)
        val ex =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                service.postSuggestion(99L, "src/Foo.kt", 10, "val x = 1")
            }
        assert(ex.message!!.contains("99"))
    }

    @Test
    fun `postSuggestion throws when diffHeadSha is null`() {
        val a = analysis(status = "COMPLETED").apply { diffHeadSha = null }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            service.postSuggestion(1L, "src/Foo.kt", 10, "val x = 1")
        }
    }

    @Test
    fun `postSuggestion delegates to mrSuggestionPort and returns url`() {
        val a =
            analysis(status = "COMPLETED").apply {
                diffBaseSha = "base"
                diffStartSha = "start"
                diffHeadSha = "head"
                gitPlatform = "GitLab"
                gitConfigId = 7L
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrSuggestionPort.postSuggestion(any())).thenReturn("https://gitlab.example.com/g/r/-/merge_requests/1#note_99")

        val url = service.postSuggestion(1L, "src/Foo.kt", 5, "val x = 1")

        assertEquals("https://gitlab.example.com/g/r/-/merge_requests/1#note_99", url)
        val captor = argumentCaptor<MrSuggestionPort.PostRequest>()
        verify(mrSuggestionPort).postSuggestion(captor.capture())
        val req = captor.firstValue
        assertEquals("src/Foo.kt", req.filePath)
        assertEquals(5, req.lineNumber)
        assertEquals("val x = 1", req.suggestedCode)
        assertEquals("head", req.diffHeadSha)
    }

    @Test
    fun `postSuggestion uses empty strings for null platform and SHAs`() {
        val a = analysis(status = "COMPLETED").apply { diffHeadSha = "head" }
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrSuggestionPort.postSuggestion(any())).thenReturn("https://url")

        service.postSuggestion(1L, "src/Foo.kt", 5, "code")

        val captor = argumentCaptor<MrSuggestionPort.PostRequest>()
        verify(mrSuggestionPort).postSuggestion(captor.capture())
        assertEquals("", captor.firstValue.platform)
        assertEquals("", captor.firstValue.diffBaseSha)
        assertEquals("", captor.firstValue.diffStartSha)
    }

    @Test
    fun `MrData SHA fields default to empty string when not provided`() {
        val data =
            MrFetchPort.MrData(
                mrUrl = "https://gitlab.example.com/g/r/-/merge_requests/1",
                platform = "gitlab",
                title = "Fix",
                description = "",
                sourceBranch = "feat",
                targetBranch = "main",
                authorName = "user",
                state = "opened",
                diffText = "",
                changedFiles = emptyList(),
            )
        assertEquals("", data.diffBaseSha)
        assertEquals("", data.diffStartSha)
        assertEquals("", data.diffHeadSha)
    }

    @Test
    fun `PostRequest exposes all properties`() {
        val gitlabCfg =
            com.stacklens.domain.model.GitlabConfig(
                id = 1L,
                name = "gl",
                baseUrl = "https://gitlab.example.com",
                accessToken = "tok",
            )
        val githubCfg =
            com.stacklens.domain.model
                .GithubRepoConfig(id = 2L, name = "gh", owner = "acme", repo = "svc")
        val req =
            MrSuggestionPort.PostRequest(
                mrUrl = "https://example.com/mr/1",
                platform = "GitLab",
                gitConfigId = 42L,
                gitlabConfigs = listOf(gitlabCfg),
                githubConfigs = listOf(githubCfg),
                diffBaseSha = "base",
                diffStartSha = "start",
                diffHeadSha = "head",
                filePath = "src/Foo.kt",
                lineNumber = 10,
                suggestedCode = "val x = 1",
            )
        assertEquals("https://example.com/mr/1", req.mrUrl)
        assertEquals(42L, req.gitConfigId)
        assertEquals(listOf(gitlabCfg), req.gitlabConfigs)
        assertEquals(listOf(githubCfg), req.githubConfigs)
    }

    @Test
    fun `startAnalysis stores integrationTestEnabled and PENDING status when enabled`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(analysis(20L))
        service.startAnalysis(
            "https://gitlab.example.com/g/r/-/merge_requests/1",
            null,
            null,
            null,
            null,
            "extra hint",
            true,
        )
        val entity = captor.firstValue
        assertEquals(true, entity.integrationTestEnabled)
        assertEquals("PENDING", entity.integrationTestStatus)
        assertEquals("extra hint", entity.additionalInstructions)
    }

    @Test
    fun `startAnalysis leaves integrationTestStatus null when disabled`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(analysis(21L))
        service.startAnalysis(
            "https://gitlab.example.com/g/r/-/merge_requests/1",
            null,
            null,
            null,
            null,
            null,
            false,
        )
        assertNull(captor.firstValue.integrationTestStatus)
        assertEquals(false, captor.firstValue.integrationTestEnabled)
    }

    @Test
    fun `startAnalysis treats blank additionalInstructions as null`() {
        whenever(i18n.t("mr_analysis.be_step_starting")).thenReturn("starting")
        val captor = argumentCaptor<CodeQaAnalysis>()
        whenever(codeQaRepo.saveCodeQaAnalysis(captor.capture())).thenReturn(analysis(22L))
        service.startAnalysis(
            "https://gitlab.example.com/g/r/-/merge_requests/1",
            null,
            null,
            null,
            null,
            "   ",
            false,
        )
        assertNull(captor.firstValue.additionalInstructions)
    }

    @Test
    fun `getIntegrationTestStreamingOutput delegates to integrationTestService`() {
        whenever(integrationTestService.getStreamingOutput(7L)).thenReturn("docker logs")
        assertEquals("docker logs", service.getIntegrationTestStreamingOutput(7L))
    }

    @Test
    fun `buildPrompt includes additionalInstructions section when provided`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(), null, "please focus on security")
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        val section = captor.firstValue["additionalInstructionsSection"]!!
        assert(section.contains("Additional Instructions"))
        assert(section.contains("please focus on security"))
    }

    @Test
    fun `buildPrompt leaves additionalInstructions section empty when blank`() {
        setupBuildPromptI18n()
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("rendered")
        service.buildPrompt(mrData(), null, "   ")
        val captor = argumentCaptor<Map<String, String>>()
        verify(promptLoader).get(any(), any(), captor.capture())
        assertEquals("", captor.firstValue["additionalInstructionsSection"])
    }

    @Test
    fun `runAnalysis triggers integration tests when integrationTestEnabled`() {
        val a = analysis(50L).apply { integrationTestEnabled = true }
        whenever(codeQaRepo.findCodeQaAnalysisById(50L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val data = mrData()
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(data))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ai-result")

        service.runAnalysis(50L)

        verify(integrationTestService).runIntegrationTests(eq(50L), eq(data))
    }

    @Test
    fun `runAnalysis discards result when analysis deleted externally during AI run`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a).thenReturn(null)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ai-result")

        service.runAnalysis(1L)

        assertNull(a.resultMarkdown)
    }

    @Test
    fun `runAnalysis discards result when analysis cancelled externally during AI run`() {
        val a = analysis(1L)
        val cancelled = analysis(1L, status = "CANCELLED")
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a).thenReturn(cancelled)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenReturn(a)
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ai-result")

        service.runAnalysis(1L)

        assertEquals("CANCELLED", cancelled.status)
        assertNull(cancelled.resultMarkdown)
    }

    @Test
    fun `runAnalysis handles ObjectOptimisticLockingFailureException on final save silently`() {
        val a = analysis(1L)
        whenever(codeQaRepo.findCodeQaAnalysisById(1L)).thenReturn(a)
        whenever(codeQaRepo.saveCodeQaAnalysis(any()))
            .thenReturn(a)
            .thenReturn(a)
            .thenReturn(a)
            .thenThrow(
                EntityConcurrentlyModifiedException("test"),
            )
        whenever(i18n.t(any())).thenReturn("msg")
        whenever(i18n.tFor(any(), any())).thenReturn("msg")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(mrFetchPort.fetchMrData(any(), any(), any())).thenReturn(MrFetchPort.MrFetchResult.Success(mrData()))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ai-result")

        service.runAnalysis(1L)
    }
}
