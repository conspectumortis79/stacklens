package com.stacklens.application.service

import com.stacklens.application.port.inbound.rex.RexTestStageSpec
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.domain.text.AgentTextUtils
import java.io.File

class RexStageRunner(
    private val processPort: AgentProcessPort,
    private val i18n: I18nPort,
    private val platformPort: PlatformPort,
    private val aiFixer: RexAiFixService,
    private val coverageStep: RexCoverageStep,
    private val fileAttributes: FileAttributes,
    private val outputFilter: OutputFilterPort,
    private val testStages: List<RexTestStageSpec> = emptyList(),
) {
    companion object {
        val DEFAULT_TEST_STAGES: List<RexTestStageSpec>
            get() = emptyList()
    }

    fun execute(
        run: AgentPipelineRun,
        model: String,
        agentOutputs: List<String>,
    ) {
        val repoPath = run.repoPath
        if (repoPath == null) {
            run.rex.output = i18n.t("copilot_pipeline.rex_no_repo_linked")
            return
        }

        val repoRoot =
            try {
                fileAttributes.canonicalFile(File(repoPath))
            } catch (e: Exception) {
                run.rex.output = i18n.t("copilot_pipeline.rex_invalid_repo_path", e.message ?: "")
                return
            }
        if (!fileAttributes.exists(repoRoot) || !fileAttributes.canWrite(repoRoot)) {
            val hint =
                if (platformPort.detect().osType == OsType.MACOS) {
                    i18n.t("copilot_pipeline.rex_macos_permission_hint")
                } else {
                    ""
                }
            run.rex.output = i18n.t("copilot_pipeline.rex_repo_not_writable", repoPath, hint)
            return
        }

        val sb = StringBuilder()

        runSniffStep(repoPath, repoRoot, sb, run, model, agentOutputs)
        if (run.cancelled) {
            flushRun(sb, run, i18n.t("ticket_implementation.be_rex_cancelled"))
            return
        }

        runAllTestSteps(repoPath, repoRoot, sb, run, model)

        coverageStep.run(repoPath, repoRoot, sb, run, model) { line -> flushLine(sb, line) }

        flushRun(sb, run, "\n🏁 Rex abgeschlossen")
    }

    private fun runAllTestSteps(
        repoPath: String,
        repoRoot: File,
        sb: StringBuilder,
        run: AgentPipelineRun,
        model: String,
    ) {
        testStages.forEach { spec ->
            runTestStep(
                repoPath = repoPath,
                repoRoot = repoRoot,
                sb = sb,
                run = run,
                model = model,
                stepLabel = spec.stepLabel,
                targetAliases = spec.targetAliases,
                noTargetMsg = spec.noTargetMsg,
                runningMsgTemplate = spec.runningMsgTemplate,
                fileExtensionRegex = spec.fileExtensionRegex,
                task = spec.task,
                fixAttemptMsgKey = spec.fixAttemptMsgKey,
                successMsgTemplate = spec.successMsgTemplate,
            )
        }
    }

    private fun runSniffStep(
        repoPath: String,
        repoRoot: File,
        sb: StringBuilder,
        run: AgentPipelineRun,
        model: String,
        agentOutputs: List<String>,
    ) {
        val flush: (String) -> Unit = { line -> flushLine(sb, line) }
        flush(i18n.t("copilot_pipeline.rex_no_sniff_target"))
        val sniffTarget = processPort.findMakeTarget(repoPath, "sniff", "phpcs", "cs", "lint") ?: return
        val hasBeautify = processPort.makefileHasTarget(repoPath, "beautify")
        flush(i18n.t("copilot_pipeline.rex_running_sniff", sniffTarget))
        var (sniffExit, sniffOut) = processPort.runMakeTarget(repoPath, sniffTarget)
        sniffOut = filterMakeOutput(listOf("make", sniffTarget), sniffOut)

        if (sniffExit == 0) {
            flush("✅ `make $sniffTarget` erfolgreich")
            return
        }
        flush("❌ `make $sniffTarget` fehlgeschlagen")

        if (hasBeautify) {
            flush(i18n.t("copilot_pipeline.rex_running_beautify"))
            val (beautyExit, _) = processPort.runMakeTarget(repoPath, "beautify")
            if (beautyExit == 0) {
                flush(i18n.t("copilot_pipeline.rex_beautify_success_rerun", sniffTarget))
                val result = processPort.runMakeTarget(repoPath, sniffTarget)
                sniffExit = result.first
                sniffOut = filterMakeOutput(listOf("make", sniffTarget), result.second)
                if (sniffExit == 0) {
                    flush("✅ `make $sniffTarget` erfolgreich nach beautify")
                }
            } else {
                flush("❌ `make beautify` fehlgeschlagen")
            }
        }

        if (sniffExit != 0) {
            runAiFixAttempts(
                run = run,
                sb = sb,
                repoPath = repoPath,
                repoRoot = repoRoot,
                target = sniffTarget,
                makeOutput = sniffOut,
                model = model,
                extractAffectedPaths = {
                    AgentContextHelpers.extractSniffAffectedPaths(sniffOut, repoRoot, fileAttributes).ifEmpty {
                        val fromAgent =
                            agentOutputs
                                .firstOrNull()
                                ?.let { AgentTextUtils.parseAgentOutput(it).map { pf -> pf.path } }
                                ?: emptyList()
                        fromAgent.ifEmpty {
                            when (val diff = AgentContextHelpers.gitDiffPaths(run.baseSha, repoRoot, processPort)) {
                                is AgentContextHelpers.GitDiffResult.Success -> diff.paths
                                is AgentContextHelpers.GitDiffResult.Failure ->
                                    throw IllegalStateException("git diff failed: ${diff.message}")
                            }
                        }
                    }
                },
                task = "Fix all PHP_CodeSniffer (PSR-12) violations reported by `make sniff`.",
                fixAttemptMsg = { attempt -> i18n.t("copilot_pipeline.rex_ai_fix_sniff_attempt", attempt) },
                successMsg = { attempt -> "✅ `make $sniffTarget` erfolgreich nach KI-Fix (Versuch $attempt)" },
                finalFailMsg = { "❌ `make $sniffTarget` nach 3 Versuchen nicht vollständig korrigiert:\n${sniffOut.take(1_500)}" },
            )
        }
    }

    private fun runTestStep(
        repoPath: String,
        repoRoot: File,
        sb: StringBuilder,
        run: AgentPipelineRun,
        model: String,
        stepLabel: String,
        targetAliases: Array<String>,
        noTargetMsg: String,
        runningMsgTemplate: String,
        fileExtensionRegex: Regex,
        task: String,
        fixAttemptMsgKey: String,
        successMsgTemplate: String,
    ) {
        val flush: (String) -> Unit = { line -> flushLine(sb, line) }
        flush(i18n.t(noTargetMsg))
        val target = processPort.findMakeTarget(repoPath, *targetAliases) ?: return
        flush(i18n.t(runningMsgTemplate, target))
        var (testExit, testOut) = processPort.runMakeTarget(repoPath, target, 300)
        testOut = filterMakeOutput(listOf("make", target), testOut)

        if (testExit == 0) {
            flush("✅ `make $target` erfolgreich")
            return
        }
        flush("❌ `make $target` fehlgeschlagen")

        runAiFixAttempts(
            run = run,
            sb = sb,
            repoPath = repoPath,
            repoRoot = repoRoot,
            target = target,
            makeOutput = testOut,
            model = model,
            extractAffectedPaths = { aiFixer.extractMentionedPaths(testOut, repoRoot, fileExtensionRegex) },
            task = task,
            fixAttemptMsg = { attempt -> i18n.t(fixAttemptMsgKey, attempt) },
            successMsg = { attempt -> successMsgTemplate.replace("\$target", target).replace("\$attempt", attempt.toString()) },
            finalFailMsg = { "❌ `make $target` nach 3 Versuchen nicht vollständig korrigiert:\n${testOut.take(1_500)}" },
        )
    }

    private fun runAiFixAttempts(
        run: AgentPipelineRun,
        sb: StringBuilder,
        repoPath: String,
        repoRoot: File,
        target: String,
        makeOutput: String,
        model: String,
        extractAffectedPaths: () -> List<String>,
        task: String,
        fixAttemptMsg: (Int) -> String,
        successMsg: (Int) -> String,
        finalFailMsg: () -> String,
    ) {
        val flush: (String) -> Unit = { line -> flushLine(sb, line) }
        repeat(3) { attempt ->
            if (run.cancelled) return@repeat
            flush(fixAttemptMsg(attempt + 1))
            val result =
                aiFixer.executeFixAttempt(
                    run = run,
                    sb = sb,
                    repoPath = repoPath,
                    repoRoot = repoRoot,
                    target = target,
                    makeOutput = makeOutput,
                    model = model,
                    extractAffectedPaths = extractAffectedPaths,
                    task = task,
                    rerunTimeoutSeconds = null,
                )
            if (result.first == 0) {
                flush(successMsg(attempt + 1))
                return@repeat
            }
            if (attempt == 2) {
                flush(finalFailMsg())
            }
        }
    }

    private fun filterMakeOutput(
        command: List<String>,
        rawOutput: String,
    ): String {
        if (!outputFilter.enabled || rawOutput.isEmpty()) return rawOutput
        val result = outputFilter.filter(command, rawOutput, "")
        return result.text
    }

    private fun flushRun(
        sb: StringBuilder,
        run: AgentPipelineRun,
        finalMessage: String,
    ) {
        flushLine(sb, finalMessage)
        run.rex.output = sb.toString()
    }

    private fun flushLine(
        sb: StringBuilder,
        line: String,
    ) {
        if (line.isNotBlank()) sb.appendLine(line)
    }
}
