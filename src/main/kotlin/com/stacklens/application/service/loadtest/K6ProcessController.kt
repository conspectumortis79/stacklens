package com.stacklens.application.service.loadtest

import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.K6ScriptPort
import com.stacklens.application.port.out.loadtest.LoadTestEnvironmentPort
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class K6ProcessController(
    private val analysisRepo: LoadTestAnalysisPort,
    private val aiService: LoadTestEnvironmentPort,
    private val i18n: I18nPort,
    private val processRunPort: ProcessRunPort,
    private val localBaseUrl: LocalBaseUrlPort,
    private val scriptTransformer: K6ScriptPort,
    private val outputFilter: OutputFilterPort,
    private val executables: ExecutableLookupPort,
    private val runRegistry: K6RunRegistry,
    private val launcher: K6ProcessLauncher,
    private val statusReporter: K6StatusReporter,
) {
    private val log = LoggerFactory.getLogger(K6ProcessController::class.java)

    fun startK6Script(
        analysisId: Long,
        bearerToken: String?,
        environmentName: String?,
        customScript: String?,
    ): K6StatusResult {
        try {
            runRegistry.find(analysisId)?.let { existing ->
                if (existing.process.isAlive) {
                    return K6StatusResult(
                        running = true,
                        exitCode = null,
                        output = existing.stdoutBuf.toString(),
                        error = existing.stderrBuf.toString(),
                    )
                } else {
                    runRegistry.remove(analysisId)
                }
            }

            val resolved = resolveScript(analysisId, customScript)
            val scriptText: String =
                when (resolved) {
                    is ScriptResolution.Ok -> resolved.script
                    is ScriptResolution.Fail -> return K6StatusResult(false, -1, "", resolved.message)
                }
            val localBaseUrl = localBaseUrl.currentBaseUrl()
            log.info("[k6] Local instance BASE_URL resolved to '{}'", localBaseUrl)
            val localPatched =
                if (customScript.isNullOrBlank()) patchScriptForLocalDefault(scriptText, localBaseUrl) else scriptText

            val envPatched = patchScriptForEnvironment(localPatched, analysisId, environmentName)
            val script = sanitizeScriptForK6V2(envPatched)
            log.info(
                "[k6] Final k6 script starts with:\n{}",
                script.lineSequence().take(20).joinToString("\n"),
            )

            val k6Bin =
                resolveToolBin("k6")
                    ?: return K6StatusResult(false, -1, "", i18n.t("load_test.be_k6_not_installed"))

            launcher.launch(k6Bin, script, bearerToken, analysisId)
            return K6StatusResult(
                running = true,
                exitCode = null,
                output =
                    "[StackLens] k6 script (first 20 lines, BASE_URL patched):\n" +
                        script.lineSequence().take(20).joinToString("\n") + "\n" +
                        "[StackLens] end of script header\n",
                error = "",
            )
        } catch (e: Exception) {
            log.error("[k6] startK6Script error: {}", e.message, e)
            return K6StatusResult(false, -1, "", i18n.t("load_test.be_k6_start_error", e.message ?: ""))
        }
    }

    private sealed class ScriptResolution {
        data class Ok(
            val script: String,
        ) : ScriptResolution()

        data class Fail(
            val message: String,
        ) : ScriptResolution()
    }

    private fun resolveScript(
        analysisId: Long,
        customScript: String?,
    ): ScriptResolution {
        customScript?.takeIf { it.isNotBlank() }?.let { return ScriptResolution.Ok(it) }
        val analysis =
            analysisRepo.findLoadTestAnalysisById(analysisId)
                ?: return ScriptResolution.Fail(i18n.t("load_test.be_analysis_not_found"))
        val script =
            analysis.k6Script
                ?: return ScriptResolution.Fail(i18n.t("load_test.be_no_k6_script"))
        return ScriptResolution.Ok(script)
    }

    private fun patchScriptForEnvironment(
        script: String,
        analysisId: Long,
        environmentName: String?,
    ): String {
        if (environmentName.isNullOrBlank()) return script
        val analysis = analysisRepo.findLoadTestAnalysisById(analysisId) ?: return script
        val env =
            aiService.parseEnvironments(analysis.environmentsJson).find { it.name == environmentName }
                ?: return script
        return aiService.patchK6BaseUrl(script, env)
    }

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    internal fun patchScriptForLocalDefault(
        script: String,
        forcedReplacement: String? = null,
    ): String = scriptTransformer.patchScriptForLocalDefault(script, forcedReplacement)

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    internal fun extractBaseUrlLiteral(script: String): String? = scriptTransformer.extractBaseUrlLiteral(script)

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    internal fun isSuspiciousTemplateDefault(url: String): Boolean = scriptTransformer.isSuspiciousTemplateDefault(url)

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    internal fun moveTopLevelStagesIntoDefaultScenario(script: String): String =
        scriptTransformer.moveTopLevelStagesIntoDefaultScenario(script)

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    internal fun sanitizeScriptForK6V2(script: String): String = scriptTransformer.sanitizeScriptForK6V2(script)

    fun getK6Status(analysisId: Long): K6StatusResult = statusReporter.report(analysisId)

    fun stopK6Script(analysisId: Long) {
        val run = runRegistry.remove(analysisId) ?: return
        runCatching { processRunPort.killProcessTree(run.process) }
            .onFailure {
                log.warn("[k6] stopK6Script: killProcessTree failed for analysisId={} — falling back to destroyForcibly: {}", analysisId, it.message)
                run.process.destroyForcibly()
            }
    }

    fun resolveToolBin(
        tool: String,
        extraCandidates: List<String> = emptyList(),
    ): String? = executables.resolve(tool, extraCandidates)
}
