package com.stacklens.application.service.pipeline

object AgentStageFlow {
    val preFlight: List<PipelineStage> =
        listOf(
            NoraStage(),
            ColeStage(),
        )

    val main: List<PipelineStage> =
        listOf(
            MaxStage(),
            FinnStage(),
            FayeStage(),
            VeraStage(),
            TessStage(),
            SydStage(),
            QuinnStage(),
            SageStage(),
        )

    val rexFeedbackStages: List<String> =
        (preFlight + main)
            .mapNotNull { stage -> stage.rexFeedbackRank?.let { it to stage.name } }
            .sortedBy { (rank, _) -> rank }
            .map { (_, name) -> name }
}
