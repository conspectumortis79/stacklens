package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.securityanalysis.AiCallDeadline
import com.stacklens.application.service.securityanalysis.SecurityAiOutputParser
import com.stacklens.application.service.securityanalysis.SecurityAnalysisPromptBuilder
import com.stacklens.application.service.securityanalysis.SecurityFileContext
import com.stacklens.application.service.securityanalysis.SecurityFinding
import com.stacklens.application.service.securityanalysis.SecurityFindingMerger
import com.stacklens.application.service.securityanalysis.SecurityFixService
import com.stacklens.application.service.securityanalysis.SecurityPentestExecutor
import com.stacklens.application.service.securityanalysis.SecurityRepoContext
import com.stacklens.application.service.securityanalysis.SecurityRepoStrategy
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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

class SecurityAnalysisServiceTest {
    private val analysisRepo: SecurityAnalysisPort = mock()
    private val copilotPort: CopilotStreamingPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private val i18n: I18nService =
        mock(
            defaultAnswer =
                org.mockito.stubbing.Answer { invocation ->
                    if (invocation.method.name == "t") {
                        val key = invocation.arguments.getOrNull(0) as? String ?: ""
                        when (key) {
                            "security_analysis_be.be_defensive_analysis_completed" ->
                                "Defensive Analyse abgeschlossen. Weiterführende Ausnutzungsdetails wurden von der KI aus Sicherheitsgründen nicht ausgegeben."
                            "security_analysis_be.be_iteration_skipped_security" -> {
                                val args =
                                    invocation.arguments.drop(1).flatMap {
                                        if (it is Array<*>) it.toList() else listOf(it)
                                    }
                                "Iteration ${args.getOrNull(
                                    0,
                                )}/${args.getOrNull(1)} übersprungen – KI blieb aus Sicherheitsgründen bei defensiver Auswertung"
                            }
                            else -> ""
                        }
                    } else {
                        ""
                    }
                },
        )
    private val mockStrategy: SecurityRepoStrategy = mock()
    private val fixAiOutput: String =
        """
        ANALYSIS:
        Fixed the bug.

        FILES:
        ```json
        [{"path":"src/Main.kt","code":"fixed code","changeType":"UPDATE"}]
        ```
        """.trimIndent()
    private val clock: FixedClockPort = FixedClockPort(1_700_000_000_000L)
    private val self: SecurityAnalysisService = mock()
    private lateinit var service: SecurityAnalysisService

    @BeforeEach
    fun setup() {
        val aiUsageContextForTest = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>()
        val promptBuilder = SecurityAnalysisPromptBuilder(copilotPort, promptLoader)
        val findingMerger =
            SecurityFindingMerger(
                jsonCodec,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiJsonRepairer(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val aiOutputParser = SecurityAiOutputParser(jsonCodec)
        val fixService =
            SecurityFixService(
                analysisRepo = analysisRepo,
                copilotPort = copilotPort,
                jsonCodec = jsonCodec,
                i18n = i18n,
                repoStrategies = listOf(mockStrategy),
                promptBuilder = promptBuilder,
                aiOutputParser = aiOutputParser,
                clock = clock,
                fixAiBudgetSeconds = SecurityAnalysisService.FIX_AI_BUDGET_SECONDS,
            )
        val pentestExecutor =
            SecurityPentestExecutor(
                analysisRepo = analysisRepo,
                copilotPort = copilotPort,
                aiUsageContext = aiUsageContextForTest,
                jsonCodec = jsonCodec,
                i18n = i18n,
                repoStrategies = listOf(mockStrategy),
                promptBuilder = promptBuilder,
                findingMerger = findingMerger,
                aiOutputParser = aiOutputParser,
                clock = clock,
            )
        service =
            spy(
                SecurityAnalysisService(
                    analysisRepo,
                    copilotPort,
                    aiUsageContext = aiUsageContextForTest,
                    jsonCodec,
                    i18n,
                    listOf(mockStrategy),
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    startupHealer = StartupHealer(),
                    promptBuilder = promptBuilder,
                    findingMerger = findingMerger,
                    aiOutputParser = aiOutputParser,
                    clock = clock,
                    fixService = fixService,
                    pentestExecutor = pentestExecutor,
                ),
            )
        whenever(copilotPort.isReady()).thenCallRealMethod()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt-text")
        whenever(i18n.translateError(any())).thenReturn("error-message")
    }

    private fun analysis(
        id: Long = 1L,
        status: String = "PENDING",
        repoConfigId: Long? = null,
        gitlabConfigId: Long? = null,
        githubRepoConfigId: Long? = null,
    ) = SecurityAnalysis(
        id = id,
        status = status,
        repoConfigId = repoConfigId,
        gitlabConfigId = gitlabConfigId,
        githubRepoConfigId = githubRepoConfigId,
    )

    @Test
    fun `resetStaleAnalyses does nothing when no stale analyses`() {
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(emptyList())
        service.heal()
        verify(analysisRepo, never()).saveSecurityAnalysis(any())
    }

    @Test
    fun `resetStaleAnalyses marks RUNNING analysis as FAILED`() {
        val a = analysis(status = "RUNNING")
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(a))
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.resetStaleAnalyses()
        assertEquals("CANCELLED", a.status)
        verify(analysisRepo).saveSecurityAnalysis(a)
    }

    @Test
    fun `resetStaleAnalyses marks PENDING analysis as FAILED`() {
        val a = analysis(status = "PENDING")
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(a))
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.resetStaleAnalyses()
        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `resetStaleAnalyses handles save exception gracefully`() {
        val a = analysis(status = "RUNNING")
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(a))
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenThrow(RuntimeException("db error"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `resetStaleAnalyses handles repo exception gracefully`() {
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenThrow(RuntimeException("db error"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `cancelAnalysis does nothing when analysis not found`() {
        whenever(analysisRepo.findSecurityAnalysisById(99L)).thenReturn(null)
        service.cancelAnalysis(99L)
        verify(analysisRepo, never()).saveSecurityAnalysis(any())
    }

    @Test
    fun `cancelAnalysis cancels RUNNING analysis`() {
        val a = analysis(status = "RUNNING")
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.cancelAnalysis(1L)
        assertEquals("CANCELLED", a.status)
        verify(analysisRepo).saveSecurityAnalysis(a)
    }

    @Test
    fun `cancelAnalysis cancels PENDING analysis`() {
        val a = analysis(status = "PENDING")
        whenever(analysisRepo.findSecurityAnalysisById(2L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.cancelAnalysis(2L)
        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `cancelAnalysis does not change COMPLETED analysis`() {
        val a = analysis(status = "COMPLETED")
        whenever(analysisRepo.findSecurityAnalysisById(3L)).thenReturn(a)
        service.cancelAnalysis(3L)
        assertEquals("COMPLETED", a.status)
        verify(analysisRepo, never()).saveSecurityAnalysis(any())
    }

    @Test
    fun `generateFix throws when analysis not found`() {
        whenever(analysisRepo.findSecurityAnalysisById(99L)).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.generateFix(99L, "F1")
        }
    }

    @Test
    fun `generateFix with null findingsJson throws when finding not found`() {
        val a = analysis(id = 1L).also { it.findingsJson = null }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        assertThrows(IllegalArgumentException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `generateFix with blank findingsJson throws when finding not found`() {
        val a = analysis(id = 1L).also { it.findingsJson = "  " }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        assertThrows(IllegalArgumentException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `generateFix with invalid findingsJson treats as empty`() {
        val a = analysis(id = 1L).also { it.findingsJson = "not-valid-json" }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        assertThrows(IllegalArgumentException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `generateFix with valid findingsJson throws when findingId not in list`() {
        val a = analysis(id = 1L).also { it.findingsJson = """[{"id":"F2","type":"BUG"}]""" }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        assertThrows(IllegalArgumentException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `generateFix throws when no strategy supports the analysis`() {
        val finding = SecurityFinding(id = "F1", type = "BUG")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(false)
        assertThrows(com.stacklens.domain.NoSecurityAnalysisStrategyException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `generateFix bounds the AI request with the fix budget and a cancellation check`() {
        stubFixFindingContext()
        val timeoutCaptor = argumentCaptor<Long>()
        val cancelledCaptor = argumentCaptor<() -> Boolean>()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(fixAiOutput)
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(localFixResult())

        service.generateFix(1L, "F1")

        verify(copilotPort).executeWithStreaming(
            eq("gpt-4"),
            any(),
            timeoutCaptor.capture(),
            cancelledCaptor.capture(),
            anyOrNull(),
            anyOrNull(),
        )
        assertEquals(
            SecurityAnalysisService.FIX_AI_BUDGET_SECONDS + AiCallDeadline.REQUEST_TIMEOUT_GRACE_SECONDS,
            timeoutCaptor.firstValue,
        )
        assertFalse(cancelledCaptor.firstValue.invoke())
        clock.advanceMillis(SecurityAnalysisService.FIX_AI_BUDGET_SECONDS * 1000)
        assertTrue(cancelledCaptor.firstValue.invoke())
    }

    @Test
    fun `generateFix reports the localized timeout and clears the interrupt flag when the AI budget is exhausted`() {
        stubFixFindingContext()
        whenever(i18n.t("bug_analysis.fix_timeout")).thenReturn("Zeitlimit überschritten")
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer {
                Thread.currentThread().interrupt()
                throw InterruptedException("AI HTTP send cancelled")
            }

        val error =
            assertThrows(IllegalStateException::class.java) {
                service.generateFix(1L, "F1")
            }

        assertEquals("Zeitlimit überschritten", error.message)
        assertFalse(Thread.interrupted())
    }

    @Test
    fun `generateFix ignores streamed partial output and keeps the final answer`() {
        stubFixFindingContext()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { invocation ->
                val onPartialOutput = invocation.getArgument<(String) -> Unit>(4)
                onPartialOutput("ANALYSIS:")
                onPartialOutput(" \n")
                fixAiOutput
            }
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(localFixResult())

        val result = service.generateFix(1L, "F1")

        assertEquals("Fixed", result.explanation)
        assertEquals(listOf("src/Main.kt"), result.filesChanged)
    }

    private fun stubFixFindingContext() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any()))
            .thenReturn(SecurityFileContext("project", "tree", "content", "testContent"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
    }

    private fun localFixResult() =
        com.stacklens.domain.model.security.SecurityFixResult(
            prUrl = null,
            branchName = null,
            explanation = "Fixed",
            filesChanged = listOf("src/Main.kt"),
            isLocalRepo = true,
            localChanges =
                listOf(
                    com.stacklens.domain.model.security.SecurityLocalFileChange(
                        "src/Main.kt",
                        "fixed code",
                        FileChangeType.UPDATE,
                    ),
                ),
        )

    @Test
    fun `generateFix calls strategy and returns result`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val fileCtx = SecurityFileContext("project", "tree", "content", "testContent")
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(fileCtx)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            """
            ANALYSIS:
            Fixed the bug.
            
            FILES:
            ```json
            [{"path":"src/Main.kt","code":"fixed code","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = "https://example.com/pr/1",
                branchName = "fix/F1",
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = false,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertEquals("https://example.com/pr/1", result.prUrl)
    }

    @Test
    fun `generateFix enriches result with unified diffs when prUrl set and allFiles non-empty`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val fileCtx =
            SecurityFileContext(
                "project",
                "tree",
                "content",
                "testContent",
                allFiles = listOf("src/Main.kt" to "val x = 1"),
            )
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(fileCtx)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            """
            ANALYSIS:
            Fixed the bug.

            FILES:
            ```json
            [{"path":"src/Main.kt","code":"val x = 2","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = "https://example.com/pr/2",
                branchName = "fix/F1",
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = false,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result.localChanges)
        val localChanges = result.localChanges!!
        assertEquals(1, localChanges.size)
        assertEquals(FileChangeType.DIFF, localChanges[0].changeType)
        assertEquals("src/Main.kt", localChanges[0].path)
        assertTrue(localChanges[0].code.contains("+val x = 2"))
    }

    @Test
    fun `generateFix skips diff enrichment when prUrl is null`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val fileCtx =
            SecurityFileContext(
                "project",
                "tree",
                "content",
                "testContent",
                allFiles = listOf("src/Main.kt" to "val x = 1"),
            )
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(fileCtx)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            """
            ANALYSIS:
            Fixed the bug.

            FILES:
            ```json
            [{"path":"src/Main.kt","code":"val x = 2","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed locally",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges =
                    listOf(
                        com.stacklens.domain.model.security.SecurityLocalFileChange(
                            "src/Main.kt",
                            "val x = 2",
                            FileChangeType.UPDATE,
                        ),
                    ),
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertEquals(null, result.prUrl)
        assertEquals(FileChangeType.UPDATE, result.localChanges!![0].changeType)
    }

    @Test
    fun `generateFix skips diff enrichment when localChanges already set`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val fileCtx =
            SecurityFileContext(
                "project",
                "tree",
                "content",
                "testContent",
                allFiles = listOf("src/Main.kt" to "val x = 1"),
            )
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(fileCtx)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            """
            ANALYSIS:
            Fixed.

            FILES:
            ```json
            [{"path":"src/Main.kt","code":"val x = 2","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val existingChange =
            com.stacklens.domain.model.security.SecurityLocalFileChange(
                "src/Main.kt",
                "already computed",
                FileChangeType.UPDATE,
            )
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = "https://example.com/pr/4",
                branchName = "fix/F1",
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = false,
                localChanges = listOf(existingChange),
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertEquals("already computed", result.localChanges!![0].code)
    }

    @Test
    fun `generateFix uses empty string for original when path not in allFiles`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a =
            analysis(id = 1L).also {
                it.findingsJson = objectMapper.writeValueAsString(listOf(finding))
            }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val fileCtx =
            SecurityFileContext(
                "project",
                "tree",
                "content",
                "testContent",
                allFiles = listOf("src/Other.kt" to "other content"),
            )
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(fileCtx)
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            """
            ANALYSIS:
            Fixed.

            FILES:
            ```json
            [{"path":"src/Main.kt","code":"val x = 2","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = "https://example.com/pr/5",
                branchName = "fix/F1",
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = false,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result.localChanges)
        val localChanges = result.localChanges!!
        assertEquals(FileChangeType.DIFF, localChanges[0].changeType)
        assertTrue(localChanges[0].code.contains("+val x = 2"))
    }

    @Test
    fun `startLocalAnalysis coerces iterations below 1 to 1`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val a = analysis()
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.startLocalAnalysis(1L, 0)
        verify(analysisRepo).saveSecurityAnalysis(
            org.mockito.kotlin.argThat { totalIterations == 1 },
        )
        verify(service).runAnalysis(1L)
    }

    @Test
    fun `startLocalAnalysis uses value within range`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val a = analysis()
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.startLocalAnalysis(5L, 2)
        verify(analysisRepo).saveSecurityAnalysis(
            org.mockito.kotlin.argThat { totalIterations == 2 },
        )
    }

    @Test
    fun `startLocalAnalysis coerces iterations above 3 to 3`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val a = analysis()
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.startLocalAnalysis(5L, 5)
        verify(analysisRepo).saveSecurityAnalysis(
            org.mockito.kotlin.argThat { totalIterations == 3 },
        )
    }

    @Test
    fun `startGitlabAnalysis saves analysis with gitlab fields`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val a = analysis()
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.startGitlabAnalysis(1L, 2L, "group/proj", 3)
        verify(analysisRepo).saveSecurityAnalysis(
            org.mockito.kotlin.argThat { gitlabConfigId == 1L && gitlabProjectId == 2L },
        )
        verify(service).runAnalysis(1L)
    }

    @Test
    fun `startGithubAnalysis saves analysis with github fields`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val a = analysis()
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.startGithubAnalysis(7L, 1)
        verify(analysisRepo).saveSecurityAnalysis(
            org.mockito.kotlin.argThat { githubRepoConfigId == 7L },
        )
        verify(service).runAnalysis(1L)
    }

    @Test
    fun `runAnalysis returns when analysis not found`() {
        whenever(analysisRepo.findSecurityAnalysisById(99L)).thenReturn(null)
        service.runAnalysis(99L)
        verify(analysisRepo, never()).saveSecurityAnalysis(any())
    }

    @Test
    fun `runAnalysis sets status to FAILED when resolveToken returns null`() {
        val a = analysis(id = 1L)
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn(null)
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis sets status to FAILED when no strategy found`() {
        val a = analysis(id = 2L)
        whenever(analysisRepo.findSecurityAnalysisById(2L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token123")
        whenever(mockStrategy.supports(a)).thenReturn(false)
        service.runAnalysis(2L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis completes successfully for single iteration`() {
        val a = analysis(id = 3L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(3L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        val ctx = SecurityRepoContext("app", "/path", "tree", "contents")
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(ctx)
        val aiOutput = "SUMMARY:\nSome summary\n\nFINDINGS:\n```json\n[]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(aiOutput)

        service.runAnalysis(3L)

        assertEquals("COMPLETED", a.status)
        assertNotNull(a.completedAt)
    }

    @Test
    fun `runAnalysis sets COMPLETED with findings from pentest iteration`() {
        val a = analysis(id = 4L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(4L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val aiOutput =
            """
            SUMMARY:
            Some security findings found.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"SECURITY_VULN","severity":"HIGH","description":"SQL injection","suggestion":"Sanitize"}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(aiOutput)

        service.runAnalysis(4L)

        assertEquals("COMPLETED", a.status)
        assertTrue(a.summaryText?.contains("Some security findings") == true)
    }

    @Test
    fun `runAnalysis handles exception and sets FAILED`() {
        val a = analysis(id = 5L)
        whenever(analysisRepo.findSecurityAnalysisById(5L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenThrow(RuntimeException("network error"))

        service.runAnalysis(5L)

        assertEquals("FAILED", a.status)
        assertNotNull(a.completedAt)
    }

    @Test
    fun `runAnalysis with 2 iterations covers iter2 step message`() {
        val a = analysis(id = 6L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(6L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val aiOutput1 =
            """
            SUMMARY:
            Iteration 1
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"MEDIUM","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        val aiOutput2 =
            """
            SUMMARY:
            Iteration 2
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d2","suggestion":"s2"}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(aiOutput1)
            .thenReturn(aiOutput2)

        service.runAnalysis(6L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with 3 iterations covers else step message`() {
        val a = analysis(id = 7L).also { it.totalIterations = 3 }
        whenever(analysisRepo.findSecurityAnalysisById(7L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val emptyOutput =
            """
            SUMMARY:
            Done.
            
            FINDINGS:
            ```json
            []
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(emptyOutput)

        service.runAnalysis(7L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis safety refusal at iter 2 breaks loop`() {
        val a = analysis(id = 8L).also { it.totalIterations = 3 }
        whenever(analysisRepo.findSecurityAnalysisById(8L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val aiOutput1 =
            """
            SUMMARY:
            Initial findings.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(aiOutput1)
            .thenThrow(IllegalStateException("defensive auswertung refused"))

        service.runAnalysis(8L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis non-safety IllegalStateException at iter 2 propagates as FAILED`() {
        val a = analysis(id = 9L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(9L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val aiOutput1 =
            """
            SUMMARY:
            Done.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(aiOutput1)
            .thenThrow(IllegalStateException("unrelated network error"))

        service.runAnalysis(9L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis IllegalStateException at iter 1 rethrows as FAILED`() {
        val a = analysis(id = 10L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(10L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenThrow(IllegalStateException("quarter blocked"))

        service.runAnalysis(10L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis existing finding is merged with updated fields`() {
        val a = analysis(id = 11L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(11L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 =
            """
            SUMMARY:
            It.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"MEDIUM","description":"d","suggestion":"s","iterationFound":1}]
            ```
            """.trimIndent()
        val iter2 =
            """SUMMARY:
It.

FINDINGS:
```json
[{"id":"F1","type":"BUG","severity":"HIGH","description":"updated","suggestion":"s2","exploitPayload":"payload","iterationFound":1}]
```"""
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenReturn(iter2)

        service.runAnalysis(11L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis without summary match in output leaves summaryText null`() {
        val a = analysis(id = 12L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(12L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val aiOutput = "No structured output here"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(aiOutput)

        service.runAnalysis(12L)

        assertEquals("COMPLETED", a.status)
        assertNotNull(a.summaryText)
    }

    @Test
    fun `runAnalysis with critical and high findings shows count in step message`() {
        val a = analysis(id = 13L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(13L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "t", "c"))
        val aiOutput =
            """
            SUMMARY:
            Serious findings.
            
            FINDINGS:
            ```json
            [{"id":"F1","severity":"CRITICAL","type":"BUG","description":"d","suggestion":"s"},
            {"id":"F2","severity":"HIGH","type":"BUG","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(aiOutput)

        service.runAnalysis(13L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `parseFindings returns empty when no FINDINGS block`() {
        val result = service.parseFindings("No findings block here", 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseFindings parses valid findings`() {
        val output =
            """
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        val result = service.parseFindings(output, 1)
        assertEquals(1, result.size)
        assertEquals("F1", result[0].id)
        assertEquals(1, result[0].iterationFound)
    }

    @Test
    fun `parseFindings updates iterationFound when iter greater than 1 and finding has iterationFound 1`() {
        val output =
            """
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s","iterationFound":1}]
            ```
            """.trimIndent()
        val result = service.parseFindings(output, 2)
        assertEquals(2, result[0].iterationFound)
    }

    @Test
    fun `parseFindings keeps iterationFound when finding has non-1 value`() {
        val output =
            """
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s","iterationFound":2}]
            ```
            """.trimIndent()
        val result = service.parseFindings(output, 3)
        assertEquals(2, result[0].iterationFound)
    }

    @Test
    fun `parseFindings returns empty on JSON parse error`() {
        val output =
            """
            FINDINGS:
            ```json
            not-valid-json-[{
            ```
            """.trimIndent()
        val result = service.parseFindings(output, 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildIterationPrompt uses iter1Prompt for iteration 1`() {
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), org.mockito.kotlin.eq("securityanalysis.summaryFormatHint"), any())).thenReturn("hint")
        whenever(promptLoader.get(any(), org.mockito.kotlin.eq("securityanalysis.iter1Prompt"), any())).thenReturn("iter1-prompt")
        val result = service.buildIterationPrompt(1, 3, "proj", "/path", "tree", "files", "[]")
        assertEquals("iter1-prompt", result)
    }

    @Test
    fun `buildIterationPrompt uses iter2Prompt for iteration 2`() {
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), org.mockito.kotlin.eq("securityanalysis.iter2Prompt"), any())).thenReturn("iter2-prompt")
        val result = service.buildIterationPrompt(2, 3, "proj", "/path", "tree", "files", "[]")
        assertEquals("iter2-prompt", result)
    }

    @Test
    fun `buildIterationPrompt uses iter3Prompt for iteration 3`() {
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), org.mockito.kotlin.eq("securityanalysis.iter3Prompt"), any())).thenReturn("iter3-prompt")
        val result = service.buildIterationPrompt(3, 3, "proj", "/path", "tree", "files", "prev-json")
        assertEquals("iter3-prompt", result)
    }

    @Test
    fun `applyAiOutput sets summaryText from SUMMARY block`() {
        val a = analysis(id = 1L)
        val output = "SUMMARY:\nThis is the summary.\n\nFINDINGS:\n```json\n[]\n```"
        service.applyAiOutput(a, output)
        assertEquals("This is the summary.", a.summaryText)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput uses prose fallback when no SUMMARY block`() {
        val a = analysis(id = 1L)
        service.applyAiOutput(a, "No structured output")
        assertEquals("No structured output", a.summaryText)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput falls through empty SUMMARY to second regex`() {
        val a = analysis(id = 1L)
        service.applyAiOutput(a, "SUMMARY:\n  \nFINDINGS:\n```json\n[]\n```")
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `parseFixOutput extracts explanation and changes`() {
        val output =
            """
            ANALYSIS:
            Fixed the vulnerability.
            
            FILES:
            ```json
            [{"path":"src/Main.kt","code":"fixed","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertEquals("Fixed the vulnerability.", result.explanation)
        assertEquals(1, result.changes.size)
        assertEquals("src/Main.kt", result.changes[0].path)
    }

    @Test
    fun `parseFixOutput returns empty when no FILES block`() {
        val result = service.parseFixOutput("ANALYSIS:\nSome explanation.\n")
        assertEquals("Some explanation.", result.explanation)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `parseFixOutput returns empty changes on invalid JSON in FILES block`() {
        val output =
            """
            ANALYSIS:
            explanation.
            
            FILES:
            ```json
            not-valid
            ```
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `parseFixOutput returns empty explanation when no ANALYSIS block`() {
        val output =
            """
            FILES:
            ```json
            [{"path":"src/Main.kt","code":"fixed","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertEquals("", result.explanation)
        assertEquals(1, result.changes.size)
    }

    @Test
    fun `generateFix with exploitPayload exercises buildExploitDetails payload branch`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt", exploitPayload = "'; DROP TABLE users;--")
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result)
    }

    @Test
    fun `generateFix with proofOfConcept exercises buildExploitDetails poc branch`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt", proofOfConcept = "curl http://localhost/vuln")
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result)
    }

    @Test
    fun `generateFix with attackSteps exercises buildExploitDetails steps branch`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt", attackSteps = listOf("Step 1", "Step 2"))
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result)
    }

    @Test
    fun `generateFix with all exploit details exercises all buildExploitDetails branches`() {
        val finding =
            SecurityFinding(
                id = "F1",
                type = "SECURITY_VULN",
                file = "src/Main.kt",
                exploitPayload = "payload",
                proofOfConcept = "poc-script",
                attackSteps = listOf("recon", "exploit", "escalate"),
            )
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", ""))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result)
    }

    @Test
    fun `runAnalysis merge keeps existing values when new fields are blank or null`() {
        val a = analysis(id = 14L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(14L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 =
            """
            SUMMARY:
            Found.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"orig","suggestion":"orig-s",
            "suggestedFix":"fix1","cwe":"CWE-89","exploitPayload":"ep","attackVector":"av",
            "attackSteps":["s1"],"proofOfConcept":"poc","cvssScore":"7.5",
            "exploitability":"easy","confirmed":true}]
            ```
            """.trimIndent()
        val iter2 =
            """
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"","description":"","suggestion":"","iterationFound":1}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenReturn(iter2)

        service.runAnalysis(14L)

        assertEquals("COMPLETED", a.status)
        val findings: List<SecurityFinding> =
            objectMapper.readValue(
                a.findingsJson!!,
                object : com.fasterxml.jackson.core.type.TypeReference<List<SecurityFinding>>() {},
            )
        assertEquals(1, findings.size)
        assertEquals("HIGH", findings[0].severity)
        assertEquals("orig", findings[0].description)
        assertEquals("orig-s", findings[0].suggestion)
        assertEquals("fix1", findings[0].suggestedFix)
        assertEquals("CWE-89", findings[0].cwe)
        assertEquals("ep", findings[0].exploitPayload)
        assertEquals("av", findings[0].attackVector)
        assertEquals(listOf("s1"), findings[0].attackSteps)
        assertEquals("poc", findings[0].proofOfConcept)
        assertEquals("7.5", findings[0].cvssScore)
        assertEquals("easy", findings[0].exploitability)
        assertTrue(findings[0].confirmed)
    }

    @Test
    fun `runAnalysis adds new finding from iteration 2`() {
        val a = analysis(id = 15L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(15L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 =
            """
            SUMMARY:
            Found.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        val iter2 =
            """
            FINDINGS:
            ```json
            [{"id":"F2","type":"SECURITY_VULN","severity":"CRITICAL","description":"new","suggestion":"s2","iterationFound":1}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenReturn(iter2)

        service.runAnalysis(15L)

        assertEquals("COMPLETED", a.status)
        val findings: List<SecurityFinding> =
            objectMapper.readValue(
                a.findingsJson!!,
                object : com.fasterxml.jackson.core.type.TypeReference<List<SecurityFinding>>() {},
            )
        assertEquals(2, findings.size)
    }

    @Test
    fun `runAnalysis safety refusal sets summaryText when previously blank`() {
        val a = analysis(id = 16L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(16L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 = ""
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenThrow(IllegalStateException("proof-of-concept refused"))

        service.runAnalysis(16L)

        assertEquals("COMPLETED", a.status)
        assertNotNull(a.summaryText)
        assertTrue(a.summaryText!!.contains("Defensive"))
    }

    @Test
    fun `runAnalysis no matching strategy sets FAILED`() {
        val a = analysis(id = 20L)
        whenever(analysisRepo.findSecurityAnalysisById(20L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(false)

        service.runAnalysis(20L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis with malformed findings JSON uses empty findings`() {
        val a = analysis(id = 21L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(21L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))

        val badOutput = "SUMMARY:\nFound.\n\nFINDINGS:\n```json\n[{invalid}]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(badOutput)

        service.runAnalysis(21L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis iter1 safety refusal propagates as FAILED`() {
        val a = analysis(id = 22L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(22L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenThrow(IllegalStateException("offensive analysis refused"))

        service.runAnalysis(22L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis with no FINDINGS block returns empty findings`() {
        val a = analysis(id = 23L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(23L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))

        val noFindings = "SUMMARY:\nEverything looks secure."
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(noFindings)

        service.runAnalysis(23L)

        assertEquals("COMPLETED", a.status)
        assertNotNull(a.summaryText)
    }

    @Test
    fun `runAnalysis error with null exception message uses fallback`() {
        val a = analysis(id = 24L)
        whenever(analysisRepo.findSecurityAnalysisById(24L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenThrow(RuntimeException(null as String?))

        service.runAnalysis(24L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis accumulates findings across iterations`() {
        val a = analysis(id = 25L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(25L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))

        val iter1 =
            "SUMMARY:\nIter1.\n\nFINDINGS:\n```json\n" +
                "[{\"id\":\"F1\",\"type\":\"BUG\",\"severity\":\"HIGH\",\"description\":\"d1\",\"suggestion\":\"s1\"}]\n```"
        val iter2 =
            "SUMMARY:\nIter2.\n\nFINDINGS:\n```json\n" +
                "[{\"id\":\"F1\",\"type\":\"BUG\",\"severity\":\"CRITICAL\"," +
                "\"description\":\"d1-updated\",\"suggestion\":\"s1b\"}," +
                "{\"id\":\"F2\",\"type\":\"SECURITY_VULN\",\"severity\":\"LOW\"," +
                "\"description\":\"d2\",\"suggestion\":\"s2\"}]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenReturn(iter2)

        service.runAnalysis(25L)

        assertEquals("COMPLETED", a.status)
        val findings: List<SecurityFinding> =
            objectMapper.readValue(
                a.findingsJson!!,
                object : com.fasterxml.jackson.core.type.TypeReference<List<SecurityFinding>>() {},
            )
        assertTrue(findings.size >= 2)
    }

    @Test
    fun `runAnalysis merge updates optional fields when iter2 provides non-null values`() {
        val a = analysis(id = 26L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(26L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))

        val iter1 =
            """
            SUMMARY:
            Initial.
            
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"HIGH","description":"d","suggestion":"s"}]
            ```
            """.trimIndent()
        val iter2 =
            """
            FINDINGS:
            ```json
            [{"id":"F1","type":"BUG","severity":"CRITICAL","description":"d-updated","suggestion":"s-updated",
              "suggestedFix":"new-fix","cwe":"CWE-79","exploitPayload":"xss-payload",
              "attackVector":"network","attackSteps":["step1","step2"],
              "proofOfConcept":"poc-code","cvssScore":"8.5","exploitability":"exploitable","confirmed":true}]
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenReturn(iter2)

        service.runAnalysis(26L)

        assertEquals("COMPLETED", a.status)
        val findings: List<SecurityFinding> =
            objectMapper.readValue(
                a.findingsJson!!,
                object : com.fasterxml.jackson.core.type.TypeReference<List<SecurityFinding>>() {},
            )
        assertEquals(1, findings.size)
        assertEquals("CRITICAL", findings[0].severity)
        assertEquals("new-fix", findings[0].suggestedFix)
        assertEquals("CWE-79", findings[0].cwe)
        assertEquals("xss-payload", findings[0].exploitPayload)
        assertEquals("network", findings[0].attackVector)
        assertEquals("poc-code", findings[0].proofOfConcept)
        assertEquals("8.5", findings[0].cvssScore)
        assertEquals("exploitable", findings[0].exploitability)
        assertTrue(findings[0].confirmed)
    }

    @Test
    fun `getAnalysis returns analysis from repository`() {
        val a = analysis(id = 42L)
        whenever(analysisRepo.findSecurityAnalysisById(42L)).thenReturn(a)
        val result = service.getAnalysis(42L)
        assertTrue(result === a)
    }

    @Test
    fun `getAnalysis returns null when not found`() {
        whenever(analysisRepo.findSecurityAnalysisById(99L)).thenReturn(null)
        val result = service.getAnalysis(99L)
        assertTrue(result == null)
    }

    @Test
    fun `getAllAnalyses returns list from repository`() {
        val analyses = listOf(analysis(id = 1L), analysis(id = 2L))
        whenever(analysisRepo.findRecentSecurityAnalyses(200)).thenReturn(analyses)
        val result = service.getAllAnalyses()
        assertEquals(2, result.size)
    }

    @Test
    fun `deleteAnalysis delegates to repository`() {
        service.deleteAnalysis(7L)
        verify(analysisRepo).deleteSecurityAnalysisById(7L)
    }

    @Test
    fun `resetStaleAnalyses ignores COMPLETED analysis and only heals stale ones`() {
        val completed = analysis(id = 1L, status = "COMPLETED")
        val running = analysis(id = 2L, status = "RUNNING")
        whenever(analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(completed, running))
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(running)
        service.resetStaleAnalyses()
        assertEquals("COMPLETED", completed.status)
        assertEquals("CANCELLED", running.status)
    }

    @Test
    fun `applyAiOutput uses loose fallback for zusammenfassung marker`() {
        val a = analysis(id = 1L)
        val output = "ZUSAMMENFASSUNG:\nKeine kritischen Probleme gefunden.\n\nBEFUNDE:\nsome"
        service.applyAiOutput(a, output)
        assertTrue(a.summaryText?.contains("Keine kritischen Probleme") == true)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput final fallback extracts prose before code fence`() {
        val a = analysis(id = 1L)
        val output = "Critical vulnerability found in auth flow.\n```json\n[]\n```"
        service.applyAiOutput(a, output)
        assertTrue(a.summaryText?.contains("Critical vulnerability found") == true)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput sets summaryText to null when output is blank`() {
        val a = analysis(id = 1L)
        service.applyAiOutput(a, "")
        assertTrue(a.summaryText == null)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `parseFixOutput uses anyFenced fallback when no FILES header present`() {
        val output =
            """
            ANALYSIS:
            Patched the issue.
            
            ```json
            [{"path":"src/Fix.kt","code":"fixed code","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertEquals(1, result.changes.size)
        assertEquals("src/Fix.kt", result.changes[0].path)
    }

    @Test
    fun `parseFixOutput uses bare JSON fallback when no fence present`() {
        val output =
            """
            ANALYSIS:
            Applied the patch.
            
            [{"path":"src/Util.kt","code":"patched","changeType":"UPDATE"}]
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertEquals(1, result.changes.size)
        assertEquals("src/Util.kt", result.changes[0].path)
    }

    @Test
    fun `parseFixOutput filters out changes with blank path`() {
        val output =
            """
            FILES:
            ```json
            [{"path":"","code":"invalid","changeType":"UPDATE"},{"path":"src/Main.kt","code":"ok","changeType":"UPDATE"}]
            ```
            """.trimIndent()
        val result = service.parseFixOutput(output)
        assertEquals(1, result.changes.size)
        assertEquals("src/Main.kt", result.changes[0].path)
    }

    @Test
    fun `parseFixOutput normalizes CRLF line endings`() {
        val output =
            "ANALYSIS:\r\nFixed.\r\n\r\nFILES:\r\n```json\r\n[{\"path\":\"src/A.kt\",\"code\":\"x\",\"changeType\":\"UPDATE\"}]\r\n```"
        val result = service.parseFixOutput(output)
        assertEquals("Fixed.", result.explanation)
        assertEquals(1, result.changes.size)
    }

    @Test
    fun `generateFix throws when AI output contains no parseable file changes`() {
        val finding = SecurityFinding(id = "F1", type = "BUG", file = "src/Main.kt")
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn("ANALYSIS:\nSome analysis with no FILES section.")
        assertThrows(IllegalStateException::class.java) {
            service.generateFix(1L, "F1")
        }
    }

    @Test
    fun `runAnalysis invokes loadContext step callback`() {
        val a = analysis(id = 30L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(30L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenAnswer { invocation ->
            val onStep: (String) -> Unit = invocation.getArgument(1)
            onStep("Loading repository files…")
            SecurityRepoContext("app", "/p", "tree", "c")
        }
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(
            "SUMMARY:\nDone.\n\nFINDINGS:\n```json\n[]\n```",
        )

        service.runAnalysis(30L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with zero totalIterations skips pentest loop`() {
        val a = analysis(id = 40L).also { it.totalIterations = 0 }
        whenever(analysisRepo.findSecurityAnalysisById(40L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))

        service.runAnalysis(40L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis safety refusal does not overwrite non-blank summaryText`() {
        val a = analysis(id = 41L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(41L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 = "SUMMARY:\nExisting summary from iter1.\n\nFINDINGS:\n```json\n[]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenThrow(IllegalStateException("proof-of-concept refused"))

        service.runAnalysis(41L)

        assertEquals("Existing summary from iter1.", a.summaryText)
    }

    @Test
    fun `runAnalysis uses zusammenfassung marker as summary fallback in pentest loop`() {
        val a = analysis(id = 42L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(42L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val output = "ZUSAMMENFASSUNG:\nKeine Schwachstellen wurden identifiziert.\n\nBEFUNDE:\n```json\n[]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(output)

        service.runAnalysis(42L)

        assertTrue(a.summaryText?.contains("Keine Schwachstellen") == true)
    }

    @Test
    fun `runAnalysis uses prose fallback when no summary header in pentest output`() {
        val a = analysis(id = 43L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(43L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val output = "Critical vulnerability found in auth module. No standard headers here."
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(output)

        service.runAnalysis(43L)

        assertNotNull(a.summaryText)
        assertTrue(a.summaryText!!.contains("Critical vulnerability"))
    }

    @Test
    fun `runAnalysis sets summaryText to null when all content is inside code fences`() {
        val a = analysis(id = 44L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(44L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val output = "```\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(output)

        service.runAnalysis(44L)

        assertTrue(a.summaryText == null)
    }

    @Test
    fun `generateFix with non-null cwe line and suggestedFix covers non-null branches`() {
        val finding =
            SecurityFinding(
                id = "F1",
                type = "SECURITY_VULN",
                file = "src/Main.kt",
                cwe = "CWE-89",
                line = 42,
                suggestedFix = "Use parameterized queries",
            )
        val a = analysis(id = 1L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(1L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(1L, "F1")
        assertNotNull(result)
    }

    @Test
    fun `runAnalysis with null message exception at iter 2 covers orEmpty null branch`() {
        val a = analysis(id = 47L).also { it.totalIterations = 2 }
        whenever(analysisRepo.findSecurityAnalysisById(47L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val iter1 = "SUMMARY:\nSome findings.\n\nFINDINGS:\n```json\n[]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn(iter1)
            .thenThrow(IllegalStateException())

        service.runAnalysis(47L)

        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis with blank SUMMARY content covers isBlank and firstFence branches`() {
        val a = analysis(id = 48L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(48L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val output = "Summary: Findings: No vulnerabilities found"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(output)

        service.runAnalysis(48L)

        assertEquals("COMPLETED", a.status)
        assertTrue(a.summaryText?.isNotBlank() == true)
    }

    @Test
    fun `runAnalysis with prose before code fence covers firstFence positive branch`() {
        val a = analysis(id = 51L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(51L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        val output = "Analysis revealed critical issues in auth module.\n\n```json\n[]\n```"
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(output)

        service.runAnalysis(51L)

        assertTrue(a.summaryText?.contains("Analysis revealed critical issues") == true)
    }

    @Test
    fun `runAnalysis with empty output skips prose fallback`() {
        val a = analysis(id = 49L).also { it.totalIterations = 1 }
        whenever(analysisRepo.findSecurityAnalysisById(49L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("")

        service.runAnalysis(49L)

        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `parseFixOutput returns empty changes when output has no JSON content`() {
        val result = service.parseFixOutput("ANALYSIS:\nNo code changes needed.")
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `generateFix with blank exploitPayload proofOfConcept and empty attackSteps covers blank branches`() {
        val finding =
            SecurityFinding(
                id = "F2",
                type = "BUG",
                file = "src/Main.kt",
                exploitPayload = "",
                proofOfConcept = "",
                attackSteps = emptyList(),
            )
        val a = analysis(id = 50L).also { it.findingsJson = objectMapper.writeValueAsString(listOf(finding)) }
        whenever(analysisRepo.findSecurityAnalysisById(50L)).thenReturn(a)
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadFileContext(any(), any())).thenReturn(SecurityFileContext("proj", "tree", "content", "test"))
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        val aiOutput =
            "ANALYSIS:\nFixed.\n\nFILES:\n```json\n" +
                "[{\"path\":\"src/Main.kt\",\"code\":\"fixed\",\"changeType\":\"UPDATE\"}]\n```"
        whenever(copilotPort.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(aiOutput)
        val fixResult =
            com.stacklens.domain.model.security.SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Fixed",
                filesChanged = listOf("src/Main.kt"),
                isLocalRepo = true,
                localChanges = null,
            )
        whenever(mockStrategy.createFixResult(any(), any(), any())).thenReturn(fixResult)

        val result = service.generateFix(50L, "F2")
        assertNotNull(result)
    }

    @Test
    fun `runAnalysis marks CANCELLED when execute throws InterruptedException`() {
        val a = analysis(id = 52L)
        whenever(analysisRepo.findSecurityAnalysisById(52L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenAnswer { throw InterruptedException("test interrupt") }

        try {
            service.runAnalysis(52L)
        } finally {
            Thread.interrupted()
        }

        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `runAnalysis marks CANCELLED when execute throws RuntimeException caused by InterruptedException`() {
        val a = analysis(id = 53L)
        whenever(analysisRepo.findSecurityAnalysisById(53L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenThrow(RuntimeException("wrapper", InterruptedException("inner interrupt")))

        service.runAnalysis(53L)

        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `runAnalysis discards result silently on ObjectOptimisticLockingFailureException`() {
        val a = analysis(id = 55L)
        whenever(analysisRepo.findSecurityAnalysisById(55L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any()))
            .thenThrow(EntityConcurrentlyModifiedException("test 55"))

        service.runAnalysis(55L)

        assertEquals("RUNNING", a.status)
    }

    @Test
    fun `runAnalysis returns early when fresh entity is not found during error handling`() {
        val a = analysis(id = 56L)
        whenever(analysisRepo.findSecurityAnalysisById(56L))
            .thenReturn(a)
            .thenReturn(null)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenThrow(RuntimeException("error"))

        service.runAnalysis(56L)

        assertEquals("RUNNING", a.status)
    }

    @Test
    fun `runAnalysis swallows ObjectOptimisticLockingFailureException during final FAILED save`() {
        val a = analysis(id = 57L)
        val fresh = analysis(id = 57L)
        whenever(analysisRepo.findSecurityAnalysisById(57L))
            .thenReturn(a)
            .thenReturn(fresh)
        whenever(analysisRepo.saveSecurityAnalysis(any()))
            .thenReturn(a)
            .thenThrow(EntityConcurrentlyModifiedException("test 57"))
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenThrow(RuntimeException("error"))

        service.runAnalysis(57L)

        assertEquals("FAILED", fresh.status)
    }

    @Test
    fun `runAnalysis swallows saveSecurityAnalysis exception in InterruptedException handler`() {
        val a = analysis(id = 54L)
        whenever(analysisRepo.findSecurityAnalysisById(54L)).thenReturn(a)
        whenever(analysisRepo.saveSecurityAnalysis(any()))
            .thenReturn(a)
            .thenReturn(a)
            .thenReturn(a)
            .thenThrow(RuntimeException("entity gone"))
        whenever(copilotPort.resolveToken()).thenReturn("token")
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(mockStrategy.supports(a)).thenReturn(true)
        whenever(mockStrategy.loadContext(any(), any())).thenReturn(SecurityRepoContext("app", "/p", "tree", "c"))
        whenever(copilotPort.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenAnswer { throw InterruptedException("test interrupt") }

        try {
            service.runAnalysis(54L)
        } finally {
            Thread.interrupted()
        }

        assertEquals("CANCELLED", a.status)
    }

    @Test
    fun `runAnalysis marks FAILED when copilotPort is not ready`() {
        val a = analysis(id = 99L)
        whenever(analysisRepo.findSecurityAnalysisById(99L)).thenReturn(a)
        whenever(copilotPort.isReady()).thenReturn(false)
        whenever(analysisRepo.saveSecurityAnalysis(any())).thenReturn(a)
        service.runAnalysis(99L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `applyAiOutput falls through to fallback when SUMMARY block is blank`() {
        val a = analysis(id = 1L)
        val output = "SUMMARY:\n   \nFINDINGS:\nsome finding"
        service.applyAiOutput(a, output)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput falls through when second regex matches but text is blank`() {
        val a = analysis(id = 1L)
        val output = "Findings:\n   "
        service.applyAiOutput(a, output)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `applyAiOutput uses second regex zusammenfassung match when first regex misses`() {
        val a = analysis(id = 1L)
        val output = "Zusammenfassung:\nDies ist eine Zusammenfassung.\nBefunde:\n[{\"severity\":\"low\"}]"
        service.applyAiOutput(a, output)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `extractSummary falls through first regex let block when SUMMARY content is blank`() {
        val a = analysis(id = 1L)
        val output = "SUMMARY:\n   \nFINDINGS:\nfinding 1"
        service.applyAiOutput(a, output)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `extractSummary first regex captures empty group when SUMMARY is at end`() {
        val a = analysis(id = 1L)
        service.applyAiOutput(a, "SUMMARY:\n")
        assertEquals("COMPLETED", a.status)
    }
}
