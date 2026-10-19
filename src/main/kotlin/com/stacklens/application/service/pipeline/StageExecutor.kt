package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.StageStatus
import org.slf4j.LoggerFactory

object StageExecutor {
    private val log = LoggerFactory.getLogger(StageExecutor::class.java)

    fun execute(
        stage: PipelineStage,
        ctx: PipelineContext,
        slot: (AgentPipelineRun) -> AgentStage,
        promptInputs: Map<String, String>,
        send: (prompt: String, onChunk: (String) -> Unit) -> String,
        transform: (String) -> String = { it },
    ): String {
        val stageName = stage.name
        val run = ctx.run
        run.currentStage = stageName
        val target = slot(run)
        target.status = StageStatus.RUNNING
        log.info("[Pipeline {}] Stage {} start", run.id, stageName)

        val prompt = ctx.loadAgentPrompt(stageName, promptInputs)
        val output = transform(send(prompt) { partial -> target.output = partial })
        target.output = output
        target.status = StageStatus.DONE
        ctx.writeStageOutput(run, stageName, output)
        log.info("[Pipeline {}] Stage {} done ({} chars)", run.id, stageName, output.length)
        return output
    }

    fun viaCopilot(ctx: PipelineContext): (String, (String) -> Unit) -> String =
        { prompt, onChunk ->
            ctx.copilot.executeWithStreamingPerLine(
                ctx.model,
                prompt,
                ctx.copilotConfig.pipelineTimeoutSeconds,
                onPartialOutput = onChunk,
            )
        }

    fun viaStream(ctx: PipelineContext): (String, (String) -> Unit) -> String = { prompt, onChunk -> ctx.stream(ctx.model, prompt, ctx.repoRoot, onChunk) }
}
