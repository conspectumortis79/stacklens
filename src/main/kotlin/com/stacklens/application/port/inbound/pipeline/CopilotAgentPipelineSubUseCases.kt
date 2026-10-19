package com.stacklens.application.port.inbound.pipeline
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.domain.pipeline.PullRequestData

interface CopilotAgentPipelineReadUseCase {
    fun get(id: Long): AgentPipelineRun?

    fun allRuns(): List<AgentPipelineRun>

    fun effectiveStatus(run: AgentPipelineRun): String

    fun getActiveAiProvider(): String?

    fun buildPullRequestData(id: Long): PullRequestData?
}

interface CopilotAgentPipelineWriteUseCase {
    fun start(
        rawText: String,
        localRepoConfigId: Long?,
        databaseConfigIds: List<Long> = emptyList(),
        mockupBase64: String? = null,
        mockupMimeType: String? = null,
        mockupImages: List<com.stacklens.application.port.model.pipeline.MockupImage> = emptyList(),
    ): Long

    fun startFromJira(
        ticketData: String,
        ticketKey: String,
        localRepoConfigId: Long?,
        databaseConfigIds: List<Long> = emptyList(),
        mockupBase64: String? = null,
        mockupMimeType: String? = null,
        mockupImages: List<com.stacklens.application.port.model.pipeline.MockupImage> = emptyList(),
    ): Long

    fun cancel(id: Long)

    fun delete(id: Long)

    fun deleteAll()

    fun chat(
        id: Long,
        userMessage: String,
        onChunk: (String) -> Unit,
    ): String
}
