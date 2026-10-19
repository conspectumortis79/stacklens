package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.analysis.GitlabRepoContentPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisCancellationService
import com.stacklens.application.service.codeanalysis.CodeAnalysisMrService
import com.stacklens.application.service.codeanalysis.MonorepoDetector

data class CodeAnalysisServiceDeps(
    val analysisRepo: CodeAnalysisPort,
    val codeRepoPort: GitlabRepoContentPort,
    val copilotCliService: CopilotPort,
    val i18n: I18nPort,
    val startupHealer: StartupRecovery,
    val monorepoDetector: MonorepoDetector,
    val mrService: CodeAnalysisMrService,
    val asyncExecutor: AsyncExecutorPort,
    val cancellationService: CodeAnalysisCancellationService,
)
