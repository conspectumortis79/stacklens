package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.pipeline.PipelineAnalysisExecutor
import com.stacklens.application.service.pipeline.PipelineAnalysisMrService

internal fun pipelineAnalysisService(
    analysisRepo: PipelineAnalysisPort,
    filesPort: PipelineFilesPort,
    copilotCliService: CopilotPort,
    aiUsageContext: AiUsageContextPort,
    i18n: I18nPort,
    aiService: PipelineAnalysisAiService,
    startupHealer: StartupRecovery,
    asyncExecutor: AsyncExecutorPort,
    mrService: PipelineAnalysisMrService,
    clock: ClockPort,
): PipelineAnalysisService =
    PipelineAnalysisService(
        analysisRepo = analysisRepo,
        filesPort = filesPort,
        copilotCliService = copilotCliService,
        aiUsageContext = aiUsageContext,
        i18n = i18n,
        aiService = aiService,
        startupHealer = startupHealer,
        asyncExecutor = asyncExecutor,
        mrService = mrService,
        clock = clock,
        executor =
            PipelineAnalysisExecutor(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilotCliService,
                aiUsageContext = aiUsageContext,
                i18n = i18n,
                aiService = aiService,
                clock = clock,
            ),
    )
