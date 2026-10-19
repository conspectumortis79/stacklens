package com.stacklens.application.service

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineWriteError
import com.stacklens.application.port.inbound.pipeline.PipelineWriteFile
import com.stacklens.application.port.inbound.pipeline.PipelineWriteOutcome
import com.stacklens.application.port.inbound.pipeline.PipelineWritePreview
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.pipeline.PullRequestData
import com.stacklens.domain.text.AgentTextUtils
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
@ManuallyWired
class CopilotAgentPipelineService
    constructor(
        private val deps: CopilotAgentPipelineDeps,
        internal val registry: CopilotPipelineRegistry,
        private val clock: ClockPort,
        private val archiveService: AgentRunArchiveService,
        private val pipelineRunner: AgentPipelineRunner,
        private val chatService: AgentPipelineChatService,
        private val pullRequestService: AgentPipelinePullRequestService,
    ) : CopilotAgentPipelineUseCase,
        StaleWorkHealer {
        private val log = LoggerFactory.getLogger(CopilotAgentPipelineService::class.java)

        private val localRepoConfigRepo get() = deps.localRepoConfigRepo
        private val aiConfigRepo get() = deps.aiConfigRepo
        private val outputPersister get() = deps.outputPersister
        private val filePort get() = deps.filePort
        private val repoScanner get() = deps.repoScanner
        private val aiUsageContext get() = deps.aiUsageContext

        override fun heal() {
            loadRunsFromDisk()
        }

        fun loadRunsFromDisk() {
            archiveService.loadFromDisk()
        }

        override fun delete(id: Long) {
            val run = registry.get(id) ?: return
            if (run.status == PipelineStatus.RUNNING || run.status == PipelineStatus.PENDING) return
            registry.remove(id)
            archiveService.deleteFile(id)
        }

        override fun deleteAll() {
            registry
                .all()
                .filter { it.status == PipelineStatus.DONE || it.status == PipelineStatus.ERROR }
                .forEach { run ->
                    registry.remove(run.id)
                    archiveService.deleteFile(run.id)
                }
        }

        override fun start(
            rawText: String,
            localRepoConfigId: Long?,
            databaseConfigIds: List<Long>,
            mockupBase64: String?,
            mockupMimeType: String?,
            mockupImages: List<MockupImage>,
        ): Long {
            val id = registry.nextRunId()
            val repoPath =
                localRepoConfigId?.let {
                    localRepoConfigRepo.findLocalRepoConfigById(it)?.path
                }
            val effectiveMockupImages =
                mockupImages.ifEmpty {
                    val b64 = mockupBase64?.trim()
                    if (b64 != null && b64.length > 0) {
                        listOf(MockupImage(b64, mockupMimeType ?: ""))
                    } else {
                        emptyList()
                    }
                }
            val run =
                AgentPipelineRun(
                    id = id,
                    createdAt = clock.epochMillis(),
                    rawText = rawText,
                    repoPath = repoPath,
                    featureSlug = AgentTextUtils.generateFeatureSlug(rawText),
                    databaseConfigIds = databaseConfigIds,
                    mockupBase64 = mockupBase64,
                    mockupMimeType = mockupMimeType,
                    mockupImages = effectiveMockupImages,
                )
            registry.put(id, run)
            registry.execute { executeRun(run) }
            return id
        }

        override fun startFromJira(
            ticketData: String,
            ticketKey: String,
            localRepoConfigId: Long?,
            databaseConfigIds: List<Long>,
            mockupBase64: String?,
            mockupMimeType: String?,
            mockupImages: List<MockupImage>,
        ): Long {
            val id = registry.nextRunId()
            val repoPath = localRepoConfigId?.let { localRepoConfigRepo.findLocalRepoConfigById(it)?.path }
            val effectiveMockupImages =
                mockupImages.ifEmpty {
                    val b64 = mockupBase64?.trim()
                    if (b64 != null && b64.length > 0) {
                        listOf(MockupImage(b64, mockupMimeType ?: ""))
                    } else {
                        emptyList()
                    }
                }
            val run =
                AgentPipelineRun(
                    id = id,
                    createdAt = clock.epochMillis(),
                    rawText = ticketKey,
                    repoPath = repoPath,
                    featureSlug = ticketKey.ifBlank { AgentTextUtils.generateFeatureSlug(ticketData) },
                    jiraTicketData = ticketData,
                    databaseConfigIds = databaseConfigIds,
                    mockupBase64 = mockupBase64,
                    mockupMimeType = mockupMimeType,
                    mockupImages = effectiveMockupImages,
                )
            run.detectedTicketKey = ticketKey.ifBlank { null }
            registry.put(id, run)
            registry.execute { executeRun(run) }
            return id
        }

        override fun get(id: Long): AgentPipelineRun? = registry.get(id)

        override fun allRuns(): List<AgentPipelineRun> = registry.all().sortedByDescending { it.id }

        override fun effectiveStatus(run: AgentPipelineRun): String {
            val stale = run.createdAt < clock.epochMillis() - STALE_THRESHOLD_MILLIS
            return if ((run.status == PipelineStatus.RUNNING || run.status == PipelineStatus.PENDING) && stale) {
                PipelineStatus.ERROR.name
            } else {
                run.status.name
            }
        }

        override fun cancel(id: Long) {
            val run = registry.get(id) ?: return
            synchronized(run) {
                if (run.status == PipelineStatus.RUNNING || run.status == PipelineStatus.PENDING) {
                    run.cancelled = true
                    run.cancellation?.cancelRequest()
                    log.info("[Pipeline {}] Cancel requested", id)
                }
            }
        }

        override fun chat(
            id: Long,
            userMessage: String,
            onChunk: (String) -> Unit,
        ): String = chatService.chat(id, userMessage, onChunk)

        private fun executeRun(run: AgentPipelineRun) {
            val pipelineRunId = run.id
            aiUsageContext.withContext("ticket_implementation", pipelineRunId) {
                pipelineRunner.execute(run)
            }
        }

        internal fun shellRead(
            vararg cmd: String,
            timeoutSeconds: Long = 10,
        ): String? = repoScanner.shellRead(*cmd, timeoutSeconds = timeoutSeconds)

        internal fun scanRepoStructure(repoPath: String): String = repoScanner.scanRepoStructure(repoPath)

        internal fun scanRepoStructureDirect(
            root: File,
            repoPath: String,
        ): String = repoScanner.scanRepoStructureDirect(root, repoPath)

        internal fun scanRepoStructureViaShell(repoPath: String): String = repoScanner.scanRepoStructureViaShell(repoPath)

        internal fun sanitiseDetectedTicketKey(raw: String): String? = AgentTextUtils.sanitiseDetectedTicketKey(raw)

        override fun getActiveAiProvider(): String? =
            aiConfigRepo
                .findAiConfig()
                ?.aiProvider
                ?.lowercase()
                ?.trim()

        override fun buildPullRequestData(id: Long): PullRequestData? = pullRequestService.buildPullRequestData(id)

        override fun previewWriteFiles(id: Long): PipelineWritePreview? {
            val run = registry.get(id) ?: return null
            if (run.finn.output.isBlank()) {
                return PipelineWritePreview(repoPath = run.repoPath, files = emptyList(), errorKey = "pipeline.be_finn_no_output")
            }
            return PipelineWritePreview(
                repoPath = run.repoPath,
                files = parseAllStageOutputs(run).map { PipelineWriteFile(path = it.path, size = it.content.length) },
            )
        }

        override fun writeFiles(
            id: Long,
            nativeWriter: Boolean?,
        ): PipelineWriteOutcome? {
            val run = registry.get(id) ?: return null
            val repoPath =
                run.repoPath
                    ?: return PipelineWriteOutcome(errorKey = "pipeline.be_no_repo_linked")
            if (run.finn.output.isBlank()) {
                return PipelineWriteOutcome(errorKey = "pipeline.be_finn_no_output")
            }

            val repoRoot =
                try {
                    filePort.canonicalFile(File(repoPath))
                } catch (e: Exception) {
                    return PipelineWriteOutcome(errorKey = "config.be_invalid_repo_path_msg", errorArg = safeMessage(e))
                }
            if (!filePort.exists(repoRoot)) {
                return PipelineWriteOutcome(errorKey = "config.be_repo_dir_not_exists", errorArg = repoPath)
            }
            if (!filePort.canWrite(repoRoot)) {
                return PipelineWriteOutcome(
                    errorKey = "config.be_no_write_permissions",
                    errorArg = repoPath,
                    showWritePermissionHint = true,
                )
            }

            val isNativeWriter = nativeWriter ?: providerWritesFilesDuringPipeline()
            if (run.rex.status == StageStatus.DONE && isNativeWriter) {
                return PipelineWriteOutcome(infoKey = "config.be_rex_already_written")
            }
            if (run.filesAutoWritten) {
                return PipelineWriteOutcome(
                    written = run.autoWrittenFiles,
                    deleted = run.autoDeletedFiles,
                    errors = run.autoWriteErrors.map { PipelineWriteError(message = it) },
                    infoKey = "config.be_rex_already_written",
                )
            }

            val result = outputPersister.persist(run, repoRoot)
            return PipelineWriteOutcome(
                written = result.written,
                deleted = result.deleted.takeIf { it.isNotEmpty() },
                errors =
                    result.failures.map {
                        PipelineWriteError(path = it.path, code = it.code, detail = it.detail)
                    },
            )
        }

        internal fun parseAllStageOutputs(run: AgentPipelineRun): List<AgentTextUtils.ParsedFile> = outputPersister.parseAllFiles(run)

        internal fun providerWritesFilesDuringPipeline(): Boolean = AiProvider.isCopilotFamily(getActiveAiProvider())

        internal fun safeMessage(e: Exception): String = e.message ?: ""

        private companion object {
            const val STALE_THRESHOLD_MILLIS: Long = 60 * 60 * 1_000L
        }
    }
