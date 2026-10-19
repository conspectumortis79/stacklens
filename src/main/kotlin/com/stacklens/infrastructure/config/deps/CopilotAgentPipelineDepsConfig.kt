package com.stacklens.infrastructure.config.deps

import com.stacklens.application.port.inbound.rex.RexTestStages
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.service.AgentOutputPersister
import com.stacklens.application.service.AgentPromptLoader
import com.stacklens.application.service.AgentTicketNumberDetector
import com.stacklens.application.service.CopilotAgentPipelineDeps
import com.stacklens.application.service.IvanStageRunner
import com.stacklens.application.service.RepoScanner
import com.stacklens.application.service.RexAiFixService
import com.stacklens.application.service.RexCoverageStep
import com.stacklens.application.service.RexStageRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CopilotAgentPipelineDepsConfig {
    @Bean
    fun copilotAgentPipelineDeps(
        copilot: com.stacklens.application.port.out.ai.CopilotStreamingPort,
        localRepoConfigRepo: LocalRepoConfigPort,
        dbConfigRepo: DatabaseConfigPort,
        dbScanPort: com.stacklens.application.port.out.database.DatabaseScanPort,
        i18n: I18nPort,
        aiConfigRepo: AiConfigPort,
        outputPersister: AgentOutputPersister,
        jsonCodec: JsonCodecPort,
        copilotConfig: CopilotConfigPort,
        stackLensConfig: StackLensConfigPort,
        processPort: AgentProcessPort,
        platformPort: com.stacklens.application.port.out.platform.PlatformPort,
        filePort: com.stacklens.application.port.out.file.FileSystemPort,
        classpathResource: com.stacklens.application.port.out.classpath.ClasspathResourcePort,
        pullRequestDiffPort: com.stacklens.application.port.out.PullRequestDiffPort,
        repoScanner: RepoScanner,
        aiUsageContext: AiUsageContextPort,
        ivanStageRunner: IvanStageRunner,
        rexStageRunner: RexStageRunner,
        ticketDetector: AgentTicketNumberDetector,
    ): CopilotAgentPipelineDeps =
        CopilotAgentPipelineDeps(
            copilot = copilot,
            localRepoConfigRepo = localRepoConfigRepo,
            dbConfigRepo = dbConfigRepo,
            dbScanPort = dbScanPort,
            i18n = i18n,
            aiConfigRepo = aiConfigRepo,
            outputPersister = outputPersister,
            jsonCodec = jsonCodec,
            copilotConfig = copilotConfig,
            stackLensConfig = stackLensConfig,
            processPort = processPort,
            platformPort = platformPort,
            filePort = filePort,
            classpathResource = classpathResource,
            pullRequestDiffPort = pullRequestDiffPort,
            repoScanner = repoScanner,
            aiUsageContext = aiUsageContext,
            ivanStageRunner = ivanStageRunner,
            rexStageRunner = rexStageRunner,
            ticketDetector = ticketDetector,
        )

    @Bean
    fun ivanStageRunner(
        processPort: AgentProcessPort,
        i18n: I18nPort,
        filePort: com.stacklens.application.port.out.file.FileSystemPort,
        outputFilter: com.stacklens.application.port.out.textfilter.OutputFilterPort,
    ): IvanStageRunner = IvanStageRunner(processPort = processPort, i18n = i18n, fileAttributes = filePort, outputFilter = outputFilter)

    @Bean
    fun rexAiFixService(
        copilot: com.stacklens.application.port.out.ai.CopilotStreamingPort,
        copilotConfig: CopilotConfigPort,
        processPort: AgentProcessPort,
        filePort: com.stacklens.application.port.out.file.FileSystemPort,
        stackLensConfig: StackLensConfigPort,
        classpathResource: com.stacklens.application.port.out.classpath.ClasspathResourcePort,
    ): RexAiFixService =
        RexAiFixService(
            copilot = copilot,
            copilotConfig = copilotConfig,
            processPort = processPort,
            filePort = filePort,
            loadPrompt = { agent, vars -> AgentPromptLoader.load(stackLensConfig.dataDir, agent, vars, classpathResource, filePort, filePort) },
            fileAttributes = filePort,
        )

    @Bean
    fun rexCoverageStep(
        processPort: AgentProcessPort,
        i18n: I18nPort,
        rexAiFixService: RexAiFixService,
    ): RexCoverageStep =
        RexCoverageStep(
            processPort = processPort,
            i18n = i18n,
            aiFixer = rexAiFixService,
        )

    @Bean
    fun rexStageRunner(
        processPort: AgentProcessPort,
        i18n: I18nPort,
        platformPort: com.stacklens.application.port.out.platform.PlatformPort,
        rexAiFixService: RexAiFixService,
        rexCoverageStep: RexCoverageStep,
        filePort: com.stacklens.application.port.out.file.FileSystemPort,
        outputFilter: com.stacklens.application.port.out.textfilter.OutputFilterPort,
    ): RexStageRunner =
        RexStageRunner(
            processPort = processPort,
            i18n = i18n,
            platformPort = platformPort,
            aiFixer = rexAiFixService,
            coverageStep = rexCoverageStep,
            fileAttributes = filePort,
            outputFilter = outputFilter,
            testStages = RexTestStages.DEFAULT,
        )
}
