package com.stacklens.application.port.model.pipeline

import com.stacklens.domain.pipeline.CancellationSignal

enum class PipelineStatus { PENDING, RUNNING, DONE, ERROR }

enum class StageStatus { PENDING, RUNNING, DONE, ERROR }

class AgentStage(
    val name: String,
) {
    @Volatile var status: StageStatus = StageStatus.PENDING

    @Volatile var output: String = ""
}

data class MockupImage(
    val base64: String,
    val mimeType: String,
)

class AgentPipelineRun(
    val id: Long,
    val rawText: String,
    val repoPath: String?,
    @Volatile var featureSlug: String,
    val jiraTicketData: String? = null,
    val createdAt: Long,
    val databaseConfigIds: List<Long> = emptyList(),
    val mockupBase64: String? = null,
    val mockupMimeType: String? = null,
    val mockupImages: List<MockupImage> = emptyList(),
) {
    @Volatile var status: PipelineStatus = PipelineStatus.PENDING

    @Volatile var currentStage: String? = null

    @Volatile var errorMessage: String? = null

    @Volatile var cancelled: Boolean = false

    @Volatile var detectedTicketKey: String? = null

    @Volatile var gitBranch: String = ""

    @Volatile var gitLog: String = ""

    @Volatile var gitRoot: String = ""

    @Volatile var baseBranch: String = ""

    @Volatile var baseSha: String = ""

    @Volatile var preExistingUntracked: List<String> = emptyList()

    @Volatile var dbSchema: String = ""

    @Volatile var filesAutoWritten: Boolean = false

    @Volatile var autoWrittenFiles: List<String> = emptyList()

    @Volatile var autoDeletedFiles: List<String> = emptyList()

    @Volatile var autoWriteErrors: List<String> = emptyList()

    @Volatile var cancellation: CancellationSignal? = null

    private val allStages: List<AgentStage> =
        listOf(
            AgentStage("lena"),
            AgentStage("nora"),
            AgentStage("cole"),
            AgentStage("ivan"),
            AgentStage("max"),
            AgentStage("finn"),
            AgentStage("faye"),
            AgentStage("vera"),
            AgentStage("tess"),
            AgentStage("syd"),
            AgentStage("quinn"),
            AgentStage("sage"),
            AgentStage("rex"),
        )

    val stagesByName: Map<String, AgentStage> = allStages.associateBy { it.name }

    fun stageByName(name: String?): AgentStage? = name?.let { stagesByName[it] }

    val lena: AgentStage get() = stagesByName.getValue("lena")
    val nora: AgentStage get() = stagesByName.getValue("nora")
    val cole: AgentStage get() = stagesByName.getValue("cole")
    val ivan: AgentStage get() = stagesByName.getValue("ivan")
    val max: AgentStage get() = stagesByName.getValue("max")
    val finn: AgentStage get() = stagesByName.getValue("finn")
    val faye: AgentStage get() = stagesByName.getValue("faye")
    val vera: AgentStage get() = stagesByName.getValue("vera")
    val tess: AgentStage get() = stagesByName.getValue("tess")
    val syd: AgentStage get() = stagesByName.getValue("syd")
    val quinn: AgentStage get() = stagesByName.getValue("quinn")
    val sage: AgentStage get() = stagesByName.getValue("sage")
    val rex: AgentStage get() = stagesByName.getValue("rex")

    val stages: List<AgentStage> get() =
        if (jiraTicketData != null) allStages else allStages.filter { it.name != "lena" }
}
