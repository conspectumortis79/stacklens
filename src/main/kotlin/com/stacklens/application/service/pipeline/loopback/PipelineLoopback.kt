package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext

interface PipelineLoopback {
    val name: String

    val afterStage: String

    fun runIfRequested(ctx: PipelineContext): Boolean
}
