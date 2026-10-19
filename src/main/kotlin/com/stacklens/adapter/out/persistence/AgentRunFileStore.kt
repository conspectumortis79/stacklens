package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.port.out.pipeline.AgentRunStorePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class AgentRunFileStore(
    private val config: StackLensConfigPort,
    private val jsonCodec: JsonCodecPort,
    private val filePort: FileListingReadWriteDeletePort,
    private val snapshotCodec: AgentPipelineRunSnapshotCodec,
) : AgentRunStorePort {
    private val log = LoggerFactory.getLogger(AgentRunFileStore::class.java)

    private val runsDir: File
        get() = File("${config.dataDir}/agent-pipeline/runs")

    override fun loadAll(): List<AgentPipelineRun> {
        val directory = runsDir
        if (!filePort.isDirectory(directory)) return emptyList()
        return filePort
            .listFilesIn(directory, "json")
            .sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .mapNotNull { file ->
                runCatching {
                    snapshotCodec.fromSnapshot(jsonCodec.decodeMap(filePort.readText(file)))
                }.onFailure { e ->
                    log.warn("Could not load pipeline run from {}: {}", file.name, e.message)
                }.getOrNull()
            }
    }

    override fun save(run: AgentPipelineRun) {
        try {
            val directory = runsDir
            filePort.writeFile(File(directory, "${run.id}.json"), jsonCodec.encode(snapshotCodec.toSnapshot(run)))
        } catch (e: Exception) {
            log.warn("[Pipeline {}] Could not persist run: {}", run.id, e.message)
        }
    }

    override fun delete(runId: Long) {
        try {
            filePort.deleteFile(File(runsDir, "$runId.json"))
        } catch (e: Exception) {
            log.warn("[Pipeline {}] Could not delete persisted run file: {}", runId, e.message)
        }
    }
}
