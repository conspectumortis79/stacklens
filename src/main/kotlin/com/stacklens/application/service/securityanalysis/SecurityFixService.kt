package com.stacklens.application.service.securityanalysis
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.NoSecurityAnalysisStrategyException
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.security.SecurityFixResult
import com.stacklens.domain.model.security.SecurityLocalFileChange
import org.slf4j.LoggerFactory

@ApplicationService
class SecurityFixService(
    private val analysisRepo: SecurityAnalysisPort,
    private val copilotPort: CopilotStreamingPort,
    private val jsonCodec: JsonCodecPort,
    private val i18n: I18nPort,
    private val repoStrategies: List<SecurityRepoStrategy>,
    private val promptBuilder: SecurityAnalysisPromptBuilder,
    private val aiOutputParser: SecurityAiOutputParser,
    private val clock: ClockPort,
    private val fixAiBudgetSeconds: Long = 600L,
) {
    private val log = LoggerFactory.getLogger(SecurityFixService::class.java)

    fun generateFix(
        analysisId: Long,
        findingId: String,
    ): SecurityFixResult {
        val analysis = analysisRepo.findSecurityAnalysisById(analysisId) ?: throw IllegalArgumentException("Analysis $analysisId not found")
        val fj = analysis.findingsJson
        val findings =
            if (!fj.isNullOrBlank()) {
                try {
                    jsonCodec.decodeList(fj, SecurityFinding::class)
                } catch (
                    _: Exception,
                ) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        val finding = findings.firstOrNull { it.id == findingId } ?: throw IllegalArgumentException("Finding $findingId not found")
        val strategy = repoStrategies.firstOrNull { it.supports(analysis) } ?: throw NoSecurityAnalysisStrategyException("Unknown repo type")
        val ctx = strategy.loadFileContext(analysis, finding)
        val prompt = promptBuilder.buildFixPrompt(ctx.projectName, finding, ctx.fileContent, ctx.testContent, ctx.tree)
        val output = requestFixGeneration(prompt)
        val parsed = aiOutputParser.parseFixOutput(output)
        if (parsed.changes.isEmpty()) {
            log.warn("Fix output empty: {}", output.take(800).replace("\n", " | "))
            error(i18n.t("config.be_security_parse_hint"))
        }
        val result = strategy.createFixResult(analysis, finding, parsed)
        return if (result.prUrl != null && result.localChanges == null && ctx.allFiles.isNotEmpty()) {
            result.copy(
                localChanges =
                    parsed.changes.map { c ->
                        val orig = ctx.allFiles.firstOrNull { it.first == c.path }?.second ?: ""
                        SecurityLocalFileChange(path = c.path, code = computeUnifiedDiff(orig, c.code, c.path), changeType = FileChangeType.DIFF)
                    },
            )
        } else {
            result
        }
    }

    private fun requestFixGeneration(prompt: String): String {
        val deadline = AiCallDeadline(clock, fixAiBudgetSeconds)
        return try {
            copilotPort.executeWithStreaming(
                model = copilotPort.resolveModel(),
                prompt = prompt,
                timeoutOverride = deadline.requestTimeoutSeconds,
                isCancelled = deadline::expired,
                onPartialOutput = {},
            )
        } catch (e: InterruptedException) {
            Thread.interrupted()
            log.warn("[SecurityFix] AI budget of {}s exhausted: {}", fixAiBudgetSeconds, e.message)
            throw IllegalStateException(i18n.t("bug_analysis.fix_timeout"), e)
        }
    }
}
