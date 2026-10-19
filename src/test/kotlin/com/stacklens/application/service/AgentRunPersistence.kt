package com.stacklens.application.service

import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.adapter.out.persistence.AgentRunFileStore
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import java.util.concurrent.ConcurrentHashMap

class AgentRunPersistence(
    dataDir: String,
    private val runs: ConcurrentHashMap<Long, AgentPipelineRun>,
    jsonCodec: JsonCodecPort,
    maxRetainedRuns: Int = 200,
    filePort: FileListingReadWriteDeletePort,
    snapshotCodec: AgentPipelineRunSnapshotCodec,
) {
    constructor(
        dataDir: String,
        registry: CopilotPipelineRegistry,
        jsonCodec: JsonCodecPort,
        maxRetainedRuns: Int = 200,
        filePort: FileListingReadWriteDeletePort,
        snapshotCodec: AgentPipelineRunSnapshotCodec,
    ) : this(dataDir, registry.rawMap(), jsonCodec, maxRetainedRuns, filePort, snapshotCodec) {
        this.seedRunId = { maxId -> registry.seed(maxId) }
    }

    private var seedRunId: ((Long) -> Unit)? = null

    private val archive =
        AgentRunArchiveService(
            store =
                AgentRunFileStore(
                    config =
                        object : StackLensConfigPort {
                            override val dataDir: String = dataDir
                            override val dockerComposeUpTimeoutSeconds: Long = 0L
                            override val dockerComposeQueryTimeoutSeconds: Long = 0L
                        },
                    jsonCodec = jsonCodec,
                    filePort = filePort,
                    snapshotCodec = snapshotCodec,
                ),
            runs = runs,
            maxRetainedRuns = maxRetainedRuns,
            seedRunId = { maxId -> seedRunId?.invoke(maxId) },
        )

    fun loadFromDisk() = archive.loadFromDisk()

    fun persist(run: AgentPipelineRun) = archive.persist(run)

    fun evictOld() = archive.evictOld()

    fun deleteFile(runId: Long) = archive.deleteFile(runId)
}
