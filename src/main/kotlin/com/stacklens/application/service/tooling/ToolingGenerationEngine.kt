package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.ToolingConfigPort
import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.application.port.out.tooling.ToolingIconPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyDeletedException
import com.stacklens.domain.model.ConfluenceTool
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ApplicationService
class ToolingGenerationEngine(
    private val toolRepo: ConfluenceToolPort,
    private val copilotCliService: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val i18n: I18nPort,
    private val compiler: KotlinCompilerPort,
    private val promptBuilder: ToolingPromptBuilder,
    private val iconService: ToolingIconPort,
    private val toolJarStore: ToolJarStore,
    private val cancellations: ToolingCancellationRegistry,
    private val toolingConfig: ToolingConfigPort,
    private val clock: ClockPort,
    private val parallelTasks: ParallelTaskPort,
) : ToolingGenerationRunner {
    private val log = LoggerFactory.getLogger(ToolingGenerationEngine::class.java)

    override fun runGeneration(toolId: Long) {
        val tool = toolRepo.findConfluenceToolById(toolId) ?: return
        aiUsageContext.setContext("tooling", toolId)
        aiUsageContext.setModelOverride(copilotCliService.resolveModel())

        try {
            tool.status = AnalysisStatus.ANALYZING
            tool.errorMessage = null
            tool.completedAt = null
            toolRepo.saveConfluenceTool(tool)
            val pageContent = tool.pageContentText ?: ""

            val generatedCode = runGenerationStep(tool, pageContent, toolId)
            tool.generatedCode = generatedCode
            if (checkCancelled(toolId, tool)) return
            toolRepo.saveConfluenceTool(tool)

            val (iconFuture, iconPngFuture) = startParallelIconGeneration(tool.name, generatedCode)
            val (finalCode, jarPath) = runCompilationStep(tool, toolId, generatedCode)
            if (finalCode != generatedCode) {
                tool.generatedCode = finalCode
                toolRepo.saveConfluenceTool(tool)
            }
            tool.jarPath = jarPath
            if (checkCancelled(toolId, tool)) return

            tool.icon =
                resolveAndEmbedIcon(toolId, iconFuture, iconPngFuture, jarPath, tool.name)

            tool.status = AnalysisStatus.READY
            tool.currentStep = null
            tool.errorMessage = null
            tool.completedAt = clock.localDateTime()
            toolRepo.saveConfluenceTool(tool)
        } catch (e: EntityConcurrentlyDeletedException) {
            log.info("[Tooling {}] Generation stopped – entity was deleted externally or is outdated", toolId)
            cancellations.consumeCancellation(toolId)
        } catch (e: Exception) {
            log.error("[Tooling {}] Error: {}", toolId, e.message, e)
            markGenerationFailed(toolId, e)
        } finally {
            aiUsageContext.clearContext()
        }
    }

    private fun markGenerationFailed(
        toolId: Long,
        cause: Exception,
    ) {
        val fresh = toolRepo.findConfluenceToolById(toolId) ?: return
        fresh.status = AnalysisStatus.FAILED
        fresh.errorMessage = i18n.translateError(cause).take(2000)
        fresh.currentStep = null
        fresh.completedAt = clock.localDateTime()
        try {
            toolRepo.saveConfluenceTool(fresh)
        } catch (e: EntityConcurrentlyDeletedException) {
            log.info("[Tooling {}] Failure state skipped – entity was deleted concurrently", toolId)
            cancellations.consumeCancellation(toolId)
        }
    }

    private fun runGenerationStep(
        tool: ConfluenceTool,
        pageContent: String,
        toolId: Long,
    ): String {
        tool.status = AnalysisStatus.GENERATING
        step(tool, i18n.t("tooling.be_step_ai_generating"))
        val prompt = promptBuilder.buildCodeGenerationPrompt(tool.name, pageContent)
        tool.generationPrompt = prompt
        toolRepo.saveConfluenceTool(tool)
        return compiler.executeWithCodeRetry(copilotCliService.resolveModel(), prompt)
    }

    private fun startParallelIconGeneration(
        toolName: String,
        generatedCode: String,
    ): Pair<CompletableFuture<String?>, CompletableFuture<ByteArray?>> {
        val iconFuture =
            parallelTasks.submit {
                try {
                    determineIcon(toolName, generatedCode)
                } catch (e: Throwable) {
                    null
                }
            }
        val iconPngFuture =
            parallelTasks.submit {
                try {
                    generateIconPng(toolName)
                } catch (e: Throwable) {
                    null
                }
            }
        return iconFuture to iconPngFuture
    }

    private fun runCompilationStep(
        tool: ConfluenceTool,
        toolId: Long,
        generatedCode: String,
    ): Pair<String, String> {
        tool.status = AnalysisStatus.BUILDING
        step(tool, i18n.t("tooling.be_step_compiling"))
        return compiler.compileWithRetry(toolId, tool.name, generatedCode)
    }

    private fun resolveAndEmbedIcon(
        toolId: Long,
        iconFuture: CompletableFuture<String?>,
        iconPngFuture: CompletableFuture<ByteArray?>,
        jarPath: String,
        toolName: String,
    ): String? {
        try {
            val icon = iconFuture.get(35L, TimeUnit.SECONDS)
            val iconPng = iconPngFuture.get(toolingConfig.iconPngTimeoutSeconds, TimeUnit.SECONDS)
            icon?.let {
                embedIconInJar(jarPath, toolName, it, iconPng)
                iconPng?.let { png -> trySetMacOsFinderIcon(jarPath, png) }
            }
            return icon
        } catch (e: java.util.concurrent.TimeoutException) {
            iconFuture.cancel(true)
            iconPngFuture.cancel(true)
            log.warn("[Tooling {}] Icon processing failed (non-critical): icon AI call exceeded time limit", toolId)
            return null
        } catch (e: Throwable) {
            log.warn("[Tooling {}] Icon processing failed (non-critical): {}", toolId, e.toString())
            return null
        }
    }

    internal fun checkCancelled(
        toolId: Long,
        tool: ConfluenceTool,
    ): Boolean {
        if (!cancellations.consumeCancellation(toolId)) return false
        log.info("[Tooling {}] Generation aborted", toolId)
        tool.jarPath?.let(toolJarStore::deleteJar)
        toolRepo.deleteConfluenceTool(tool)
        return true
    }

    internal fun determineIcon(
        toolName: String,
        generatedCode: String,
    ): String? = iconService.determineIcon(toolName, generatedCode)

    internal fun generateIconPng(toolName: String): ByteArray? = iconService.generateIconPng(toolName)

    internal fun embedIconInJar(
        jarPath: String,
        toolName: String,
        icon: String,
        iconPng: ByteArray? = null,
    ) = iconService.embedIconInJar(jarPath, toolName, icon, iconPng)

    internal fun trySetMacOsFinderIcon(
        jarPath: String,
        iconPngBytes: ByteArray,
    ) = iconService.trySetMacOsFinderIcon(jarPath, iconPngBytes)

    internal fun step(
        tool: ConfluenceTool,
        message: String,
    ) {
        tool.currentStep = message
        toolRepo.saveConfluenceTool(tool)
    }
}
