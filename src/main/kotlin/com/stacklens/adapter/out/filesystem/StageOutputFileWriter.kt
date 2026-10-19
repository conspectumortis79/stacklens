package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.pipeline.StageOutputPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class StageOutputFileWriter(
    private val filePort: FileReadWritePort,
) : StageOutputPort {
    private val log = LoggerFactory.getLogger(StageOutputFileWriter::class.java)

    override fun write(
        run: AgentPipelineRun,
        stageName: String,
        content: String,
    ) {
        val directory = stageOutputDir(run) ?: return
        if (!filePort.isDirectory(File(run.repoPath!!))) return
        try {
            filePort.writeFile(File(directory, "$stageName.md"), content)
            log.debug("[Pipeline {}] Wrote stage output: .stacklens/{}/{}.md", run.id, run.featureSlug, stageName)
        } catch (e: Exception) {
            log.warn("[Pipeline {}] Could not write stage output for {}: {}", run.id, stageName, e.message)
        }
    }

    internal fun stageOutputDir(run: AgentPipelineRun): File? {
        val repoPath = run.repoPath ?: return null
        return File(repoPath, ".stacklens/${run.featureSlug}")
    }
}
