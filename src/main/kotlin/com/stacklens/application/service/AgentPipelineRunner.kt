package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.StageOutputPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.pipeline.AgentStageFlow
import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.PipelineDeps
import com.stacklens.application.service.pipeline.lenaOutput
import com.stacklens.domain.pipeline.CancellationSignal
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class AgentPipelineRunner(
    private val deps: PipelineDeps,
    private val outputPersister: AgentOutputPersister,
    private val repoScanner: RepoScanner,
    private val ticketDetector: AgentTicketNumberDetector,
    private val onRunComplete: (AgentPipelineRun) -> Unit,
    private val injectedLoopbacks: List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback>,
) {
    private val copilot: CopilotStreamingPort get() = deps.copilot
    private val copilotConfig get() = deps.copilotConfig
    private val stackLensConfig get() = deps.stackLensConfig
    private val dbConfigRepo get() = deps.dbConfigRepo
    private val dbSchemaPort get() = deps.dbSchemaPort
    private val aiConfigRepo get() = deps.aiConfigRepo
    private val i18n: I18nPort get() = deps.i18n
    private val classpathResource get() = deps.classpathResource
    private val filePort get() = deps.filePort
    private val processPort get() = deps.processPort

    constructor(
        copilot: CopilotStreamingPort,
        copilotConfig: com.stacklens.application.port.out.appconfig.CopilotConfigPort,
        stackLensConfig: com.stacklens.application.port.out.appconfig.StackLensConfigPort,
        dbConfigRepo: com.stacklens.application.port.out.DatabaseConfigPort,
        dbSchemaPort: com.stacklens.application.port.out.database.DatabaseSchemaPort,
        aiConfigRepo: com.stacklens.application.port.out.AiConfigPort,
        outputPersister: AgentOutputPersister,
        i18n: I18nPort,
        filePort: com.stacklens.application.port.out.file.FileListingReadWriteDeletePort,
        classpathResource: com.stacklens.application.port.out.classpath.ClasspathResourcePort,
        processPort: com.stacklens.application.port.out.process.AgentProcessPort,
        platformPort: com.stacklens.application.port.out.platform.PlatformPort,
        clock: ClockPort,
        stageOutputWriter: StageOutputPort,
        repoScanner: RepoScanner,
        ivanStageRunner: IvanStageRunner,
        rexStageRunner: RexStageRunner,
        ticketDetector: AgentTicketNumberDetector,
        onRunComplete: (AgentPipelineRun) -> Unit,
        injectedLoopbacks: List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback>,
    ) : this(
        deps =
            PipelineDeps(
                copilot = copilot,
                copilotConfig = copilotConfig,
                stackLensConfig = stackLensConfig,
                dbConfigRepo = dbConfigRepo,
                dbSchemaPort = dbSchemaPort,
                aiConfigRepo = aiConfigRepo,
                i18n = i18n,
                filePort = filePort,
                classpathResource = classpathResource,
                processPort = processPort,
                platformPort = platformPort,
                clock = clock,
                stageOutputWriter = stageOutputWriter,
                ivanStageRunner = ivanStageRunner,
                rexStageRunner = rexStageRunner,
            ),
        outputPersister = outputPersister,
        repoScanner = repoScanner,
        ticketDetector = ticketDetector,
        onRunComplete = onRunComplete,
        injectedLoopbacks = injectedLoopbacks,
    )

    private val log = LoggerFactory.getLogger(AgentPipelineRunner::class.java)
    private val ivanStageRunner = deps.ivanStageRunner
    private val rexStageRunner = deps.rexStageRunner

    private val loopbacks: List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback> = injectedLoopbacks

    fun execute(run: AgentPipelineRun) {
        run.status = PipelineStatus.RUNNING
        val worker = Thread.currentThread()
        run.cancellation = CancellationSignal { worker.interrupt() }
        val model = copilot.resolveModel()

        try {
            val ctx =
                PipelineContext(
                    run = run,
                    model = model,
                    copilot = copilot,
                    copilotConfig = copilotConfig,
                    i18n = i18n,
                    aiConfigRepo = aiConfigRepo,
                    dbConfigRepo = dbConfigRepo,
                    dbSchemaPort = dbSchemaPort,
                    loadAgentPrompt = ::loadAgentPrompt,
                    writeStageOutput = ::writeStageOutput,
                    stream = ::stream,
                    repoRoot = null,
                )

            preProcess(ctx)
            if (run.cancelled) {
                markCancelled(run)
                return
            }
            runStages(ctx)
            if (run.cancelled) {
                markCancelled(run)
                return
            }

            run.rex.status = com.stacklens.application.port.model.pipeline.StageStatus.RUNNING
            run.currentStage = "rex"
            rexStageRunner.execute(
                run,
                model,
                ctx.outputs.valuesFor(AgentStageFlow.rexFeedbackStages),
            )
            if (run.cancelled) {
                run.errorMessage = i18n.t("ticket_implementation.be_pipeline_cancelled")
                run.rex.status = com.stacklens.application.port.model.pipeline.StageStatus.ERROR
                run.currentStage = null
                run.status = PipelineStatus.ERROR
                return
            }
            run.rex.status = com.stacklens.application.port.model.pipeline.StageStatus.DONE
            log.info("[Pipeline {}] Stage ⑪ Rex done", run.id)
            run.currentStage = null
            run.status = PipelineStatus.DONE
        } catch (e: InterruptedException) {
            log.info("[Pipeline {}] Cancelled", run.id)
            run.errorMessage = i18n.t("ticket_implementation.be_pipeline_cancelled")
            StageStatusHelper.markCurrentRunningAsFailed(run)
            run.currentStage = null
            run.status = PipelineStatus.ERROR
        } catch (e: Exception) {
            log.error("[Pipeline {}] Error in stage '{}': {}", run.id, run.currentStage, e.message, e)
            run.errorMessage = i18n.translateError(e).take(300)
            StageStatusHelper.markCurrentRunningAsFailed(run)
            run.currentStage = null
            run.status = PipelineStatus.ERROR
        } finally {
            run.cancellation = null
            try {
                autoPersistFilesIfNeeded(run)
            } catch (e: Exception) {
                log.warn("[Pipeline {}] Auto-persist in finally failed: {}", run.id, e.message, e)
            }
            onRunComplete(run)
        }
    }

    private fun preProcess(ctx: PipelineContext) {
        val run = ctx.run
        val model = ctx.model

        ctx.lenaOutput =
            if (run.jiraTicketData != null) {
                run.currentStage = "lena"
                run.lena.status = com.stacklens.application.port.model.pipeline.StageStatus.RUNNING
                log.info("[Pipeline {}] Stage ⓪ Lena start", run.id)
                val lenaPrompt = ctx.loadAgentPrompt("lena", mapOf("ticketData" to run.jiraTicketData))
                val output =
                    ctx.copilot
                        .executeWithStreamingPerLine(model, lenaPrompt, copilotConfig.pipelineTimeoutSeconds, onPartialOutput = { partial ->
                            run.lena.output = partial
                        })
                run.lena.output = output
                run.lena.status = com.stacklens.application.port.model.pipeline.StageStatus.DONE
                log.info("[Pipeline {}] Stage ⓪ Lena done ({} chars)", run.id, output.length)
                output
            } else {
                null
            }

        if (run.jiraTicketData == null) {
            val detectedTicket = ticketDetector.detect(model, run.rawText)
            run.detectedTicketKey = detectedTicket ?: ""
            if (detectedTicket != null) {
                run.featureSlug = detectedTicket
                log.info("[Pipeline {}] AI detected ticket: {}", run.id, detectedTicket)
            } else {
                log.info("[Pipeline {}] AI found no ticket key — using slug: {}", run.id, run.featureSlug)
            }
        }

        run.repoPath?.let { rp ->
            try {
                val info = GitBranchPreparer.prepare(rp, run.featureSlug, processPort, filePort, deps.clock)
                run.gitBranch = info.branch
                run.gitLog = info.log
                run.baseBranch = info.baseBranch
                run.baseSha = info.baseSha
                run.preExistingUntracked = info.preExistingUntracked
                run.gitRoot = info.gitRoot
                log.info("[Pipeline {}] git: {}", run.id, info.log.lineSequence().joinToString(" | "))
            } catch (e: InterruptedException) {
                run.gitLog = i18n.t("ticket_implementation.be_git_setup_cancelled")
                run.cancelled = true
            } catch (e: Exception) {
                log.warn("[Pipeline {}] git setup failed: {}", run.id, e.message)
                run.gitLog = "git setup failed: ${e.message}"
            }
        }

        ctx.repoStructure = run.repoPath?.let { repoScanner.scanRepoStructure(it) } ?: "(no repository selected)"
        val repoRoot =
            run.gitRoot.takeIf { it.isNotBlank() }?.let { Path.of(it).toFile() }
                ?: run.repoPath?.let {
                    try {
                        filePort.canonicalFile(Path.of(it).toFile())
                    } catch (_: Exception) {
                        null
                    }
                }
        ctx.repoRoot = repoRoot

        if (run.databaseConfigIds.isNotEmpty() && run.dbSchema.isBlank()) {
            val dbConfigs =
                runCatching { dbConfigRepo.findAllDatabaseConfigsByIds(run.databaseConfigIds) }
                    .getOrNull()
                    .orEmpty()
            val schemaBuilder = StringBuilder()
            for (dbConfig in dbConfigs) {
                log.info("[Pipeline {}] Fetching DB schema for config '{}' (id={})", run.id, dbConfig.name, dbConfig.id)
                val schema =
                    runCatching { dbSchemaPort.fetchSchemaSummary(dbConfig) }.getOrElse { e ->
                        log.warn("[Pipeline {}] DB schema fetch failed for config '{}': {}", run.id, dbConfig.name, e.message)
                        ""
                    }
                if (schema.isNotBlank()) {
                    if (schemaBuilder.isNotEmpty()) schemaBuilder.append("\n\n---\n\n")
                    schemaBuilder.append("## Database: ${dbConfig.name}\n\n").append(schema)
                }
            }
            if (dbConfigs.isEmpty()) {
                log.warn("[Pipeline {}] databaseConfigIds={} — none found — skipping schema fetch", run.id, run.databaseConfigIds)
            }
            run.dbSchema = schemaBuilder.toString()
            if (run.dbSchema.isNotBlank()) log.info("[Pipeline {}] DB schema fetched ({} chars)", run.id, run.dbSchema.length)
        }
    }

    private fun runStages(ctx: PipelineContext) {
        val loopbacksByStage: Map<String, List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback>> =
            loopbacks.groupBy { it.afterStage }

        for (stage in AgentStageFlow.preFlight) {
            if (ctx.run.cancelled) return
            stage.execute(ctx)
        }

        ivanStageRunner.execute(ctx.run) { writeStageOutput(ctx.run, "ivan", it) }

        for (stage in AgentStageFlow.main) {
            if (ctx.run.cancelled) return
            stage.execute(ctx)
            if (ctx.run.cancelled) return
            loopbacksByStage[stage.name]?.forEach { loopback ->
                try {
                    loopback.runIfRequested(ctx)
                } catch (e: Exception) {
                    log.warn(
                        "[Pipeline {}] Loopback {} failed: {} — continuing",
                        ctx.run.id,
                        loopback.name,
                        e.message,
                    )
                }
            }
        }
    }

    private fun markCancelled(run: AgentPipelineRun) {
        run.errorMessage = i18n.t("ticket_implementation.be_pipeline_cancelled")
        run.currentStage = null
        run.status = PipelineStatus.ERROR
    }

    private fun autoPersistFilesIfNeeded(run: AgentPipelineRun) {
        if (run.filesAutoWritten) return
        if (providerWritesFilesNatively()) return
        val repoPath = run.repoPath?.takeIf { it.isNotBlank() } ?: return
        val repoRoot =
            try {
                filePort.canonicalFile(Path.of(repoPath).toFile())
            } catch (e: Exception) {
                log.warn("[Pipeline {}] Auto-persist skipped — invalid repo path '{}': {}", run.id, repoPath, e.message)
                return
            }
        if (!filePort.exists(repoRoot) || !filePort.isDirectory(repoRoot)) {
            log.warn("[Pipeline {}] Auto-persist skipped — repo dir missing: {}", run.id, repoRoot.path)
            return
        }
        if (!filePort.canWrite(repoRoot)) {
            log.warn("[Pipeline {}] Auto-persist skipped — repo dir not writable: {}", run.id, repoRoot.path)
            run.autoWriteErrors = listOf("Repository not writable: ${repoRoot.path}")
            return
        }
        try {
            val result = outputPersister.persist(run, repoRoot)
            run.autoWrittenFiles = result.written
            run.autoDeletedFiles = result.deleted
            run.autoWriteErrors = result.errors
            run.filesAutoWritten = true
        } catch (e: Exception) {
            log.warn("[Pipeline {}] Auto-persist failed: {}", run.id, e.message, e)
            run.autoWriteErrors = listOf("Auto-persist failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun providerWritesFilesNatively(): Boolean = copilot.writesFilesNatively

    private fun stream(
        model: String,
        prompt: String,
        repoRoot: File?,
        onChunk: (String) -> Unit,
    ): String =
        if (repoRoot != null) {
            copilot.executeWithStreamingPerLine(model, prompt, copilotConfig.pipelineTimeoutSeconds, repoRoot, null, onChunk)
        } else {
            copilot.executeWithStreamingPerLine(model, prompt, copilotConfig.pipelineTimeoutSeconds, null, null, onChunk)
        }

    private fun loadAgentPrompt(
        agent: String,
        vars: Map<String, String>,
    ): String = AgentPromptLoader.load(stackLensConfig.dataDir, agent, vars, classpathResource, filePort, filePort)

    private fun writeStageOutput(
        run: AgentPipelineRun,
        stageName: String,
        content: String,
    ) = deps.stageOutputWriter.write(run, stageName, content)
}
