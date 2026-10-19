package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import java.io.File

sealed interface PipelineStage {
    val name: String

    val rexFeedbackRank: Int?

    fun execute(ctx: PipelineContext)
}

typealias StageStreamFn = (model: String, prompt: String, repoRoot: File?, onChunk: (String) -> Unit) -> String

class PipelineContext(
    val run: AgentPipelineRun,
    val model: String,
    val copilot: CopilotStreamingPort,
    val copilotConfig: CopilotConfigPort,
    val i18n: I18nPort,
    val aiConfigRepo: AiConfigPort,
    val dbConfigRepo: DatabaseConfigPort,
    val dbSchemaPort: DatabaseSchemaPort,
    val loadAgentPrompt: (String, Map<String, String>) -> String,
    val writeStageOutput: (AgentPipelineRun, String, String) -> Unit,
    val stream: StageStreamFn,
    var repoRoot: File?,
) {
    val outputs: StageOutputs = StageOutputs()

    var repoStructure: String = "(no repository selected)"
}

var PipelineContext.lenaOutput: String?
    get() = outputs.optional("lena")
    set(value) {
        outputs.record("lena", value)
    }
