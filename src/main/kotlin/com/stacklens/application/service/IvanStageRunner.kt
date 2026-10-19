package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import org.slf4j.LoggerFactory
import java.io.File

class IvanStageRunner(
    private val processPort: AgentProcessPort,
    private val i18n: I18nPort,
    private val fileAttributes: FileAttributes,
    private val outputFilter: OutputFilterPort,
) {
    private val log = LoggerFactory.getLogger(IvanStageRunner::class.java)

    private fun filterSubprocessOutput(
        command: List<String>,
        rawOutput: String,
    ): String {
        if (!outputFilter.enabled || rawOutput.isEmpty()) return rawOutput
        return outputFilter.filter(command, rawOutput, "").text
    }

    fun execute(
        run: AgentPipelineRun,
        writeOutput: (String) -> Unit,
    ) {
        val rp = run.repoPath
        if (rp == null) {
            run.ivan.output = i18n.t("copilot_pipeline.be_no_repo_configured")
            run.ivan.status = StageStatus.DONE
            return
        }

        val ivanLog = StringBuilder()
        val ctx = IvanStageContext(run, ivanLog)
        try {
            val gitBin = processPort.resolveGitBinary()
            val makeBin = processPort.resolveMakeBinary()
            ctx.root =
                run.gitRoot.takeIf { it.isNotBlank() }?.let { File(it) }
                    ?: fileAttributes.canonicalFile(File(rp))
            ctx.gitBin = gitBin
            ctx.makeBin = makeBin
            ctx.featureBranch = run.gitBranch.takeIf { it.isNotBlank() }

            if (ctx.featureBranch != null) {
                runBaseBranchCheckoutAndPull(ctx)
            } else {
                ivanLog.appendLine(i18n.t("copilot_pipeline.be_feature_branch_unknown"))
                run.ivan.output = ivanLog.toString()
            }

            if (ctx.checkedOutBase || ctx.featureBranch == null) {
                runMakeTarget(ctx, target = "update", makeBin = makeBin, failMsgKey = "copilot_pipeline.be_make_update_failed")
                runMakeTarget(ctx, target = "install", makeBin = makeBin, failMsgKey = "copilot_pipeline.be_make_install_failed")
            }

            if (ctx.checkedOutBase) {
                runFeatureBranchCheckout(ctx, ctx.featureBranch!!)
            }

            run.ivan.status = if (ctx.featureBranchCheckoutFailed) StageStatus.ERROR else StageStatus.DONE
            run.ivan.output = ivanLog.toString()
            writeOutput(ivanLog.toString())
            log.info("[Pipeline {}] Stage Ⓘ Ivan done", run.id)
        } catch (e: InterruptedException) {
            run.ivan.output = ivanLog.appendLine("⚠️ cancelled").toString()
            run.ivan.status = StageStatus.ERROR
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.warn("[Pipeline {}] Ivan stage failed: {}", run.id, e.message)
            ivanLog.appendLine("⚠️ Error: ${e.message}")
            run.ivan.output = ivanLog.toString()
            run.ivan.status = StageStatus.ERROR
        }
    }

    internal class IvanStageContext(
        val run: AgentPipelineRun,
        val log: StringBuilder,
    ) {
        lateinit var root: File
        lateinit var gitBin: String
        lateinit var makeBin: String
        var featureBranch: String? = null
        var checkedOutBase: Boolean = false
        var featureBranchCheckoutFailed: Boolean = false
    }

    private fun runBaseBranchCheckoutAndPull(ctx: IvanStageContext) {
        val baseBranch = resolveBaseBranch(ctx)
        ctx.log.appendLine("$ git checkout $baseBranch -f")
        val (rcCO, outCO) = runCmd(ctx, ctx.gitBin, "checkout", baseBranch, "-f")
        if (outCO.isNotBlank()) ctx.log.appendLine(outCO)
        ctx.log.appendLine("  → exit=$rcCO")
        ctx.run.ivan.output = ctx.log.toString()
        if (rcCO != 0) {
            ctx.log.appendLine(i18n.t("copilot_pipeline.be_checkout_failed_skipped", baseBranch))
            ctx.run.ivan.output = ctx.log.toString()
            return
        }
        ctx.checkedOutBase = true

        ctx.log.appendLine("$ git pull origin $baseBranch")
        val (rcPull, outPull) = runCmd(ctx, ctx.gitBin, "pull", "origin", baseBranch)
        if (outPull.isNotBlank()) ctx.log.appendLine(outPull)
        ctx.log.appendLine("  → exit=$rcPull")
        ctx.run.ivan.output = ctx.log.toString()
        if (rcPull != 0) {
            val pullMsg =
                if (outPull == "timeout") {
                    i18n.t("copilot_pipeline.be_git_pull_timeout")
                } else {
                    i18n.t("copilot_pipeline.be_git_pull_failed")
                }
            ctx.log.appendLine("  ⚠️ $pullMsg")
            ctx.run.ivan.output = ctx.log.toString()
        }
    }

    private fun resolveBaseBranch(ctx: IvanStageContext): String {
        ctx.run.baseBranch
            .takeIf { it.isNotBlank() }
            ?.let { return it }
        val (rc1, _) = runCmd(ctx, ctx.gitBin, "show-ref", "--verify", "--quiet", "refs/heads/main")
        if (rc1 == 0) return "main"
        val (rc2, _) = runCmd(ctx, ctx.gitBin, "show-ref", "--verify", "--quiet", "refs/heads/master")
        if (rc2 == 0) return "master"
        val (_, head) = runCmd(ctx, ctx.gitBin, "rev-parse", "--abbrev-ref", "HEAD")
        val valid =
            head.isNotBlank() &&
                !head.contains(" ") &&
                head != "timeout" &&
                head.length <= 100
        return if (valid) head else "main"
    }

    private fun runMakeTarget(
        ctx: IvanStageContext,
        target: String,
        makeBin: String,
        failMsgKey: String,
    ) {
        if (!processPort.makefileHasTarget(ctx.root.path, target)) {
            ctx.log.appendLine("# make $target — target not found, skipped")
            ctx.run.ivan.output = ctx.log.toString()
            return
        }
        ctx.log.appendLine("$ make $target")
        val (rc, out) = runCmd(ctx, makeBin, target)
        if (out.isNotBlank()) ctx.log.appendLine(out)
        ctx.log.appendLine("  → exit=$rc")
        ctx.run.ivan.output = ctx.log.toString()
        if (rc != 0) {
            ctx.log.appendLine(i18n.t(failMsgKey))
            ctx.run.ivan.output = ctx.log.toString()
        }
    }

    private fun runFeatureBranchCheckout(
        ctx: IvanStageContext,
        featureBranch: String,
    ) {
        ctx.log.appendLine("$ git checkout $featureBranch -f")
        val (rc, out) = runCmd(ctx, ctx.gitBin, "checkout", featureBranch, "-f")
        if (out.isNotBlank()) ctx.log.appendLine(out)
        ctx.log.appendLine("  → exit=$rc")
        ctx.run.ivan.output = ctx.log.toString()
        if (rc != 0) {
            ctx.log.appendLine(i18n.t("copilot_pipeline.be_checkout_feature_failed", featureBranch))
            ctx.run.ivan.output = ctx.log.toString()
            ctx.run.gitBranch = ""
            ctx.featureBranchCheckoutFailed = true
        }
    }

    private fun runCmd(
        ctx: IvanStageContext,
        vararg cmd: String,
    ): Pair<Int, String> {
        val result = processPort.run(cmd.toList(), 120L, ctx.root)
        val combined =
            buildString {
                if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
                if (result.stderr.isNotBlank()) {
                    if (isNotEmpty()) appendLine()
                    append(result.stderr.trimEnd())
                }
            }.trim()
        val filtered = filterSubprocessOutput(cmd.toList(), combined)
        return if (result.exitCode == 124) Pair(-1, "timeout") else Pair(result.exitCode, filtered)
    }
}
