package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.domain.text.AgentTextUtils
import java.io.File

class RexCoverageStep(
    private val processPort: AgentProcessPort,
    private val i18n: I18nPort,
    private val aiFixer: RexAiFixService,
) {
    fun run(
        repoPath: String,
        repoRoot: File,
        sb: StringBuilder,
        run: AgentPipelineRun,
        model: String,
        flush: (String) -> Unit,
    ) {
        val coverageTarget = processPort.findMakeTarget(repoPath, "coverage", "coverage-report")
        if (coverageTarget == null) {
            flush(i18n.t("copilot_pipeline.rex_no_coverage_target"))
            return
        }
        flush(i18n.t("copilot_pipeline.rex_running_coverage", coverageTarget))

        val (initialExit, initialOut, initialCoverage) = runInitialCoverageCheck(repoPath, coverageTarget, flush)
        if (initialExit != 0 || initialCoverage == null) return

        if (initialCoverage >= 100.0) {
            flush("✅ 100% Code Coverage erreicht")
            return
        }

        var covOut = initialOut
        var coverage = initialCoverage
        repeat(3) { attempt ->
            if (run.cancelled) return@repeat
            if ((AgentTextUtils.parseCoveragePercent(covOut) ?: 0.0) >= 100.0) return@repeat
            flush(i18n.t("copilot_pipeline.rex_ai_fix_coverage_attempt", attempt + 1, formatPercent(coverage)))
            val result =
                aiFixer.executeFixAttempt(
                    run = run,
                    sb = sb,
                    repoPath = repoPath,
                    repoRoot = repoRoot,
                    target = coverageTarget,
                    makeOutput = covOut,
                    model = model,
                    extractAffectedPaths = { aiFixer.extractMentionedPaths(covOut, repoRoot, COVERAGE_PATH_REGEX) },
                    task = COVERAGE_TASK,
                    rerunTimeoutSeconds = COVERAGE_RERUN_TIMEOUT_SECONDS,
                )
            covOut = result.second
            coverage = AgentTextUtils.parseCoveragePercent(covOut) ?: 0.0
            flushCoverageAttemptResult(attempt, coverage, flush)
        }
    }

    private fun runInitialCoverageCheck(
        repoPath: String,
        coverageTarget: String,
        flush: (String) -> Unit,
    ): Triple<Int, String, Double?> {
        val (covExit, covOut) = processPort.runMakeTarget(repoPath, coverageTarget, 300)
        if (covExit != 0) {
            flush("❌ `make $coverageTarget` fehlgeschlagen:\n${covOut.take(1_000)}")
            return Triple(covExit, covOut, null)
        }
        val coverage = AgentTextUtils.parseCoveragePercent(covOut)
        flush("📈 Code Coverage: ${formatPercent(coverage)}")
        if (coverage == null) {
            flush(i18n.t("copilot_pipeline.rex_coverage_output_not_recognized"))
        }
        return Triple(covExit, covOut, coverage)
    }

    private fun flushCoverageAttemptResult(
        attempt: Int,
        coverage: Double,
        flush: (String) -> Unit,
    ) {
        val newPct = formatPercent(coverage)
        when {
            coverage >= 100.0 -> flush("✅ 100% Coverage erreicht nach KI-Fix (Versuch ${attempt + 1})")
            attempt == 2 -> flush("⚠️ Coverage nach 3 Versuchen: $newPct (Ziel: 100%)")
            else -> flush("📈 Coverage nach Versuch ${attempt + 1}: $newPct")
        }
    }

    internal fun formatPercent(coverage: Double?): String = coverage?.let { String.format("%.1f", it) + "%" } ?: "unbekannt"

    private companion object {
        const val COVERAGE_TASK =
            "Add PHPUnit test methods to reach 100% line and branch coverage. The coverage report below shows which lines/methods are uncovered."
        const val COVERAGE_RERUN_TIMEOUT_SECONDS = 300L
    }
}

private val COVERAGE_PATH_REGEX = Regex("""[\w./\-]+\.php""")
