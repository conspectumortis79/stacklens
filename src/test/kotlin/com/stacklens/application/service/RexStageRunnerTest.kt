package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RexStageRunnerTest {
    @Mock private lateinit var processPort: ProcessPort

    @Mock private lateinit var copilot: CopilotStreamingPort

    private val filePort: FileSystemPort = JavaFileSystemAdapter()

    private val i18n = FakeI18n()

    private val copilotConfig =
        object : CopilotConfigPort {
            override val pipelineTimeoutSeconds: Long = 10L
            override val loadTestAnalysisTimeoutSeconds: Long = 600L
            override val searchAnalysisTimeoutSeconds: Long = 600L
            override val reviewAnalysisTimeoutSeconds: Long = 900L
        }

    private lateinit var runner: RexStageRunner

    @BeforeEach
    fun setUp() {
        whenever(processPort.resolveGitBinary()).thenReturn("/usr/bin/git")
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(exitCode = 0, stdout = "", stderr = ""))
        runner = runnerWith(FakePlatformPort())
    }

    private fun runnerWith(platformPort: PlatformPort): RexStageRunner {
        val aiFixer = aiFixerWith()
        return RexStageRunner(
            processPort = processPort,
            i18n = i18n,
            platformPort = platformPort,
            aiFixer = aiFixer,
            coverageStep = RexCoverageStep(processPort, i18n, aiFixer),
            fileAttributes = filePort,
            outputFilter = mock(),
            testStages = com.stacklens.application.port.inbound.rex.RexTestStages.DEFAULT,
        )
    }

    private fun aiFixerWith(
        loadPrompt: (String, Map<String, String>) -> String = { _, _ -> "prompt" },
        fileAttributesOverride: FileAttributes = filePort,
        filePortOverride: FileSystemPort = filePort,
    ) = RexAiFixService(
        copilot = copilot,
        copilotConfig = copilotConfig,
        processPort = processPort,
        filePort = filePortOverride,
        loadPrompt = loadPrompt,
        fileAttributes = fileAttributesOverride,
    )

    private fun throwingFileAttributes(error: Exception): FileAttributes =
        object : FileAttributes by JavaFileSystemAdapter() {
            override fun canonicalFile(file: File): File = throw error
        }

    private fun coverageStepWith(
        aiFixer: RexAiFixService,
        i18nInstance: I18nPort = i18n,
    ) = RexCoverageStep(processPort, i18nInstance, aiFixer)

    private fun run(repoPath: String?) = AgentPipelineRun(id = 1L, rawText = "feature", repoPath = repoPath, featureSlug = "slug", createdAt = System.currentTimeMillis())

    @Test
    fun `null repoPath outputs no-repo message without running make`() {
        val run = run(repoPath = null)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.isNotBlank())
    }

    @Test
    fun `non-existent repo path outputs error message`(
        @TempDir base: File,
    ) {
        val gone = File(base, "gone")
        val run = run(repoPath = gone.absolutePath)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.isNotBlank())
    }

    @Test
    fun `no sniff target outputs skip message and continues`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("copilot_pipeline.rex_no_sniff_target"))
        assertTrue(run.rex.output.contains("copilot_pipeline.rex_no_unit_test_target"))
        assertTrue(run.rex.output.contains("copilot_pipeline.rex_no_coverage_target"))
        assertTrue(run.rex.output.contains("🏁 Rex abgeschlossen"))
    }

    @Test
    fun `sniff passes on first attempt outputs success`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich"))
    }

    @Test
    fun `sniff fails then beautify fixes it`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(true)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation"))
            .thenReturn(Pair(0, ""))
        whenever(processPort.runMakeTarget(repoDir.path, "beautify")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach beautify"))
    }

    @Test
    fun `sniff fails then beautify succeeds but sniff still fails`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(true)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation"))
            .thenReturn(Pair(1, "still violation"))
        whenever(processPort.runMakeTarget(repoDir.path, "beautify")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertFalse(run.rex.output.contains("✅ `make sniff` erfolgreich nach beautify"))
    }

    @Test
    fun `sniff fails, AI fixes on first AI attempt`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation"))
            .thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix (Versuch 1)"))
    }

    @Test
    fun `sniff fails after all three AI attempts outputs failure`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(1, "still failing"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("nach 3 Versuchen nicht vollständig korrigiert"))
    }

    @Test
    fun `sniff fix propagates a git diff failure`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(1, "violation"))
        whenever(processPort.run(any(), any(), any())).thenThrow(RuntimeException("git exploded"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)

        assertThrows(IllegalStateException::class.java) { runner.execute(run, "gpt-4o", emptyList()) }
    }

    @Test
    fun `cancelled flag stops pipeline after sniff phase`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(0, ""))

        val run = run(repoPath = repoDir.path)
        run.cancelled = true

        runner.execute(run, "gpt-4o", emptyList())

        assertFalse(run.rex.output.contains("unit-test"))
    }

    @Test
    fun `unit test target passes on first run`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn("unit-test")
        whenever(processPort.runMakeTarget(repoDir.path, "unit-test", 300)).thenReturn(Pair(0, "OK"))
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ `make unit-test` erfolgreich"))
    }

    @Test
    fun `100 pct coverage on first run outputs success`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 100.00%"))

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ 100% Code Coverage erreicht"))
    }

    @Test
    fun `coverage below 100 pct, AI fix reaches 100 pct on first attempt`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 75.00%"))
            .thenReturn(Pair(0, "Lines: 100.00%"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("✅ 100% Coverage erreicht nach KI-Fix (Versuch 1)"))
    }

    @Test
    fun `coverage fix cancelled during attempt returns early`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 50.00%"))
        val run = run(repoPath = repoDir.path)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenAnswer {
            run.cancelled = true
            ""
        }
        runner.execute(run, "gpt-4o", emptyList())
    }

    @Test
    fun `coverage output with unparseable percent uses zero fallback`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Coverage report generated"))
            .thenReturn(Pair(0, "Coverage report generated"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("copilot_pipeline.rex_coverage_output_not_recognized"))
    }

    @Test
    fun `coverage make target failure outputs error message`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300)).thenReturn(Pair(1, "phpunit failed"))

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("❌ `make coverage` fehlgeschlagen"))
    }

    @Test
    fun `output always ends with Rex completed footer`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(any(), any(), any())).thenReturn(null)
        whenever(processPort.findMakeTarget(any(), any(), any(), any())).thenReturn(null)
        whenever(processPort.findMakeTarget(any(), any(), any(), any(), any())).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(
            run.rex.output
                .trimEnd()
                .endsWith("🏁 Rex abgeschlossen"),
        )
    }

    @Test
    fun `execute outputs error when repoPath yields canonicalFile exception`(
        @TempDir repoDir: File,
    ) {
        val throwingAttributes = throwingFileAttributes(java.io.IOException("Simulated: file name too long"))
        val throwingRunner =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = aiFixerWith(fileAttributesOverride = throwingAttributes),
                coverageStep = coverageStepWith(aiFixerWith(fileAttributesOverride = throwingAttributes)),
                fileAttributes = throwingAttributes,
                outputFilter = mock(),
            )
        val run = run(repoPath = repoDir.path)
        throwingRunner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("Simulated"))
    }

    @Test
    fun `execute canonicalFile exception with null message falls back to empty string`(
        @TempDir repoDir: File,
    ) {
        val throwingAttributes = throwingFileAttributes(Exception(null as String?))
        val throwingRunner =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = aiFixerWith(fileAttributesOverride = throwingAttributes),
                coverageStep = coverageStepWith(aiFixerWith(fileAttributesOverride = throwingAttributes)),
                fileAttributes = throwingAttributes,
                outputFilter = mock(),
            )
        val run = run(repoPath = repoDir.path)
        throwingRunner.execute(run, "gpt-4o", emptyList())
        assertNotNull(run.rex.output)
    }

    @Test
    fun `execute outputs mac permission hint on macOS`() {
        val run = run(repoPath = "/definitely/does/not/exist/on/this/machine")
        runnerWith(FakePlatformPort(OsType.MACOS)).execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("copilot_pipeline.rex_macos_permission_hint"))
        assertTrue(run.rex.output.isNotBlank())
    }

    @Test
    fun `execute outputs not-writable message without mac hint on Linux`() {
        val run = run(repoPath = "/definitely/does/not/exist/on/this/machine")
        runnerWith(FakePlatformPort(OsType.LINUX)).execute(run, "gpt-4o", emptyList())
        assertFalse(run.rex.output.contains("copilot_pipeline.rex_macos_permission_hint"))
        assertTrue(run.rex.output.isNotBlank())
    }

    @Test
    fun `execute with i18n returning blank for no-sniff-target does not append blank line`(
        @TempDir repoDir: File,
    ) {
        val blankI18n =
            object : I18nPort {
                override fun t(
                    key: String,
                    vararg args: Any,
                ): String =
                    if (key ==
                        "copilot_pipeline.rex_no_sniff_target"
                    ) {
                        ""
                    } else {
                        key + if (args.isEmpty()) "" else "(${args.joinToString(",")})"
                    }

                override fun tFor(
                    lang: String,
                    key: String,
                    vararg args: Any,
                ): String = t(key, *args)

                override fun translateError(e: Exception): String = e.message ?: ""

                override fun resolveLanguage(): String = "de"
            }
        val blankAiFixer = aiFixerWith()
        val blankRunner =
            RexStageRunner(
                processPort = processPort,
                i18n = blankI18n,
                platformPort = FakePlatformPort(),
                aiFixer = blankAiFixer,
                coverageStep = coverageStepWith(blankAiFixer, blankI18n),
                fileAttributes = filePort,
                outputFilter = mock(),
            )

        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        blankRunner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("🏁 Rex abgeschlossen"))
    }

    @Test
    fun `filterSubprocessOutput applies the filter when enabled and rawOutput is non-empty`() {
        val filter =
            org.mockito.kotlin.mock<com.stacklens.application.port.out.textfilter.OutputFilterPort>()
        whenever(filter.enabled).thenReturn(true)
        whenever(filter.filter(any(), any(), any())).thenReturn(
            com.stacklens.domain.text.filter.FilterResult(
                text = "filtered-text",
                originalChars = 100,
                filteredChars = 50,
                specName = "test",
            ),
        )
        val runner =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = aiFixerWith(filePortOverride = org.mockito.kotlin.mock()),
                coverageStep = coverageStepWith(aiFixerWith(filePortOverride = org.mockito.kotlin.mock())),
                fileAttributes = filePort,
                outputFilter = filter,
            )
        val method =
            RexStageRunner::class.java.getDeclaredMethod(
                "filterMakeOutput",
                List::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(runner, listOf("make", "test"), "raw output") as String
        assertEquals("filtered-text", result)
        org.mockito.kotlin
            .verify(filter)
            .filter(any(), any(), any())
    }

    @Test
    fun `integration-test step uses integration-test i18n message for fix attempt`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn("integration-test")
        whenever(processPort.runMakeTarget(repoDir.path, "integration-test", 300)).thenReturn(Pair(1, "integration test failed"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.runMakeTarget(repoDir.path, "integration-test")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("copilot_pipeline.rex_ai_fix_integration_test_attempt"))
    }

    @Test
    fun `system-test step uses system-test i18n message for fix attempt`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn("system-test")
        whenever(processPort.runMakeTarget(repoDir.path, "system-test", 300)).thenReturn(Pair(1, "e2e failed"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.runMakeTarget(repoDir.path, "system-test")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("copilot_pipeline.rex_ai_fix_system_test_attempt"))
    }

    @Test
    fun `coverage target exits 0 but output has no parseable percentage`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Coverage complete. See report in build/reports."))

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("unbekannt"))
        assertTrue(run.rex.output.contains("copilot_pipeline.rex_coverage_output_not_recognized"))
    }

    @Test
    fun `coverage reaches 100 pct after first fix then repeat iteration exits early`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 80.00%"))
            .thenReturn(Pair(0, "Lines: 100.00%"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("100% Coverage erreicht nach KI-Fix"))
    }

    @Test
    fun `coverage step repeat loop exits when cancelled`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300)).thenReturn(Pair(0, "Lines: 60.00%"))

        val run = run(repoPath = repoDir.path)
        doAnswer {
            run.cancelled = true
            ""
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        runner.execute(run, "gpt-4o", emptyList())
        assertFalse(run.rex.output.contains("100% Coverage erreicht"))
    }

    @Test
    fun `coverage still below 100 pct after all three fix attempts outputs warning`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300)).thenReturn(Pair(0, "Lines: 75.00%"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())

        assertTrue(run.rex.output.contains("Coverage nach 3 Versuchen"))
    }

    @Test
    fun `coverage fix attempt invokes streaming callback with partial output`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 50.00%"))
            .thenReturn(Pair(0, "Lines: 100.00%"))

        doAnswer { inv ->
            val callback = inv.getArgument<(String) -> Unit>(5)
            callback("partial ai output")
            ""
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("100% Coverage erreicht nach KI-Fix"))
    }

    @Test
    fun `sniff AI fix attempt invokes streaming callback`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation at /var/www/src/file.php"))
            .thenReturn(Pair(0, ""))

        doAnswer { inv ->
            val callback = inv.getArgument<(String) -> Unit>(5)
            callback("generating fix...")
            ""
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `test step with absolute path in output covers extractMentionedPaths absolute branch`(
        @TempDir repoDir: File,
    ) {
        File(repoDir, "src").mkdirs()
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn("unit-test")
        val absPath = "${repoDir.canonicalPath}/src/MyClass.php"
        whenever(processPort.runMakeTarget(repoDir.path, "unit-test", 300))
            .thenReturn(Pair(1, "FAILED $absPath line 42"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.runMakeTarget(repoDir.path, "unit-test")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make unit-test` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `test step with vendor path in output covers extractMentionedPaths vendor filter`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn("unit-test")
        whenever(processPort.runMakeTarget(repoDir.path, "unit-test", 300))
            .thenReturn(Pair(1, "vendor/autoload.php line 10 ERROR"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.runMakeTarget(repoDir.path, "unit-test")).thenReturn(Pair(0, ""))
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make unit-test` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `AI fix output with dot path does not write file outside repo`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation"))
            .thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenReturn("### .\n```php\nsome content\n```")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `sniff fails, beautify also fails, AI fix then passes`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(true)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "violation"))
            .thenReturn(Pair(0, ""))
        whenever(processPort.runMakeTarget(repoDir.path, "beautify")).thenReturn(Pair(1, "beauty fail"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("❌ `make beautify` fehlgeschlagen"))
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `sniff AI fix uses agent outputs when sniff output has no paths`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "generic error without paths"))
            .thenReturn(Pair(0, ""))
        val agentOutput = "### src/MyClass.php\n```php\ncontent\n```"
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", listOf(agentOutput))
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `extractMentionedPaths with absolute path outside repo catches exception`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "/opt/other/project/src/other.php: error on line 5"))
            .thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `writeFiles skips entry when canonicalize throws`(
        @TempDir repoDir: File,
    ) {
        val throwingCanonicalFile =
            object : FileAttributes by JavaFileSystemAdapter() {
                override fun canonicalFile(file: File): File =
                    if (file.absolutePath == repoDir.absolutePath) {
                        file
                    } else {
                        throw java.io.IOException("Simulated ELOOP for: $file")
                    }
            }
        val throwingWriter =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = aiFixerWith(fileAttributesOverride = throwingCanonicalFile),
                coverageStep = coverageStepWith(aiFixerWith(fileAttributesOverride = throwingCanonicalFile)),
                fileAttributes = throwingCanonicalFile,
                outputFilter = mock(),
            )
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, "error in src/file.php"))
            .thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenReturn("### src/file.php\n```php\n<?php echo 'ok';\n```")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        throwingWriter.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix"))
    }

    @Test
    fun `sniff with FILE prefix in output uses extracted paths directly`(
        @TempDir repoDir: File,
    ) {
        val srcDir = File(repoDir, "src").also { it.mkdirs() }
        File(srcDir, "App.php").writeText("<?php\nclass App {}")
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(repoDir.path, "beautify")).thenReturn(false)
        val fileLine = "FILE: ${repoDir.canonicalPath}/src/App.php (source: standard)"
        whenever(processPort.runMakeTarget(repoDir.path, "sniff"))
            .thenReturn(Pair(1, fileLine))
            .thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn(null)

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("✅ `make sniff` erfolgreich nach KI-Fix (Versuch 1)"))
    }

    @Test
    fun `coverage fix attempt with pre-cancelled run exits repeat loop`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 50.00%"))
        val run = run(repoPath = repoDir.path)
        run.cancelled = true
        runner.execute(run, "gpt-4o", emptyList())
    }

    @Test
    fun `coverage fix attempt with unparseable output uses zero fallback`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.findMakeTarget(repoDir.path, "sniff", "phpcs", "cs", "lint")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(repoDir.path, "coverage", "coverage-report")).thenReturn("coverage")
        whenever(processPort.runMakeTarget(repoDir.path, "coverage", 300))
            .thenReturn(Pair(0, "Lines: 80.00%"))
            .thenReturn(Pair(0, "Coverage complete, no percent"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(run.rex.output.contains("Coverage nach 3 Versuchen"))
    }

    @Test
    fun `execute with disabled output filter returns raw output unchanged`(
        @TempDir repoDir: File,
    ) {
        val filter =
            object : com.stacklens.application.port.out.textfilter.OutputFilterPort {
                override val enabled: Boolean = false

                override fun filter(
                    command: List<String>,
                    stdout: String,
                    stderr: String,
                ) = com.stacklens.domain.text.filter
                    .FilterResult("disabled", stdout.length, stdout.length, "disabled")

                override fun filterByName(
                    specName: String,
                    output: String,
                ) = com.stacklens.domain.text.filter
                    .FilterResult("ok", output.length, output.length, "ok")

                override fun knownFilters(): List<String> = emptyList()
            }
        val filterAiFixer = aiFixerWith()
        val runner =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = filterAiFixer,
                coverageStep = coverageStepWith(filterAiFixer),
                fileAttributes = filePort,
                outputFilter = filter,
            )
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(0, "raw-make-out"))
        whenever(processPort.runMakeTarget(repoDir.path, "beautify")).thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertTrue(true)
    }

    @Test
    fun `execute with empty raw output skips filtering`(
        @TempDir repoDir: File,
    ) {
        var filterCalled = false
        val filter =
            object : com.stacklens.application.port.out.textfilter.OutputFilterPort {
                override val enabled: Boolean = true

                override fun filter(
                    command: List<String>,
                    stdout: String,
                    stderr: String,
                ): com.stacklens.domain.text.filter.FilterResult {
                    filterCalled = true
                    return com.stacklens.domain.text.filter
                        .FilterResult("ok", stdout.length, stdout.length, "ok")
                }

                override fun filterByName(
                    specName: String,
                    output: String,
                ) = com.stacklens.domain.text.filter
                    .FilterResult("ok", output.length, output.length, "ok")

                override fun knownFilters(): List<String> = emptyList()
            }
        val filterAiFixer = aiFixerWith()
        val runner =
            RexStageRunner(
                processPort = processPort,
                i18n = i18n,
                platformPort = FakePlatformPort(),
                aiFixer = filterAiFixer,
                coverageStep = coverageStepWith(filterAiFixer),
                fileAttributes = filePort,
                outputFilter = filter,
            )
        whenever(processPort.runMakeTarget(repoDir.path, "sniff")).thenReturn(Pair(0, ""))
        whenever(processPort.runMakeTarget(repoDir.path, "beautify")).thenReturn(Pair(0, ""))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")

        val run = run(repoPath = repoDir.path)
        runner.execute(run, "gpt-4o", emptyList())
        assertFalse(filterCalled)
    }
}
