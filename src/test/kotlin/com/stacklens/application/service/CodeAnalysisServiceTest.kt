package com.stacklens.application.service
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.analysis.CodeAnalysisRepoData
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.application.service.codeanalysis.CodeAnalysisAi
import com.stacklens.application.service.codeanalysis.CodeAnalysisCancellationService
import com.stacklens.application.service.codeanalysis.CodeAnalysisExecutor
import com.stacklens.application.service.codeanalysis.CodeAnalysisMrService
import com.stacklens.application.service.codeanalysis.GithubCodeAnalysisStrategy
import com.stacklens.application.service.codeanalysis.GitlabCodeAnalysisStrategy
import com.stacklens.application.service.codeanalysis.LocalCodeAnalysisStrategy
import com.stacklens.application.service.codeanalysis.MonorepoDetector
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CodeAnalysisServiceTest {
    private lateinit var analysisRepo: CodeAnalysisPort
    private lateinit var codeRepoPort: CodeAnalysisRepoApi
    private lateinit var copilotCliService: CopilotPort
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private lateinit var gitlabMrPort: GitlabMrPort
    private lateinit var i18n: I18nService
    private lateinit var aiService: CodeAnalysisAi
    private lateinit var self: CodeAnalysisService
    private lateinit var service: CodeAnalysisService
    private lateinit var monorepoDetector: MonorepoDetector
    private lateinit var mrService: CodeAnalysisMrService

    @BeforeEach
    fun setup() {
        analysisRepo = mock()
        codeRepoPort = mock()
        copilotCliService = mock()
        gitlabMrPort = mock()
        i18n = mock()
        aiService = mock()
        monorepoDetector = MonorepoDetector()
        mrService = CodeAnalysisMrService(analysisRepo, codeRepoPort, gitlabMrPort, jsonCodec, i18n)
        val deps =
            CodeAnalysisServiceDeps(
                analysisRepo,
                codeRepoPort,
                copilotCliService,
                i18n,
                startupHealer = StartupHealer(),
                monorepoDetector = monorepoDetector,
                mrService = mrService,
                asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                cancellationService = CodeAnalysisCancellationService(analysisRepo, i18n, clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        val executor =
            CodeAnalysisExecutor(
                analysisRepo = analysisRepo,
                copilotCliService = copilotCliService,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = i18n,
                aiService = aiService,
                localStrategy = LocalCodeAnalysisStrategy(codeRepoPort),
                gitlabStrategy = GitlabCodeAnalysisStrategy(codeRepoPort),
                githubStrategy = GithubCodeAnalysisStrategy(codeRepoPort),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        service =
            spy(
                CodeAnalysisService(
                    deps = deps,
                    clock = com.stacklens.adapter.out.time.SystemClockPort,
                    executor = executor,
                ),
            )
        whenever(copilotCliService.isReady()).thenCallRealMethod()
    }

    @Test
    fun `detectMonorepoSubApps returns empty for single app with one build file`() {
        val paths = listOf("src/Main.kt", "build.gradle.kts")
        val result = monorepoDetector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectMonorepoSubApps detects depth-1 gradle monorepo`() {
        val paths =
            listOf(
                "backend/build.gradle.kts",
                "frontend/package.json",
                "shared/build.gradle.kts",
            )
        val result = monorepoDetector.detectSubApps(paths)
        assertEquals(3, result.size)
        assertTrue(result.any { it.first == "backend" && it.second == "backend" })
        assertTrue(result.any { it.first == "frontend" && it.second == "frontend" })
    }

    @Test
    fun `CodeFileChange default constructor uses expected defaults`() {
        val fc =
            com.stacklens.domain.model
                .CodeFileChange()
        assertEquals("", fc.path)
        assertEquals(FileChangeType.UPDATE, fc.changeType)
        assertEquals("", fc.code)
    }

    @Test
    fun `CodeFinding default constructor uses expected defaults`() {
        val f =
            com.stacklens.domain.model
                .CodeFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertNull(f.line)
        assertNull(f.snippet)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertNull(f.suggestedCode)
    }

    @Test
    fun `detectMonorepoSubApps returns empty when only one depth-2 app`() {
        val paths = listOf("packages/app1/package.json", "docs/README.md")
        val result = monorepoDetector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectMonorepoSubApps deduplicates depth-1 apps`() {
        val paths =
            listOf(
                "backend/build.gradle.kts",
                "backend/settings.gradle.kts",
                "frontend/package.json",
            )
        val result = monorepoDetector.detectSubApps(paths)
        assertEquals(2, result.size)
        assertTrue(result.any { it.first == "backend" })
        assertTrue(result.any { it.first == "frontend" })
    }

    @Test
    fun `detectMonorepoSubApps recognizes all build markers`() {
        val markers =
            listOf(
                "app1/pom.xml",
                "app2/composer.json",
                "app3/pyproject.toml",
                "app4/go.mod",
                "app5/Cargo.toml",
            )
        val result = monorepoDetector.detectSubApps(markers)
        assertEquals(5, result.size)
    }

    @Test
    fun `detectMonorepoSubApps ignores non-build files at depth-1`() {
        val paths =
            listOf(
                "app1/README.md",
                "app2/src/Main.kt",
                "app3/build.gradle.kts",
            )
        val result = monorepoDetector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectMonorepoSubApps depth-1 takes priority over depth-2`() {
        val paths =
            listOf(
                "backend/build.gradle.kts",
                "frontend/package.json",
                "packages/sub1/package.json",
                "packages/sub2/pyproject.toml",
            )
        val result = monorepoDetector.detectSubApps(paths)
        assertTrue(result.any { it.first == "backend" })
        assertTrue(result.any { it.first == "frontend" })
    }

    @Test
    fun `getAnalysis returns analysis from repo`() {
        val analysis = CodeAnalysis(id = 42L, status = "COMPLETED")
        whenever(analysisRepo.findCodeAnalysisById(42L)).thenReturn(analysis)
        val result = service.getAnalysis(42L)
        assertEquals(analysis, result)
    }

    @Test
    fun `getAnalysis returns null when not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)
        val result = service.getAnalysis(99L)
        assertNull(result)
    }

    @Test
    fun `getAllAnalyses delegates to repo`() {
        val analyses = listOf(CodeAnalysis(id = 1L), CodeAnalysis(id = 2L))
        whenever(analysisRepo.findRecentCodeAnalyses(200)).thenReturn(analyses)
        val result = service.getAllAnalyses()
        assertEquals(2, result.size)
    }

    @Test
    fun `deleteAnalysis delegates to repo`() {
        service.deleteAnalysis(5L)
        verify(analysisRepo).deleteCodeAnalysisById(5L)
    }

    @Test
    fun `cancelAnalysis does nothing when analysis not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)
        service.cancelAnalysis(99L)
    }

    @Test
    fun `cancelAnalysis marks RUNNING analysis as FAILED`() {
        val analysis = CodeAnalysis(id = 1L, status = "RUNNING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(i18n.t(any())).thenReturn("Cancelled")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        service.cancelAnalysis(1L)
        assertEquals("CANCELLED", analysis.status)
        verify(analysisRepo).saveCodeAnalysis(analysis)
    }

    @Test
    fun `cancelAnalysis marks PENDING analysis as FAILED`() {
        val analysis = CodeAnalysis(id = 2L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(2L)).thenReturn(analysis)
        whenever(i18n.t(any())).thenReturn("Cancelled")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        service.cancelAnalysis(2L)
        assertEquals("CANCELLED", analysis.status)
    }

    @Test
    fun `cancelAnalysis does not modify already COMPLETED analysis`() {
        val analysis = CodeAnalysis(id = 3L, status = "COMPLETED")
        whenever(analysisRepo.findCodeAnalysisById(3L)).thenReturn(analysis)
        service.cancelAnalysis(3L)
        assertEquals("COMPLETED", analysis.status)
    }

    @Test
    fun `cancelLatestRunningForRepo returns null when no analysis found`() {
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(null)
        val result = service.cancelLatestRunningForRepo(1L)
        assertNull(result)
    }

    @Test
    fun `cancelLatestRunningForRepo cancels running analysis and returns id`() {
        val analysis = CodeAnalysis(id = 10L, status = "RUNNING")
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(analysis)
        whenever(i18n.t(any())).thenReturn("Cancelled")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        val result = service.cancelLatestRunningForRepo(1L)
        assertEquals(10L, result)
        assertEquals("CANCELLED", analysis.status)
    }

    @Test
    fun `cancelLatestRunningForRepo returns null when analysis is already COMPLETED`() {
        val analysis = CodeAnalysis(id = 10L, status = "COMPLETED")
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(analysis)
        val result = service.cancelLatestRunningForRepo(1L)
        assertNull(result)
    }

    @Test
    fun `getLatestLocalAnalysis delegates to repo`() {
        val analysis = CodeAnalysis(id = 5L)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(analysis)
        val result = service.getLatestLocalAnalysis(1L)
        assertEquals(analysis, result)
    }

    @Test
    fun `getLatestGitlabAnalysis delegates to repo`() {
        val analysis = CodeAnalysis(id = 6L)
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(1L, 2L)).thenReturn(analysis)
        val result = service.getLatestGitlabAnalysis(1L, 2L)
        assertEquals(analysis, result)
    }

    @Test
    fun `resetStaleAnalyses sets RUNNING analyses to FAILED`() {
        val running = CodeAnalysis(id = 1L, status = "RUNNING")
        val pending = CodeAnalysis(id = 2L, status = "PENDING")
        val completed = CodeAnalysis(id = 3L, status = "COMPLETED")
        whenever(analysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(running, pending, completed))
        whenever(i18n.t(any())).thenReturn("Aborted")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenAnswer { it.arguments[0] }
        service.heal()
        assertEquals("CANCELLED", running.status)
        assertEquals("CANCELLED", pending.status)
        assertEquals("COMPLETED", completed.status)
    }

    @Test
    fun `resetStaleAnalyses does nothing when no stale analyses`() {
        whenever(analysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(
            listOf(CodeAnalysis(id = 1L, status = "COMPLETED")),
        )
        service.resetStaleAnalyses()
    }

    @Test
    fun `startLocalAnalysis saves analysis and delegates to self`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        val result = service.startLocalAnalysis(10L, includeMermaid = false, includeDrawio = true)

        assertEquals(analysis, result)
        verify(service).runAnalysis(1L, false, true)
    }

    @Test
    fun `startGitlabAnalysis with null config creates single analysis`() {
        whenever(codeRepoPort.listGitlabFilePaths(99L, 1L)).thenReturn(emptyList())
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        val analysis = CodeAnalysis(id = 5L, status = "PENDING")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        val results = service.startGitlabAnalysis(99L, 1L, "g/project")

        assertEquals(1, results.size)
        verify(service).runGitlabAnalysis(5L, 99L, 1L, "g/project", false, false, "")
    }

    @Test
    fun `startGitlabAnalysis detects monorepo and creates multiple analyses`() {
        whenever(codeRepoPort.listGitlabFilePaths(1L, 10L)).thenReturn(
            listOf("backend/build.gradle.kts", "frontend/package.json"),
        )
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        val analysis1 = CodeAnalysis(id = 1L, status = "PENDING", monorepoSubPath = "backend")
        val analysis2 = CodeAnalysis(id = 2L, status = "PENDING", monorepoSubPath = "frontend")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis1, analysis2)

        val results = service.startGitlabAnalysis(1L, 10L, "g/project")

        assertEquals(2, results.size)
        verify(service).runGitlabAnalysis(1L, 1L, 10L, "g/project", false, false, "backend")
        verify(service).runGitlabAnalysis(2L, 1L, 10L, "g/project", false, false, "frontend")
    }

    @Test
    fun `startGitlabAnalysis with exception during file listing falls back to single analysis`() {
        whenever(codeRepoPort.listGitlabFilePaths(1L, 10L))
            .thenThrow(RuntimeException("network error"))
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        val analysis = CodeAnalysis(id = 3L, status = "PENDING")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        val results = service.startGitlabAnalysis(1L, 10L, "g/project")

        assertEquals(1, results.size)
        verify(service).runGitlabAnalysis(3L, 1L, 10L, "g/project", false, false, "")
    }

    @Test
    fun `startGitlabAnalysis with single app creates single analysis`() {
        whenever(codeRepoPort.listGitlabFilePaths(1L, 10L)).thenReturn(
            listOf("build.gradle.kts", "src/Main.kt"),
        )
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        val analysis = CodeAnalysis(id = 4L, status = "PENDING")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        val results = service.startGitlabAnalysis(1L, 10L, "g/project", includeMermaid = true)

        assertEquals(1, results.size)
        verify(service).runGitlabAnalysis(4L, 1L, 10L, "g/project", true, false, "")
    }

    @Test
    fun `runAnalysis returns immediately when analysis not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)
        service.runAnalysis(99L)
    }

    @Test
    fun `runAnalysis marks FAILED when copilot token is null`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn(null)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token")

        service.runAnalysis(1L)

        assertEquals("FAILED", analysis.status)
        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(analysis)
    }

    @Test
    fun `runAnalysis marks FAILED when repo config not found`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadLocal(10L)).thenReturn(null)
        whenever(i18n.t(any<String>(), any())).thenReturn("config not found")
        whenever(i18n.translateError(any())).thenReturn("config not found")

        service.runAnalysis(1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runAnalysis success path completes analysis`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        val repoData = CodeAnalysisRepoData("myrepo", "/repo/path", "tree", "contents")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadLocal(10L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("ai output")

        service.runAnalysis(1L)

        verify(aiService).applyAiOutput(any(), any(), any(), any())
        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(analysis)
    }

    @Test
    fun `runAnalysis skips completed save when cancelled during AI run`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        val cancelled = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "FAILED")
        val repoData = CodeAnalysisRepoData("myrepo", "/repo/path", "tree", "contents")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(analysis)
            .thenReturn(cancelled)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadLocal(10L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("ai output")

        service.runAnalysis(1L)

        verify(analysisRepo, org.mockito.kotlin.times(1)).saveCodeAnalysis(any())
    }

    @Test
    fun `runAnalysis saves completed when guard check returns null`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        val repoData = CodeAnalysisRepoData("myrepo", "/repo/path", "tree", "contents")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(analysis)
            .thenReturn(null)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadLocal(10L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("ai output")

        service.runAnalysis(1L)

        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(any())
    }

    @Test
    fun `runAnalysis marks FAILED on exception`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = 10L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadLocal(10L)).thenThrow(RuntimeException("disk error"))
        whenever(i18n.translateError(any())).thenReturn("disk error")

        service.runAnalysis(1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runAnalysis uses original analysis when re-fetch in catch block throws`() {
        val analysis = CodeAnalysis(id = 2L, repoConfigId = 10L, status = "PENDING")
        var callCount = 0
        whenever(analysisRepo.findCodeAnalysisById(2L)).thenAnswer {
            callCount++
            if (callCount <= 2) analysis else throw RuntimeException("DB gone")
        }
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadLocal(10L)).thenThrow(RuntimeException("error"))
        whenever(i18n.translateError(any())).thenReturn("error")

        service.runAnalysis(2L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGitlabAnalysis returns when analysis not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)
        service.runGitlabAnalysis(99L, 1L, 2L, "g/p")
    }

    @Test
    fun `runGitlabAnalysis with blank subPath delegates to codeRepoPort`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val repoData = CodeAnalysisRepoData("project", "g/project", "src/Main.kt", "=== src/Main.kt ===\nfun main() {}")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGitlab(1L, 2L, "g/project", "")).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project", subPath = "")

        verify(codeRepoPort).loadGitlab(1L, 2L, "g/project", "")
        verify(aiService).applyAiOutput(any(), any(), any(), any())
    }

    @Test
    fun `runGitlabAnalysis skips completed save when cancelled during AI run`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val cancelled = CodeAnalysis(id = 1L, status = "FAILED")
        val repoData = CodeAnalysisRepoData("project", "g/project", "src/Main.kt", "=== src/Main.kt ===\nfun main() {}")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(cancelled)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGitlab(1L, 2L, "g/project", "")).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project")

        verify(analysisRepo, org.mockito.kotlin.times(1)).saveCodeAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis saves completed when guard check returns null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val repoData = CodeAnalysisRepoData("project", "g/project", "src/Main.kt", "=== src/Main.kt ===\nfun main() {}")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(null)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGitlab(1L, 2L, "g/project", "")).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project")

        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis with non-blank subPath passes subPath to codeRepoPort`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val repoData =
            CodeAnalysisRepoData(
                "backend",
                "g/project/backend",
                "backend/Main.kt",
                "=== backend/Main.kt ===\nfun main() {}",
            )
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGitlab(1L, 2L, "g/project", "backend")).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project", subPath = "backend")

        verify(codeRepoPort).loadGitlab(1L, 2L, "g/project", "backend")
    }

    @Test
    fun `runGitlabAnalysis marks FAILED when codeRepoPort returns null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadGitlab(1L, 2L, "g/project", "")).thenReturn(null)
        whenever(i18n.t(any<String>(), any())).thenReturn("not found")
        whenever(i18n.translateError(any())).thenReturn("not found")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project")

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `createMr throws when analysis not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)
        whenever(i18n.t(any<String>(), any())).thenReturn("not found")

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.createMr(99L, 1L, 2L)
        }
    }

    @Test
    fun `createMr throws when gitlab config not found`() {
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(99L)).thenReturn(null)
        whenever(i18n.t(any<String>(), any())).thenReturn("not found")

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.createMr(1L, 99L, 2L)
        }
    }

    @Test
    fun `createMr with empty fileChanges skips commitFiles`() {
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", fileChangesJson = null)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/1")

        val result = service.createMr(1L, 1L, 2L)

        assertEquals("http://mr/1", result)
        verify(gitlabMrPort, org.mockito.kotlin.never()).commitFiles(any(), any(), any(), any(), any())
    }

    @Test
    fun `createMr with UPDATE fileChange commits with update action`() {
        val fileChangesJson = """[{"path":"src/Foo.kt","changeType":"UPDATE","code":"class Foo {}"}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", fileChangesJson = fileChangesJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/2")

        service.createMr(1L, 1L, 2L)

        val actionsCaptor = org.mockito.kotlin.argumentCaptor<List<Map<String, String>>>()
        verify(gitlabMrPort).commitFiles(any(), any(), any(), any(), actionsCaptor.capture())
        assertEquals("update", actionsCaptor.firstValue[0]["action"])
    }

    @Test
    fun `createMr with CREATE fileChange commits with create action`() {
        val fileChangesJson = """[{"path":"src/New.kt","changeType":"CREATE","code":"class New {}"}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", fileChangesJson = fileChangesJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/3")

        service.createMr(1L, 1L, 2L)

        val actionsCaptor = org.mockito.kotlin.argumentCaptor<List<Map<String, String>>>()
        verify(gitlabMrPort).commitFiles(any(), any(), any(), any(), actionsCaptor.capture())
        assertEquals("create", actionsCaptor.firstValue[0]["action"])
        verify(gitlabMrPort).createMergeRequest(any(), any(), any(), org.mockito.kotlin.eq("main"), any(), any())
    }

    @Test
    fun `createMr saves mrUrl and mrBranch to analysis`() {
        val analysis = CodeAnalysis(id = 5L, status = "COMPLETED")
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(5L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "develop"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/5")

        service.createMr(5L, 1L, 2L)

        assertEquals("http://mr/5", analysis.mrUrl)
        assertEquals("code-analyse-5", analysis.mrBranch)
    }

    @Test
    fun `buildMrDescription includes summaryText when present`() {
        val analysis =
            CodeAnalysis(
                id = 1L,
                status = "COMPLETED",
                summaryText = "Great code overall.",
                findingsJson = null,
            )
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "main"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/1")

        service.createMr(1L, 1L, 2L)

        assertTrue(descCaptor.firstValue.contains("Great code overall."))
    }

    @Test
    fun `buildMrDescription includes HIGH severity finding badge`() {
        val findingsJson =
            """[{"id":"f1","file":"Foo.kt","type":"DUPLICATE_CODE","severity":"HIGH","description":"dup","suggestion":"fix"}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "main"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/1")

        service.createMr(1L, 1L, 2L)

        assertTrue(descCaptor.firstValue.contains("🔴"))
    }

    @Test
    fun `buildMrDescription includes MEDIUM severity finding badge`() {
        val findingsJson =
            """[{"id":"f2","file":"Bar.kt","type":"UNUSED_FUNCTION","severity":"MEDIUM","description":"unused","suggestion":"remove"}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "main"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/1")

        service.createMr(1L, 1L, 2L)

        assertTrue(descCaptor.firstValue.contains("🟡"))
    }

    @Test
    fun `buildMrDescription uses green badge for LOW severity finding`() {
        val findingsJson = """[{"id":"f3","file":"Baz.kt","type":"UNUSED_VARIABLE","severity":"LOW","description":"low","suggestion":""}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "main"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/1")

        service.createMr(1L, 1L, 2L)

        assertTrue(descCaptor.firstValue.contains("🟢"))
    }

    @Test
    fun `getLatestGithubAnalysis returns analysis from repo`() {
        val analysis = CodeAnalysis(id = 7L, status = "COMPLETED", githubRepoConfigId = 42L)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(42L)).thenReturn(analysis)
        val result = service.getLatestGithubAnalysis(42L)
        assertEquals(analysis, result)
    }

    @Test
    fun `getLatestGithubAnalysis returns null when not found`() {
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(99L)).thenReturn(null)
        assertNull(service.getLatestGithubAnalysis(99L))
    }

    @Test
    fun `cancelLatestRunningForRepo cancels PENDING analysis`() {
        val analysis = CodeAnalysis(id = 11L, status = "PENDING")
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(5L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(i18n.t(any<String>())).thenReturn("cancelled")

        val result = service.cancelLatestRunningForRepo(5L)

        assertEquals(11L, result)
        assertEquals("CANCELLED", analysis.status)
    }

    @Test
    fun `cancelLatestRunningForRepo returns null when analysis is COMPLETED`() {
        val analysis = CodeAnalysis(id = 12L, status = "COMPLETED")
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(6L)).thenReturn(analysis)

        val result = service.cancelLatestRunningForRepo(6L)

        assertNull(result)
    }

    @Test
    fun `startGithubAnalysis saves analysis and delegates to runGithubAnalysis`() {
        val analysis = CodeAnalysis(id = 20L, status = "PENDING", githubRepoConfigId = 3L)
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        val result = service.startGithubAnalysis(3L, includeMermaid = false, includeDrawio = false)

        assertEquals(analysis, result)
        verify(service).runGithubAnalysis(20L, 3L, false, false)
    }

    @Test
    fun `runGithubAnalysis returns immediately when analysis not found`() {
        whenever(analysisRepo.findCodeAnalysisById(99L)).thenReturn(null)

        service.runGithubAnalysis(99L, 1L)

        verify(analysisRepo, org.mockito.kotlin.never()).saveCodeAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis marks FAILED when copilot token is null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn(null)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token")

        service.runGithubAnalysis(1L, 1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGithubAnalysis marks FAILED when github config not found`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadGithub(42L)).thenThrow(IllegalStateException("GitHub repository configuration not found (id=42)"))
        whenever(i18n.translateError(any())).thenReturn("not found")

        service.runGithubAnalysis(1L, 42L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGithubAnalysis marks FAILED when fetchRepoContext returns null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadGithub(1L)).thenThrow(IllegalStateException("GitHub repository acme/app is not accessible."))
        whenever(i18n.translateError(any())).thenReturn("not accessible")

        service.runGithubAnalysis(1L, 1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGithubAnalysis success path completes analysis`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val repoData =
            CodeAnalysisRepoData(
                "acme/app",
                "acme/app",
                "src/main/kotlin/App.kt\n",
                "=== src/main/kotlin/App.kt ===\nclass App",
            )
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGithub(1L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGithubAnalysis(1L, 1L)

        verify(aiService).applyAiOutput(any(), any(), any(), any())
        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(analysis)
    }

    @Test
    fun `runGithubAnalysis skips completed save when cancelled during AI run`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val cancelled = CodeAnalysis(id = 1L, status = "FAILED")
        val repoData = CodeAnalysisRepoData("acme/app", "acme/app", "tree", "contents")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(cancelled)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGithub(1L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGithubAnalysis(1L, 1L)

        verify(analysisRepo, org.mockito.kotlin.times(1)).saveCodeAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis saves completed when guard check returns null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        val repoData = CodeAnalysisRepoData("acme/app", "acme/app", "tree", "contents")
        whenever(analysisRepo.findCodeAnalysisById(1L))
            .thenReturn(analysis)
            .thenReturn(null)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        whenever(codeRepoPort.loadGithub(1L)).thenReturn(repoData)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("output")

        service.runGithubAnalysis(1L, 1L)

        verify(analysisRepo, org.mockito.kotlin.atLeast(2)).saveCodeAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis marks FAILED on exception`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadGithub(1L)).thenThrow(RuntimeException("network error"))
        whenever(i18n.translateError(any())).thenReturn("network error")

        service.runGithubAnalysis(1L, 1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `buildMrDescription includes line number when finding has line set`() {
        val findingsJson = """[{"id":"f4","file":"Foo.kt","line":42,"type":"BUG","severity":"HIGH","description":"oops","suggestion":""}]"""
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.fetchProjectDetail(any(), any()))
            .thenReturn(GitlabMrProjectDetail(id = 2L, defaultBranch = "main"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/1")

        service.createMr(1L, 1L, 2L)

        assertTrue(descCaptor.firstValue.contains("Zeile 42"))
    }

    @Test
    fun `FileChange default constructor uses expected defaults`() {
        val fc =
            com.stacklens.domain.model
                .CodeFileChange()
        assertEquals("", fc.path)
        assertEquals(FileChangeType.UPDATE, fc.changeType)
        assertEquals("", fc.code)
    }

    @Test
    fun `Finding default constructor uses expected defaults`() {
        val f =
            com.stacklens.domain.model
                .CodeFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertNull(f.line)
        assertNull(f.snippet)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertNull(f.suggestedCode)
    }

    @Test
    fun `startGitlabAnalysis with null monorepoSubPath in saved analysis falls back to empty string`() {
        whenever(codeRepoPort.listGitlabFilePaths(1L, 10L)).thenReturn(
            listOf("backend/build.gradle.kts", "frontend/package.json"),
        )
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4o")
        val savedAnalysis1 = CodeAnalysis(id = 1L, status = "PENDING", monorepoSubPath = null)
        val savedAnalysis2 = CodeAnalysis(id = 2L, status = "PENDING", monorepoSubPath = null)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(savedAnalysis1, savedAnalysis2)

        val results = service.startGitlabAnalysis(1L, 10L, "g/project")

        assertEquals(2, results.size)
        verify(service).runGitlabAnalysis(1L, 1L, 10L, "g/project", false, false, "")
        verify(service).runGitlabAnalysis(2L, 1L, 10L, "g/project", false, false, "")
    }

    @Test
    fun `createMr with invalid JSON in fileChangesJson uses empty list`() {
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", fileChangesJson = "NOT_VALID_JSON")
        val projectDetail = GitlabMrProjectDetail(id = 1L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(any())).thenReturn(
            GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok"),
        )
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/1")

        service.createMr(1L, 1L, 1L)
    }

    @Test
    fun `runAnalysis handles null repoConfigId by using 0L fallback`() {
        val analysis = CodeAnalysis(id = 1L, repoConfigId = null, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(codeRepoPort.loadLocal(0L)).thenReturn(null)
        whenever(i18n.t(any<String>(), any())).thenReturn("repo not found")
        whenever(i18n.translateError(any())).thenReturn("error")

        service.runAnalysis(1L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `buildMrDescription with null summaryText skips summary section`() {
        val findingsJson = """[{"id":"f1","file":"Foo.kt","type":"BUG","severity":"HIGH","description":"desc","suggestion":"fix"}]"""
        val fileChangesJson = """[{"path":"src/Foo.kt","changeType":"UPDATE","code":"class Foo {}"}]"""
        val analysis =
            CodeAnalysis(
                id = 1L,
                status = "COMPLETED",
                summaryText = null,
                findingsJson = findingsJson,
                fileChangesJson = fileChangesJson,
            )
        val projectDetail = GitlabMrProjectDetail(id = 1L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        val gitlabConfig = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(codeRepoPort.findGitlabConfig(any())).thenReturn(gitlabConfig)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("https://gl/mr/1")

        service.createMr(1L, 1L, 1L)

        verify(gitlabMrPort).createMergeRequest(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `buildMrDescription with invalid findingsJson uses empty findings list`() {
        val fileChangesJson = """[{"path":"src/Foo.kt","changeType":"UPDATE","code":"class Foo {}"}]"""
        val analysis =
            CodeAnalysis(
                id = 1L,
                status = "COMPLETED",
                summaryText = "summary",
                findingsJson = "{NOT_JSON}",
                fileChangesJson = fileChangesJson,
            )
        val projectDetail = GitlabMrProjectDetail(id = 1L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        val gitlabConfig = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(codeRepoPort.findGitlabConfig(any())).thenReturn(gitlabConfig)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("https://gl/mr/2")

        service.createMr(1L, 1L, 1L)

        verify(gitlabMrPort).createMergeRequest(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `runGitlabAnalysis marks FAILED when copilot token is null`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn(null)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token error")

        service.runGitlabAnalysis(1L, 1L, 2L, "g/project")

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGithubAnalysis marks FAILED when config id does not match any in list`() {
        val analysis = CodeAnalysis(id = 1L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("ghp_token")
        whenever(codeRepoPort.loadGithub(42L)).thenThrow(IllegalStateException("GitHub repository configuration not found (id=42)"))
        whenever(i18n.translateError(any())).thenReturn("not found")

        service.runGithubAnalysis(1L, 42L)

        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `createMr with blank fileChangesJson uses empty list`() {
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", fileChangesJson = "")
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/blank")

        val result = service.createMr(1L, 1L, 2L)

        assertEquals("http://mr/blank", result)
        verify(gitlabMrPort, org.mockito.kotlin.never()).commitFiles(any(), any(), any(), any(), any())
    }

    @Test
    fun `buildMrDescription with blank summaryText skips summary section`() {
        val analysis = CodeAnalysis(id = 1L, status = "COMPLETED", summaryText = "  ")
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(1L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://mr/blank-summary")

        service.createMr(1L, 1L, 2L)

        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        verify(gitlabMrPort).createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture())
        assertTrue(!descCaptor.firstValue.contains("Zusammenfassung"))
    }

    @Test
    fun `buildMrDescription renders clickable file link with line anchor when project webUrl is set`() {
        val findingsJson =
            """[{"id":"f1","file":"src/Foo.kt","line":42,"type":"BUG","severity":"HIGH","description":"oops","suggestion":"fix"}]"""
        val analysis = CodeAnalysis(id = 7L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail =
            GitlabMrProjectDetail(
                id = 2L,
                defaultBranch = "main",
                pathWithNamespace = "group/project",
                webUrl = "http://gl.test/group/project",
            )
        whenever(analysisRepo.findCodeAnalysisById(7L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/7")

        service.createMr(7L, 1L, 2L)

        val desc = descCaptor.firstValue
        val expectedUrl = "http://gl.test/group/project/-/blob/code-analyse-7/src/Foo.kt#L42"
        assertTrue(
            desc.contains("[src/Foo.kt (Zeile 42)]($expectedUrl)"),
            "Expected clickable link to $expectedUrl, got: $desc",
        )
    }

    @Test
    fun `buildMrDescription renders clickable file link without line anchor when line is null`() {
        val findingsJson =
            """[{"id":"f1","file":"src/Foo.kt","line":null,"type":"BUG","severity":"HIGH","description":"oops","suggestion":""}]"""
        val analysis = CodeAnalysis(id = 8L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail =
            GitlabMrProjectDetail(
                id = 2L,
                defaultBranch = "main",
                webUrl = "http://gl.test/group/project",
            )
        whenever(analysisRepo.findCodeAnalysisById(8L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/8")

        service.createMr(8L, 1L, 2L)

        val desc = descCaptor.firstValue
        val expectedUrl = "http://gl.test/group/project/-/blob/code-analyse-8/src/Foo.kt"
        assertTrue(
            desc.contains("[src/Foo.kt]($expectedUrl)"),
            "Expected file-only link (no #L) when line is null, got: $desc",
        )
        assertFalse(desc.contains("#L"), "No line anchor expected when line is null, got: $desc")
    }

    @Test
    fun `buildMrDescription falls back to plain text file ref when project webUrl is blank`() {
        val findingsJson =
            """[{"id":"f1","file":"src/Foo.kt","line":42,"type":"BUG","severity":"HIGH","description":"oops","suggestion":""}]"""
        val analysis = CodeAnalysis(id = 9L, status = "COMPLETED", findingsJson = findingsJson)
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail = GitlabMrProjectDetail(id = 2L, defaultBranch = "main")
        whenever(analysisRepo.findCodeAnalysisById(9L)).thenReturn(analysis)
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        whenever(codeRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(any(), any())).thenReturn(projectDetail)
        val descCaptor = org.mockito.kotlin.argumentCaptor<String>()
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), descCaptor.capture()))
            .thenReturn("http://mr/9")

        service.createMr(9L, 1L, 2L)

        val desc = descCaptor.firstValue
        assertTrue(desc.contains("src/Foo.kt (Zeile 42)"), "Expected plain-text label, got: $desc")
        assertFalse(desc.contains("]("), "No Markdown link expected when webUrl is blank, got: $desc")
    }

    @Test
    fun `runAnalysis marks FAILED when copilotCliService is not ready`() {
        val analysis = CodeAnalysis(id = 51L, status = "PENDING", repoConfigId = 1L)
        whenever(analysisRepo.findCodeAnalysisById(51L)).thenReturn(analysis)
        whenever(copilotCliService.isReady()).thenReturn(false)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        service.runAnalysis(51L)
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGitlabAnalysis marks FAILED when copilotCliService is not ready`() {
        val analysis = CodeAnalysis(id = 52L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(52L)).thenReturn(analysis)
        whenever(copilotCliService.isReady()).thenReturn(false)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        service.runGitlabAnalysis(52L, 1L, 100L, "group/project")
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runGithubAnalysis marks FAILED when copilotCliService is not ready`() {
        val analysis = CodeAnalysis(id = 53L, status = "PENDING")
        whenever(analysisRepo.findCodeAnalysisById(53L)).thenReturn(analysis)
        whenever(copilotCliService.isReady()).thenReturn(false)
        whenever(i18n.t(any<String>())).thenReturn("no token")
        whenever(i18n.translateError(any())).thenReturn("no token")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)
        service.runGithubAnalysis(53L, 1L)
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runAnalysis skips COMPLETED save when analysis was set to FAILED during AI call`() {
        val analysis = CodeAnalysis(id = 60L, status = "PENDING", repoConfigId = 1L)
        val failedAnalysis = CodeAnalysis(id = 60L, status = "FAILED", repoConfigId = 1L)
        whenever(analysisRepo.findCodeAnalysisById(60L))
            .thenReturn(analysis)
            .thenReturn(failedAnalysis)
        whenever(copilotCliService.isReady()).thenReturn(true)
        val repoData = CodeAnalysisRepoData("TestApp", "/repo", "src/Main.kt", "fun main() {}")
        whenever(codeRepoPort.loadLocal(1L)).thenReturn(repoData)
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("AI output")
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(analysisRepo.saveCodeAnalysis(any())).thenReturn(analysis)

        service.runAnalysis(60L)

        val captor = org.mockito.kotlin.argumentCaptor<CodeAnalysis>()
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).saveCodeAnalysis(captor.capture())
        val savedStatuses = captor.allValues.map { it.status }
        assertFalse(savedStatuses.contains("COMPLETED"), "COMPLETED should not be saved when analysis was cancelled during AI run")
    }
}
